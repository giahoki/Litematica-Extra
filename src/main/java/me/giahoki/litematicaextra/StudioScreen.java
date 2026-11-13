package me.giahoki.litematicaextra;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.*;
import static me.giahoki.litematicaextra.ExtraButton.Tone.*;

public final class StudioScreen extends PlatformScreen {
    private static final Identifier ICON=Identifier.of("litematica_extra","icon.png");
    private static final int BG=0xff0c111b,PANEL=0xff151e2b,BORDER=0xff263347,TEXT=0xffe2ecf7,MUTED=0xff879ab3,ACCENT=0xff66dfc9;
    private final Screen parent;
    private final PreviewRenderer preview=new PreviewRenderer();
    private final List<Path> files=new ArrayList<>(),filtered=new ArrayList<>();
    private SchematicDocument document;
    private SchematicDocument.Cell selection;
    private Path root;
    private TextFieldWidget search,replacement;
    private ExtraButton replace,replaceAll,delete,undo,redo,save,place,playerView;
    private int left,right,rx,vx,vy,vw,vh,bottom,listY,listOffset,dragButton=-1;
    private String status="Выбери схему в библиотеке",query="",blockInput="minecraft:stone";
    private boolean statusError,grid=true,showHelp;

    public StudioScreen(){this(new GuiMainMenu());}
    public StudioScreen(Screen parent){super(Text.literal("Litematica extra"));this.parent=parent;}
    @Override protected void init(){
        left=Math.max(96,Math.min(180,width/5));right=Math.max(124,Math.min(208,width/4));
        rx=width-right-8;vx=left+16;vy=44;vw=Math.max(1,rx-vx-8);bottom=height-28;vh=Math.max(1,bottom-vy);listY=94;
        root=SchematicFiles.directory();
        playerView=control("Креатив-мир",14,bottom-24,left-12,18,ExtraButton.Tone.ACCENT,"Открыть настоящий локальный мир. Доступно со стартового экрана",this::openPlayerView);
        control("?",vx+vw-23,vy+4,17,16,QUIET,"Управление камерой и горячие клавиши",()->showHelp=!showHelp);
        control("‹",8,10,20,22,QUIET,"Вернуться в меню Litematica (Esc)",this::close);
        save=control("Сохранить",width-165,10,76,22,ExtraButton.Tone.ACCENT,"Сохранить новую копию схемы (Ctrl+S)",this::save);
        place=control("В Litematica",width-83,10,75,22,NORMAL,"Сохранить копию и создать размещение у игрока",this::place);
        control("F5",left-18,49,22,16,QUIET,"Обновить список схем",this::refresh);
        search=addDrawableChild(new TextFieldWidget(textRenderer,14,69,left-12,18,Text.literal("Поиск схем")));
        search.setPlaceholder(Text.literal("Поиск…"));search.setText(query);search.setEditableColor(TEXT);search.setChangedListener(s->{query=s;filter();});
        replacement=addDrawableChild(new TextFieldWidget(textRenderer,rx+8,117,right-16,18,Text.literal("Новый блок")));
        replacement.setMaxLength(256);replacement.setText(blockInput);replacement.setEditableColor(TEXT);
        replacement.setTooltip(Tooltip.of(Text.literal("ID блока: minecraft:stone\nСвойства: oak_stairs[facing=east,half=bottom]")));
        replacement.setChangedListener(s->{blockInput=s;updateButtons();});
        replace=control("Заменить блок",rx+8,141,right-16,19,ExtraButton.Tone.ACCENT,"Изменить выбранный блок",()->edit(false,false));
        replaceAll=control("Заменить этот тип",rx+8,164,right-16,19,NORMAL,"Заменить все блоки с таким ID. Свойства будут взяты из поля выше",()->edit(true,false));
        delete=control("Удалить блок",rx+8,187,right-16,19,DANGER,"Заменить выбранный блок воздухом (Delete)",()->edit(false,true));
        int ty=bottom-24,tx=vx+6;
        undo=control(vw>=156?"Отм.":"↶",tx,ty,28,18,QUIET,"Отменить (Ctrl+Z)",()->history(false));
        redo=control(vw>=156?"Повт.":"↷",tx+32,ty,32,18,QUIET,"Повторить (Ctrl+Y)",()->history(true));
        if(vw>=156){
            control("Вид",tx+68,ty,32,18,NORMAL,"Сбросить камеру (Home)",preview::resetView);
            control("−",tx+104,ty,18,18,QUIET,"Отдалить",()->preview.camera.zoomSteps(-1));
            control("+",tx+126,ty,18,18,QUIET,"Приблизить",()->preview.camera.zoomSteps(1));
        }
        if(files.isEmpty())refresh();else filter();
        updateButtons();
    }
    private ExtraButton control(String label,int x,int y,int w,int h,ExtraButton.Tone tone,String tip,Runnable action){
        ExtraButton b=addDrawableChild(new ExtraButton(label,x,y,w,h,tone,action));b.setTooltip(Tooltip.of(Text.literal(tip)));return b;
    }
    private void refresh(){
        files.clear();
        try{
            Files.createDirectories(root);
            try(var walk=Files.walk(root,8)){
                walk.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litematic"))
                        .sorted().limit(10_000).forEach(files::add);
            }
            filter();
        }catch(Exception e){error(e);}
    }
    private void filter(){
        filtered.clear();for(Path p:files)if(root.relativize(p).toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))filtered.add(p);
        listOffset=0;
    }
    private int visibleRows(){return Math.max(1,(bottom-listY-31)/28);}
    private void updateButtons(){
        if(replace==null)return;
        boolean selected=document!=null&&selection!=null&&!document.state(selection).isAir();
        replace.active=replaceAll.active=selected&&!blockInput.isBlank();delete.active=selected;
        undo.active=document!=null&&document.canUndo();redo.active=document!=null&&document.canRedo();
        save.active=document!=null;place.active=document!=null&&client.world!=null&&client.player!=null;
        playerView.active=document!=null&&client.world==null;
    }
    void openPlayerView(){
        if(document==null)return;
        try{NativeCreativeWorld.open(document,this);}catch(Exception e){error(e);}
    }
    void returnFromPlayerView(){
        selection=null;
        try{preview.rebuild(document,true);info("Изменения Player View остались в схеме — можно сохранить копию");}catch(Exception e){error(e);}
        updateButtons();
    }
    private void load(Path path){
        if(document!=null&&path.equals(document.source))return;
        guard(()->{
            try{
                SchematicDocument loaded=new SchematicDocument(path);preview.rebuild(loaded,true);document=loaded;selection=null;
                info("Открыта схема: "+displayName(path));
            }catch(Exception e){if(document!=null)try{preview.rebuild(document,true);}catch(Exception ignored){}error(e);}
            updateButtons();
        });
    }
    private void guard(Runnable action){
        if(document==null||!document.dirty()){action.run();return;}
        client.setScreen(new ConfirmScreen(discard->{client.setScreen(this);if(discard)action.run();},
                Text.literal("Есть несохранённые изменения"),Text.literal("Вернись и сохрани копию или отбрось изменения."),Text.literal("Отбросить"),Text.literal("Назад")));
    }
    private void edit(boolean all,boolean remove){
        if(document==null||selection==null)return;
        try{
            BlockState state=remove?Blocks.AIR.getDefaultState():WorldApi.parseBlock(blockInput);
            if(document.state(selection).equals(state)){info("Этот блок уже выбранного типа");return;}
            document.replace(selection,state,all);
            try{preview.rebuild(document,false);}catch(Exception failure){document.undo();preview.rebuild(document,false);throw failure;}
            if(remove||state.isAir())selection=null;
            info(remove?"Блок удалён · Ctrl+Z — отменить":all?"Все блоки этого типа заменены":"Блок заменён · Ctrl+Z — отменить");
        }catch(Exception e){error(e);}
        updateButtons();
    }
    private void history(boolean forward){
        if(document==null)return;
        if(forward)document.redo();else document.undo();
        try{preview.rebuild(document,false);info(forward?"Действие повторено":"Действие отменено");}catch(Exception e){error(e);}
        selection=null;updateButtons();
    }
    private void save(){
        if(document==null)return;
        try{Path saved=document.saveCopy();info("Копия сохранена: "+saved.getFileName());refresh();}catch(Exception e){error(e);}
    }
    private void place(){
        if(document==null||client.player==null||client.world==null)return;
        try{
            Path path=document.saveCopy();var schematic=SchematicDocument.loadSchematic(path);
            if(schematic==null)throw new IllegalStateException("Не удалось загрузить сохранённую копию");
            SchematicHolder.getInstance().addSchematic(schematic,true);
            var manager=DataManager.getSchematicPlacementManager();
            var placement=SchematicPlacement.createFor(schematic,client.player.getBlockPos(),path.getFileName().toString(),true,true);
            manager.addSchematicPlacement(placement,true);manager.setSelectedSchematicPlacement(placement);
            info("Копия сохранена и размещена у игрока");refresh();
        }catch(Exception e){error(e);}
    }
    private void info(String message){status=message;statusError=false;}
    private void error(Exception e){status=e.getMessage()==null?"Не удалось выполнить действие":e.getMessage();statusError=true;LoggerFactory.getLogger("Litematica extra").warn("Editor operation failed",e);}
    private String displayName(Path file){return file.getFileName().toString().replaceFirst("(?i)\\.litematic$","");}
    private String middleName(String name,int max){
        if(textRenderer.getWidth(name)<=max)return name;
        int tail=Math.min(name.length(),8);
        while(tail>1&&textRenderer.getWidth("…"+name.substring(name.length()-tail))>max-12)tail--;
        String end="…"+name.substring(name.length()-tail);
        return textRenderer.trimToWidth(name,Math.max(0,max-textRenderer.getWidth(end)))+end;
    }
    private void text(DrawContext g,String s,int x,int y,int max,int color){g.drawText(textRenderer,textRenderer.trimToWidth(s,Math.max(1,max)),x,y,color,false);}
    private void panel(DrawContext g,int x,int y,int w,int h){ExtraButton.round(g,x,y,w,h,BORDER);ExtraButton.round(g,x+1,y+1,w-2,h-2,PANEL);}
    private void logo(DrawContext g,int x,int y,int size){Platform.texture(g,ICON,x,y,0,0,size,size,24,24,24,24);}
    @Override public void render(DrawContext g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,38,0xff111a28);g.fill(8,37,width-8,38,BORDER);
        logo(g,34,8,24);text(g,"Litematica extra",64,11,width-238,TEXT);text(g,"РЕДАКТОР СХЕМ",64,24,width-238,MUTED);
        panel(g,8,44,left,vh);panel(g,vx,vy,vw,vh);panel(g,rx,44,right,vh);
        text(g,"СХЕМЫ",16,53,left-36,ACCENT);
        int rows=visibleRows();
        g.enableScissor(12,listY-2,left+4,bottom-30);
        for(int i=0;i<rows&&i+listOffset<filtered.size();i++){
            Path p=filtered.get(i+listOffset);int y=listY+i*28;
            boolean active=document!=null&&document.source.equals(p),hover=mouseX>=12&&mouseX<left+4&&mouseY>=y-2&&mouseY<y+24;
            if(active||hover)ExtraButton.round(g,12,y-2,left-8,26,active?0xff244841:0xff233247);
            if(active)g.fill(12,y+2,14,y+20,ACCENT);
            text(g,middleName(displayName(p),left-20),18,y+2,left-20,active?0xffb7ffee:TEXT);
            String folder=root.relativize(p).getParent()==null?"Схема .litematic":root.relativize(p).getParent().toString();
            text(g,folder,18,y+14,left-20,MUTED);
        }
        if(filtered.isEmpty())text(g,query.isBlank()?"Пока нет схем":"Ничего не найдено",16,listY+4,left-16,MUTED);
        g.disableScissor();
        g.enableScissor(vx+1,vy+1,vx+vw-1,bottom-1);
        g.fill(vx+1,vy+1,vx+vw-1,bottom-1,0xff0c1420);
        if(grid){
            for(int x=vx+vw/2%16;x<vx+vw;x+=16)g.fill(x,vy+1,x+1,bottom-1,0xff162334);
            for(int y=vy+vh/2%16;y<bottom;y+=16)g.fill(vx+1,y,vx+vw-1,y+1,0xff162334);
        }
        if(document!=null){preview.draw(g,vx+1,vy+1,vw-2,vh-2,selection);Platform.nextLayer(g);}
        else{logo(g,vx+vw/2-18,vy+vh/2-33,36);g.drawCenteredTextWithShadow(textRenderer,"Открой схему",vx+vw/2,vy+vh/2+12,TEXT);}
        g.disableScissor();
        g.fill(vx+1,vy+1,vx+vw-1,vy+23,0xe8151e2b);g.fill(vx+1,bottom-27,vx+vw-1,bottom-1,0xe8151e2b);
        text(g,document==null?"3D-ПРОСМОТР":displayName(document.source)+(document.dirty()?" *":""),vx+8,vy+8,vw-39,TEXT);
        if(showHelp){
            panel(g,vx+7,vy+28,vw-14,86);
            String[] lines={"ЛКМ — выбрать блок","ПКМ / СКМ — вращать","Shift + ПКМ — сдвиг","Колесо — масштаб","Home — сброс камеры","G — показать сетку"};
            for(int i=0;i<lines.length;i++)text(g,lines[i],vx+14,vy+35+i*12,vw-28,i==0?ACCENT:TEXT);
        }
        text(g,"БЛОК",rx+8,53,right-16,ACCENT);
        if(document!=null&&selection!=null){
            var state=document.state(selection);g.drawItem(new ItemStack(state.getBlock()),rx+8,67);
            text(g,state.getBlock().getName().getString(),rx+29,69,right-37,TEXT);
            text(g,Registries.BLOCK.getId(state.getBlock()).toString().replace("minecraft:",""),rx+29,81,right-37,MUTED);
            var p=selection.position();text(g,"X "+p.getX()+"   Y "+p.getY()+"   Z "+p.getZ(),rx+8,94,right-16,MUTED);
        }else{text(g,"Выбери блок мышью",rx+8,73,right-16,TEXT);text(g,"ЛКМ по 3D-модели",rx+8,88,right-16,MUTED);}
        text(g,"ЗАМЕНИТЬ НА",rx+8,106,right-16,MUTED);
        if(height>=290){
            g.fill(rx+8,218,rx+right-8,219,BORDER);text(g,"НАВИГАЦИЯ",rx+8,229,right-16,ACCENT);
            text(g,"ПКМ / СКМ — вращать",rx+8,246,right-16,MUTED);text(g,"Shift + мышь — сдвиг",rx+8,260,right-16,MUTED);
            if(height>=320)text(g,"Колесо — масштаб",rx+8,274,right-16,MUTED);
        }
        g.fill(10,height-16,13,height-13,statusError?0xfff18b9b:ACCENT);
        String footer=status;if(document!=null&&!statusError)footer+="  ·  "+document.blockCount()+" блоков";
        text(g,footer,19,height-19,width-27,statusError?0xfff7a4b3:MUTED);
        super.render(g,mouseX,mouseY,delta);
        if(mouseX>=12&&mouseX<left+4&&mouseY>=listY-2&&mouseY<Math.min(bottom-30,listY+rows*28-2)){
            int index=(mouseY-listY+2)/28+listOffset;
            if(index>=0&&index<filtered.size())g.drawTooltip(textRenderer,Text.literal(root.relativize(filtered.get(index)).toString()),mouseX,mouseY);
        }
    }
    private boolean inPreview(double x,double y){return x>=vx&&x<vx+vw&&y>vy+23&&y<bottom-27;}
    @Override protected boolean onMouseClicked(double x,double y,int button,BooleanSupplier vanilla){
        if(vanilla.getAsBoolean())return true;
        if(button==0&&x>=12&&x<left+4&&y>=listY-2&&y<Math.min(bottom-30,listY+visibleRows()*28-2)){
            int index=(int)((y-listY+2)/28)+listOffset;if(index>=0&&index<filtered.size())load(filtered.get(index));return true;
        }
        if(inPreview(x,y)){
            if(showHelp){showHelp=false;return true;}
            setFocused(null);search.setFocused(false);replacement.setFocused(false);
            if(button==0){selection=preview.pick(x,y);updateButtons();}else if(button==1||button==2)dragButton=button;
            return true;
        }
        return false;
    }
    @Override protected boolean onMouseDragged(double x,double y,int button,double dx,double dy,BooleanSupplier vanilla){
        if(dragButton==button&&(button==1||button==2)){
            if(modifierDown(GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_KEY_RIGHT_SHIFT))preview.camera.panPixels(dx,dy);else preview.camera.orbitPixels(dx,dy);
            return true;
        }
        return vanilla.getAsBoolean();
    }
    @Override protected boolean onMouseReleased(double x,double y,int button,BooleanSupplier vanilla){if(button==dragButton)dragButton=-1;return vanilla.getAsBoolean();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x<left+8&&y>=listY&&y<bottom){listOffset=Math.max(0,Math.min(Math.max(0,filtered.size()-visibleRows()),listOffset-(int)Math.signum(vertical)*2));return true;}
        if(inPreview(x,y)){preview.camera.zoomSteps(vertical);return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private boolean modifierDown(int left,int right){
        long window=client.getWindow().getHandle();
        return GLFW.glfwGetKey(window,left)==GLFW.GLFW_PRESS||GLFW.glfwGetKey(window,right)==GLFW.GLFW_PRESS;
    }
    @Override protected boolean onKeyPressed(int key,int scan,int modifiers,BooleanSupplier vanilla){
        if((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0){
            if(key==GLFW.GLFW_KEY_S){save();return true;}
            if(!search.isFocused()&&!replacement.isFocused()){
                if(key==GLFW.GLFW_KEY_Z){history(modifierDown(GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_KEY_RIGHT_SHIFT));return true;}
                if(key==GLFW.GLFW_KEY_Y){history(true);return true;}
            }
        }
        if(!search.isFocused()&&!replacement.isFocused()){
            if(key==GLFW.GLFW_KEY_DELETE){edit(false,true);return true;}
            if(key==GLFW.GLFW_KEY_HOME){preview.resetView();return true;}
            if(key==GLFW.GLFW_KEY_G){grid=!grid;return true;}
        }
        if(key==GLFW.GLFW_KEY_F5){refresh();return true;}
        return vanilla.getAsBoolean();
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){guard(()->client.setScreen(parent));}
}
