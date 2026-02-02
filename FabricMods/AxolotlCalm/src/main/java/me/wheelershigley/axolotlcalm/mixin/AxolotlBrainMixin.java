package me.wheelershigley.axolotlcalm.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aggressively throttles axolotl AI to reduce CPU usage.
 * Skips 80% of all brain ticks.
 */
@Mixin(AxolotlEntity.class)
public abstract class AxolotlBrainMixin extends AnimalEntity {

    protected AxolotlBrainMixin(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "mobTick", at = @At("HEAD"), cancellable = true)
    private void axolotlcalm$throttleBrainTick(CallbackInfo ci) {
        if (this.random.nextFloat() < 0.8f) {
            ci.cancel();
        }
    }
}
