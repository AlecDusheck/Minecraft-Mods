package me.wheelershigley.bemine;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ValentineEffect {
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final List<TameableEntity> pets = new ArrayList<>();
    private final Vec3d startPos;

    private int ticksElapsed = 0;
    private static final int DURATION_TICKS = 200; // 10 seconds
    private static final int RISE_TICKS = 40;
    private static final int FALL_TICKS = 40;
    private static final int PET_COUNT = 6;
    private static final double ORBIT_RADIUS = 2.5;

    public ValentineEffect(ServerPlayerEntity player) {
        this.player = player;
        this.world = (ServerWorld) player.getEntityWorld();
        this.startPos = new Vec3d(player.getX(), player.getY(), player.getZ());

        spawnPets();
        playStartSound();
    }

    private void spawnPets() {
        for (int i = 0; i < PET_COUNT; i++) {
            TameableEntity pet;
            if (i % 2 == 0) {
                pet = EntityType.CAT.create(world, SpawnReason.COMMAND);
            } else {
                pet = EntityType.WOLF.create(world, SpawnReason.COMMAND);
            }

            if (pet != null) {
                double angle = (2 * Math.PI * i) / PET_COUNT;
                double x = startPos.x + Math.cos(angle) * ORBIT_RADIUS;
                double z = startPos.z + Math.sin(angle) * ORBIT_RADIUS;

                pet.setPosition(x, startPos.y, z);
                pet.setInvulnerable(true);
                pet.setNoGravity(true);
                pet.setSilent(true);
                pet.setAiDisabled(true);

                world.spawnEntity(pet);
                pets.add(pet);
            }
        }
    }

    private void playStartSound() {
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_CAT_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.5f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    public boolean tick() {
        ticksElapsed++;

        if (!player.isAlive() || player.isDisconnected()) {
            cleanup();
            return true;
        }

        if (ticksElapsed <= RISE_TICKS) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 5, 2, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false));
        } else if (ticksElapsed == RISE_TICKS + 1) {
            player.removeStatusEffect(StatusEffects.LEVITATION);
        } else if (ticksElapsed >= DURATION_TICKS - FALL_TICKS) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false));
        }

        spawnParticles();
        updatePets();

        if (ticksElapsed >= DURATION_TICKS) {
            cleanup();
            return true;
        }

        return false;
    }

    private void spawnParticles() {
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // Heart particles
        for (int i = 0; i < 3; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 2;
            double offsetY = world.random.nextDouble() * 2;
            double offsetZ = (world.random.nextDouble() - 0.5) * 2;
            world.spawnParticles(ParticleTypes.HEART,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, 0, 0, 0);
        }

        // Cherry blossom particles
        for (int i = 0; i < 5; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 3;
            double offsetY = world.random.nextDouble() * 2.5;
            double offsetZ = (world.random.nextDouble() - 0.5) * 3;
            world.spawnParticles(ParticleTypes.CHERRY_LEAVES,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0, -0.05, 0, 0.02);
        }

        // Enchant spiral
        double spiralAngle = ticksElapsed * 0.2;
        for (int i = 0; i < 2; i++) {
            double angle = spiralAngle + (Math.PI * i);
            double radius = 1.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + (ticksElapsed % 20) * 0.1;
            world.spawnParticles(ParticleTypes.ENCHANT,
                x, y, z, 1, 0, 0.1, 0, 0);
        }

        // Note particles
        if (ticksElapsed % 10 == 0) {
            world.spawnParticles(ParticleTypes.NOTE,
                pos.x, pos.y + 2.5, pos.z,
                1, 0.5, 0.2, 0.5, 0);
        }
    }

    private void updatePets() {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double baseAngle = ticksElapsed * 0.1;
        double bobOffset = Math.sin(ticksElapsed * 0.15) * 0.3;

        for (int i = 0; i < pets.size(); i++) {
            TameableEntity pet = pets.get(i);
            if (pet.isAlive()) {
                double angle = baseAngle + (2 * Math.PI * i) / pets.size();
                double x = playerPos.x + Math.cos(angle) * ORBIT_RADIUS;
                double z = playerPos.z + Math.sin(angle) * ORBIT_RADIUS;
                double y = playerPos.y + 0.5 + bobOffset + (i % 2 == 0 ? 0.3 : 0);

                pet.setPosition(x, y, z);

                double dx = playerPos.x - x;
                double dz = playerPos.z - z;
                float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90;
                pet.setYaw(yaw);
                pet.setHeadYaw(yaw);
                pet.setBodyYaw(yaw);

                if (ticksElapsed % 20 == i * 3) {
                    world.spawnParticles(ParticleTypes.HEART,
                        x, y + 0.8, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void cleanup() {
        for (TameableEntity pet : pets) {
            if (pet.isAlive()) {
                world.spawnParticles(ParticleTypes.POOF,
                    pet.getX(), pet.getY() + 0.5, pet.getZ(),
                    5, 0.2, 0.2, 0.2, 0.02);
                pet.discard();
            }
        }
        pets.clear();

        player.removeStatusEffect(StatusEffects.LEVITATION);

        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        world.spawnParticles(ParticleTypes.HEART,
            pos.x, pos.y + 1, pos.z, 15, 1, 1, 1, 0.1);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }
}
