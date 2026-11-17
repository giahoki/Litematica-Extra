package me.giahoki.litematicaextra;

import java.util.Properties;
import net.minecraft.text.Text;

record CreativePhysics(boolean fluids,boolean gravity) {
    static final CreativePhysics LEGACY=new CreativePhysics(true,false);

    static Text option(String key,boolean enabled){
        return ExtraText.label(key,Text.translatable(enabled?"options.on":"options.off"));
    }

    static CreativePhysics read(Properties properties){
        return new CreativePhysics(Boolean.parseBoolean(properties.getProperty("simulateFluids","true")),
                Boolean.parseBoolean(properties.getProperty("simulateGravity","false")));
    }

    void write(Properties properties){
        properties.setProperty("simulateFluids",Boolean.toString(fluids));
        properties.setProperty("simulateGravity",Boolean.toString(gravity));
    }
}
