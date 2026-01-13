package com.vladisss.marrelics.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ManaShieldClientState {

    private static final Map<UUID, Boolean> STATE = new ConcurrentHashMap<>();

    public static void set(UUID playerId, boolean enabled) {
        STATE.put(playerId, enabled);
    }

    public static boolean isEnabled(UUID playerId) {
        return STATE.getOrDefault(playerId, false);
    }
}
