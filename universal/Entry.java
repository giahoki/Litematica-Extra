package me.giahoki.universal;

import net.fabricmc.api.ClientModInitializer;

public final class Entry implements ClientModInitializer {
    @Override public void onInitializeClient() {
        String version = Selector.minecraft();
        String patch = version.equals("1.21") ? "0" : version.substring("1.21.".length());
        try {
            var type = Class.forName("me.giahoki.litematicaextra.v" + patch + ".ExtraClient");
            ((ClientModInitializer) type.getConstructor().newInstance()).onInitializeClient();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not initialize Litematica extra on Minecraft " + version, e);
        }
    }
}
