package me.giahoki.litematicaextra;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

import static me.giahoki.litematicaextra.CreativePhysics.option;

final class CreativeEnvironmentScreen extends PlatformScreen {
    interface Launch {
        void open(CreativeEnvironment environment,CreativePhysics physics,Screen fallback) throws Exception;
    }

    private final Screen parent;
    private final Launch launch;
    private String error="";
    private boolean fluids,gravity;
    private int top,step;

    CreativeEnvironmentScreen(Screen parent,Launch launch) {
        super(ExtraText.label("world.environment"));
        this.parent=parent;
        this.launch=launch;
    }

    @Override protected void init() {
        int x=(width-Math.min(200,width-16))/2;
        int w=width-2*x;
        step=Math.min(23,Math.max(12,(height-43)/8));
        top=Math.max(20,(height-step*8)/2);
        int y=top;
        addDrawableChild(ButtonWidget.builder(option("world.physics.fluids",fluids),button->{
            fluids=!fluids;
            button.setMessage(option("world.physics.fluids",fluids));
        }).dimensions(x,y,w,step-2).build());
        y+=step;
        addDrawableChild(ButtonWidget.builder(option("world.physics.gravity",gravity),button->{
            gravity=!gravity;
            button.setMessage(option("world.physics.gravity",gravity));
        }).dimensions(x,y,w,step-2).build());
        y+=step;
        for(var environment:CreativeEnvironment.values()){
            addDrawableChild(ButtonWidget.builder(ExtraText.label("world.environment."+environment.id),button->{
                try{launch.open(environment,new CreativePhysics(fluids,gravity),this);}catch(Exception failure){
                    error=failure.getMessage()==null?ExtraText.message("world.read_error"):failure.getMessage();
                }
            }).dimensions(x,y,w,step-2).build());
            y+=step;
        }
        addDrawableChild(ButtonWidget.builder(ExtraText.label("back"),button->close())
                .dimensions(x,y,w,step-2).build());
    }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,top-15,0xffffffff);
        if(!error.isEmpty())context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(error,width-16),width/2,top+step*8+3,0xffff8b84);
        super.render(context,mouseX,mouseY,delta);
    }

    @Override public void close(){client.setScreen(parent);}
}
