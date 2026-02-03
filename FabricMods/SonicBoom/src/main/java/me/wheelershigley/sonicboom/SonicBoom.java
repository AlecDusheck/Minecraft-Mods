package me.wheelershigley.sonicboom;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SonicBoom implements ModInitializer {
    public static final String MOD_ID = "sonicboom";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Mach speed thresholds (blocks per tick) - each tier unlocks at higher speeds
    public static final double MACH_1_THRESHOLD = 1.0;   // ~20 blocks/sec - easy entry
    public static final double MACH_2_THRESHOLD = 1.6;   // ~32 blocks/sec
    public static final double MACH_3_THRESHOLD = 2.2;   // ~44 blocks/sec
    public static final double MACH_4_THRESHOLD = 2.8;   // ~56 blocks/sec - extreme speed

    public static final double CONTRAIL_THRESHOLD = 0.6;
    public static final int MIN_HEIGHT_ABOVE_GROUND = 10;
    public static final int BOOM_COOLDOWN_TICKS = 40; // 2 seconds

    private static final Map<UUID, FlightState> flightStates = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayer(player);
            }
        });
        LOGGER.info("Sonic Boom loaded!");
    }

    private void tickPlayer(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        FlightState state = flightStates.computeIfAbsent(id, k -> new FlightState());

        if (state.boomCooldown > 0) state.boomCooldown--;

        if (!player.isGliding()) {
            state.wasFlying = false;
            state.currentMach = 0;
            state.speedBuildup = 0;
            return;
        }

        Vec3d velocity = player.getVelocity();
        double speed = velocity.length();
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Contrails at any decent speed
        if (speed > CONTRAIL_THRESHOLD) {
            spawnContrails(player, world, speed, state.currentMach);
        }

        // Check for mach level transitions
        if (state.wasFlying && state.boomCooldown <= 0 && isHighEnough(player, world)) {
            int newMach = getMachLevel(speed);

            // Trigger boom when breaking into a NEW higher mach level
            if (newMach > state.currentMach && newMach >= 1) {
                triggerSonicBoom(player, world, velocity, newMach);
                state.boomCooldown = BOOM_COOLDOWN_TICKS;
                state.currentMach = newMach;
            } else if (newMach > state.currentMach) {
                // Smoothly update mach without boom if below mach 1
                state.currentMach = newMach;
            }
        }

        // Momentum maintenance scales with mach level
        if (speed > MACH_1_THRESHOLD * 0.8 && state.currentMach >= 1) {
            double momentumBoost = 0.005 * state.currentMach;
            Vec3d boost = velocity.normalize().multiply(momentumBoost);
            player.addVelocity(boost.x, boost.y * 0.3, boost.z);
        }

        // Decay mach level if slowing down significantly
        if (speed < getMachThreshold(state.currentMach) * 0.7) {
            state.currentMach = Math.max(0, getMachLevel(speed));
        }

        state.wasFlying = true;
        state.lastSpeed = speed;
    }

    private int getMachLevel(double speed) {
        if (speed >= MACH_4_THRESHOLD) return 4;
        if (speed >= MACH_3_THRESHOLD) return 3;
        if (speed >= MACH_2_THRESHOLD) return 2;
        if (speed >= MACH_1_THRESHOLD) return 1;
        return 0;
    }

    private double getMachThreshold(int mach) {
        return switch (mach) {
            case 4 -> MACH_4_THRESHOLD;
            case 3 -> MACH_3_THRESHOLD;
            case 2 -> MACH_2_THRESHOLD;
            case 1 -> MACH_1_THRESHOLD;
            default -> 0;
        };
    }

    private boolean isHighEnough(ServerPlayerEntity player, ServerWorld world) {
        int playerY = (int) player.getY();
        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) player.getX(), (int) player.getZ());
        return (playerY - groundY) >= MIN_HEIGHT_ABOVE_GROUND;
    }

    private void spawnContrails(ServerPlayerEntity player, ServerWorld world, double speed, int mach) {
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d vel = player.getVelocity().normalize();

        double x = pos.x - vel.x * 0.5;
        double y = pos.y - vel.y * 0.5 + 0.3;
        double z = pos.z - vel.z * 0.5;

        // Base contrails
        int count = 1 + mach;
        world.spawnParticles(ParticleTypes.CLOUD, x, y, z, count, 0.1, 0.1, 0.1, 0.01);

        // Extra effects at higher mach
        if (mach >= 2) {
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.005);
        }
        if (mach >= 3) {
            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        if (mach >= 4) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void triggerSonicBoom(ServerPlayerEntity player, ServerWorld world, Vec3d velocity, int mach) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        Vec3d dir = velocity.normalize();

        // Scale everything by mach level - kept subtle
        float volume = 0.6f + mach * 0.25f;
        float pitch = 1.5f + mach * 0.1f;
        double boostMult = 0.5 + mach * 0.4;
        int rings = 1 + mach;
        int particles = 8 + mach * 8;

        // === SOUNDS - layered and scaled ===
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, volume, pitch);
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, volume * 0.8f, pitch * 0.9f);

        if (mach >= 2) {
            world.playSound(null, x, y, z,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, volume * 0.6f, pitch);
        }
        if (mach >= 3) {
            world.playSound(null, x, y, z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, volume * 0.5f, pitch);
        }
        if (mach >= 4) {
            world.playSound(null, x, y, z,
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.5f);
        }

        // === SHOCKWAVE RINGS ===
        for (int ring = 0; ring < rings; ring++) {
            double radius = 1.0 + ring * 0.8;
            int points = 16 + ring * 6;
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;

                world.spawnParticles(ParticleTypes.CLOUD, px, y, pz, 1, 0.1, 0.1, 0.1, 0.05);
                if (mach >= 2) {
                    world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, y, pz, 1, 0, 0, 0, 0);
                }
            }
        }

        // === CONE TRAIL BEHIND ===
        for (int i = 0; i < particles; i++) {
            double spread = i * 0.1;
            double dist = -i * 0.25;
            double ox = (world.random.nextDouble() - 0.5) * spread;
            double oy = (world.random.nextDouble() - 0.5) * spread;
            double oz = (world.random.nextDouble() - 0.5) * spread;

            world.spawnParticles(ParticleTypes.CLOUD,
                x + dir.x * dist + ox,
                y + dir.y * dist + oy,
                z + dir.z * dist + oz,
                1, 0, 0, 0, 0.01);
        }

        // Central effects - subtle but satisfying
        world.spawnParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, particles / 3, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z, particles / 2, 0.5, 0.5, 0.5, 0.1);

        if (mach >= 3) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.8, 0.8, 0.8, 0.1);
        }
        if (mach >= 4) {
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.GUST, x, y, z, 3, 1, 1, 1, 0);
        }

        // === SPEED BOOST ===
        Vec3d boost = velocity.normalize().multiply(boostMult);
        player.addVelocity(boost.x, boost.y * 0.5, boost.z);

        // Buffs scale with mach
        int duration = 30 + mach * 15;
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.SPEED, duration, mach - 1, false, false, false));
        if (mach >= 3) {
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, duration / 2, 0, false, false, false));
        }
    }

    private static class FlightState {
        boolean wasFlying = false;
        double lastSpeed = 0;
        double speedBuildup = 0;
        int currentMach = 0;
        int boomCooldown = 0;
    }
}
