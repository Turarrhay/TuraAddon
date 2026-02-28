package com.example.addon.modules;  // Замени на свой пакет!

import com.example.addon.TuraAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

public class MacePearl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> pearlSlot = sgGeneral.add(new IntSetting.Builder()
        .name("pearl-slot")
        .description("Слот с эндер-перлом (0-8 hotbar).")
        .defaultValue(2)
        .min(0).max(8)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Только игроки (не мобы).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-range")
        .description("Макс расстояние до цели.")
        .defaultValue(6.0)
        .min(3.0).max(10.0)
        .sliderRange(3.0, 10.0)
        .build()
    );

    private final Setting<Boolean> onlyHigher = sgGeneral.add(new BoolSetting.Builder()
        .name("only-higher")
        .description("Только если цель выше тебя.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> heightDiff = sgGeneral.add(new DoubleSetting.Builder()
        .name("height-diff")
        .description("Мин разница высоты (цель выше на столько).")
        .defaultValue(0.5)
        .min(0.0).max(3.0)
        .sliderRange(0.0, 3.0)
        .visible(onlyHigher::get)
        .build()
    );

    private final Setting<Integer> rotationTicks = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-ticks")
        .description("Тики на поворот перед кидом перла.")
        .defaultValue(3)
        .min(1).max(10)
        .sliderRange(1, 10)
        .build()
    );

    // НОВАЯ НАСТРОЙКА: задержка кида перла (тики после поворота)
    private final Setting<Integer> throwDelay = sgGeneral.add(new IntSetting.Builder()
        .name("throw-delay")
        .description("Задержка перед кидом перла (тики после поворота).")
        .defaultValue(2)
        .min(0).max(20)
        .sliderRange(0, 20)
        .build()
    );

    // Поля для таймера задержки
    private int delayCounter = 0;
    private boolean readyToThrow = false;
    private Entity lastTarget = null;

    public MacePearl() {
        super(TuraAddon.pvp, "mace-pearl", "Throws a pearl into the neck when hitting with a mace if the target is higher (with a delay).");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity target = event.entity;
        if (!isValidTarget(target)) return;

        // Проверяем булаву в главной руке
        ItemStack mainHand = mc.player.getMainHandStack();
        if (!(mainHand.getItem() instanceof MaceItem)) return;

        // Проверяем перл в слоте
        int slot = pearlSlot.get();
        ItemStack pearlStack = mc.player.getInventory().getStack(slot);
        if (!(pearlStack.getItem() instanceof EnderPearlItem)) {
            warning("There is no pearl in the slot " + slot + "!");
            return;
        }

        // Сохраняем цель
        lastTarget = target;

        // Swap на перл
        InvUtils.swap(slot, true);

        // Вычисляем позицию шеи (без getPos() — используем getX/Y/Z)
        double neckX = target.getX();
        double neckY = target.getY() + target.getHeight() * 0.75;  // 75% высоты ≈ шея
        double neckZ = target.getZ();
        Vec3d neckPos = new Vec3d(neckX, neckY, neckZ);

        // Поворот к шее + callback для задержки кида
        Rotations.rotate(Rotations.getYaw(neckPos), Rotations.getPitch(neckPos), rotationTicks.get(), () -> {
            readyToThrow = true;
            delayCounter = throwDelay.get();  // запускаем таймер задержки
        });
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!readyToThrow || delayCounter <= 0 || lastTarget == null || !lastTarget.isAlive()) {
            return;
        }

        delayCounter--;

        if (delayCounter <= 0) {
            // Кид перла
            mc.interactionManager.interactItem(mc.player, mc.player.getActiveHand());
            mc.player.swingHand(mc.player.getActiveHand());

            // Swap back на булаву
            InvUtils.swapBack();

            // Сброс
            readyToThrow = false;
            lastTarget = null;
            delayCounter = 0;
        }
    }

    private boolean isValidTarget(Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive() || mc.player.distanceTo(target) > maxRange.get()) return false;

        if (onlyPlayers.get() && !(target instanceof PlayerEntity)) return false;

        if (onlyHigher.get()) {
            // Центр игрока и цели (без getPos())
            double playerCenterY = mc.player.getY() + mc.player.getHeight() / 2;
            double targetCenterY = target.getY() + target.getHeight() / 2;
            if (targetCenterY - playerCenterY < heightDiff.get()) return false;
        }

        return true;
    }

    @Override
    public String getInfoString() {
        return pearlSlot.get() + " | " + throwDelay.get() + "t";
    }
}
