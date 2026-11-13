package me.giahoki.litematicaextra.mixin;
import me.giahoki.litematicaextra.PlacementScreen;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class PlacementHudMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void extra$hideHud(CallbackInfo ci){if(PlacementScreen.current()!=null)ci.cancel();}
}
