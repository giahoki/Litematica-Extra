package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.PlacementSettings;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=GuiConfigs.class,remap=false)
public class PlacementConfigMixin {
    @Inject(method="getConfigs",at=@At("RETURN"),cancellable=true,remap=false)
    private void extra$config(CallbackInfoReturnable<List<ConfigOptionWrapper>> cir){
        if(DataManager.getConfigGuiTab()==GuiConfigs.ConfigGuiTab.GENERIC){
            PlacementSettings.init();var list=new ArrayList<>(cir.getReturnValue());
            list.add(0,new ConfigOptionWrapper(PlacementSettings.HOLD_ALT));cir.setReturnValue(list);
        }
    }
}
