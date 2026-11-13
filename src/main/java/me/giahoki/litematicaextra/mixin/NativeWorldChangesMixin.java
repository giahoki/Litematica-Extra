package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class NativeWorldChangesMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("RETURN"))
    private void extra$track(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir){
        if((Object)this instanceof ServerWorld world&&cir.getReturnValueZ()&&!state.isAir())NativeCreativeWorld.track(world,pos);
    }
}
