package me.giahoki.litematicaextra;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class StudioField extends TextFieldWidget {
    StudioField(TextRenderer font, int x, int y, int width, int height, Text label) {
        super(font, x + 4, y, width - 8, height, label);
        setDrawsBackground(false);
        setEditableColor(StudioTheme.TEXT);
    }

    @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        StudioTheme.surface(context, getX() - 4, getY(), getWidth() + 8, getHeight(), 0xff10151b,
                isFocused() ? StudioTheme.ACCENT : StudioTheme.BORDER);
        // A borderless vanilla field starts text at its Y coordinate.
        int y = getY();
        setY(y + (getHeight() - 8) / 2);
        try {
            super.renderWidget(context, mouseX, mouseY, delta);
        } finally {
            setY(y);
        }
    }
}
