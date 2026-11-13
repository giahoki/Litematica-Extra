package me.giahoki.litematicaextra.mixin;
import me.giahoki.litematicaextra.PlacementScreen;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class PlacementRenderMixin {
    @Inject(method="renderHand",at=@At("HEAD"),cancellable=true)
    private void extra$hideHand(CallbackInfo ci){if(PlacementScreen.current()!=null)ci.cancel();}
}
