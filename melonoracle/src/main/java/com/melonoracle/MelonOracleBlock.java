package com.melonoracle;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class MelonOracleBlock extends Block {

    private static final Random RNG = new Random();

    private static final List<String> PROPHECIES = List.of(
        "Из дыни ты вышел. В дыню и вернешься.",
        "Сегодня крипер пощадит твой дом... если ты поделишься семенем.",
        "Тот, кто копает ночью, найдет не алмазы, а то, что копало до него.",
        "Я вижу все. Я помню все. Я помню и ТЕБЯ, %s.",
        "Твой инвентарь скоро переполнится. Выбрось лишнее. Выбрось себя.",
        "Слушай писк паука - в нем мое имя.",
        "Три двери впереди. За одной - сокровище. За двумя - то, что носит твое лицо.",
        "Семя, посаженное сегодня, прорастет через три дня. Три дня - это долго. Ты доживешь?",
        "Берегись полуночного зомби. Он не идет за мясом. Он идет, потому что ты его позвал.",
        "Я доволен. Пока что.",
        "%s, не оборачивайся.",
        "Здесь была дверь. Теперь ее нет. Спроси себя, почему.",
        "Твой последний респаун был не случайностью.",
        "Я считал твои шаги. Их было на один больше, чем должно быть.",
        "Положи семя обратно. Пожалуйста. Я не хочу говорить это снова.",
        "Лошадь, которую ты доил, помнит тебя."
    );

    private static final List<String> LIE_PROPHECIES = List.of(
        "Впереди безопасно. (Это ложь.)",
        "Алмазы лежат ровно в 12 блоках на север. (Копай и узнай сам, кто солгал.)",
        "Твой дом не горит. (Пока ты это читаешь - уже горит.)"
    );

    public MelonOracleBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                  PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        boolean offeringSeed = held.isOf(Items.MELON_SEEDS);
        boolean offeringMelon = held.isOf(Items.MELON_SLICE);

        String name = player.getName().getString();
        String line;

        double creepy = RNG.nextDouble();
        if (creepy < 0.12) {
            line = LIE_PROPHECIES.get(RNG.nextInt(LIE_PROPHECIES.size()));
        } else {
            String raw = PROPHECIES.get(RNG.nextInt(PROPHECIES.size()));
            line = raw.contains("%s") ? String.format(raw, name) : raw;
        }

        player.sendMessage(Text.literal("[Дынный Оракул] " + line), false);

        if (world instanceof ServerWorld sw) {
            if (RNG.nextDouble() < 0.20) {
                int delayTicks = 60 + RNG.nextInt(140);
                String delayedLine = pickDelayedLine(name);
                scheduleDelayed(sw, player, delayedLine, delayTicks);
            }

            if (offeringSeed || offeringMelon) {
                held.decrement(1);
                double roll = RNG.nextDouble();
                if (roll < 0.15) {
                    ItemStack reward = new ItemStack(MelonOracleMod.CURSED_MELON_ITEM, 1);
                    if (!player.getInventory().insertStack(reward)) {
                        player.dropItem(reward, false);
                    }
                    player.sendMessage(Text.literal("[Дынный Оракул] Жертва принята. Прими дар."), false);
                    sw.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1.0f, 0.6f);
                } else if (roll < 0.22) {
                    player.sendMessage(Text.literal("[Дынный Оракул] ...кто-то идет."), false);
                    sw.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_STEP, SoundCategory.AMBIENT, 1.0f, 0.5f);
                    sw.playSound(null, pos.add(0, 0, 3), SoundEvents.ENTITY_ZOMBIE_STEP, SoundCategory.AMBIENT, 0.6f, 0.4f);
                } else {
                    player.sendMessage(Text.literal("[Дынный Оракул] Дар принят. Молчание."), false);
                    sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 0.3f);
                }
            } else {
                sw.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 1.0f, 0.4f);
            }
        }

        return ActionResult.SUCCESS;
    }

    private String pickDelayedLine(String name) {
        List<String> delayed = List.of(
            name + ". Я все еще здесь.",
            "Ты не услышал меня в первый раз.",
            "Обернись.",
            "Это снова я.",
            "Семя. Еще одно. Сейчас."
        );
        return delayed.get(RNG.nextInt(delayed.size()));
    }

    private void scheduleDelayed(ServerWorld sw, PlayerEntity player, String line, int delayTicks) {
        MelonOracleMod.scheduleTickCallback(delayTicks, () -> {
            if (player.isAlive() && !player.isRemoved()) {
                player.sendMessage(Text.literal("[Дынный Оракул] " + line), false);
                sw.playSound(player.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.AMBIENT, 0.5f, 0.3f, true);
            }
        });
    }
}