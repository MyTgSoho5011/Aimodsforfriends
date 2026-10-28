package com.sohonet.codeforge.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Фасад над игроком для скриптов: только Java-типы в сигнатурах,
 * поэтому скрипт не зависит от названий классов Minecraft.
 */
public final class Player {

    private final MinecraftClient client;

    Player(MinecraftClient client) {
        this.client = client;
    }

    public String name() {
        return client.player.getGameProfile().getName();
    }

    public double x() {
        return client.player.getX();
    }

    public double y() {
        return client.player.getY();
    }

    public double z() {
        return client.player.getZ();
    }

    /** Короткая запись позиции, например "12, 64, -3". */
    public String blockPos() {
        return client.player.getBlockPos().toShortString();
    }

    public float health() {
        return client.player.getHealth();
    }

    public float maxHealth() {
        return client.player.getMaxHealth();
    }

    public int xpLevel() {
        return client.player.experienceLevel;
    }

    /** Например "minecraft:overworld". */
    public String dimension() {
        return client.world.getRegistryKey().getValue().toString();
    }

    public boolean isFlying() {
        return client.player.getAbilities().flying;
    }

    public boolean isCreative() {
        return client.player.isCreative();
    }

    /** id предмета в руке, например "minecraft:diamond_pickaxe". */
    public String mainHand() {
        return itemId(client.player.getMainHandStack());
    }

    public int mainHandCount() {
        return client.player.getMainHandStack().getCount();
    }

    /** Сколько пустых слотов в инвентаре. */
    public int emptySlots() {
        PlayerInventory inventory = client.player.getInventory();
        int empty = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    @Override
    public String toString() {
        return name() + " @ " + blockPos();
    }
}
