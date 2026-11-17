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
    private static final int BG=0xff0d1117,PANEL=0xff151a21,BORDER=StudioTheme.BORDER,TEXT=StudioTheme.TEXT,MUTED=StudioTheme.MUTED,ACCENT=StudioTheme.ACCENT;
    private final Screen parent;
    private net.minecraft.client.font.TextRenderer studioFont;
    private final PreviewRenderer preview=new PreviewRenderer();
    private final List<Path> files=new ArrayList<>(),filtered=new ArrayList<>();
    private SchematicDocument document;
    private SchematicDocument.Cell selection;
    private Path root;
    private TextFieldWidget search,replacement;
    private ExtraButton replace,replaceAll,delete,undo,redo,save,place,playerView;
    private int left,right,rx,vx,vy,vw,vh,bottom,listY,listOffset,dragButton=-1;
    private int zoomLabelX,zoomLabelWidth;
    private String status=ExtraText.message("studio.choose"),query="",blockInput="minecraft:stone";
    private boolean statusError,grid=true,showHelp;

    public StudioScreen(){this(new GuiMainMenu());}
    public StudioScreen(Screen parent){super(Text.literal("Litematica extra"));this.parent=parent;}
    @Override protected void init(){
        studioFont=studioFont(textRenderer);
        left=Math.max(96,width*22/100);right=Math.max(124,width*25/100);
        rx=width-right-8;vx=left+14;vy=44;vw=Math.max(1,rx-vx-6);bottom=height-28;vh=Math.max(1,bottom-vy);listY=94;
        root=SchematicFiles.directory();
        playerView=control(ExtraText.message("studio.creative"),14,bottom-24,left-12,18,NORMAL,ExtraText.message("studio.creative_tip"),this::openPlayerView);
        control("?",vx+vw-23,vy+4,17,16,QUIET,ExtraText.message("studio.help"),()->showHelp=!showHelp);
        control("←",8,10,20,22,QUIET,ExtraText.message("studio.back"),this::close);
        save=control(ExtraText.message("save.submit"),width-165,10,76,22,ExtraButton.Tone.ACCENT,ExtraText.message("studio.save_tip"),this::save);
        place=control(ExtraText.message("studio.place"),width-83,10,75,22,NORMAL,ExtraText.message("studio.place_tip"),this::place);
        control("F5",left-18,49,22,16,QUIET,ExtraText.message("studio.refresh"),this::refresh);
        search=addDrawableChild(new StudioField(studioFont,14,69,left-12,18,ExtraText.label("search")));
        search.setPlaceholder(ExtraText.label("search.short"));search.setText(query);search.setEditableColor(TEXT);search.setChangedListener(s->{query=s;filter();});
        replacement=addDrawableChild(new StudioField(studioFont,rx+8,117,right-16,18,ExtraText.label("studio.new_block")));
        replacement.setMaxLength(256);replacement.setText(blockInput);replacement.setEditableColor(TEXT);
        replacement.setTooltip(Tooltip.of(ExtraText.label("studio.block_format")));
        replacement.setChangedListener(s->{blockInput=s;updateButtons();});
        replace=control(ExtraText.message("studio.replace"),rx+8,141,right-16,19,NORMAL,ExtraText.message("studio.replace_tip"),()->edit(false,false));
        replaceAll=control(ExtraText.message("studio.replace_type"),rx+8,164,right-16,19,NORMAL,ExtraText.message("studio.replace_type_tip"),()->edit(true,false));
        delete=control(ExtraText.message("studio.delete"),rx+8,187,right-16,19,DANGER,ExtraText.message("studio.delete_tip"),()->edit(false,true));
        int ty=bottom-23,tx=vx+6;
        int zoomWidth=studioFont.getWidth("8000%")+8;
        boolean wide=vw>=6+63+4+66+4+33+16+zoomWidth+16+6;
        int undoWidth=wide?63:24, redoWidth=wide?66:24;
        undo=control(wide?"↶ "+ExtraText.message("studio.undo_label"):"↶",tx,ty,undoWidth,18,QUIET,ExtraText.message("studio.undo"),()->history(false));
        tx+=undoWidth+4;
        redo=control(wide?"↷ "+ExtraText.message("studio.redo_label"):"↷",tx,ty,redoWidth,18,QUIET,ExtraText.message("studio.redo"),()->history(true));
        tx+=redoWidth+4;
        if(vw>=128){
            control(ExtraText.message("studio.view"),tx,ty,29,18,NORMAL,ExtraText.message("studio.reset"),preview::resetView);
            control("−",tx+33,ty,16,18,QUIET,ExtraText.message("studio.zoom_out"),()->preview.camera.zoomSteps(-1));
            zoomLabelX=tx+49;
            zoomLabelWidth=wide?zoomWidth:0;
            control("+",zoomLabelX+(wide?zoomWidth:4),ty,16,18,QUIET,ExtraText.message("studio.zoom_in"),()->preview.camera.zoomSteps(1));
        }
        if(files.isEmpty())refresh();else filter();
        updateButtons();
    }
    private ExtraButton control(String label,int x,int y,int w,int h,ExtraButton.Tone tone,String tip,Runnable action){
        ExtraButton b=addDrawableChild(new ExtraButton(label,x,y,w,h,tone,action));b.useStudioTheme(studioFont);b.setTooltip(Tooltip.of(Text.literal(tip)));return b;
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
        client.setScreen(new CreativeEnvironmentScreen(this,(environment,physics,fallback)->
                NativeCreativeWorld.open(document,environment,physics,fallback)));
    }
    void returnFromPlayerView(){
        selection=null;
        try{preview.rebuild(document,true);info(ExtraText.message("studio.player_changes"));}catch(Exception e){error(e);}
        updateButtons();
    }
    private void load(Path path){
        if(document!=null&&path.equals(document.source))return;
        guard(()->{
            try{
                SchematicDocument loaded=new SchematicDocument(path);preview.rebuild(loaded,true);document=loaded;selection=null;
                info(ExtraText.message("studio.opened",displayName(path)));
            }catch(Exception e){if(document!=null)try{preview.rebuild(document,true);}catch(Exception ignored){}error(e);}
            updateButtons();
        });
    }
    private void guard(Runnable action){
        if(document==null||!document.dirty()){action.run();return;}
        client.setScreen(new ConfirmScreen(discard->{client.setScreen(this);if(discard)action.run();},
                ExtraText.label("studio.unsaved"),ExtraText.label("studio.unsaved_tip"),ExtraText.label("studio.discard"),ExtraText.label("back")));
    }
    private void edit(boolean all,boolean remove){
        if(document==null||selection==null)return;
        try{
            BlockState state=remove?Blocks.AIR.getDefaultState():WorldApi.parseBlock(blockInput);
            if(document.state(selection).equals(state)){info(ExtraText.message("studio.same"));return;}
            document.replace(selection,state,all);
            try{preview.rebuild(document,false);}catch(Exception failure){document.undo();preview.rebuild(document,false);throw failure;}
            if(remove||state.isAir())selection=null;
            info(remove?ExtraText.message("studio.removed"):all?ExtraText.message("studio.replaced_type"):ExtraText.message("studio.replaced"));
        }catch(Exception e){error(e);}
        updateButtons();
    }
    private void history(boolean forward){
        if(document==null)return;
        if(forward)document.redo();else document.undo();
        try{preview.rebuild(document,false);info(forward?ExtraText.message("studio.redone"):ExtraText.message("studio.undone"));}catch(Exception e){error(e);}
        selection=null;updateButtons();
    }
    private void save(){
        if(document==null)return;
        try{Path saved=document.saveCopy();info(ExtraText.message("studio.copied",saved.getFileName().toString()));refresh();}catch(Exception e){error(e);}
    }
    private void place(){
        if(document==null||client.player==null||client.world==null)return;
        try{
            Path path=document.saveCopy();var schematic=SchematicDocument.loadSchematic(path);
            if(schematic==null)throw new IllegalStateException(ExtraText.message("studio.copy_failed"));
            SchematicHolder.getInstance().addSchematic(schematic,true);
            var manager=DataManager.getSchematicPlacementManager();
            var placement=SchematicPlacement.createFor(schematic,client.player.getBlockPos(),path.getFileName().toString(),true,true);
            manager.addSchematicPlacement(placement,true);manager.setSelectedSchematicPlacement(placement);
            info(ExtraText.message("studio.placed"));refresh();
        }catch(Exception e){error(e);}
    }
    private void info(String message){status=message;statusError=false;}
    private void error(Exception e){status=e.getMessage()==null?ExtraText.message("studio.error"):e.getMessage();statusError=true;LoggerFactory.getLogger("Litematica extra").warn("Editor operation failed",e);}
    private String displayName(Path file){return file.getFileName().toString().replaceFirst("(?i)\\.litematic$","");}
    private String middleName(String name,int max){
        if(studioFont.getWidth(name)<=max)return name;
        int tail=Math.min(name.length(),8);
        while(tail>1&&studioFont.getWidth("…"+name.substring(name.length()-tail))>max-12)tail--;
        String end="…"+name.substring(name.length()-tail);
        return studioFont.trimToWidth(name,Math.max(0,max-studioFont.getWidth(end)))+end;
    }
    private void text(DrawContext g,String s,int x,int y,int max,int color){g.drawText(studioFont,studioFont.trimToWidth(s,Math.max(1,max)),x,y,color,false);}
    private void panel(DrawContext g,int x,int y,int w,int h){StudioTheme.surface(g,x,y,w,h,PANEL,BORDER);}
    private void logo(DrawContext g,int x,int y,int size){Platform.texture(g,ICON,x,y,0,0,size,size,24,24,24,24);}
    @Override public void render(DrawContext g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,38,0xff141920);g.fill(8,37,width-8,38,BORDER);
        logo(g,34,8,24);text(g,"Litematica extra",64,11,width-238,TEXT);text(g,ExtraText.message("studio.title"),64,24,width-238,MUTED);
        panel(g,8,44,left,vh);panel(g,vx,vy,vw,vh);panel(g,rx,44,right,vh);
        text(g,ExtraText.message("studio.library"),16,53,left-36,ACCENT);
        int rows=visibleRows();
        g.enableScissor(12,listY-2,left+4,bottom-30);
        for(int i=0;i<rows&&i+listOffset<filtered.size();i++){
            Path p=filtered.get(i+listOffset);int y=listY+i*28;
            boolean active=document!=null&&document.source.equals(p),hover=mouseX>=12&&mouseX<left+4&&mouseY>=y-2&&mouseY<y+24;
            StudioTheme.surface(g,12,y-2,left-8,26,hover?0xff242e37:0xff171d24,active?ACCENT:0xff2a333e);
            g.drawItem(new ItemStack(Blocks.STRUCTURE_BLOCK),16,y+2);
            text(g,middleName(displayName(p),left-38),36,y+2,left-38,active?0xffb7ffee:TEXT);
            String folder=root.relativize(p).getParent()==null?ExtraText.message("studio.file"):root.relativize(p).getParent().toString();
            text(g,folder,36,y+14,left-38,MUTED);
        }
        if(filtered.isEmpty())text(g,query.isBlank()?ExtraText.message("studio.empty"):ExtraText.message("studio.no_results"),16,listY+4,left-16,MUTED);
        g.disableScissor();
        g.enableScissor(vx+1,vy+1,vx+vw-1,bottom-1);
        g.fill(vx+1,vy+1,vx+vw-1,bottom-1,0xff10151b);
        if(grid&&document==null){
            for(int x=vx+vw/2%16;x<vx+vw;x+=16)g.fill(x,vy+1,x+1,bottom-1,0xff1a222c);
            for(int y=vy+vh/2%16;y<bottom;y+=16)g.fill(vx+1,y,vx+vw-1,y+1,0xff1a222c);
        }
        if(document!=null){preview.draw(g,vx+1,vy+23,vw-2,Math.max(1,vh-50),selection,grid);Platform.nextLayer(g);}
        else{logo(g,vx+vw/2-18,vy+vh/2-33,36);String hint=ExtraText.message("studio.open");g.drawText(studioFont,hint,vx+(vw-studioFont.getWidth(hint))/2,vy+vh/2+12,TEXT,false);}
        g.disableScissor();
        g.fill(vx+1,vy+1,vx+vw-1,vy+23,0xff191e26);g.fill(vx+1,bottom-27,vx+vw-1,bottom-1,0xff191e26);
        text(g,ExtraText.message("studio.preview")+(document!=null&&document.dirty()?" *":""),vx+8,vy+8,vw-39,TEXT);
        if(zoomLabelWidth>0&&vw>=128){
            String zoom=Math.round(preview.camera.zoom*100)+"%";
            g.drawText(studioFont,zoom,zoomLabelX+(zoomLabelWidth-studioFont.getWidth(zoom))/2,
                    bottom-18,TEXT,false);
        }
        if(showHelp){
            panel(g,vx+7,vy+28,vw-14,86);
            String[] lines={ExtraText.message("studio.pick"),ExtraText.message("studio.orbit"),ExtraText.message("studio.pan"),ExtraText.message("studio.zoom"),ExtraText.message("studio.reset_key"),ExtraText.message("studio.grid")};
            for(int i=0;i<lines.length;i++)text(g,lines[i],vx+14,vy+35+i*12,vw-28,i==0?ACCENT:TEXT);
        }
        text(g,ExtraText.message("studio.block"),rx+8,53,right-16,ACCENT);
        if(document!=null&&selection!=null){
            var state=document.state(selection);g.drawItem(new ItemStack(state.getBlock()),rx+8,67);
            text(g,state.getBlock().getName().getString(),rx+29,69,right-37,TEXT);
            text(g,Registries.BLOCK.getId(state.getBlock()).toString(),rx+29,81,right-37,MUTED);
            var p=selection.position();text(g,"X "+p.getX()+"   Y "+p.getY()+"   Z "+p.getZ(),rx+8,94,right-16,MUTED);
        }else{text(g,ExtraText.message("studio.pick_mouse"),rx+8,73,right-16,TEXT);text(g,ExtraText.message("studio.pick_model"),rx+8,88,right-16,MUTED);}
        text(g,ExtraText.message("studio.replace_with"),rx+8,106,right-16,MUTED);
        if(height>=290){
            g.fill(rx+8,218,rx+right-8,219,BORDER);text(g,ExtraText.message("studio.navigation"),rx+8,229,right-16,ACCENT);
            text(g,ExtraText.message("studio.orbit"),rx+8,246,right-16,MUTED);text(g,ExtraText.message("studio.pan_mouse"),rx+8,260,right-16,MUTED);
            if(height>=320)text(g,ExtraText.message("studio.zoom"),rx+8,274,right-16,MUTED);
        }
        if(height>=350){
            g.fill(rx+1,bottom-26,rx+right-1,bottom-25,0xff313942);
            int chipX=rx+8;
            for(String key:new String[]{"ПКМ / СКМ","Shift","↕"}){
                int chipWidth=studioFont.getWidth(key)+8;
                StudioTheme.surface(g,chipX,bottom-21,chipWidth,16,0xff202730,BORDER);
                text(g,key,chipX+4,bottom-17,chipWidth-8,TEXT);
                chipX+=chipWidth+5;
            }
        }
        g.fill(10,height-16,13,height-13,statusError?0xfff18b9b:ACCENT);
        String footer=status;if(document!=null&&!statusError)footer+="  ·  "+ExtraText.message("studio.block_count",document.blockCount());
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
