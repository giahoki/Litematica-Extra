package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public abstract class FrozenFluidsMixin {
    @Inject(method="onScheduledTick",at=@At("HEAD"),cancellable=true)
    private void extra$keepFluid(ServerWorld world,BlockPos pos,BlockState state,CallbackInfo ci){
        if(NativeCreativeWorld.physicsFrozen(world))ci.cancel();
    }
    @Inject(method="onRandomTick",at=@At("HEAD"),cancellable=true)
    private void extra$keepLava(ServerWorld world,BlockPos pos,net.minecraft.util.math.random.Random random,CallbackInfo ci){
        if(NativeCreativeWorld.physicsFrozen(world))ci.cancel();
    }
}
