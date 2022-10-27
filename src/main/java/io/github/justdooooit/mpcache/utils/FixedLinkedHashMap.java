package io.github.justdooooit.mpcache.utils;

import java.util.LinkedHashMap;

public class FixedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

    private int capacity;

    public FixedLinkedHashMap(int capacity) {
        super(capacity + 1, 1.0f, true);
        this.capacity = capacity;
    }

    public int getCapacity() {
        return this.capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        //当链表元素大于容量时，移除最老（最久未被使用）的元素
        return size() > this.capacity;
    }

}
