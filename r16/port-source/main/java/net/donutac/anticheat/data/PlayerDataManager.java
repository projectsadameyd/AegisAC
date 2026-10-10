package net.donutac.anticheat.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {
   private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

   public PlayerData get(UUID var1) {
      return this.data.computeIfAbsent(var1, PlayerData::new);
   }

   public void remove(UUID var1) {
      this.data.remove(var1);
   }

   public Map<UUID, PlayerData> all() {
      return this.data;
   }
}
