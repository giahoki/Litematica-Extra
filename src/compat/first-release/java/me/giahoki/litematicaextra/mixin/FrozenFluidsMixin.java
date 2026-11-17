package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public abstract class FrozenFluidsMixin {
    @Inject(method="onScheduledTick",at=@At("HEAD"),cancellable=true)
    private void extra$keepFluid(World world,BlockPos pos,CallbackInfo ci){
        if(NativeCreativeWorld.fluidsFrozen(world)){
            world.scheduleFluidTick(pos,((FluidState)(Object)this).getFluid(),NativeCreativeWorld.PHYSICS_TICK_RETRY_DELAY);
            ci.cancel();
        }
    }
}
