package edu.berkeley.cs186.database.concurrency;

import java.util.*;
/**
 * Utility methods to track the relationships between different lock types.
 */
public enum LockType {
    S,   // shared
    X,   // exclusive
    IS,  // intention shared
    IX,  // intention exclusive
    SIX, // shared intention exclusive
    NL;  // no lock held

    /**
     * This method checks whether lock types A and B are compatible with
     * each other. If a transaction can hold lock type A on a resource
     * at the same time another transaction holds lock type B on the same
     * resource, the lock types are compatible.
     */
    public static boolean compatible(LockType a, LockType b) {
        if (a == null || b == null) {
            throw new NullPointerException("null lock type");
        }
        // TODO(proj4_part1): implement
        Map<LockType, Integer> lockTypeId = new HashMap<LockType,Integer>(){{
            put(LockType.NL, 0);
            put(LockType.IS, 1);
            put(LockType.IX, 2);
            put(LockType.S, 3);
            put(LockType.SIX, 4);
            put(LockType.X, 5);
        }};
        boolean compMatrix[][] = {{true, true, true, true, true, true},
                                {true, true, true, true, true, false},
                                {true, true, true, false, false, false},
                                {true, true, false, true, false, false},
                                {true, true, false, false, false, false},
                               {true, false, false, false, false, false}};
        if(!lockTypeId.containsKey(a) || !lockTypeId.containsKey(b)) 
            throw new UnsupportedOperationException("bad lock type");
        int aId = lockTypeId.get(a);
        int bId = lockTypeId.get(b);
        return compMatrix[aId][bId];
    }

    /**
     * This method returns the lock on the parent resource
     * that should be requested for a lock of type A to be granted.
     */
    public static LockType parentLock(LockType a) {
        if (a == null) {
            throw new NullPointerException("null lock type");
        }
        switch (a) {
        case S: return IS;
        case X: return IX;
        case IS: return IS;
        case IX: return IX;
        case SIX: return IX;
        case NL: return NL;
        default: throw new UnsupportedOperationException("bad lock type");
        }
    }

    /**
     * This method returns if parentLockType has permissions to grant a childLockType
     * on a child.
     */
    public static boolean canBeParentLock(LockType parentLockType, LockType childLockType) {
        if (parentLockType == null || childLockType == null) {
            throw new NullPointerException("null lock type");
        }
        // TODO(proj4_part1): implement
        Map<LockType, Integer> lockTypeId = new HashMap<LockType,Integer>(){{
            put(LockType.NL, 0);
            put(LockType.IS, 1);
            put(LockType.IX, 2);
            put(LockType.S, 3);
            put(LockType.SIX, 4);
            put(LockType.X, 5);
        }};
        boolean parentMatrix[][] = {{true, false, false, false, false, false},
                                {true, true, false, true, false, false},
                                {true, true, true, true, true, true},
                                {true, true, false, true, false, false},
                                {true, false, true, false, true, true},
                               {true, false, false, false, false, false}};
        if(!lockTypeId.containsKey(parentLockType) || !lockTypeId.containsKey(childLockType)) 
        throw new UnsupportedOperationException("bad lock type");        
        int aId = lockTypeId.get(parentLockType);
        int bId = lockTypeId.get(childLockType);
        return parentMatrix[aId][bId];
    }

    /**
     * This method returns whether a lock can be used for a situation
     * requiring another lock (e.g. an S lock can be substituted with
     * an X lock, because an X lock allows the transaction to do everything
     * the S lock allowed it to do).
     */
    public static boolean substitutable(LockType substitute, LockType required) {
        if (required == null || substitute == null) {
            throw new NullPointerException("null lock type");
        }
        // TODO(proj4_part1): implement
        Map<LockType, Integer> lockTypeId = new HashMap<LockType,Integer>(){{
            put(LockType.NL, 0);
            put(LockType.IS, 1);
            put(LockType.IX, 2);
            put(LockType.S, 3);
            put(LockType.SIX, 4);
            put(LockType.X, 5);
        }};
        boolean substituteMatrix[][] = {{true, false, false, false, false, false},
                                {true, true, false, false, false, false},
                                {true, true, true, false, false, false},
                                {true, true, false, true, false, false},
                                {true, true, true, true, true, false},
                               {true, false, true, true, true, true}};
        if(!lockTypeId.containsKey(substitute) || !lockTypeId.containsKey(required)) 
        throw new UnsupportedOperationException("bad lock type");
        int aId = lockTypeId.get(substitute);
        int bId = lockTypeId.get(required);
        return substituteMatrix[aId][bId];
    }

    /**
     * @return True if this lock is IX, IS, or SIX. False otherwise.
     */
    public boolean isIntent() {
        return this == LockType.IX || this == LockType.IS || this == LockType.SIX;
    }

    @Override
    public String toString() {
        switch (this) {
        case S: return "S";
        case X: return "X";
        case IS: return "IS";
        case IX: return "IX";
        case SIX: return "SIX";
        case NL: return "NL";
        default: throw new UnsupportedOperationException("bad lock type");
        }
    }
}

