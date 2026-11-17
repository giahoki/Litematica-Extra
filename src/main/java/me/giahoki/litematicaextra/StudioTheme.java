package me.giahoki.litematicaextra;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.util.Identifier;

final class StudioTheme {
    static final Identifier FONT = Identifier.of("litematica_extra", "studio");
    static final int TEXT = 0xffeff2f6;
    static final int MUTED = 0xffaab3bf;
    static final int ACCENT = 0xff35ebd9;
    static final int BORDER = 0xff535e69;

    private StudioTheme() {}

    static void round(DrawContext context, int x, int y, int width, int height, int color) {
        if (width < 2 || height < 2) return;
        Platform.roundedRect(context, new ScreenRect(x - 1, y - 1, width + 2, height + 2), color);
    }

    static void surface(DrawContext context, int x, int y, int width, int height, int fill, int border) {
        round(context, x, y, width, height, border);
        round(context, x + 1, y + 1, width - 2, height - 2, fill);
    }

    static void button(DrawContext context, TextRenderer font, String label, int x, int y, int width,
                       int height, ExtraButton.Tone tone, boolean active, boolean hover, boolean focused) {
        int fill = switch (tone) {
            case ACCENT -> hover ? 0xff78f6e8 : ACCENT;
            case DANGER -> hover ? 0xff583435 : 0xff362426;
            case QUIET -> hover ? 0xff303a44 : 0xff1c232b;
            case NORMAL -> hover ? 0xff303a44 : 0xff242a32;
        };
        int border = switch (tone) {
            case ACCENT -> fill;
            case DANGER -> 0xff784246;
            case QUIET, NORMAL -> BORDER;
        };
        int ink = switch (tone) {
            case ACCENT -> 0xff082825;
            case DANGER -> 0xffff8b84;
            case QUIET, NORMAL -> TEXT;
        };
        if (!active) {
            fill = 0xff191f26;
            border = 0xff343d47;
            ink = 0xff8b95a1;
        } else if (focused) {
            border = ACCENT;
        }
        surface(context, x, y, width, height, fill, border);
        String clipped = font.trimToWidth(label, Math.max(1, width - 6));
        context.drawText(font, clipped, x + (width - font.getWidth(clipped)) / 2,
                y + (height - font.fontHeight + 1) / 2, ink, false);
    }
}
