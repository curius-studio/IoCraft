package com.curius.iocraft.api;

import java.util.Optional;

public final class IoCraftApiProvider {
    public static final int API_MAJOR = 1;
    public static final int API_MINOR = 2;
    public static final int API_PATCH = 0;

    private static volatile IoCraftApi INSTANCE;

    private IoCraftApiProvider() {}

    public static Optional<IoCraftApi> get() {
        return Optional.ofNullable(INSTANCE);
    }

    public static boolean isAvailable() {
        return INSTANCE != null;
    }

    public static void register(IoCraftApi api) {
        INSTANCE = api;
    }

    public static int apiMajor() { return API_MAJOR; }
    public static int apiMinor() { return API_MINOR; }
    public static int apiPatch() { return API_PATCH; }
    public static String apiVersion() {
        return API_MAJOR + "." + API_MINOR + "." + API_PATCH;
    }
}

