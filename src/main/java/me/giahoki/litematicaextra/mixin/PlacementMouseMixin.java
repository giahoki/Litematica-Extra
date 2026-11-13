package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.PlacementScreen;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Mouse.class)
public abstract class PlacementMouseMixin {
    @Redirect(method="updateMouse",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void extra$look(ClientPlayerEntity player,double dx,double dy){
        var screen=PlacementScreen.current();
        if(screen!=null)screen.look(dx,dy);else player.changeLookDirection(dx,dy);
    }
}
