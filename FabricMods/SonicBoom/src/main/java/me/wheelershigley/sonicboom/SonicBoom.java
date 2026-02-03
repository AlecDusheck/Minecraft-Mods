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

    // Speed thresholds (blocks per tick)
    public static final double SONIC_BOOM_THRESHOLD = 1.8;    // ~36 blocks/sec
    public static final double CONTRAIL_THRESHOLD = 1.0;      // ~20 blocks/sec
    public static final double WIND_SOUND_THRESHOLD = 0.8;    // ~16 blocks/sec

    // Height requirement - must be this many blocks above ground for sonic boom
    public static final int MIN_HEIGHT_ABOVE_GROUND = 20;

    // Boost settings
    public static final double BOOST_MULTIPLIER = 1.4;
    public static final int BOOM_COOLDOWN_TICKS = 100; // 5 seconds

    // Track player states
    private static final Map<UUID, FlightState> flightStates = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayer(player);
            }
        });

        LOGGER.info("Sonic Boom loaded! Break the sound barrier with your elytra!");
    }

    private void tickPlayer(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        FlightState state = flightStates.computeIfAbsent(id, k -> new FlightState());

        // Decrease cooldowns
        if (state.boomCooldown > 0) state.boomCooldown--;
        if (state.windSoundCooldown > 0) state.windSoundCooldown--;

        // Only process if flying with elytra
        if (!player.isGliding()) {
            state.wasFlying = false;
            state.speedBuildup = 0;
            return;
        }

        Vec3d velocity = player.getVelocity();
        double speed = velocity.length();

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // === CONTRAILS at medium-high speed ===
        if (speed > CONTRAIL_THRESHOLD) {
            spawnContrails(player, world, speed);
        }

        // === WIND SOUNDS at high speed ===
        if (speed > WIND_SOUND_THRESHOLD && state.windSoundCooldown <= 0) {
            float pitch = 0.5f + (float)(speed / 3.0);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS,
                0.3f, Math.min(pitch, 2.0f));
            state.windSoundCooldown = 10;
        }

        // === SONIC BOOM at threshold ===
        // Requires: high speed, cooldown expired, already flying, and high enough altitude
        boolean highEnough = isHighEnough(player, world);
        if (speed > SONIC_BOOM_THRESHOLD && state.boomCooldown <= 0 && state.wasFlying && highEnough) {
            triggerSonicBoom(player, world, velocity, speed);
            state.boomCooldown = BOOM_COOLDOWN_TICKS;
            state.speedBuildup = 0;
        }

        // === SPEED BUILDUP for smoother acceleration ===
        if (speed > CONTRAIL_THRESHOLD) {
            state.speedBuildup = Math.min(state.speedBuildup + 0.01, 1.0);

            // Subtle speed maintenance at high velocity (reduces drag feel)
            if (state.speedBuildup > 0.5 && speed > SONIC_BOOM_THRESHOLD * 0.8) {
                Vec3d boost = velocity.normalize().multiply(0.01 * state.speedBuildup);
                player.addVelocity(boost.x, boost.y * 0.5, boost.z);
            }
        }

        state.wasFlying = true;
        state.lastSpeed = speed;
    }

    private boolean isHighEnough(ServerPlayerEntity player, ServerWorld world) {
        int playerY = (int) player.getY();
        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) player.getX(), (int) player.getZ());
        return (playerY - groundY) >= MIN_HEIGHT_ABOVE_GROUND;
    }

    private void spawnContrails(ServerPlayerEntity player, ServerWorld world, double speed) {
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d velocity = player.getVelocity().normalize();

        // Trail behind the player
        double trailOffset = -0.5;
        double x = pos.x + velocity.x * trailOffset;
        double y = pos.y + velocity.y * trailOffset + 0.3;
        double z = pos.z + velocity.z * trailOffset;

        // More particles at higher speeds
        int particleCount = (int) Math.min(3, speed);

        // Cloud/smoke contrails
        world.spawnParticles(ParticleTypes.CLOUD, x, y, z,
            particleCount, 0.1, 0.1, 0.1, 0.02);

        // Subtle end rod sparkles at very high speed
        if (speed > SONIC_BOOM_THRESHOLD * 0.9) {
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z,
                1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void triggerSonicBoom(ServerPlayerEntity player, ServerWorld world, Vec3d velocity, double speed) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // === SOUND EFFECTS ===
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 1.5f);
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8f, 2.0f);
        world.playSound(null, x, y, z,
            SoundEvents.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 1.0f, 1.2f);

        // === SHOCKWAVE PARTICLES ===
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.0 + ring * 0.5;
            int points = 16 + ring * 8;
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;

                world.spawnParticles(ParticleTypes.CLOUD, px, y, pz,
                    1, 0.1, 0.1, 0.1, 0.05);
                world.spawnParticles(ParticleTypes.EXPLOSION, px, y, pz,
                    1, 0, 0, 0, 0);
            }
        }

        // Central burst effects
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0, 0, 0, 0);

        // Smoke burst
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
            20, 0.5, 0.5, 0.5, 0.1);

        // End rod sparkles
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z,
            30, 1, 1, 1, 0.2);

        // Gust particles for wind effect
        world.spawnParticles(ParticleTypes.GUST, x, y, z, 5, 0.5, 0.5, 0.5, 0);

        // === SPEED BOOST ===
        Vec3d boost = velocity.normalize().multiply(BOOST_MULTIPLIER);
        player.addVelocity(boost.x, boost.y, boost.z);

        // Brief speed effect for visual feedback
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.SPEED, 40, 2, false, false, true));

        // Notify player
        player.sendMessage(
            net.minecraft.text.Text.literal("§b§l>> SONIC BOOM! <<"), true);
    }

    private static class FlightState {
        boolean wasFlying = false;
        double lastSpeed = 0;
        double speedBuildup = 0;
        int boomCooldown = 0;
        int windSoundCooldown = 0;
    }
}
