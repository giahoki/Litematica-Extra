package me.giahoki.litematicaextra.mixin;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=GuiBase.class,remap=false)
public interface ExtraGuiAccess {
    @Accessor("buttons") List<ButtonBase> extra$buttons();
    @Accessor("widgets") List<WidgetBase> extra$widgets();
}
