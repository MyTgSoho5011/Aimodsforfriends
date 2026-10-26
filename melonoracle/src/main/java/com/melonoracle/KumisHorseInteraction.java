package com.melonoracle;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class KumisHorseInteraction {

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            if (!(entity instanceof AbstractHorseEntity horse)) {
                return ActionResult.PASS;
            }
            ItemStack held = player.getStackInHand(hand);
            if (!held.isOf(Items.BUCKET)) {
                return ActionResult.PASS;
            }
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }
            if (!horse.isTame()) {
                player.sendMessage(Text.literal("[Кумыс] Эта лошадь не доверяет тебе. Пока что."), false);
                return ActionResult.FAIL;
            }

            held.decrement(1);
            ItemStack kumis = new ItemStack(MelonOracleMod.KUMIS_BUCKET_ITEM, 1);
            if (!player.getInventory().insertStack(kumis)) {
                player.dropItem(kumis, false);
            }
            if (world instanceof ServerWorld sw) {
                sw.playSound(null, horse.getBlockPos(), SoundEvents.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 1.0f, 1.2f);
            }
            player.sendMessage(Text.literal("[Кумыс] Готово. Не спрашивай, как."), false);
            return ActionResult.SUCCESS;
        });
    }
}