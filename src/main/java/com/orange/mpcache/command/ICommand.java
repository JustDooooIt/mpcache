package com.orange.mpcache.command;

public interface ICommand {
    void execute();
    void undo();
}
