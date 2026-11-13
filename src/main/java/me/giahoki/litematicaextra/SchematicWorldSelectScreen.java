package me.giahoki.litematicaextra;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;
import java.nio.file.*;
import java.util.*;

public final class SchematicWorldSelectScreen extends PlatformScreen {
    private final Screen parent;
    private final List<Path> files=new ArrayList<>(),filtered=new ArrayList<>();
    private Path root,selected;
    private int scroll;
    private TextFieldWidget search;
    private ButtonWidget load;
    private String status="Выбери схему — она откроется в отдельном одиночном мире",query="";
    public SchematicWorldSelectScreen(Screen parent){super(Text.literal("Litematica extra — локальный креатив"));this.parent=parent;}
    @Override protected void init(){
        root=SchematicFiles.directory();
        search=addDrawableChild(new TextFieldWidget(textRenderer,width/2-150,36,300,20,Text.literal("Поиск схем")));
        search.setPlaceholder(Text.literal("Поиск .litematic…"));search.setText(query);search.setChangedListener(s->{query=s;filter();});
        load=addDrawableChild(ButtonWidget.builder(Text.literal("Загрузить в креативе"),b->loadSelected()).dimensions(width/2-154,height-30,150,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Создать лайтматику"),b->createEmpty()).dimensions(width/2-154,height-55,150,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Открыть папку схем"),b->openFolder()).dimensions(width/2+4,height-55,150,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->close()).dimensions(width/2+4,height-30,150,20).build());
        try{
            Files.createDirectories(root);files.clear();
            try(var walk=Files.walk(root,8)){walk.filter(Files::isRegularFile).filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".litematic")).sorted().limit(10000).forEach(files::add);}
            filter();
        }catch(Exception e){status=e.getMessage();}
        load.active=selected!=null;
    }
    private void filter(){filtered.clear();for(Path p:files)if(root.relativize(p).toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))filtered.add(p);scroll=0;}
    private int rows(){return Math.max(1,(height-164)/23);}
    private void openFolder(){
        try{
            Files.createDirectories(root);
            Util.getOperatingSystem().open(root);
        }catch(Exception e){status=e.getMessage();}
    }
    private void createEmpty(){
        try{NativeCreativeWorld.createEmpty(root,this);}catch(Exception e){status=e.getMessage();}
    }
    private void loadSelected(){
        if(selected==null)return;
        try{NativeCreativeWorld.open(new SchematicDocument(selected),this);}catch(Exception e){status=e.getMessage();}
    }
    @Override public void render(DrawContext g,int mx,int my,float delta){
        // Screen.renderWithTooltip already renders the background (including blur) in 1.21.8.
        g.drawCenteredTextWithShadow(textRenderer,title,width/2,15,0xffffffff);
        int left=Math.max(12,width/2-180),right=Math.min(width-12,width/2+180);
        g.fill(left,65,right,height-92,0x99000000);
        g.enableScissor(left,65,right,height-92);
        for(int row=0;row<rows()&&scroll+row<filtered.size();row++){
            Path p=filtered.get(scroll+row);int y=69+row*23;
            if(p.equals(selected)){g.fill(left+2,y-2,right-2,y+20,0x99516b75);GuiMesh.border(g,left+2,y-2,right-left-4,22,0xffa1ded0);}
            else if(mx>=left&&mx<right&&my>=y-2&&my<y+20)g.fill(left+2,y-2,right-2,y+20,0x554f6471);
            g.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(root.relativize(p).toString(),right-left-18),left+9,y+4,0xffeeeeee);
        }
        if(filtered.isEmpty())g.drawTextWithShadow(textRenderer,"Схемы не найдены. Папка: schematics",left+9,77,0xffaaaaaa);
        g.disableScissor();
        g.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status==null?"Ошибка чтения":status,width-20),width/2,height-79,0xffc9dcda);
        super.render(g,mx,my,delta);
    }
    @Override protected boolean onMouseClicked(double x,double y,int button,BooleanSupplier vanilla){
        if(vanilla.getAsBoolean())return true;
        int left=Math.max(12,width/2-180),right=Math.min(width-12,width/2+180);
        if(button==0&&x>=left&&x<right&&y>=67&&y<Math.min(height-92,67+rows()*23)){
            int index=(int)((y-67)/23)+scroll;if(index<filtered.size()){selected=filtered.get(index);load.active=true;status="Ванильный креатив · песок и жидкости остаются на месте";}return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.max(0,Math.min(Math.max(0,filtered.size()-rows()),scroll-(int)Math.signum(v)*3));return true;}
    @Override protected boolean onKeyPressed(int key,int scan,int mods,BooleanSupplier vanilla){if(key==GLFW.GLFW_KEY_ENTER&&!search.isFocused()){loadSelected();return true;}return vanilla.getAsBoolean();}
    @Override public void close(){client.setScreen(parent);}
}
