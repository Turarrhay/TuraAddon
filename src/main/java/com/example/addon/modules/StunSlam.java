package com.example.addon.modules;

import com.example.addon.TuraAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public class StunSlam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Slot selection mode")
        .defaultValue(Mode.Auto)
        .build()
    );

    private final Setting<Integer> axeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("axe-slot")
        .description("Axe slot (1-9)")
        .defaultValue(1)
        .min(1)
        .max(9)
        .visible(() -> mode.get() == Mode.Manual)
        .build()
    );

    private final Setting<Integer> maceSlot = sgGeneral.add(new IntSetting.Builder()
        .name("mace-slot")
        .description("Mace slot (1-9)")
        .defaultValue(2)
        .min(1)
        .max(9)
        .visible(() -> mode.get() == Mode.Manual)
        .build()
    );

    // Настройки задержки
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum delay between strikes (ticks)")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Maximum delay between strikes (ticks)")
        .defaultValue(5)
        .min(0)
        .max(15)
        .sliderRange(2, 15)
        .build()
    );

    private final Setting<Boolean> humanize = sgGeneral.add(new BoolSetting.Builder()
        .name("humanize")
        .description("Add human factor (random delays)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> missChance = sgGeneral.add(new IntSetting.Builder()
        .name("miss-chance")
        .description("Miss chance (%)")
        .defaultValue(5)
        .min(0)
        .max(50)
        .sliderRange(0, 30)
        .visible(humanize::get)
        .build()
    );

    private final Setting<Boolean> onlyWhenFalling = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-falling")
        .description("Only when falling")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Turn towards the target")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shieldCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("shield-check")
        .description("Check shield usage")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
        .name("shield-mode")
        .description("What to do when checking the shield")
        .defaultValue(ShieldMode.AxeThenMace)
        .visible(shieldCheck::get)
        .build()
    );

    private final Setting<Integer> maxCheckTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-check-ticks")
        .description("Maximum shield check time (ticks)")
        .defaultValue(15)
        .min(0)
        .max(40)
        .sliderRange(5, 40)
        .visible(shieldCheck::get)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Show messages")
        .defaultValue(false)
        .build()
    );

    public enum Mode {
        Auto,
        Manual
    }

    public enum ShieldMode {
        AxeThenMace("Axe → Mace"),
        MaceOnly("Only Mace");

        private final String title;

        ShieldMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private enum Stage {
        NONE,
        CHECK_SHIELD,
        HIT_AXE,
        HIT_MACE,
        COOLDOWN
    }

    private final Random random = new Random();
    private Stage stage = Stage.NONE;
    private PlayerEntity target = null;
    private int timer = 0;
    private int checkTicks = 0;
    private int axeSlotFound = -1;
    private int maceSlotFound = -1;
    private boolean shieldWasUsed = false;
    private int comboCount = 0;
    private long lastComboTime = 0;

    public StunSlam() {
        super(TuraAddon.pvp, "stun-slam", "Умное комбо с анти-античит защитой");
    }

    @Override
    public void onActivate() {
        reset();
        comboCount = 0;
        if (debug.get()) info("StunSlam активирован");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (onlyWhenFalling.get() && mc.player.fallDistance < 2) {
            reset();
            return;
        }

        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof PlayerEntity player) {
            target = player;
        } else {
            reset();
            return;
        }

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target));
        }

        if (timer > 0) {
            timer--;
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (stage == Stage.NONE && currentTime - lastComboTime < 1000) {
            if (debug.get()) info("Кулдаун комбо...");
            return;
        }

        if (stage == Stage.NONE) {
            findSlots();

            if (shieldCheck.get()) {
                stage = Stage.CHECK_SHIELD;
                checkTicks = 0;
                shieldWasUsed = false;
                if (debug.get()) info("Начинаем проверку щита...");
            } else {
                stage = Stage.HIT_AXE;
                timer = getRandomDelay();
            }
        }

        if (stage == Stage.CHECK_SHIELD) {
            checkTicks++;

            boolean isBlocking = target.isBlocking();

            if (checkTicks == 1) {
                shieldWasUsed = isBlocking;
                if (debug.get()) info("Цель " + (isBlocking ? "использует щит" : "не использует щит"));
            }


            if (!isBlocking || checkTicks >= maxCheckTicks.get()) {
                if (debug.get()) {
                    if (!isBlocking) info("Щит больше не используется");
                    else info("Время проверки истекло");
                }


                if (humanize.get() && random.nextInt(100) < missChance.get()) {
                    if (debug.get()) info("☠️ Ой, промахнулся...");
                    reset();
                    lastComboTime = currentTime;
                    return;
                }

                if (shieldMode.get() == ShieldMode.AxeThenMace && shieldWasUsed) {
                    if (debug.get()) info("Бьем топором (был щит)");
                    stage = Stage.HIT_AXE;
                } else {
                    if (debug.get()) info("Бьем сразу булавой");
                    stage = Stage.HIT_MACE;
                }
                timer = getRandomDelay();
            } else {
                if (debug.get()) info("Щит все еще активен... тик " + checkTicks);
                timer = 1 + random.nextInt(2); // Рандомная задержка между проверками
            }
        }


        if (stage == Stage.HIT_AXE && axeSlotFound != -1) {
            InvUtils.swap(axeSlotFound, true);


            if (humanize.get() && random.nextInt(100) < missChance.get()) {
                if (debug.get()) info("☠️ Топор промахнулся");
            } else {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                if (debug.get()) info("💥 Топор -> " + target.getName().getString());
            }

            stage = Stage.HIT_MACE;
            timer = getRandomDelay();
        }


        if (stage == Stage.HIT_MACE && maceSlotFound != -1) {
            InvUtils.swap(maceSlotFound, true);


            if (humanize.get() && random.nextInt(100) < missChance.get()) {
                if (debug.get()) info("☠️ Булава промахнулась");
            } else {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                if (debug.get()) info("✨ Булава -> " + target.getName().getString());
            }

            comboCount++;
            lastComboTime = System.currentTimeMillis();

            if (comboCount >= 3) {
                if (debug.get()) info("😴 Отдыхаем после 3 комбо");
                timer = 20 + random.nextInt(20); // Отдых 1-2 секунды
                comboCount = 0;
            }

            reset();
        }
    }

    private int getRandomDelay() {
        if (!humanize.get()) {
            return minDelay.get();
        }
        return minDelay.get() + random.nextInt(maxDelay.get() - minDelay.get() + 1);
    }

    private void findSlots() {
        if (mode.get() == Mode.Auto) {
            FindItemResult axe = InvUtils.find(itemStack -> itemStack.isIn(ItemTags.AXES));
            FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem() instanceof MaceItem);

            if (axe.found()) axeSlotFound = axe.slot();
            if (mace.found()) maceSlotFound = mace.slot();

            if (debug.get()) {
                if (axe.found()) info("Найден топор в слоте " + (axe.slot() + 1));
                if (mace.found()) info("Найдена булава в слоте " + (mace.slot() + 1));
            }
        } else {
            axeSlotFound = axeSlot.get() - 1;
            maceSlotFound = maceSlot.get() - 1;

            if (debug.get()) {
                info("Ручной режим: топор слот " + axeSlot.get() + ", булава слот " + maceSlot.get());
            }
        }
    }

    private void reset() {
        stage = Stage.NONE;
        timer = 0;
        checkTicks = 0;
    }
}
