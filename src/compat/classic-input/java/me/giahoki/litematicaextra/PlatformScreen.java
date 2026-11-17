package me.giahoki.litematicaextra;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class PlatformScreen extends Screen {
    PlatformScreen(Text title) { super(title); }

    static net.minecraft.client.font.TextRenderer studioFont(net.minecraft.client.font.TextRenderer renderer) {
        var fonts = (me.giahoki.litematicaextra.mixin.StudioFontAccess) (Object) renderer;
        return new net.minecraft.client.font.TextRenderer(id -> fonts.studio$getStorage(StudioTheme.FONT), false);
    }

    @Override public final boolean mouseClicked(double x, double y, int button) {
        return onMouseClicked(x, y, button, () -> super.mouseClicked(x, y, button));
    }
    protected boolean onMouseClicked(double x, double y, int button, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return onMouseDragged(x, y, button, dx, dy, () -> super.mouseDragged(x, y, button, dx, dy));
    }
    protected boolean onMouseDragged(double x, double y, int button, double dx, double dy, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean mouseReleased(double x, double y, int button) {
        return onMouseReleased(x, y, button, () -> super.mouseReleased(x, y, button));
    }
    protected boolean onMouseReleased(double x, double y, int button, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean keyPressed(int key, int scan, int modifiers) {
        return onKeyPressed(key, scan, modifiers, () -> super.keyPressed(key, scan, modifiers));
    }
    protected boolean onKeyPressed(int key, int scan, int modifiers, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }

    @Override public final boolean keyReleased(int key, int scan, int modifiers) {
        return onKeyReleased(key, scan, modifiers, () -> super.keyReleased(key, scan, modifiers));
    }
    protected boolean onKeyReleased(int key, int scan, int modifiers, BooleanSupplier vanilla) {
        return vanilla.getAsBoolean();
    }
}
