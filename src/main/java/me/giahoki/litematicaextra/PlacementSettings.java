package me.giahoki.litematicaextra;

import fi.dy.masa.malilib.config.options.ConfigBoolean;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.Properties;
import org.slf4j.LoggerFactory;

public final class PlacementSettings {
    public static final ConfigBoolean HOLD_ALT=new ConfigBoolean("extraPlacementHoldAlt",false);
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("litematica-extra.properties");
    private static boolean initialized;
    public static void init(){
        if(initialized)return;initialized=true;
        Properties p=new Properties();
        if(Files.isRegularFile(FILE))try(var in=Files.newInputStream(FILE)){p.load(in);HOLD_ALT.setBooleanValue(Boolean.parseBoolean(p.getProperty("placementHoldAlt","false")));}
        catch(Exception e){LoggerFactory.getLogger("litematica_extra").warn("Could not load placement settings",e);}
        HOLD_ALT.setValueChangeCallback(c->save());
    }
    public static void updateLabels(){
        HOLD_ALT.setPrettyName(ExtraText.message("config.hold_alt"));
        HOLD_ALT.setTranslatedName(ExtraText.message("config.hold_alt"));
        HOLD_ALT.setComment(ExtraText.message("config.hold_alt.help"));
    }
    private static void save(){
        Properties p=new Properties();p.setProperty("placementHoldAlt",Boolean.toString(HOLD_ALT.getBooleanValue()));
        try{Files.createDirectories(FILE.getParent());try(var out=Files.newOutputStream(FILE)){p.store(out,"Litematica extra");}}
        catch(Exception e){LoggerFactory.getLogger("litematica_extra").warn("Could not save placement settings",e);}
    }
    private PlacementSettings(){}
}
