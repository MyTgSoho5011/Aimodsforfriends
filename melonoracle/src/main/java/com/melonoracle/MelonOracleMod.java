package com.melonoracle;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MelonOracleMod implements ModInitializer {

    public static final String MOD_ID = "melonoracle";

    public static Block ORACLE_BLOCK;
    public static Item ORACLE_BLOCK_ITEM;
    public static Item CURSED_MELON_ITEM;

    public static Block KUMIS_STATUE_BLOCK;
    public static Item KUMIS_STATUE_BLOCK_ITEM;
    public static Item KUMIS_BUCKET_ITEM;

    private record Scheduled(int[] ticksLeft, Runnable task) {}
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();

    public static void scheduleTickCallback(int delayTicks, Runnable task) {
        SCHEDULED.add(new Scheduled(new int[]{delayTicks}, task));
    }

    // Players who already received their "first dawn" melon gift this world session.
    private static final Set<String> GIFTED_PLAYERS = new HashSet<>();

    @Override
    public void onInitialize() {
        // --- Melon Oracle statue (1-tall) ---
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "melon_oracle"));
        ORACLE_BLOCK = new MelonOracleBlock(AbstractBlock.Settings.copy(Blocks.MELON).registryKey(blockKey).luminance(s -> 4));
        Registry.register(Registries.BLOCK, blockKey, ORACLE_BLOCK);

        RegistryKey<Item> blockItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "melon_oracle"));
        ORACLE_BLOCK_ITEM = new BlockItem(ORACLE_BLOCK, new Item.Settings().registryKey(blockItemKey).useBlockPrefixedTranslationKey());
        Registry.register(Registries.ITEM, blockItemKey, ORACLE_BLOCK_ITEM);

        RegistryKey<Item> cursedKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "cursed_melon"));
        CURSED_MELON_ITEM = new Item(new Item.Settings().registryKey(cursedKey).food(
            new net.minecraft.component.type.FoodComponent.Builder()
                .nutrition(4)
                .saturationModifier(0.3f)
                .statusEffect(new net.minecraft.util.Pair<>(
                    new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.LUCK, 6000, 0), 1.0f))
                .statusEffect(new net.minecraft.util.Pair<>(
                    new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.NAUSEA, 60, 0), 0.3f))
                .build()
        ));
        Registry.register(Registries.ITEM, cursedKey, CURSED_MELON_ITEM);

        // --- Kumis statue (2-tall) ---
        RegistryKey<Block> kumisBlockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, "kumis_statue"));
        KUMIS_STATUE_BLOCK = new KumisStatueBlock(AbstractBlock.Settings.copy(Blocks.QUARTZ_BLOCK).registryKey(kumisBlockKey).luminance(s -> 2));
        Registry.register(Registries.BLOCK, kumisBlockKey, KUMIS_STATUE_BLOCK);

        RegistryKey<Item> kumisBlockItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "kumis_statue"));
        KUMIS_STATUE_BLOCK_ITEM = new BlockItem(KUMIS_STATUE_BLOCK, new Item.Settings().registryKey(kumisBlockItemKey).useBlockPrefixedTranslationKey());
        Registry.register(Registries.ITEM, kumisBlockItemKey, KUMIS_STATUE_BLOCK_ITEM);

        // --- Bucket of Kumis (plain item, obtained by right-clicking a tamed horse with a vanilla bucket) ---
        RegistryKey<Item> kumisKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "kumis_bucket"));
        KUMIS_BUCKET_ITEM = new Item(new Item.Settings().registryKey(kumisKey).maxCount(16));
        Registry.register(Registries.ITEM, kumisKey, KUMIS_BUCKET_ITEM);

        KumisHorseInteraction.register();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(e -> {
            e.add(ORACLE_BLOCK_ITEM);
            e.add(KUMIS_STATUE_BLOCK_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(e -> {
            e.add(CURSED_MELON_ITEM);
            e.add(KUMIS_BUCKET_ITEM);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!SCHEDULED.isEmpty()) {
                List<Scheduled> toRun = new ArrayList<>();
                var it = SCHEDULED.iterator();
                while (it.hasNext()) {
                    Scheduled s = it.next();
                    s.ticksLeft()[0]--;
                    if (s.ticksLeft()[0] <= 0) {
                        toRun.add(s);
                        it.remove();
                    }
                }
                for (Scheduled s : toRun) {
                    s.task().run();
                }
            }

            // First-dawn melon gift: at the first sunrise each player experiences,
            // give them exactly one melon so the "melon seeds" crafting chain
            // is never a dead end in a fresh world (vanilla has no melons otherwise).
            for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
                long timeOfDay = sp.getWorld().getTimeOfDay() % 24000L;
                boolean isDawn = timeOfDay >= 0 && timeOfDay < 20;
                String id = sp.getUuidAsString();
                if (isDawn && !GIFTED_PLAYERS.contains(id)) {
                    GIFTED_PLAYERS.add(id);
                    ItemStack gift = new ItemStack(Items.MELON_SLICE, 1);
                    if (!sp.getInventory().insertStack(gift)) {
                        sp.dropItem(gift, false);
                    }
                    sp.sendMessage(net.minecraft.text.Text.literal("[Рассвет] Что-то оставило кусок дыни у твоих ног."), false);
                }
            }
        });
    }
}