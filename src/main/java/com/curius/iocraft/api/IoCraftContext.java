package com.curius.iocraft.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record IoCraftContext(
        ServerLevel level,
        BlockPos pos,
        String device
) {}

