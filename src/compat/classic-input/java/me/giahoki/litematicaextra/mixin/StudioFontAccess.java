package me.giahoki.litematicaextra.mixin;

import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextRenderer.class)
public interface StudioFontAccess {
    @Invoker("getFontStorage")
    net.minecraft.client.font.FontStorage studio$getStorage(net.minecraft.util.Identifier id);
}
