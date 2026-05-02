package com.curius.iocraft.api;

import com.google.gson.JsonObject;

import java.util.UUID;

public record IoCraftMessage(
        String type,
        JsonObject data,
        String text,
        UUID to,
        UUID from
) {}

