package com.example.hideenemyhealth.systems.hideentityui;

/**
 * Minimal primitive int hash set (open addressing).
 */
final class IntHashSet {

    private int[] values;
    private boolean[] used;
    private int mask;
    private int count;

    IntHashSet(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;
        values = new int[cap];
        used = new boolean[cap];
        mask = cap - 1;
        count = 0;
    }

    void add(int v) {
        // Resize at ~50% load to keep probes short.
        if ((count + 1) * 2 >= values.length) {
            rehash(values.length << 1);
        }

        int idx = mix32(v) & mask;
        while (used[idx]) {
            if (values[idx] == v) return;
            idx = (idx + 1) & mask;
        }
        used[idx] = true;
        values[idx] = v;
        count++;
    }

    boolean isEmpty() {
        return count == 0;
    }

    int capacity() {
        return values.length;
    }

    int count() {
        return count;
    }

    int getValueAt(int idx) {
        return values[idx];
    }

    boolean isUsedAt(int idx) {
        return used[idx];
    }

    private void rehash(int newCap) {
        final int[] oldValues = values;
        final boolean[] oldUsed = used;

        values = new int[newCap];
        used = new boolean[newCap];
        mask = newCap - 1;
        count = 0;

        for (int i = 0; i < oldValues.length; i++) {
            if (oldUsed[i]) {
                add(oldValues[i]);
            }
        }
    }

    private static int mix32(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }
}
