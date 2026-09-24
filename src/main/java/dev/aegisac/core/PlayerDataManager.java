package dev.aegisac.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData get(UUID uuid) {
        return data.computeIfAbsent(uuid, ignored -> new PlayerData());
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public int size() {
        return data.size();
    }
}
