package com.sohonet.codeforge.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/**
 * Фасад над миром для скриптов (те же правила, что у {@link Player}:
 * никаких типов Minecraft в сигнатурах).
 */
public final class World {

    private final MinecraftClient client;

    World(MinecraftClient client) {
        this.client = client;
    }

    /** Сколько сущностей (включая мобов и игроков) в радиусе от игрока. */
    public int entitiesNear(double radius) {
        double limit = radius * radius;
        int count = 0;
        for (Entity entity : client.world.getEntities()) {
            if (entity != client.player && entity.squaredDistanceTo(client.player) <= limit) {
                count++;
            }
        }
        return count;
    }

    /** id блока по смещению от игрока, например blockId(0, -1, 0) - блок под ногами. */
    public String blockId(int dx, int dy, int dz) {
        BlockPos pos = client.player.getBlockPos().add(dx, dy, dz);
        return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
    }

    /** Блок под ногами. */
    public String blockBelow() {
        return blockId(0, -1, 0);
    }

    public boolean isAir(int dx, int dy, int dz) {
        return client.world.getBlockState(client.player.getBlockPos().add(dx, dy, dz)).isAir();
    }

    /** Время суток в тиках (0..24000). */
    public long timeOfDay() {
        return client.world.getTimeOfDay();
    }

    public boolean isDay() {
        return timeOfDay() < 13000;
    }

    public String dimension() {
        return client.world.getRegistryKey().getValue().toString();
    }

    /** Сколько всего игроков/мобов загружено вокруг. */
    public int loadedEntities() {
        int count = 0;
        for (Entity ignored : client.world.getEntities()) {
            count++;
        }
        return count;
    }
}
