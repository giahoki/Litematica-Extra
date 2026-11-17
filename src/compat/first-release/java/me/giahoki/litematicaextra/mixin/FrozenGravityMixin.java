package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WorldAccess;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class FrozenGravityMixin {
    private boolean extra$isGravityBlock(){var block=((BlockState)(Object)this).getBlock();return block instanceof FallingBlock||block instanceof DragonEggBlock;}
    @Inject(method="scheduledTick",at=@At("HEAD"),cancellable=true)
    private void extra$freezeGravity(ServerWorld world,BlockPos pos,Random random,CallbackInfo ci){
        if(extra$isGravityBlock()&&NativeCreativeWorld.physicsFrozen(world)){
            if(FallingBlock.canFallThrough(world.getBlockState(pos.down())))
                world.scheduleBlockTick(pos,((BlockState)(Object)this).getBlock(),NativeCreativeWorld.PHYSICS_TICK_RETRY_DELAY);
            ci.cancel();
        }
    }
    @Inject(method="getStateForNeighborUpdate",at=@At("HEAD"),cancellable=true)
    private void extra$keepPowder(Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos,CallbackInfoReturnable<BlockState> cir){
        if(extra$isGravityBlock()&&NativeCreativeWorld.physicsFrozen(world)){
            BlockState state=(BlockState)(Object)this;
            if(direction==Direction.DOWN&&FallingBlock.canFallThrough(neighbor))
                world.scheduleBlockTick(pos,state.getBlock(),NativeCreativeWorld.PHYSICS_TICK_RETRY_DELAY);
            cir.setReturnValue(state);
        }
    }
    @Inject(method="getStateForNeighborUpdate",at=@At("RETURN"),cancellable=true)
    private void extra$keepUnsupportedBlock(Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos,CallbackInfoReturnable<BlockState> cir){
        BlockState current=(BlockState)(Object)this;
        if(!current.isAir()&&cir.getReturnValue().isAir()&&NativeCreativeWorld.physicsFrozen(world))cir.setReturnValue(current);
    }
}
