package com.example.addon.modules;

import com.example.addon.TuraAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;

public class WindPearlMacrros extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("Задержка")
        .description("Задержка между броском эндер пёрлом и зарядом ветра.")
        .defaultValue(8)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build());


    private int tickCounter = 0;
    private boolean throwingPearl = false;
    private int previousSlot = -1;

    public WindPearlMacrros() {
        super(TuraAddon.pvp, "pearl-wind-macro", "Сначала бросает эндер-пёрл, затем использует заряд ветра после задержки..");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        int pearlSlot = findPearlSlot();
        if (pearlSlot == -1) {
            error("Ender Pearl was not found in Hotbar.");
            toggle();
            return;
        }

        previousSlot = getSelectedSlotReflectively();

        InvUtils.swap(pearlSlot, true);
        throwPearl();
        throwingPearl = true;
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!throwingPearl || mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter >= delayTicks.get()) {
            int windSlot = findWindChargeSlot();
            if (windSlot == -1) {
                error("Wind Charge not found in Hotbar.");
                toggle();
                return;
            }

            InvUtils.swap(windSlot, true);
            useWindCharge();

            if (previousSlot != -1) InvUtils.swap(previousSlot, true);
            toggle();
        }
    }

    private void throwPearl() {
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void useWindCharge() {
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private int findWindChargeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.WIND_CHARGE)) return i;
        }
        return -1;
    }

    private int getSelectedSlotReflectively() {
        try {
            Field field = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            field.setAccessible(true);
            return field.getInt(mc.player.getInventory());
        } catch (Exception e) {
            return -1;
        }
    }


}
