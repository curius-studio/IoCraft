package com.curius.iocraft.api;

@FunctionalInterface
public interface IoCraftMessageHandler {
    void handle(IoCraftMessage message, IoCraftContext context);
}

