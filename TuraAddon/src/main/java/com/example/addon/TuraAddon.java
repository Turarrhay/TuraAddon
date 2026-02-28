package com.example.addon;

import com.example.addon.modules.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import meteordevelopment.meteorclient.systems.modules.Category;


public class TuraAddon extends MeteorAddon {

    public static final Category pvp = new Category("TA", new ItemStack(Items.DIAMOND_SWORD));
    public static final HudGroup HUD_GROUP = new HudGroup("Tura Addon");




    public static int MyScreenVERSION = 15;

    @Override
    public void onInitialize() {
        Modules.get().add(new StunSlam());
        Modules.get().add(new WindPearlMacrros());
        Modules.get().add(new AimAssist());
        Modules.get().add(new MacePearl());
        Modules.get().add(new KillSound());
        Modules.get().add(new BreachSwap());


        Hud.get().register(Watermark.INFO);

    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(pvp);


        //mc.setScreen(new MyScreen(GuiThemes.get()));
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }


}
