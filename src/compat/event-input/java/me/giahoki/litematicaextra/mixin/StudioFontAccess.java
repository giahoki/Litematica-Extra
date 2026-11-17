package me.giahoki.litematicaextra.mixin;

import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextRenderer.class)
public interface StudioFontAccess {
    @Accessor("fonts")
    TextRenderer.GlyphsProvider studio$getFonts();
}
