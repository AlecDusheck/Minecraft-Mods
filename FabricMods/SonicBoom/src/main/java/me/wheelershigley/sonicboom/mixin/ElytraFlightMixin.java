package me.wheelershigley.sonicboom.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to enhance elytra flight physics for smoother, more dynamic flying.
 */
@Mixin(LivingEntity.class)
public abstract class ElytraFlightMixin {

    /**
     * Reduces air drag at high speeds for smoother momentum.
     * Vanilla elytra has aggressive drag that makes high-speed flight feel sluggish.
     */
    @Inject(method = "travel", at = @At("HEAD"))
    private void sonicboom$enhanceElytraPhysics(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Only apply to players gliding with elytra
        if (!(self instanceof PlayerEntity player)) return;
        if (!player.isGliding()) return;

        Vec3d velocity = player.getVelocity();
        double speed = velocity.horizontalLength();

        // At high horizontal speeds, slightly reduce drag by adding back momentum
        // This creates a smoother flight feel without being overpowered
        if (speed > 1.2) {
            // Calculate drag compensation (very subtle)
            double compensation = 0.003 * (speed - 1.2);
            compensation = Math.min(compensation, 0.02); // Cap it

            Vec3d horizontalDir = new Vec3d(velocity.x, 0, velocity.z).normalize();
            player.addVelocity(
                horizontalDir.x * compensation,
                0,
                horizontalDir.z * compensation
            );
        }

        // Improve vertical stability at high speeds
        // Reduces the "bobbing" effect when flying fast
        if (speed > 1.5 && Math.abs(velocity.y) < 0.3) {
            double verticalDamping = velocity.y * 0.02;
            player.addVelocity(0, -verticalDamping, 0);
        }
    }
}
