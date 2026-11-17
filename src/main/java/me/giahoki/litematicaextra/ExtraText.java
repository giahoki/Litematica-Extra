package me.giahoki.litematicaextra;

import net.minecraft.text.Text;

public final class ExtraText {
    private ExtraText() {}

    public static Text label(String key, Object... arguments) {
        return Text.translatable("litematica_extra." + key, arguments);
    }

    public static String message(String key, Object... arguments) {
        return label(key, arguments).getString();
    }
}
