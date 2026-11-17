package me.giahoki.litematicaextra;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

final class SaveSchematicScreen extends PlatformScreen {
    private final Screen parent;
    private final Function<String,String> save;
    private String name;
    private String error="";
    private TextFieldWidget input;

    SaveSchematicScreen(Screen parent,String name,Function<String,String> save){
        super(ExtraText.label("save.title"));
        this.parent=parent;
        this.name=name;
        this.save=save;
    }

    @Override protected void init(){
        int fieldWidth=Math.min(300,width-24);
        input=addDrawableChild(new TextFieldWidget(textRenderer,(width-fieldWidth)/2,height/2-10,fieldWidth,20,title));
        input.setMaxLength(100);
        input.setText(name);
        input.setChangedListener(value->{name=value;error="";});
        addDrawableChild(ButtonWidget.builder(ExtraText.label("save.submit"),b->submit()).dimensions(width/2-104,height/2+25,100,20).build());
        addDrawableChild(ButtonWidget.builder(ExtraText.label("cancel"),b->close()).dimensions(width/2+4,height/2+25,100,20).build());
        setInitialFocus(input);
    }

    private void submit(){
        if(input.active)error=save.apply(name);
    }

    void saving(){input.active=false;error=ExtraText.message("save.progress");}
    void failed(String message){input.active=true;error=message;}

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(input.active)client.setScreen(parent);}
    @Override protected boolean onKeyPressed(int key,int scan,int modifiers,BooleanSupplier vanilla){
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){submit();return true;}
        return vanilla.getAsBoolean();
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-30,0xffffffff);
        context.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(error,width-24),width/2,height/2+51,0xffffb7a6);
        super.render(context,mouseX,mouseY,delta);
    }
}
