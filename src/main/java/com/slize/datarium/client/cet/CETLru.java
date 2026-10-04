package com.slize.datarium.client.cet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CETLru<V> extends LinkedHashMap<UUID, V> {
    private final int capacity;

    public CETLru() {
        this(2048);
    }

    public CETLru(int capacity) {
        super(16, 0.75F, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<UUID, V> eldest) {
        return size() > capacity;
    }

    public static final class Ints extends CETLru<Integer> {
        public Ints() {
            super();
        }

        public Ints(int capacity) {
            super(capacity);
        }

        public int getInt(UUID id) {
            Integer value = get(id);
            return value == null ? -1 : value;
        }
    }

    public static final class Booleans extends CETLru<Boolean> {
        public boolean getBoolean(UUID id) {
            Boolean value = get(id);
            return value != null && value;
        }
    }
}
