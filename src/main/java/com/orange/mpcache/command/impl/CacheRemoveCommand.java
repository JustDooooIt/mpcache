package com.orange.mpcache.command.impl;

import com.orange.mpcache.base.Key;
import com.orange.mpcache.command.ICommand;

import java.util.Map;

public class CacheRemoveCommand implements ICommand {

    private final Map<Key, Object> map;

    private final Key key;

    private Object oldValue;

    public CacheRemoveCommand(Map<Key, Object> map, Key key, Object oldValue) {
        this.map = map;
        this.key = key;
        this.oldValue = oldValue;
    }

    @Override
    public void execute() {
        oldValue = map.remove(key);
    }

    @Override
    public void undo() {
        map.put(key, oldValue);
    }
}
