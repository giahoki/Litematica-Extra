package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.NativeCreativeWorld;
import net.minecraft.entity.FallingBlockEntity;
import me.giahoki.litematicaextra.Platform;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class FrozenFallingEntityMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void extra$stabilizeFallingEntity(CallbackInfo ci){
        FallingBlockEntity entity=(FallingBlockEntity)(Object)this;
        var world=Platform.world(entity);
        if(NativeCreativeWorld.physicsFrozen(world)){
            var pos=entity.getBlockPos();
            if(world.getBlockState(pos).isAir())world.setBlockState(pos,entity.getBlockState(),3);
            entity.discard();ci.cancel();
        }
    }
}
