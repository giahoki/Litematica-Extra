package me.giahoki.litematicaextra;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ExtraButton extends ButtonWidget {
    public enum Tone { NORMAL, ACCENT, DANGER, QUIET }
    private net.minecraft.client.font.TextRenderer studioFont;
    void useStudioTheme(net.minecraft.client.font.TextRenderer font) { studioFont = font; }
    private final Tone tone;
    public ExtraButton(String label,int x,int y,int width,int height,Tone tone,Runnable action){
        super(x,y,width,height,Text.literal(label),b->action.run(),DEFAULT_NARRATION_SUPPLIER);this.tone=tone;
    }
    public static void round(DrawContext g,int x,int y,int w,int h,int color){
        if(w<=0||h<=0)return;
        g.fill(x+2,y,x+w-2,y+h,color);g.fill(x,y+2,x+w,y+h-2,color);
        g.fill(x+1,y+1,x+w-1,y+h-1,color);
    }
    @Override protected void renderWidget(DrawContext g,int mouseX,int mouseY,float delta){
        boolean hover=isHovered()||isFocused();
        if(studioFont!=null){
            StudioTheme.button(g,studioFont,getMessage().getString(),getX(),getY(),getWidth(),getHeight(),
                    tone,active,hover,isFocused());
            return;
        }
        int background=switch(tone){case ACCENT->hover?0xff65e1ce:0xff46c7b5;case DANGER->hover?0xff603343:0xff34232f;case QUIET->hover?0xff283849:0xff182331;default->hover?0xff334459:0xff243244;};
        int text=tone==Tone.ACCENT?0xff082c2a:tone==Tone.DANGER?0xffffa7b4:0xffdce8f4;
        if(!active){background=0xff18212d;text=0xff536174;}
        round(g,getX(),getY(),getWidth(),getHeight(),background);
        if(isFocused()&&active)GuiMesh.border(g,getX(),getY(),getWidth(),getHeight(),0xffb3f5e6);
        var font=MinecraftClient.getInstance().textRenderer;
        String label=font.trimToWidth(getMessage().getString(),Math.max(1,getWidth()-8));
        g.drawText(font,label,getX()+(getWidth()-font.getWidth(label))/2,getY()+(getHeight()-8)/2,text,false);
    }
}
