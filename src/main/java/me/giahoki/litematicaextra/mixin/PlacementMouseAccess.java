package me.giahoki.litematicaextra.mixin;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Mouse.class)
public interface PlacementMouseAccess {
    @Accessor("cursorLocked") void extra$cursorLocked(boolean value);
    @Accessor("x") void extra$x(double value);
    @Accessor("y") void extra$y(double value);
    @Accessor("hasResolutionChanged") void extra$resetDelta(boolean value);
}
