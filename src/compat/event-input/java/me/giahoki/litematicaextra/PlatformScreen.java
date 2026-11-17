package me.giahoki.litematicaextra;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;

abstract class PlatformScreen extends Screen {
    PlatformScreen(Text title) { super(title); }

    static net.minecraft.client.font.TextRenderer studioFont(net.minecraft.client.font.TextRenderer renderer) {
        var original = ((me.giahoki.litematicaextra.mixin.StudioFontAccess) (Object) renderer).studio$getFonts();
        return new net.minecraft.client.font.TextRenderer(new net.minecraft.client.font.TextRenderer.GlyphsProvider() {
            private final net.minecraft.text.StyleSpriteSource font =
                    new net.minecraft.text.StyleSpriteSource.Font(StudioTheme.FONT);

            @Override public net.minecraft.client.font.GlyphProvider getGlyphs(net.minecraft.text.StyleSpriteSource source) {
                return original.getGlyphs(font);
            }

            @Override public net.minecraft.client.font.EffectGlyph getRectangleGlyph() {
                return original.getRectangleGlyph();
            }
        });
    }

    @Override public final boolean mouseClicked(Click click, boolean doubled) {
        return onMouseClicked(click.x(), click.y(), click.button(), () -> super.mouseClicked(click, doubled));
    }
    protected boolean onMouseClicked(double x, double y, int button, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean mouseDragged(Click click, double dx, double dy) {
        return onMouseDragged(click.x(), click.y(), click.button(), dx, dy, () -> super.mouseDragged(click, dx, dy));
    }
    protected boolean onMouseDragged(double x, double y, int button, double dx, double dy, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean mouseReleased(Click click) {
        return onMouseReleased(click.x(), click.y(), click.button(), () -> super.mouseReleased(click));
    }
    protected boolean onMouseReleased(double x, double y, int button, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean keyPressed(KeyInput key) {
        return onKeyPressed(key.key(), key.scancode(), key.modifiers(), () -> super.keyPressed(key));
    }
    protected boolean onKeyPressed(int key, int scan, int modifiers, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean keyReleased(KeyInput key) {
        return onKeyReleased(key.key(), key.scancode(), key.modifiers(), () -> super.keyReleased(key));
    }
    protected boolean onKeyReleased(int key, int scan, int modifiers, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }
}
