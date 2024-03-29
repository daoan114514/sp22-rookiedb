package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LockContext wraps around LockManager to provide the hierarchical structure
 * of multigranularity locking. Calls to acquire/release/etc. locks should
 * be mostly done through a LockContext, which provides access to locking
 * methods at a certain point in the hierarchy (database, table X, etc.)
 */
public class LockContext {
    // You should not remove any of these fields. You may add additional
    // fields/methods as you see fit.

    // The underlying lock manager.
    protected final LockManager lockman;

    // The parent LockContext object, or null if this LockContext is at the top of the hierarchy.
    protected final LockContext parent;

    // The name of the resource this LockContext represents.
    protected ResourceName name;

    // Whether this LockContext is readonly. If a LockContext is readonly, acquire/release/promote/escalate should
    // throw an UnsupportedOperationException.
    protected boolean readonly;

    // A mapping between transaction numbers, and the number of locks on descendents of this LockContext
    // that the transaction holds.
    protected final Map<Long, Integer> numChildLocks;

    // You should not modify or use this directly.
    protected final Map<String, LockContext> children;

    // Whether or not any new child LockContexts should be marked readonly.
    protected boolean childLocksDisabled;

    public LockContext(LockManager lockman, LockContext parent, String name) {
        this(lockman, parent, name, false);
    }

    protected LockContext(LockManager lockman, LockContext parent, String name,
                          boolean readonly) {
        this.lockman = lockman;
        this.parent = parent;
        if (parent == null) {
            this.name = new ResourceName(name);
        } else {
            this.name = new ResourceName(parent.getResourceName(), name);
        }
        this.readonly = readonly;
        this.numChildLocks = new ConcurrentHashMap<>();
        this.children = new ConcurrentHashMap<>();
        this.childLocksDisabled = readonly;
    }

    /**
     * Gets a lock context corresponding to `name` from a lock manager.
     */
    public static LockContext fromResourceName(LockManager lockman, ResourceName name) {
        Iterator<String> names = name.getNames().iterator();
        LockContext ctx;
        String n1 = names.next();
        ctx = lockman.context(n1);
        while (names.hasNext()) {
            String n = names.next();
            ctx = ctx.childContext(n);
        }
        return ctx;
    }

    /**
     * Get the name of the resource that this lock context pertains to.
     */
    public ResourceName getResourceName() {
        return name;
    }

    /**
     * Acquire a `lockType` lock, for transaction `transaction`.
     * <p>
     * Note: you must make any necessary updates to numChildLocks, or else calls
     * to LockContext#getNumChildren will not work properly.
     *
     * @throws InvalidLockException          if the request is invalid
     * @throws DuplicateLockRequestException if a lock is already held by the
     *                                       transaction.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void acquire(TransactionContext transaction, LockType lockType)
            throws InvalidLockException, DuplicateLockRequestException {
        // TODO(proj4_part2): implement

        if (this.parentContext() != null && !LockType.canBeParentLock(this.parent.getEffectiveLockType(transaction), lockType)) {
            throw new InvalidLockException("");
        }
        if (this.readonly) {
            throw new UnsupportedOperationException("");
        }
        this.lockman.acquire(transaction, this.getResourceName(), lockType);
        if (this.parentContext() != null) {
            this.parentContext().updateAncestorNumChildLocks(transaction, 1);
        }
    }

    /**
     * Release `transaction`'s lock on `name`.
     * <p>
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#getNumChildren will not work properly.
     *
     * @throws NoLockHeldException           if no lock on `name` is held by `transaction`
     * @throws InvalidLockException          if the lock cannot be released because
     *                                       doing so would violate multigranularity locking constraints
     * @throws UnsupportedOperationException if context is readonly
     */
    public void release(TransactionContext transaction)
            throws NoLockHeldException, InvalidLockException {
        // TODO(proj4_part2): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("");
        }
        if (this.getNumChildren(transaction) != 0) {
            throw new InvalidLockException("");
        }
        this.lockman.release(transaction, this.getResourceName());
        if (this.parentContext() != null) {
            this.parentContext().updateAncestorNumChildLocks(transaction, -1);
        }
    }

    /**
     * Promote `transaction`'s lock to `newLockType`. For promotion to SIX from
     * IS/IX, all S and IS locks on descendants must be simultaneously
     * released. The helper function sisDescendants may be helpful here.
     * <p>
     * Note: you *must* make any necessary updates to numChildLocks, or else
     * calls to LockContext#getNumChildren will not work properly.
     *
     * @throws DuplicateLockRequestException if `transaction` already has a
     *                                       `newLockType` lock
     * @throws NoLockHeldException           if `transaction` has no lock
     * @throws InvalidLockException          if the requested lock type is not a
     *                                       promotion or promoting would cause the lock manager to enter an invalid
     *                                       state (e.g. IS(parent), X(child)). A promotion from lock type A to lock
     *                                       type B is valid if B is substitutable for A and B is not equal to A, or
     *                                       if B is SIX and A is IS/IX/S, and invalid otherwise. hasSIXAncestor may
     *                                       be helpful here.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void promote(TransactionContext transaction, LockType newLockType)
            throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        // TODO(proj4_part2): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("");
        }
        if ((this.parentContext() != null &&
                !LockType.canBeParentLock(this.parent.getEffectiveLockType(transaction), newLockType))) {
            throw new InvalidLockException("");
        }
        if (newLockType == LockType.SIX) {
            if (this.hasSIXAncestor(transaction)) {
                throw new InvalidLockException("");
            }
            if(this.getExplicitLockType(transaction) == null) {
                throw new NoLockHeldException("");
            }
            if(!LockType.substitutable(newLockType, getExplicitLockType(transaction))){
                throw new InvalidLockException("");
            }
            //release S and IS descendants
            //update numChildLock
            List<ResourceName> sisResourceName = this.sisDescendants(transaction);
            sisResourceName.add(this.getResourceName());
            this.lockman.acquireAndRelease(transaction, this.getResourceName(), newLockType, sisResourceName);
            for (ResourceName name : sisResourceName) {
                LockContext childContext = this.childContext(name.toString());
                childContext.parentContext().updateAncestorNumChildLocks(transaction, -1);
            }
        } else this.lockman.promote(transaction, this.getResourceName(), newLockType);
    }

    /**
     * Escalate `transaction`'s lock from descendants of this context to this
     * level, using either an S or X lock. There should be no descendant locks
     * after this call, and every operation valid on descendants of this context
     * before this call must still be valid. You should only make *one* mutating
     * call to the lock manager, and should only request information about
     * TRANSACTION from the lock manager.
     * <p>
     * For example, if a transaction has the following locks:
     * <p>
     * IX(database)
     * /         \
     * IX(table1)    S(table2)
     * /      \
     * S(table1 page3)  X(table1 page5)
     * <p>
     * then after table1Context.escalate(transaction) is called, we should have:
     * <p>
     * IX(database)
     * /         \
     * X(table1)     S(table2)
     * <p>
     * You should not make any mutating calls if the locks held by the
     * transaction do not change (such as when you call escalate multiple times
     * in a row).
     * <p>
     * Note: you *must* make any necessary updates to numChildLocks of all
     * relevant contexts, or else calls to LockContext#getNumChildren will not
     * work properly.
     *
     * @throws NoLockHeldException           if `transaction` has no lock at this level
     * @throws UnsupportedOperationException if context is readonly
     */
    public void escalate(TransactionContext transaction) throws NoLockHeldException {
        // TODO(proj4_part2): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("");
        }

        int numChildren = this.getNumChildren(transaction);
        List<ResourceName> releaseNames;
        LockType currType = this.getExplicitLockType(transaction);

        if (numChildren == 0 && currType == LockType.NL) {
            throw new NoLockHeldException("");
        }
        if(currType == LockType.S ||currType == LockType.X) return;
        if (numChildren == this.sisDescendants(transaction).size() && (currType == LockType.NL ||currType == LockType.IS)) {
            //all locks of descendants are S/IS
            releaseNames = this.sisDescendants(transaction);
            if(currType != LockType.NL) releaseNames.add(this.getResourceName());
            this.lockman.acquireAndRelease(transaction, this.name, LockType.S, releaseNames);
        } else {
            //some locks of descendants are X/IX/SIX
            releaseNames = this.lockDescendants(transaction);
            if(currType != LockType.NL) releaseNames.add(this.getResourceName());
            this.lockman.acquireAndRelease(transaction, this.name, LockType.X, releaseNames);
        }
        //update numChildLocks
        for (ResourceName name : releaseNames) {
            if(name == this.getResourceName() && this.parentContext() == null) continue;
            LockContext childContext = this.childContext(name.toString());
            childContext.parentContext().updateAncestorNumChildLocks(transaction, -1);
        }
    }

    /**
     * Get the type of lock that `transaction` holds at this level, or NL if no
     * lock is held at this level.
     */
    public LockType getExplicitLockType(TransactionContext transaction) {
        if (transaction == null) return LockType.NL;
        // TODO(proj4_part2): implement
        return this.lockman.getLockType(transaction, this.getResourceName());
    }

    /**
     * Gets the type of lock that the transaction has at this level, either
     * implicitly (e.g. explicit S lock at higher level implies S lock at this
     * level) or explicitly. Returns NL if there is no explicit nor implicit
     * lock.
     */
    public LockType getEffectiveLockType(TransactionContext transaction) {
        if (transaction == null) return LockType.NL;
        // TODO(proj4_part2): implement
        if (this.parentContext() == null) return this.getExplicitLockType(transaction);
        if (this.getExplicitLockType(transaction) == LockType.NL) {
            LockType parentType = this.parentContext().getEffectiveLockType(transaction);
            if (parentType == LockType.SIX) return LockType.S;
            if (parentType == LockType.IS || parentType == LockType.IX) return LockType.NL;
            return parentType;
        }
        return this.getExplicitLockType(transaction);
    }

    /**
     * Helper method to see if the transaction holds a SIX lock at an ancestor
     * of this context
     *
     * @param transaction the transaction
     * @return true if holds a SIX at an ancestor, false if not
     */
    private boolean hasSIXAncestor(TransactionContext transaction) {
        // TODO(proj4_part2): implement
        LockType thisType = this.getExplicitLockType(transaction);
        if (this.parentContext() == null && thisType != LockType.SIX) return false;
        if (thisType == LockType.SIX) return true;
        return this.parentContext().hasSIXAncestor(transaction);
    }

    /**
     * Helper method to get a list of resourceNames of all locks that are S or
     * IS and are descendants of current context for the given transaction.
     *
     * @param transaction the given transaction
     * @return a list of ResourceNames of descendants which the transaction
     * holds an S or IS lock.
     */
    private List<ResourceName> sisDescendants(TransactionContext transaction) {
        // TODO(proj4_part2): implement
        List<ResourceName> sisResourceNames = new ArrayList<>();
        List<Lock> locks = this.lockman.getLocks(transaction);
        for (Lock lock : locks) {
            if (lock.name.isDescendantOf(this.name) && (lock.lockType == LockType.S || lock.lockType == LockType.IS)) {
                sisResourceNames.add(lock.name);
            }
        }
        return sisResourceNames;
    }

    /**
     * Helper method to get a list of resourceNames of all locks that are S/IS/X/IX/SIX
     * and are descendants of current context for the given transaction.
     *
     * @param transaction the given transaction
     * @return a list of ResourceNames of all descendants
     */
    private List<ResourceName> lockDescendants(TransactionContext transaction) {
        List<ResourceName> resourceNames = new ArrayList<>();
        List<Lock> locks = this.lockman.getLocks(transaction);
        for (Lock lock : locks) {
            if (lock.name.isDescendantOf(this.name) && lock.lockType != LockType.NL) {
                resourceNames.add(lock.name);
            }
        }
        return resourceNames;
    }

    /**
     * Disables locking descendants. This causes all new child contexts of this
     * context to be readonly. This is used for indices and temporary tables
     * (where we disallow finer-grain locks), the former due to complexity
     * locking B+ trees, and the latter due to the fact that temporary tables
     * are only accessible to one transaction, so finer-grain locks make no
     * sense.
     */
    public void disableChildLocks() {
        this.childLocksDisabled = true;
    }

    /**
     * Gets the parent context.
     */
    public LockContext parentContext() {
        return parent;
    }

    /**
     * Gets the context for the child with name `name` and readable name
     * `readable`
     */
    public synchronized LockContext childContext(String name) {
        LockContext temp = new LockContext(lockman, this, name,
                this.childLocksDisabled || this.readonly);
        LockContext child = this.children.putIfAbsent(name, temp);
        if (child == null) child = temp;
        return child;
    }

    /**
     * Gets the context for the child with name `name`.
     */
    public synchronized LockContext childContext(long name) {
        return childContext(Long.toString(name));
    }

    /**
     * Gets the number of locks held on children a single transaction.
     */
    public int getNumChildren(TransactionContext transaction) {
        return numChildLocks.getOrDefault(transaction.getTransNum(), 0);
    }

    /**
     * update numChildLocks of all the ancestors of the LockContext recursively ,
     * each numChildLock increase by num
     */
    private void updateAncestorNumChildLocks(TransactionContext transaction, Integer num) {
        this.numChildLocks.putIfAbsent(transaction.getTransNum(), 0);
        Integer oldValue = this.getNumChildren(transaction);
        this.numChildLocks.replace(transaction.getTransNum(), oldValue + num);
        if (this.parentContext() != null) this.parentContext().updateAncestorNumChildLocks(transaction, num);
    }

    @Override
    public String toString() {
        return "LockContext(" + name.toString() + ")";
    }
}

