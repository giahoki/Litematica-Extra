package me.giahoki.litematicaextra;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class StudioSmoke implements ClientModInitializer {
    private int stage,startedTick,selectorTicks;
    private final Map<net.minecraft.client.gui.screen.Screen,Integer> frames=new IdentityHashMap<>();
    private long start=System.nanoTime();
    private byte[] original;
    private Path fixture;
    private boolean oldPause;
    private String nativeFolder;
    private CompletableFuture<Void> task;
    private SmokeLoad loadScreen;
    private net.minecraft.util.math.Vec3d playerPosition;
    private float playerYaw,playerPitch;
    private net.minecraft.client.option.Perspective oldPerspective;
    private static final class SmokeLoad extends fi.dy.masa.litematica.gui.GuiSchematicLoad {
        void select(Path fixture){
            var browser=getListWidget();browser.switchToDirectory(fixture.getParent());
            var entries=browser.getCurrentEntries();
            for(int i=0;i<entries.size();i++)if(entries.get(i).getName().equals(fixture.getFileName().toString())){browser.setLastSelectedEntry(entries.get(i),i);return;}
            throw new AssertionError("fixture absent from Litematica browser");
        }
    }
    private static double[] project(PlacementScreen screen,net.minecraft.util.math.Vec3d pos)throws Exception {
        var f=PlacementScreen.class.getDeclaredField("projection");f.setAccessible(true);var projection=f.get(screen);
        var m=projection.getClass().getDeclaredMethod("project",net.minecraft.util.math.Vec3d.class);m.setAccessible(true);var point=m.invoke(projection,pos);
        check(point!=null,"gizmo point is in front of camera");
        var x=point.getClass().getDeclaredMethod("x");var y=point.getClass().getDeclaredMethod("y");x.setAccessible(true);y.setAccessible(true);
        return new double[]{(double)x.invoke(point),(double)y.invoke(point)};
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(net.minecraft.client.gui.widget.ClickableWidget button){
        check(button.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0),"button accepts click: "+button.getMessage().getString());
    }
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc,screen,w,h)->{
            frames.put(screen,0);
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((scr,g,mx,my,delta)->frames.merge(scr,1,Integer::sum));
        });
        if(Boolean.getBoolean("litematicaextra.smokeHidden")){
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client->org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getHandle()));
        }
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(stage>=100)return;
            try{
                if(System.nanoTime()-start>300_000_000_000L)throw new AssertionError("native world test timed out");
                if(stage==0&&client.currentScreen instanceof TitleScreen&&client.getOverlay()==null){
                    check(Screens.getButtons(client.currentScreen).stream().anyMatch(b->b.getMessage().getString().equals("Litematica extra")),"title screen has Litematica extra button");
                    fixture=client.runDirectory.toPath().resolve("schematics/studio-demo.litematic");
                    original=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture));
                    client.options.getMenuBackgroundBlurriness().setValue(5);
                    oldPause=client.options.pauseOnLostFocus;client.options.pauseOnLostFocus=false;
                    click(Screens.getButtons(client.currentScreen).stream().filter(b->b.getMessage().getString().equals("Litematica extra")).findFirst().orElseThrow());
                    check(client.currentScreen instanceof SchematicWorldSelectScreen,"title button opens file selector");
                    stage=-1;
                }
                if(stage==-1&&++selectorTicks>=20&&frames.getOrDefault(client.currentScreen,0)>=3){
                    check(client.currentScreen instanceof SchematicWorldSelectScreen,"selector survives rendering with menu blur");
                    click(Screens.getButtons(client.currentScreen).stream().filter(b->b.getMessage().getString().equals("Назад")).findFirst().orElseThrow());
                    check(client.currentScreen instanceof TitleScreen,"back button returns to title");
                    click(Screens.getButtons(client.currentScreen).stream().filter(b->b.getMessage().getString().equals("Litematica extra")).findFirst().orElseThrow());
                    selectorTicks=0;stage=-2;
                }
                if(stage==-2&&++selectorTicks>=20&&frames.getOrDefault(client.currentScreen,0)>=3){
                    check(client.currentScreen instanceof SchematicWorldSelectScreen,"selector reopens and renders");
                    var screen=client.currentScreen;
                    screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.widget.TextFieldWidget).map(c->(net.minecraft.client.gui.widget.TextFieldWidget)c).findFirst().orElseThrow().setText("studio-demo.litematic");
                    check(screen.mouseClicked(screen.width/2.0,75,0),"schematic row can be selected");
                    var load=Screens.getButtons(screen).stream().filter(b->b.getMessage().getString().equals("Загрузить в креативе")).findFirst().orElseThrow();
                    check(load.active,"load button enabled after selection");
                    click(load);selectorTicks=0;stage=1;
                }
                if(stage==1&&NativeCreativeWorld.isEditing()&&client.world!=null&&client.player!=null&&client.interactionManager!=null&&client.currentScreen==null&&++selectorTicks>=40){
                    check(client.isIntegratedServerRunning(),"actual integrated server is running");
                    check(client.interactionManager.getCurrentGameMode()==GameMode.CREATIVE,"vanilla game mode is creative");
                    check(client.player.getAbilities().creativeMode&&client.player.getAbilities().allowFlying,"real player has creative abilities");
                    nativeFolder=client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString();
                    BlockPos offset=NativeCreativeWorld.sourceOffset();
                    check(NativeCreativeWorld.activeWorld().getBlockState(new BlockPos(4,5,3).add(offset)).isOf(Blocks.GOLD_BLOCK),"schematic is pasted as real world blocks");
                    fi.dy.masa.litematica.data.DataManager.setConfigGuiTab(fi.dy.masa.litematica.gui.GuiConfigs.ConfigGuiTab.GENERIC);
                    var config=new fi.dy.masa.litematica.gui.GuiConfigs();
                    check(config.getConfigs().stream().anyMatch(c->c.getConfig()==PlacementSettings.HOLD_ALT),"hold Alt setting appears in Litematica Generic");
                    client.setScreen(new fi.dy.masa.litematica.gui.GuiMainMenu());selectorTicks=0;stage=19;
                }
                if(stage==19&&++selectorTicks>=10&&client.currentScreen instanceof fi.dy.masa.litematica.gui.GuiMainMenu&&frames.getOrDefault(client.currentScreen,0)>=3){
                    loadScreen=new SmokeLoad();client.setScreen(loadScreen);loadScreen.select(fixture);
                    selectorTicks=0;stage=20;
                }
                if(stage==20&&++selectorTicks>=10&&frames.getOrDefault(loadScreen,0)>=3){
                    playerPosition=client.player.getPos();playerYaw=client.player.getYaw();playerPitch=client.player.getPitch();
                    oldPerspective=client.options.getPerspective();client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                    var buttons=((me.giahoki.litematicaextra.mixin.ExtraGuiAccess)(Object)loadScreen).extra$buttons();
                    for(int i=0;i<buttons.size();i++){
                        var a=buttons.get(i);check(a.getX()>=0&&a.getY()>=0&&a.getX()+a.getWidth()<=loadScreen.width&&a.getY()+a.getHeight()<=loadScreen.height,"load buttons stay on screen");
                        for(int j=i+1;j<buttons.size();j++){
                            var b=buttons.get(j);check(a.getX()+a.getWidth()<=b.getX()||b.getX()+b.getWidth()<=a.getX()||a.getY()+a.getHeight()<=b.getY()||b.getY()+b.getHeight()<=a.getY(),"load buttons do not overlap");
                        }
                    }
                    var placeButton=buttons.get(1);loadScreen.mouseClicked(placeButton.getX()+placeButton.getWidth()/2.0,placeButton.getY()+10,0);
                    check(client.currentScreen instanceof PlacementScreen,"placement button opens local camera");
                    var screen=(PlacementScreen)client.currentScreen;
                    check(client.getCameraEntity()==screen.camera()&&client.getCameraEntity()!=client.player,"detached camera is active");
                    check(client.world.getEntityById(screen.camera().getId())==null,"camera is not added to world entities");
                    var o=net.minecraft.util.math.Vec3d.of(screen.placement().getOrigin());
                    screen.camera().setPosition(o.add(8,5,8));screen.camera().setYaw(135);screen.camera().setPitch(30);screen.look(0,1);
                    selectorTicks=0;stage=21;
                }
                if(stage==21&&++selectorTicks>=20&&frames.getOrDefault(client.currentScreen,0)>=3){
                    var screen=(PlacementScreen)client.currentScreen;
                    check(client.player.getPos().squaredDistanceTo(playerPosition)<1e-8,"local camera does not move player body");
                    check(client.player.getYaw()==playerYaw&&client.player.getPitch()==playerPitch,"local camera does not rotate player body");
                    float cameraYaw=screen.camera().getYaw();
                    var mouseX=net.minecraft.client.Mouse.class.getDeclaredField("cursorDeltaX");mouseX.setAccessible(true);mouseX.setDouble(client.mouse,20);
                    var update=net.minecraft.client.Mouse.class.getDeclaredMethod("updateMouse",double.class);update.setAccessible(true);update.invoke(client.mouse,.016);
                    mouseX.setDouble(client.mouse,0);
                    check(screen.camera().getYaw()!=cameraYaw,"vanilla mouse update rotates detached camera");
                    check(client.player.getYaw()==playerYaw&&client.player.getPitch()==playerPitch,"mouse redirect preserves actual player rotation");
                    PlacementSettings.HOLD_ALT.setBooleanValue(false);
                    screen.handleAlt(true);check(screen.cursorShown(),"Alt toggles cursor on");
                    screen.handleAlt(true);check(screen.cursorShown(),"repeated Alt does not toggle twice");
                    screen.handleAlt(false);check(screen.cursorShown(),"toggle cursor stays visible after release");
                    screen.handleAlt(true);check(!screen.cursorShown(),"second Alt hides cursor");screen.handleAlt(false);
                    PlacementSettings.HOLD_ALT.setBooleanValue(true);
                    screen.handleAlt(true);check(screen.cursorShown(),"hold mode shows cursor");
                    screen.handleAlt(false);check(!screen.cursorShown(),"hold mode hides cursor on release");
                    PlacementSettings.HOLD_ALT.setBooleanValue(false);screen.setCursorShown(true);
                    selectorTicks=0;stage=22;
                }
                if(stage==22&&++selectorTicks>=10&&frames.getOrDefault(client.currentScreen,0)>=6){
                    var screen=(PlacementScreen)client.currentScreen;
                    var origin=screen.placement().getOrigin();
                    var pivotField=PlacementScreen.class.getDeclaredField("pivot");
                    pivotField.setAccessible(true);
                    var center=(net.minecraft.util.math.Vec3d)pivotField.get(screen);
                    var axes=new net.minecraft.util.math.Vec3d[]{new net.minecraft.util.math.Vec3d(1,0,0),new net.minecraft.util.math.Vec3d(0,1,0),new net.minecraft.util.math.Vec3d(0,0,1)};
                    for(int axis=0;axis<3;axis++){
                        var a=axes[axis];var click=project(screen,center.add(a.multiply(1.5)));
                        var drag=project(screen,center.add(a.multiply(4.5)));
                        screen.mouseClicked(click[0],click[1],0);screen.mouseDragged(drag[0],drag[1],0,drag[0]-click[0],drag[1]-click[1]);screen.mouseReleased(drag[0],drag[1],0);
                        check(screen.placement().getOrigin().equals(origin.add((int)a.x*3,(int)a.y*3,(int)a.z*3)),"actual Litematica placement moves along axis "+axis);
                        screen.placement().setOrigin(origin,message->{throw new AssertionError(message);});
                    }
                    var placement=screen.placement();
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_R,0,0);
                    screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_R,0,0);
                    var update=PlacementScreen.class.getDeclaredMethod("updateProjection",float.class);
                    update.setAccessible(true);update.invoke(screen,1f);
                    var radiusField=PlacementScreen.class.getDeclaredField("ringRadius");
                    radiusField.setAccessible(true);
                    double radius=radiusField.getDouble(screen);
                    var start=project(screen,center.add(radius,0,0));
                    var end=project(screen,center.add(0,0,radius));
                    screen.mouseClicked(start[0],start[1],0);
                    screen.mouseDragged(end[0],end[1],0,end[0]-start[0],end[1]-start[1]);
                    screen.mouseReleased(end[0],end[1],0);
                    check(placement.getRotation()==BlockRotation.CLOCKWISE_90,"ring rotates the Litematica placement");
                    screen.close();
                    check(placement.getRotation()==BlockRotation.CLOCKWISE_90,"finished placement keeps its rotation");
                    check(client.getCameraEntity()==client.player,"camera restored to player");
                    check(client.options.getPerspective()==net.minecraft.client.option.Perspective.THIRD_PERSON_BACK,"previous perspective restored");
                    check(fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().contains(placement),"finished placement stays in Litematica");
                    fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);
                    client.options.setPerspective(oldPerspective);
                    stage=23;
                }
                if(stage==23){
                    BlockPos offset=NativeCreativeWorld.sourceOffset();
                    client.setScreen(new CreativeInventoryScreen(client.player,client.world.getEnabledFeatures(),true));
                    check(client.currentScreen instanceof CreativeInventoryScreen,"ordinary vanilla creative inventory opens");
                    task=new CompletableFuture<>();client.getServer().execute(()->{
                        try{
                            var w=NativeCreativeWorld.activeWorld();
                            BlockPos chest=new BlockPos(12,0,0).add(offset);w.setBlockState(chest,Blocks.CHEST.getDefaultState(),3);
                            ((ChestBlockEntity)w.getBlockEntity(chest)).setStack(0,new ItemStack(Items.DIAMOND,3));
                            w.getBlockEntity(chest).markDirty();
                            w.setBlockState(new BlockPos(12,0,12).add(offset),Blocks.STONE.getDefaultState(),3);
                            w.setBlockState(new BlockPos(12,4,12).add(offset),Blocks.SAND.getDefaultState(),3);
                            var gravity=new net.minecraft.block.Block[]{Blocks.GRAVEL,Blocks.ANVIL,Blocks.WHITE_CONCRETE_POWDER,Blocks.DRAGON_EGG};
                            for(int i=0;i<gravity.length;i++)w.setBlockState(new BlockPos(13+i,4,20).add(offset),gravity[i].getDefaultState(),3);
                            w.setBlockState(new BlockPos(20,4,20).add(offset),Blocks.LAVA.getDefaultState(),3);
                            var torchBase=new BlockPos(25,0,20).add(offset);
                            w.setBlockState(torchBase,Blocks.STONE.getDefaultState(),3);
                            w.setBlockState(torchBase.up(),Blocks.TORCH.getDefaultState(),3);w.removeBlock(torchBase,false);
                            for(int x=14;x<=18;x++)for(int z=12;z<=16;z++){
                                w.setBlockState(new BlockPos(x,0,z).add(offset),Blocks.STONE.getDefaultState(),3);
                                if(x==14||x==18||z==12||z==16)for(int y=1;y<=2;y++)w.setBlockState(new BlockPos(x,y,z).add(offset),Blocks.STONE.getDefaultState(),3);
                            }
                            w.setBlockState(new BlockPos(16,1,14).add(offset),Blocks.WATER.getDefaultState(),3);
                            w.setBlockState(new BlockPos(22,1,12).add(offset),Blocks.REDSTONE_LAMP.getDefaultState(),3);
                            w.setBlockState(new BlockPos(23,1,12).add(offset),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
                            task.complete(null);
                        }catch(Throwable t){task.completeExceptionally(t);}
                    });startedTick=client.getServer().getTicks();stage=2;
                }
                if(stage==2&&task.isDone()&&client.getServer().getTicks()-startedTick>=100){
                    task.join();client.setScreen(null);
                    task=new CompletableFuture<>();client.getServer().execute(()->{
                        try{
                            var w=NativeCreativeWorld.activeWorld();var o=NativeCreativeWorld.sourceOffset();
                            check(NativeCreativeWorld.physicsFrozen(w),"physics is frozen in editing world");
                            check(w.getBlockState(new BlockPos(12,4,12).add(o)).isOf(Blocks.SAND),"unsupported sand stays at original position");
                            check(w.getBlockState(new BlockPos(12,1,12).add(o)).isAir(),"sand did not fall onto support");
                            var gravity=new net.minecraft.block.Block[]{Blocks.GRAVEL,Blocks.ANVIL,Blocks.WHITE_CONCRETE_POWDER,Blocks.DRAGON_EGG};
                            for(int i=0;i<gravity.length;i++)check(w.getBlockState(new BlockPos(13+i,4,20).add(o)).isOf(gravity[i]),"gravity block stays suspended: "+gravity[i]);
                            check(w.getFluidState(new BlockPos(15,1,14).add(o)).isEmpty(),"water does not spread");
                            check(w.getBlockState(new BlockPos(20,4,20).add(o)).isOf(Blocks.LAVA)&&w.getBlockState(new BlockPos(20,3,20).add(o)).isAir(),"lava stays suspended without flowing");
                            check(w.getBlockState(new BlockPos(25,1,20).add(o)).isOf(Blocks.TORCH),"unsupported torch does not break automatically");
                            check(w.getBlockState(new BlockPos(22,1,12).add(o)).get(RedstoneLampBlock.LIT),"vanilla redstone powers lamp");
                            task.complete(null);
                        }catch(Throwable t){task.completeExceptionally(t);}
                    });stage=3;
                }
                if(stage==3&&task.isDone()){task.join();NativeCreativeWorld.save(false);stage=4;}
                if(stage==4&&NativeCreativeWorld.lastResult()!=null){
                    var saved=new SchematicDocument(NativeCreativeWorld.lastResult());
                    check(saved.stateAt(new BlockPos(12,0,0)).isOf(Blocks.CHEST),"new outside block round trips to litematic");
                    check(saved.stateAt(new BlockPos(12,4,12)).isOf(Blocks.SAND),"suspended block is preserved in exported schematic");
                    var data=saved.schematic.getBlockEntityMapForRegion("CreativeEdit");
                    check(data.values().stream().anyMatch(n->n.toString().contains("minecraft:diamond")),"container inventory is preserved in exported schematic");
                    check(Arrays.equals(original,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(fixture))),"original schematic is unchanged");
                    Files.writeString(client.runDirectory.toPath().resolve("studio-smoke-result.txt"),"RUNNING: editing-world freeze and export passed; checking ordinary world and reopen\n");
                    NativeCreativeWorld.save(true);stage=5;
                }
                if(stage==5&&client.world==null&&client.getServer()==null){
                    var rules=new net.minecraft.world.GameRules(net.minecraft.resource.DataConfiguration.SAFE_MODE.enabledFeatures());
                    rules.get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,null);
                    var level=new net.minecraft.world.level.LevelInfo("Extra physics control",GameMode.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,rules,net.minecraft.resource.DataConfiguration.SAFE_MODE);
                    stage=6;
                    client.createIntegratedServerLoader().createAndStart("extra-physics-control-"+UUID.randomUUID().toString().substring(0,8),level,new net.minecraft.world.gen.GeneratorOptions(0,false,false),lookup->{
                        var flat=new net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig(Optional.empty(),lookup.getOrThrow(net.minecraft.registry.RegistryKeys.BIOME).getOrThrow(net.minecraft.world.biome.BiomeKeys.THE_VOID),List.of());
                        return net.minecraft.world.gen.WorldPresets.createDemoOptions(lookup).with(lookup,new net.minecraft.world.gen.chunk.FlatChunkGenerator(flat));
                    },new TitleScreen());
                }
                if(stage==6&&client.world!=null&&client.player!=null&&client.getServer()!=null){
                    task=new CompletableFuture<>();client.getServer().execute(()->{
                        try{
                            var w=client.getServer().getOverworld();check(!NativeCreativeWorld.physicsFrozen(w),"ordinary world physics is not frozen");
                            var player=client.getServer().getPlayerManager().getPlayerList().getFirst();player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.requestTeleport(.5,72,.5);
                            w.setBlockState(new BlockPos(0,64,0),Blocks.STONE.getDefaultState(),3);
                            w.setBlockState(new BlockPos(0,68,0),Blocks.SAND.getDefaultState(),3);
                            for(int x=3;x<=7;x++)for(int z=3;z<=7;z++)w.setBlockState(new BlockPos(x,64,z),Blocks.STONE.getDefaultState(),3);
                            w.setBlockState(new BlockPos(5,65,5),Blocks.WATER.getDefaultState(),3);
                            task.complete(null);
                        }catch(Throwable t){task.completeExceptionally(t);}
                    });startedTick=client.getServer().getTicks();stage=7;
                }
                if(stage==7&&task.isDone()&&client.getServer().getTicks()-startedTick>=100){
                    task.join();task=new CompletableFuture<>();
                    client.getServer().execute(()->{
                        try{
                            var w=client.getServer().getOverworld();
                            check(w.getBlockState(new BlockPos(0,65,0)).isOf(Blocks.SAND)&&w.getBlockState(new BlockPos(0,68,0)).isAir(),"sand still falls in ordinary worlds");
                            check(!w.getFluidState(new BlockPos(4,65,5)).isEmpty(),"water still flows in ordinary worlds");
                            task.complete(null);
                        }catch(Throwable t){task.completeExceptionally(t);}
                    });stage=8;
                }
                if(stage==8&&task.isDone()){
                    task.join();net.minecraft.client.gui.screen.GameMenuScreen.disconnect(client,net.minecraft.text.Text.literal("Control world complete"));stage=9;
                }
                if(stage==9&&client.world==null&&client.getServer()==null){
                    stage=10;client.createIntegratedServerLoader().start(nativeFolder,()->{throw new AssertionError("could not reopen editing world");});
                }
                if(stage==10&&NativeCreativeWorld.isEditing()&&client.world!=null&&client.player!=null){
                    var w=NativeCreativeWorld.activeWorld();var o=NativeCreativeWorld.sourceOffset();
                    check(NativeCreativeWorld.physicsFrozen(w),"freeze restored after reopening saved editing world");
                    check(w.getBlockState(new BlockPos(12,4,12).add(o)).isOf(Blocks.SAND),"unsupported sand survives reopening");
                    Files.writeString(client.runDirectory.toPath().resolve("studio-smoke-result.txt"),"PASS: Litematica placement button, detached local camera, player body unchanged, Alt toggle and hold, real X/Y/Z gizmo dragging, camera and perspective restored, Litematica Generic setting, title button, file selector rendered for 40 ticks with menu blur, back and reopen, search and row selection, load button, real integrated CREATIVE world, vanilla creative inventory, schematic import, sand/gravel/anvil/powder/dragon egg frozen, water/lava frozen, redstone works, native chest contents and suspended block export, original hash unchanged, ordinary-world sand and water unchanged, saved-world freeze restored\n");
                    NativeCreativeWorld.save(true);stage=11;
                }
                if(stage==11&&client.world==null&&client.getServer()==null){client.options.pauseOnLostFocus=oldPause;stage=100;client.scheduleStop();}
            }catch(Throwable t){
                stage=100;t.printStackTrace();
                try{Files.writeString(client.runDirectory.toPath().resolve("studio-smoke-result.txt"),"FAIL: "+t);}catch(Exception ignored){}
                client.scheduleStop();
            }
        });
    }
}
