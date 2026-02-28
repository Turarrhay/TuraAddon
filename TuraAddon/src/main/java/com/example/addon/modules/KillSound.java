package com.example.addon.modules;  // ← замени на свой пакет

import com.example.addon.TuraAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;  // ← правильный импорт
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public class KillSound extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Громкость (0.0 – 2.0)")
        .defaultValue(1.0)
        .min(0.0).max(2.0)
        .sliderRange(0.0, 2.0)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Тон (0.5 – 2.0)")
        .defaultValue(1.0)
        .min(0.5).max(2.0)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private final Setting<Boolean> randomPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("random-pitch")
        .description("Случайный тон ±0.3")
        .defaultValue(true)
        .build()
    );

    private final Setting<SoundType> soundType = sgGeneral.add(new EnumSetting.Builder<SoundType>()
        .name("sound")
        .description("Звук при килле")
        .defaultValue(SoundType.LevelUp)
        .build()
    );

    private PlayerEntity lastHit = null;

    public KillSound() {
        super(TuraAddon.pvp, "kill-sound", "ЗSound when killing a player");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity entity = event.entity;

        if (!(entity instanceof PlayerEntity player)) return;

        lastHit = player;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {  // ← правильный TickEvent.Post
        if (lastHit == null) return;

        if (lastHit.isDead() || lastHit.getHealth() <= 0.0f) {
            playKillSound();
            lastHit = null;  // сбрасываем, чтобы не повторялось
        }
    }

    private void playKillSound() {
        double finalPitch = pitch.get();

        if (randomPitch.get()) {
            finalPitch += (Math.random() * 0.6 - 0.3);
            finalPitch = MathHelper.clamp(finalPitch, 0.5, 2.0);
        }

        SoundEvent sound = switch (soundType.get()) {
            case Crit           -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
            case Strong         -> SoundEvents.ENTITY_PLAYER_ATTACK_STRONG;
            case LevelUp        -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case Totem          -> SoundEvents.ITEM_TOTEM_USE;
            case Explosion      -> null;
            case WitherSpawn    -> SoundEvents.ENTITY_WITHER_SPAWN;
            case DragonGrowl    -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
            case OrbPickup      -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case Bell           -> SoundEvents.BLOCK_BELL_RESONATE;
        };

        mc.getSoundManager().play(
            new net.minecraft.client.sound.PositionedSoundInstance(
                sound,
                SoundCategory.PLAYERS,
                volume.get().floatValue(),
                (float) finalPitch,
                Random.create(),
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
            )
        );
    }

    public enum SoundType {
        Crit("Crit"),
        Strong("Strong Attack"),
        LevelUp("Level Up"),
        Totem("Totem Use"),
        Explosion("Explosion (ts is not work)"),
        WitherSpawn("Wither Spawn"),
        DragonGrowl("Dragon Growl"),
        OrbPickup("Orb Pickup"),
        Bell("Bell");

        private final String title;
        SoundType(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    @Override
    public String getInfoString() {
        return soundType.get().toString();
    }
}
