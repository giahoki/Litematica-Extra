package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class StarterBlockTrackingMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void extra$track(BlockPos pos,BlockState state,int flags,CallbackInfoReturnable<BlockState> cir){
        var chunk=(WorldChunk)(Object)this;
        var previous=cir.getReturnValue();
        if(previous!=null&&chunk.getWorld() instanceof ServerWorld world){
            NativeCreativeWorld.trackBlockChange(world,pos,previous,state);
        }
    }
}
