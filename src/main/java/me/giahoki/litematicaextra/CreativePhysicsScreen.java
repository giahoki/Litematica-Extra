package me.giahoki.litematicaextra;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import static me.giahoki.litematicaextra.CreativePhysics.option;

final class CreativePhysicsScreen extends PlatformScreen {
    private final Screen parent;
    private CreativePhysics physics;
    private ButtonWidget fluids,gravity;
    private boolean saving;
    private Text error;

    CreativePhysicsScreen(Screen parent,CreativePhysics physics){
        super(ExtraText.label("world.physics"));
        this.parent=parent;
        this.physics=physics;
    }

    @Override protected void init(){
        int w=Math.min(220,width-16);
        int x=(width-w)/2,y=height/2-34;
        fluids=addDrawableChild(ButtonWidget.builder(option("world.physics.fluids",physics.fluids()),
                b->update(new CreativePhysics(!physics.fluids(),physics.gravity()))).dimensions(x,y,w,20).build());
        gravity=addDrawableChild(ButtonWidget.builder(option("world.physics.gravity",physics.gravity()),
                b->update(new CreativePhysics(physics.fluids(),!physics.gravity()))).dimensions(x,y+24,w,20).build());
        addDrawableChild(ButtonWidget.builder(ExtraText.label("back"),b->close()).dimensions(x,y+55,w,20).build());
        refreshButtons();
    }

    private void refreshButtons(){
        fluids.active=gravity.active=!saving;
        fluids.setMessage(option("world.physics.fluids",physics.fluids()));
        gravity.setMessage(option("world.physics.gravity",physics.gravity()));
    }

    private void update(CreativePhysics next){
        saving=true;
        error=null;
        refreshButtons();
        NativeCreativeWorld.updatePhysics(next).whenCompleteAsync((saved,failure)->{
            saving=false;
            if(Boolean.TRUE.equals(saved))physics=next;
            else error=ExtraText.label("world.physics.error");
            refreshButtons();
        },client);
    }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-50,0xffffffff);
        if(error!=null)context.drawCenteredTextWithShadow(textRenderer,error,width/2,height/2+48,0xffff8b84);
    }

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(!saving)client.setScreen(parent);}
}
