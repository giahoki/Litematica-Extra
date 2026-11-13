package me.giahoki.litematicaextra.mixin;

import me.giahoki.litematicaextra.PlacementScreen;
import me.giahoki.litematicaextra.ExtraIcons;
import org.spongepowered.asm.mixin.Unique;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GuiSchematicLoad.class,remap=false)
public abstract class PlacementLoadMixin extends GuiSchematicBrowserBase {
    @Unique private int extra$browserHeight;
    protected PlacementLoadMixin(){super(12,24);}
    @Override protected int getBrowserHeight(){return extra$browserHeight>0?extra$browserHeight:super.getBrowserHeight();}
    @Inject(method="initGui",at=@At("TAIL"),remap=false)
    private void extra$placement(CallbackInfo ci){
        ButtonGeneric button=new ButtonGeneric(0,0,getStringWidth("Размещение")+30,20,"Размещение",ExtraIcons.PLACEMENT);
        button.setHoverStrings("Загрузить голограмму и разместить её стрелками X / Y / Z из локальной фрикамеры");
        addButton(button,(b,mouse)->{
            if(mc.world==null||mc.player==null){addMessage(MessageType.ERROR,"Сначала зайди в мир или на сервер");return;}
            var list=getListWidget();var entry=list==null?null:list.getLastSelectedEntry();
            if(entry==null){addMessage(MessageType.ERROR,"Выбери файл .litematic");return;}
            var schematic=LitematicaSchematic.createFromFile(entry.getDirectory(),entry.getName());
            if(schematic==null){addMessage(MessageType.ERROR,"Не удалось прочитать схему .litematic");return;}
            PlacementScreen.open(schematic);
        });
        var access=(ExtraGuiAccess)this;var buttons=access.extra$buttons();
        buttons.remove(button);buttons.add(1,button);
        int available=getScreenWidth()-24,x=0,row=0;
        int[] xs=new int[buttons.size()],rs=new int[buttons.size()];
        for(int i=0;i<buttons.size();i++){
            var b=buttons.get(i);int w=Math.min(b.getWidth(),available);b.setWidth(w);
            if(x>0&&x+w>available){row++;x=0;}
            xs[i]=12+x;rs[i]=row;x+=w+4;
        }
        int footer=getScreenHeight()-26-row*24;
        for(int i=0;i<buttons.size();i++)buttons.get(i).setPosition(xs[i],footer+rs[i]*24);
        var last=buttons.getLast();last.setX(getScreenWidth()-12-last.getWidth());
        int checkboxY=footer-14;
        for(var w:access.extra$widgets())if(w instanceof fi.dy.masa.malilib.gui.widgets.WidgetCheckBox)w.setY(checkboxY);
        extra$browserHeight=Math.max(40,checkboxY-30);
        if(getListWidget()!=null)getListWidget().setSize(getBrowserWidth(),extra$browserHeight);
    }
}
