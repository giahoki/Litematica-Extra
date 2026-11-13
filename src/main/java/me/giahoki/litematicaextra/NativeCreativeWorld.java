package me.giahoki.litematicaextra;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;

import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.util.WorldSavePath;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public final class NativeCreativeWorld {
    private static final String LINK="litematica-extra-world.properties";
    private static volatile Session current;
    private static volatile Path lastResult;
    private static final Map<MinecraftServer,Boolean> linkedWorlds=Collections.synchronizedMap(new WeakHashMap<>());
    enum Phase { WAITING, IMPORTING, UPDATING, READY, STOPPED, ERROR }
    private record Placed(BlockPos pos,BlockState state,NbtCompound data){}
    private record Mob(BlockPos origin,NbtCompound data){}
    private static final class Session {
        final Path source;
        final String folder,name;
        final BlockPos offset;
        final int[] bounds;
        final List<Placed> blocks;
        final List<Mob> entities;
        final boolean newSchematic;
        volatile boolean starterVisible;
        boolean changingStarter,sourceWritten;
        long blockCount;
        volatile Phase phase=Phase.WAITING;
        volatile MinecraftServer server;
        volatile ServerWorld world;
        volatile boolean playerPrepared,announced,exporting;
        Path deleteAfterExport;
        int index,updates,ticks;
        Session(SchematicDocument doc){
            newSchematic=false;
            source=doc.source.toAbsolutePath();folder="litematica-extra-"+UUID.randomUUID().toString().substring(0,8);
            name="Litematica extra — "+source.getFileName().toString().replaceFirst("(?i)\\.litematic$","");
            int[] b={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
            for(var e:doc.schematic.getAreaSizes().entrySet()){
                var p=doc.schematic.getSubRegionPosition(e.getKey());var size=e.getValue();
                int x=OrbitCamera.regionMin(p.getX(),size.getX()),y=OrbitCamera.regionMin(p.getY(),size.getY()),z=OrbitCamera.regionMin(p.getZ(),size.getZ());
                expand(b,new BlockPos(x,y,z));expand(b,new BlockPos(x+Math.abs(size.getX())-1,y+Math.abs(size.getY())-1,z+Math.abs(size.getZ())-1));
            }
            for(BlockPos p:doc.occupied.keySet())expand(b,p);
            if(b[0]==Integer.MAX_VALUE){Arrays.fill(b,0);}
            int height=b[4]-b[1]+1;
            if(height>384)throw new IllegalArgumentException("Схема выше 384 блоков — не помещается в обычный мир Minecraft");
            int baseY=Math.min(64,320-height);offset=new BlockPos(-b[0],baseY-b[1],-b[2]);
            bounds=new int[]{b[0]+offset.getX(),b[1]+offset.getY(),b[2]+offset.getZ(),b[3]+offset.getX(),b[4]+offset.getY(),b[5]+offset.getZ()};
            blocks=new ArrayList<>(doc.occupied.size());entities=new ArrayList<>();
            for(var cell:doc.occupied.values()){
                NbtCompound data=null;
                if(cell.region()!=null){
                    var map=doc.schematic.getBlockEntityMapForRegion(cell.region());
                    if(map!=null&&map.get(new BlockPos(cell.x(),cell.y(),cell.z()))!=null)data=SchematicData.vanilla(map.get(new BlockPos(cell.x(),cell.y(),cell.z())));
                }
                blocks.add(new Placed(cell.position().add(offset),doc.state(cell),data));
            }
            for(String region:doc.schematic.getAreaSizes().keySet()){
                var list=doc.schematic.getEntityListForRegion(region);
                if(list!=null)for(var entity:list)entities.add(new Mob(doc.schematic.getSubRegionPosition(region).add(offset),SchematicData.entity(entity)));
            }
        }
        Session(Path directory){
            folder="litematica-extra-"+UUID.randomUUID().toString();
            source=directory.toAbsolutePath().resolve("Новая схема "+folder.substring(folder.length()-8)+".litematic");
            name=source.getFileName().toString().replace(".litematic","");
            offset=new BlockPos(0,64,0);
            bounds=new int[]{0,64,0,0,64,0};
            blocks=List.of();entities=List.of();newSchematic=true;
        }
        Session(Path root,Properties p){
            source=Path.of(p.getProperty("source"));folder=root.getFileName().toString();name=p.getProperty("name","Litematica extra");
            offset=new BlockPos(Integer.parseInt(p.getProperty("ox")),Integer.parseInt(p.getProperty("oy")),Integer.parseInt(p.getProperty("oz")));
            bounds=new int[6];for(int i=0;i<6;i++)bounds[i]=Integer.parseInt(p.getProperty("b"+i));
            blocks=List.of();entities=List.of();phase=Phase.READY;playerPrepared=true;
            newSchematic=Boolean.parseBoolean(p.getProperty("newSchematic"));
            starterVisible=Boolean.parseBoolean(p.getProperty("starterVisible"));
            blockCount=Long.parseLong(p.getProperty("blockCount","0"));
            sourceWritten=newSchematic;
        }
    }
    public static void open(SchematicDocument doc,Screen fallback){
        open(new Session(doc),fallback);
    }
    public static void createEmpty(Path directory,Screen fallback)throws IOException{
        Files.createDirectories(directory);
        open(new Session(directory),fallback);
    }
    private static void open(Session s,Screen fallback){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world!=null)throw new IllegalStateException("Открой локальный мир через кнопку Litematica extra на стартовом экране");
        current=s;lastResult=null;
        var rules=WorldApi.rules();
        WorldApi.configure(rules);
        LevelInfo level=new LevelInfo(s.name,GameMode.CREATIVE,false,Difficulty.NORMAL,true,rules,DataConfiguration.SAFE_MODE);
        client.setScreen(new MessageScreen(Text.literal(s.newSchematic?"Создание пустой схемы…":"Создание локального мира из схемы…")));
        client.createIntegratedServerLoader().createAndStart(s.folder,level,new GeneratorOptions(0L,false,false),lookup->{
            var flat=new FlatChunkGeneratorConfig(Optional.empty(),WorldApi.voidBiome(lookup),List.of());
            return WorldPresets.createDemoOptions(lookup).with(lookup,new FlatChunkGenerator(flat));
        },fallback);
    }
    public static boolean isEditing(){Session s=current;return s!=null&&s.phase==Phase.READY&&s.server==MinecraftClient.getInstance().getServer();}
    public static boolean physicsFrozen(net.minecraft.world.WorldView view){
        if(!(view instanceof ServerWorld world))return false;
        MinecraftServer server=world.getServer();Session s=current;
        if(s!=null&&s.phase!=Phase.STOPPED){
            if(s.server==server)return true;
            if(s.server==null&&server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(s.folder))return true;
        }
        return linkedWorlds.computeIfAbsent(server,key->Files.isRegularFile(key.getSavePath(WorldSavePath.ROOT).resolve(LINK)));
    }
    static ServerWorld activeWorld(){Session s=current;return s==null?null:s.world;}
    static BlockPos sourceOffset(){return current.offset;}
    static Path lastResult(){return lastResult;}
    public static void serverTick(MinecraftServer server){
        Session s=current;
        if(s==null||s.phase==Phase.STOPPED){
            Path root=server.getSavePath(WorldSavePath.ROOT);
            if(!Files.isRegularFile(root.resolve(LINK)))return;
            try{Properties p=new Properties();try(var in=Files.newInputStream(root.resolve(LINK))){p.load(in);}s=new Session(root,p);s.server=server;s.world=server.getOverworld();current=s;
                WorldApi.freeze(s.world);
            }
            catch(Exception e){LoggerFactory.getLogger("Litematica extra").warn("Could not read local world link",e);return;}
        }
        if(s.server==null){
            if(!server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(s.folder))return;
            s.server=server;s.world=server.getOverworld();s.phase=Phase.IMPORTING;s.world.setTimeOfDay(6000);
        }
        if(s.server!=server)return;
        try{
            if(s.phase==Phase.IMPORTING){
                int end=Math.min(s.blocks.size(),s.index+4096);
                while(s.index<end){
                    Placed b=s.blocks.get(s.index++);s.world.setBlockState(b.pos,b.state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                    if(b.data!=null){
                        var be=s.world.getBlockEntity(b.pos);
                        if(be!=null){NbtCompound data=b.data.copy();data.putInt("x",b.pos.getX());data.putInt("y",b.pos.getY());data.putInt("z",b.pos.getZ());Platform.readBlockEntity(be,data,s.world);be.markDirty();}
                    }
                }
                if(s.index==s.blocks.size())s.phase=Phase.UPDATING;
            }
            if(s.phase==Phase.UPDATING){
                int end=Math.min(s.blocks.size(),s.updates+2048);
                while(s.updates<end){
                    Placed b=s.blocks.get(s.updates++);var state=s.world.getBlockState(b.pos);s.world.updateNeighbors(b.pos,state.getBlock());
                    if(!state.getFluidState().isEmpty())s.world.scheduleFluidTick(b.pos,state.getFluidState().getFluid(),1);
                }
                if(s.updates==s.blocks.size()){
                    for(Mob mob:s.entities){
                        var world=s.world;
                        var entity=WorldApi.loadEntity(mob.data,world,e->{
                            e.refreshPositionAndAngles(e.getX()+mob.origin.getX(),e.getY()+mob.origin.getY(),e.getZ()+mob.origin.getZ(),e.getYaw(),e.getPitch());return e;
                        });
                        if(entity!=null)world.spawnNewEntityAndPassengers(entity);
                    }
                    if(s.newSchematic)export(s,sourceName(s));
                    s.phase=Phase.READY;persist(s);
                }
            }
            if(s.phase==Phase.READY){
                if(s.newSchematic)setStarter(s,s.blockCount==0);
                if(!s.playerPrepared&&!server.getPlayerManager().getPlayerList().isEmpty()){
                    var player=server.getPlayerManager().getPlayerList().getFirst();
                    player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
                    double x=(s.bounds[0]+s.bounds[3]+1)/2.0,y=Math.min(315,s.bounds[4]+3),z=s.bounds[5]+6;
                    WorldApi.teleport(player,s.world,x,y,z);Platform.spawn(s.world,BlockPos.ofFloored(x,y,z));s.playerPrepared=true;
                }
                if(++s.ticks%200==0)persist(s);
            }
        }catch(Exception e){s.phase=Phase.ERROR;LoggerFactory.getLogger("Litematica extra").error("Ошибка подготовки мира",e);}
    }
    public static void clientTick(MinecraftClient client){
        Session s=current;
        if(s!=null&&s.deleteAfterExport!=null&&client.world==null&&!s.server.getThread().isAlive()){
            Path exported=s.deleteAfterExport;
            s.deleteAfterExport=null;
            deleteExportedWorld(client,s,exported);
        }
        if(s!=null&&s.server==client.getServer()&&s.phase==Phase.READY&&s.playerPrepared&&!s.announced&&client.world!=null&&client.player!=null){
            s.announced=true;client.setScreen(null);
            if(s.newSchematic)client.player.sendMessage(Text.literal("Поставь первый блок на морской фонарь. Фонарь исчезнет, а когда мир опустеет, появится снова."),false);
            client.player.sendMessage(Text.literal(s.newSchematic
                    ?"Esc → Сохранить и выйти. После экспорта временный мир удалится."
                    :"Litematica extra: локальный креатив, физика блоков выключена. Esc → Сохранить .litematic. Мир остаётся в «Одиночной игре»."),false);
        }
    }
    public static void track(ServerWorld world,BlockPos pos){
        Session s=current;if(s!=null&&s.world==world&&(s.phase==Phase.READY||s.phase==Phase.UPDATING))expand(s.bounds,pos);
    }
    public static boolean isStarter(net.minecraft.world.World world,BlockPos pos){
        Session s=current;
        return s!=null&&s.starterVisible&&s.phase==Phase.READY&&s.offset.equals(pos)
                &&(s.world==world||world.isClient()&&MinecraftClient.getInstance().getServer()==s.server
                &&world.getRegistryKey()==s.world.getRegistryKey());
    }
    public static void trackBlockChange(ServerWorld world,BlockPos pos,BlockState oldState,BlockState newState){
        Session s=current;
        if(s==null||s.world!=world||!s.newSchematic||s.phase!=Phase.READY||s.changingStarter)return;
        boolean wasStarter=s.starterVisible&&s.offset.equals(pos)&&oldState.isOf(Blocks.SEA_LANTERN);
        if(wasStarter)s.starterVisible=false;
        else if(!oldState.isAir())s.blockCount--;
        if(!newState.isAir())s.blockCount++;
    }
    private static void setStarter(Session s,boolean visible){
        if(s.starterVisible==visible)return;
        var state=s.world.getBlockState(s.offset);
        if(visible&&!state.isAir()||!visible&&!state.isOf(Blocks.SEA_LANTERN))return;
        s.changingStarter=true;
        try{
            var replacement=visible?Blocks.SEA_LANTERN:Blocks.AIR;
            if(s.world.setBlockState(s.offset,replacement.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE)){
                s.starterVisible=visible;
            }
        }finally{s.changingStarter=false;}
    }
    private static void expand(int[] b,BlockPos p){b[0]=Math.min(b[0],p.getX());b[1]=Math.min(b[1],p.getY());b[2]=Math.min(b[2],p.getZ());b[3]=Math.max(b[3],p.getX());b[4]=Math.max(b[4],p.getY());b[5]=Math.max(b[5],p.getZ());}
    private static String sourceName(Session s){
        return s.source.getFileName().toString().replaceFirst("(?i)\\.litematic$","");
    }
    private static String suggestedName(Session s){
        String base=sourceName(s)+(s.newSchematic?"":"_creative");
        String name=base;
        int suffix=2;
        while(Files.exists(s.source.resolveSibling(name+".litematic"))
                &&!(s.newSchematic&&name.equals(sourceName(s))))name=base+"_"+suffix++;
        return name;
    }
    public static void promptSave(Screen parent,boolean exit){
        Session s=current;
        if(!isEditing()||s.exporting)return;
        var client=MinecraftClient.getInstance();
        client.setScreen(new SaveSchematicScreen(parent,suggestedName(s),value->{
            String name=value.trim().replaceFirst("(?i)\\.litematic$","");
            if(name.isBlank()||name.startsWith(".")||name.endsWith(".")||name.endsWith(" ")
                    ||name.matches(".*[\\p{Cntrl}<>:\"/\\\\|?*].*")
                    ||name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")){
                return "Введи допустимое имя файла";
            }
            if(current!=s||!isEditing()||s.exporting)return "Мир сейчас недоступен";
            var dialog=(SaveSchematicScreen)client.currentScreen;
            save(s,name,exit,dialog);
            return "Сохранение…";
        }));
    }
    static void save(boolean exit){
        Session s=current;
        if(isEditing()&&!s.exporting)save(s,suggestedName(s),exit,null);
    }
    private static void save(Session s,String name,boolean exit,SaveSchematicScreen dialog){
        if(dialog!=null)dialog.saving();
        lastResult=null;
        s.exporting=true;s.server.execute(()->{
            try{
                Path result=export(s,name);
                MinecraftClient.getInstance().execute(()->{
                    var client=MinecraftClient.getInstance();
                    if(client.getServer()!=s.server||client.world==null)return;
                    if(exit){
                        if(s.newSchematic)s.deleteAfterExport=result;
                        Platform.disconnect(client,Text.literal("Выход из локального мира Litematica extra"));
                    }
                    else if(client.player!=null){client.setScreen(null);client.player.sendMessage(Text.literal("Сохранено: "+result.getFileName()),false);}
                });
            }catch(Exception e){
                report(e);
                if(dialog!=null)MinecraftClient.getInstance().execute(()->dialog.failed(e.getMessage()==null?"Ошибка сохранения":e.getMessage()));
            }
            finally{s.exporting=false;}
        });
    }
    private static Path export(Session s,String name)throws Exception{
        Path result=s.source.resolveSibling(name+".litematic");
        boolean overwrite=s.newSchematic&&s.sourceWritten&&name.equals(sourceName(s));
        if(!overwrite&&Files.exists(result))throw new IOException("Файл с таким именем уже существует");
        for(var entity:s.world.iterateEntities())if(!(entity instanceof PlayerEntity))expand(s.bounds,entity.getBlockPos());
        int[] b=s.bounds;
        long volume=(long)(b[3]-b[0]+1)*(b[4]-b[1]+1)*(b[5]-b[2]+1);
        if(volume>SchematicDocument.MAX_VOLUME)throw new IOException("Область больше 2 млн блоков. Мир сохранён; экспортируй нужную область средствами Litematica.");
        AreaSelection area=new AreaSelection();area.setName(name);area.setExplicitOrigin(s.offset);
        area.addSubRegionBox(new Box(new BlockPos(b[0],b[1],b[2]),new BlockPos(b[3],b[4],b[5]),"CreativeEdit"),false);
        var handler=SchematicWorldHandler.INSTANCE;var previous=handler.getRegistryManager();
        var registries=s.world.getRegistryManager();handler.setDynamicRegistryManager(registries.getOptional(RegistryKeys.BLOCK).isPresent()?registries.toImmutable():DynamicRegistryManager.of(Registries.REGISTRIES));
        LitematicaSchematic output;
        boolean restoreStarter=s.starterVisible;
        try{
            if(restoreStarter)setStarter(s,false);
            output=LitematicaSchematic.createFromWorld(s.world,area,new LitematicaSchematic.SchematicSaveInfo(false,false),MinecraftClient.getInstance().getSession().getUsername(),text->LoggerFactory.getLogger("Litematica extra").warn(text));
        }finally{
            handler.setDynamicRegistryManager(previous.toImmutable());
            if(restoreStarter)setStarter(s,true);
        }
        if(output==null)throw new IOException("Не удалось прочитать область локального мира");
        if(!SchematicFiles.write(output,s.source.getParent(),name,overwrite))throw new IOException("Не удалось записать новую схему");
        if(result.equals(s.source))s.sourceWritten=true;
        lastResult=result;persist(s);return result;
    }
    public static void serverStopping(MinecraftServer server){
        Session s=current;if(s==null||s.server!=server)return;
        try{persist(s);}catch(Exception e){report(e);}
        s.phase=Phase.STOPPED;
    }
    private static void deleteExportedWorld(MinecraftClient client,Session s,Path exported){
        var storage=client.getLevelStorage();
        Path saves=storage.getSavesDirectory().toAbsolutePath().normalize();
        Path world=saves.resolve(s.folder).normalize();
        if(!saves.equals(world.getParent())||!s.folder.startsWith("litematica-extra-")
                ||Files.isSymbolicLink(world)||!Files.isRegularFile(world.resolve(LINK))
                ||!Files.isRegularFile(exported))return;
        try(var level=storage.createSession(s.folder)){
            level.deleteSessionLock();
        }catch(Exception e){
            LoggerFactory.getLogger("Litematica extra").warn("Не удалось удалить временный мир {}",s.folder,e);
        }
    }
    public static boolean isTemporaryWorld(String folder){
        if(!folder.matches("litematica-extra-[0-9a-f-]{36}"))return false;
        Path world=MinecraftClient.getInstance().getLevelStorage().getSavesDirectory().resolve(folder);
        if(Files.isSymbolicLink(world))return false;
        Properties link=new Properties();
        try(var in=Files.newInputStream(world.resolve(LINK))){
            link.load(in);
            return Boolean.parseBoolean(link.getProperty("newSchematic"));
        }catch(IOException|IllegalArgumentException ignored){
            return false;
        }
    }
    private static void persist(Session s)throws IOException{
        Properties p=new Properties();p.setProperty("source",s.source.toString());p.setProperty("name",s.name);
        if(s.newSchematic){
            p.setProperty("newSchematic","true");p.setProperty("starterVisible",Boolean.toString(s.starterVisible));
            p.setProperty("blockCount",Long.toString(s.blockCount));
        }
        p.setProperty("ox",""+s.offset.getX());p.setProperty("oy",""+s.offset.getY());p.setProperty("oz",""+s.offset.getZ());
        for(int i=0;i<6;i++)p.setProperty("b"+i,""+s.bounds[i]);
        Path path=s.server.getSavePath(WorldSavePath.ROOT).resolve(LINK);
        try(var out=Files.newOutputStream(path)){p.store(out,"Litematica extra local creative world");}
    }
    private static void report(Exception e){
        String message=e.getMessage();LoggerFactory.getLogger("Litematica extra").error("Local creative world export failed",e);
        MinecraftClient.getInstance().execute(()->{var client=MinecraftClient.getInstance();if(client.player!=null)client.player.sendMessage(Text.literal("Litematica extra: "+message),false);});
    }
}
