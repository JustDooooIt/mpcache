package com.orange.mpcache.command.impl;

import com.orange.mpcache.base.Key;
import com.orange.mpcache.command.ICommand;

import java.util.Map;

public class CachePutCommand implements ICommand {

    private final Map<Key, Object> map;

    private final Key key;

    private final Object newValue;

    public CachePutCommand(Map<Key, Object> map, Key key, Object newValue) {
        this.map = map;
        this.key = key;
        this.newValue = newValue;
    }

    @Override
    public void execute() {
        map.put(key, newValue);
    }

    @Override
    public void undo() {
        map.remove(key);
    }
}
