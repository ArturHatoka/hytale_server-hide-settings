package com.example.hideenemyhealth.systems.hideentityui;

/**
 * Minimal primitive long hash set (open addressing).
 */
final class LongHashSet implements EntityUiBaselineCache.LongKeySet {

    private long[] keys;
    private boolean[] used;
    private int size;

    LongHashSet(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;
        keys = new long[cap];
        used = new boolean[cap];
    }

    void add(long key) {
        if ((size + 1) * 2 >= keys.length) rehash(keys.length << 1);
        int idx = mix64To32(key) & (keys.length - 1);
        while (used[idx]) {
            if (keys[idx] == key) return;
            idx = (idx + 1) & (keys.length - 1);
        }
        used[idx] = true;
        keys[idx] = key;
        size++;
    }

    @Override
    public boolean contains(long key) {
        int idx = mix64To32(key) & (keys.length - 1);
        while (used[idx]) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & (keys.length - 1);
        }
        return false;
    }

    private void rehash(int newCap) {
        final long[] oldKeys = keys;
        final boolean[] oldUsed = used;
        keys = new long[newCap];
        used = new boolean[newCap];
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldUsed[i]) add(oldKeys[i]);
        }
    }

    private static int mix64To32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }
}
