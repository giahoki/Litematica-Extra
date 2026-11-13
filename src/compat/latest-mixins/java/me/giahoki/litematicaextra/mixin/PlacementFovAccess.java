package me.giahoki.litematicaextra.mixin;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface PlacementFovAccess {
    @Invoker("getFov") float extra$fov(Camera camera,float tickProgress,boolean changingFov);
}
