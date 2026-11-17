package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.StudioScreen;
import me.giahoki.litematicaextra.ExtraText;
import me.giahoki.litematicaextra.ExtraIcons;
import me.giahoki.litematicaextra.PlacementScreen;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GuiMainMenu.class,remap=false)
public abstract class LitematicaMenuMixin extends GuiBase {
    @Shadow private int getButtonWidth(){throw new AssertionError();}
    @Inject(method="initGui",at=@At("TAIL"),remap=false)
    private void extra$addEditorButton(CallbackInfo ci){
        int w=getButtonWidth();
        ButtonGeneric button=new ButtonGeneric(12+w+20,52,w,20,"3D Editor",ExtraIcons.EDITOR);
        button.setHoverStrings(ExtraText.message("studio.menu_tip"));
        addButton(button,(b,mouseButton)->MinecraftClient.getInstance().setScreen(new StudioScreen((Screen)(Object)this)));
        ButtonGeneric selection=new ButtonGeneric(12,184,w,20,ExtraText.message("selection.title"),ExtraIcons.EDITOR);
        addButton(selection,(b,mouseButton)->PlacementScreen.openSelection());
    }
}
