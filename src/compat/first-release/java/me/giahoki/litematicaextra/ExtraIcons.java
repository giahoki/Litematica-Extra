package me.giahoki.litematicaextra;

import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public enum ExtraIcons implements IGuiIcon {
    EDITOR(0), PLACEMENT(16);
    private final int v;
    ExtraIcons(int v){this.v=v;}
    @Override public int getWidth(){return 16;}
    @Override public int getHeight(){return 16;}
    @Override public int getU(){return 0;}
    @Override public int getV(){return v;}
    @Override public Identifier getTexture(){return Identifier.of("litematica_extra","textures/gui/extra_icons.png");}
    public void renderAt(DrawContext g,int x,int y,float z,boolean enabled,boolean selected){
        Platform.texture(g,getTexture(),x,y,(enabled?16:0)+(selected?16:0),getV(),16,16,16,16,256,256);
    }
    public void renderAt(int x,int y,float z,boolean enabled,boolean selected,DrawContext g){
        renderAt(g,x,y,z,enabled,selected);
    }
    @Override public void renderAt(int x,int y,float z,boolean enabled,boolean selected){
        fi.dy.masa.malilib.render.RenderUtils.bindTexture(getTexture());
        fi.dy.masa.malilib.render.RenderUtils.drawTexturedRect(x,y,(enabled?16:0)+(selected?16:0),getV(),16,16,z);
    }
}
