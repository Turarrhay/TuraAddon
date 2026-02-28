package com.example.addon.modules;

import com.example.addon.TuraAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Максимальная дистанция до цели.")
        .defaultValue(4.5)
        .min(2).max(7)
        .sliderRange(2, 7)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Режим наведения.")
        .defaultValue(Mode.Normal)
        .build()
    );

    private final Setting<Double> yawSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw-speed")
        .description("Скорость yaw (Normal mode).")
        .defaultValue(0.10)
        .min(0.01).max(9.0)
        .sliderRange(0.01, 9.0)
        .build()
    );

    private final Setting<Double> pitchSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-speed")
        .description("Скорость pitch (Normal mode).")
        .defaultValue(0.085)
        .min(0.01).max(9.0)
        .sliderRange(0.01, 9.0)
        .build()
    );

    private final Setting<Double> noiseYawMult = sgGeneral.add(new DoubleSetting.Builder()
        .name("noise-yaw-mult")
        .description("Множитель шума по yaw (± значение в градусах). 0 = без шума.")
        .defaultValue(1.2)
        .min(0).max(5)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Double> noisePitchMult = sgGeneral.add(new DoubleSetting.Builder()
        .name("noise-pitch-mult")
        .description("Множитель шума по pitch (± значение в градусах). 0 = без шума.")
        .defaultValue(0.9)
        .min(0).max(5)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<AimPoint> aimPoint = sgGeneral.add(new EnumSetting.Builder<AimPoint>()
        .name("aim-point")
        .description("Точка прицела (override в special modes).")
        .defaultValue(AimPoint.Body)
        .build()
    );

    private final Setting<Boolean> onlyAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-attacking")
        .description("Только при зажатой ЛКМ.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> animals = sgGeneral.add(new BoolSetting.Builder()
        .name("animals")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> monsters = sgGeneral.add(new BoolSetting.Builder()
        .name("monsters")
        .defaultValue(false)
        .build()
    );

    // ────────────────────────────────────────────────
    // Настройка отключения при смене мира
    private final Setting<Boolean> disableOnWorldChange = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-world-change")
        .description("Автоматически выключать модуль при смене мира / измерения / телепорте.")
        .defaultValue(true)
        .build()
    );

    private final Random random = new Random();

    // Переменная для отслеживания мира
    private World lastWorld = null;

    public AimAssist() {
        super(TuraAddon.pvp, "Aim-Assist", "Smooth assist with PvP top modes and customizable noise. idk why i add pvp modes");
    }

    @Override
    public void onActivate() {
        lastWorld = mc.world;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Проверка смены мира только если настройка включена
        if (disableOnWorldChange.get()) {
            if (mc.world != lastWorld) {
                if (isActive()) {
                    toggle();
                    info("AimAssist off: change world/dimension/teleport.");
                }
                lastWorld = mc.world;
                return;
            }
        }

        // Основная логика ассиста
        if (onlyAttack.get() && !mc.options.attackKey.isPressed()) return;

        LivingEntity target = findClosest();
        if (target == null) return;

        Vec3d targetPos = getTargetPos(target);
        Vec3d eyes = mc.player.getEyePos();
        Vec3d dir = targetPos.subtract(eyes);

        double dx = dir.x, dy = dir.y, dz = dir.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz == 0) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

        float noiseYaw = 0f;
        float noisePitch = 0f;

        if (mode.get() == Mode.Marlow) {
            noiseYaw   = (random.nextFloat() - 0.5f) * 2.5f;
            noisePitch = (random.nextFloat() - 0.5f) * 1.8f;
        } else if (mode.get() == Mode.Kylaz) {
            noiseYaw   = (random.nextFloat() - 0.5f) * 1.2f;
            noisePitch = (random.nextFloat() - 0.5f) * 0.9f;
        } else if (mode.get() == Mode.Rappture) {
            noiseYaw   = (random.nextFloat() - 0.5f) * 1.5f;
            noisePitch = (random.nextFloat() - 0.5f) * 1.2f;
        } else {
            float yawMult   = noiseYawMult.get().floatValue();
            float pitchMult = noisePitchMult.get().floatValue();

            if (yawMult   > 0) noiseYaw   = (random.nextFloat() - 0.5f) * yawMult;
            if (pitchMult > 0) noisePitch = (random.nextFloat() - 0.5f) * pitchMult;
        }

        targetYaw   += noiseYaw;
        targetPitch += noisePitch;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapDegrees(targetPitch - currentPitch);

        float ySpeed, pSpeed;
        if (mode.get() == Mode.Marlow) {
            ySpeed = 0.95f;
            pSpeed = 0.92f;
        } else if (mode.get() == Mode.Kylaz) {
            ySpeed = 0.88f;
            pSpeed = 0.90f;
        } else if (mode.get() == Mode.Rappture) {
            ySpeed = 0.92f;
            pSpeed = 0.94f;
        } else {
            ySpeed = yawSpeed.get().floatValue();
            pSpeed = pitchSpeed.get().floatValue();
        }

        float yawDelta = MathHelper.clamp(yawDiff, -ySpeed, ySpeed);
        float pitchDelta = MathHelper.clamp(pitchDiff, -pSpeed, pSpeed);

        float lockMultiplier = switch (mode.get()) {
            case Kylaz, Rappture -> 1.9f;
            case Marlow -> 2.2f;
            default -> 2.2f;
        };

        if (Math.abs(yawDiff) < ySpeed * lockMultiplier) yawDelta = yawDiff;
        if (Math.abs(pitchDiff) < pSpeed * lockMultiplier) pitchDelta = pitchDiff;

        mc.player.setYaw(currentYaw + yawDelta);
        mc.player.setPitch(currentPitch + pitchDelta);
    }

    private LivingEntity findClosest() {
        Comparator<LivingEntity> comparator = switch (mode.get()) {
            case Marlow -> Comparator.comparingDouble(this::getAngleDeltaSqr);
            default -> Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e));
        };

        return mc.world.getEntitiesByClass(LivingEntity.class,
                mc.player.getBoundingBox().expand(range.get() + 0.8),
                this::isValid)
            .stream()
            .filter(e -> e.isAlive() && e != mc.player)
            .min(comparator)
            .orElse(null);
    }

    private double getAngleDeltaSqr(LivingEntity e) {
        Vec3d targetPos = getTargetPos(e);
        Vec3d eyes = mc.player.getEyePos();
        Vec3d dir = targetPos.subtract(eyes);

        double dx = dir.x, dy = dir.y, dz = dir.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz == 0) return Double.MAX_VALUE;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapDegrees(targetPitch - currentPitch);

        return yawDiff * yawDiff + pitchDiff * pitchDiff;
    }

    private Vec3d getTargetPos(LivingEntity target) {
        AimPoint effectiveAim = switch (mode.get()) {
            case Marlow -> AimPoint.Neck;
            case Kylaz -> AimPoint.Head;
            case Rappture -> AimPoint.Body;
            default -> aimPoint.get();
        };

        double heightFraction = switch (effectiveAim) {
            case Head -> 0.92;
            case Neck -> 0.78;
            case Body -> 0.50;
            case Legs -> 0.18;
            default -> 0.50;
        };

        double targetY = target.getY() + (target.getHeight() * heightFraction);
        return new Vec3d(target.getX(), targetY, target.getZ());
    }

    private boolean isValid(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof AnimalEntity) return animals.get();
        if (e instanceof HostileEntity) return monsters.get();
        return false;
    }

    public enum Mode {
        Normal("Обычный"),
        Marlow("Marlowww (crystal snap, neck, angle prio)"),
        Kylaz("Kylaz (sword HT1, precise head, distance prio)"),
        Rappture("Rappture (mace HT1, body smash, fast pitch)");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum AimPoint {
        Head("Head"),
        Neck("Neck"),
        Body("Body"),
        Legs("Legs");

        private final String title;

        AimPoint(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
