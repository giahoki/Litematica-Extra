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
import java.util.concurrent.CompletableFuture;

public final class NativeCreativeWorld {
    public static final int PHYSICS_TICK_RETRY_DELAY=20;
    private static final String LINK="litematica-extra-world.properties";
    private static volatile Session current;
    private static volatile Path lastResult;
    private static final Map<MinecraftServer,Optional<CreativePhysics>> linkedWorlds=Collections.synchronizedMap(new WeakHashMap<>());
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
        final CreativeEnvironment environment;
        volatile CreativePhysics physics;
        volatile boolean starterVisible;
        boolean changingStarter,sourceWritten;
        long blockCount;
        volatile Phase phase=Phase.WAITING;
        volatile MinecraftServer server;
        volatile ServerWorld world;
        volatile boolean playerPrepared,announced,exporting;
        Path deleteAfterExport;
        int index,updates,ticks;
        Session(SchematicDocument doc,CreativeEnvironment environment,CreativePhysics physics){
            this.environment=environment;
            this.physics=physics;
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
            int baseY=environment.dimension==net.minecraft.world.World.OVERWORLD
                    ?Math.min(environment.baseY,environment.topY-height):environment.baseY;
            if(baseY < (environment==CreativeEnvironment.FLAT?-60:-64)||baseY+height>environment.topY)
                throw new IllegalArgumentException(ExtraText.message("world.environment.height"));
            int baseX=environment==CreativeEnvironment.END?1024:0;
            offset=new BlockPos(baseX-b[0],baseY-b[1],-b[2]);
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
        Session(Path directory,CreativeEnvironment environment,CreativePhysics physics){
            this.environment=environment;
            this.physics=physics;
            folder="litematica-extra-"+UUID.randomUUID().toString();
            source=directory.toAbsolutePath().resolve(ExtraText.message("world.new")+folder.substring(folder.length()-8)+".litematic");
            name=source.getFileName().toString().replace(".litematic","");
            int x=environment==CreativeEnvironment.END?1024:0;
            offset=new BlockPos(x,environment.baseY,0);
            bounds=new int[]{x,environment.baseY,0,x,environment.baseY,0};
            blocks=List.of();entities=List.of();newSchematic=true;
        }
        Session(Path root,Properties p){
            environment=CreativeEnvironment.fromId(p.getProperty("environment"));
            physics=CreativePhysics.read(p);
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
    static void open(SchematicDocument doc,CreativeEnvironment environment,CreativePhysics physics,Screen fallback){
        open(new Session(doc,environment,physics),fallback);
    }
    static void createEmpty(Path directory,CreativeEnvironment environment,CreativePhysics physics,Screen fallback)throws IOException{
        Files.createDirectories(directory);
        open(new Session(directory,environment,physics),fallback);
    }
    private static void open(Session s,Screen fallback){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world!=null)throw new IllegalStateException(ExtraText.message("world.open_from_title"));
        current=s;lastResult=null;
        var rules=WorldApi.rules();
        WorldApi.configure(rules);
        LevelInfo level=new LevelInfo(s.name,GameMode.CREATIVE,false,Difficulty.NORMAL,true,rules,DataConfiguration.SAFE_MODE);
        client.setScreen(new MessageScreen(Text.literal(s.newSchematic?ExtraText.message("world.creating"):ExtraText.message("world.loading"))));
        client.createIntegratedServerLoader().createAndStart(s.folder,level,new GeneratorOptions(0L,false,false),lookup->{
            var dimensions=WorldPresets.createDemoOptions(lookup);
            return switch(s.environment){
                case NETHER,END -> dimensions;
                case FLAT -> dimensions.with(lookup,new FlatChunkGenerator(WorldApi.flatConfig(lookup)));
                case VOID,VOID_NIGHT -> dimensions.with(lookup,new FlatChunkGenerator(
                        new FlatChunkGeneratorConfig(Optional.empty(),WorldApi.voidBiome(lookup),List.of())));
            };
        },fallback);
    }
    public static boolean isEditing(){Session s=current;return s!=null&&s.phase==Phase.READY&&s.server==MinecraftClient.getInstance().getServer();}
    static CreativePhysics getPhysics(){
        Session s=current;
        return s!=null&&s.phase==Phase.READY&&s.server==MinecraftClient.getInstance().getServer()?s.physics:null;
    }
    static CompletableFuture<Boolean> updatePhysics(CreativePhysics physics){
        Session s=current;
        if(s==null||s.phase!=Phase.READY||s.server!=MinecraftClient.getInstance().getServer())
            return CompletableFuture.completedFuture(false);
        return s.server.submit(()->{
            if(current!=s||s.phase!=Phase.READY)return false;
            try{
                persist(s,physics);
                s.physics=physics;
                linkedWorlds.put(s.server,Optional.of(physics));
                return true;
            }catch(IOException e){
                LoggerFactory.getLogger("Litematica extra").warn("Could not save creative world physics",e);
                return false;
            }
        });
    }
    public static boolean physicsFrozen(net.minecraft.world.WorldView view){
        CreativePhysics physics=physicsFor(view);
        return physics!=null&&!physics.gravity();
    }
    public static boolean fluidsFrozen(net.minecraft.world.WorldView view){
        CreativePhysics physics=physicsFor(view);
        return physics!=null&&!physics.fluids();
    }
    private static CreativePhysics physicsFor(net.minecraft.world.WorldView view){
        if(!(view instanceof ServerWorld world))return null;
        MinecraftServer server=world.getServer();Session s=current;
        Path root=server.getSavePath(WorldSavePath.ROOT);
        if(s!=null&&s.phase!=Phase.STOPPED
                &&(s.server==server||s.server==null&&root.toAbsolutePath().normalize().getFileName().toString().equals(s.folder)))
            return s.physics;
        return linkedWorlds.computeIfAbsent(server,key->{
            Path path=root.resolve(LINK);
            if(!Files.isRegularFile(path))return Optional.empty();
            try(var in=Files.newInputStream(path)){
                Properties properties=new Properties();
                properties.load(in);
                return Optional.of(CreativePhysics.read(properties));
            }catch(IOException failure){
                return Optional.of(CreativePhysics.LEGACY);
            }
        }).orElse(null);
    }
    static ServerWorld activeWorld(){Session s=current;return s==null?null:s.world;}
    static BlockPos sourceOffset(){return current.offset;}
    static Path lastResult(){return lastResult;}
    public static void serverTick(MinecraftServer server){
        Session s=current;
        if(s==null||s.phase==Phase.STOPPED){
            Path root=server.getSavePath(WorldSavePath.ROOT);
            if(!Files.isRegularFile(root.resolve(LINK)))return;
            try{Properties p=new Properties();try(var in=Files.newInputStream(root.resolve(LINK))){p.load(in);}s=new Session(root,p);s.server=server;s.world=server.getWorld(s.environment.dimension);current=s;
                WorldApi.freeze(s.world);
            }
            catch(Exception e){LoggerFactory.getLogger("Litematica extra").warn("Could not read local world link",e);return;}
        }
        if(s.server==null){
            if(!server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(s.folder))return;
            s.server=server;s.world=server.getWorld(s.environment.dimension);s.phase=Phase.IMPORTING;
            server.getOverworld().setTimeOfDay(s.environment.time);
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
                    double x=(s.bounds[0]+s.bounds[3]+1)/2.0,y=Math.min(s.environment.topY-5,s.bounds[4]+3),z=s.bounds[5]+6;
                    WorldApi.teleport(player,s.world,x,y,z);
                    if(s.world==server.getOverworld())Platform.spawn(s.world,BlockPos.ofFloored(x,y,z));
                    s.playerPrepared=true;
                }
                if(++s.ticks%200==0)persist(s);
            }
        }catch(Exception e){s.phase=Phase.ERROR;LoggerFactory.getLogger("Litematica extra").error("Could not prepare the world",e);}
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
            if(s.newSchematic)client.player.sendMessage(ExtraText.label("world.starter"),false);
            client.player.sendMessage(Text.literal(s.newSchematic
                    ?ExtraText.message("world.temp_help")
                    :ExtraText.message("world.persistent_help")),false);
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
            if(!validName(name)){
                return ExtraText.message("save.name_error");
            }
            if(current!=s||!isEditing()||s.exporting)return ExtraText.message("world.unavailable");
            var dialog=(SaveSchematicScreen)client.currentScreen;
            save(s,name,exit,dialog);
            return ExtraText.message("save.progress");
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
                        Platform.disconnect(client,ExtraText.label("world.leaving"));
                    }
                    else if(client.player!=null){client.setScreen(null);client.player.sendMessage(ExtraText.label("save.saved",result.getFileName().toString()),false);}
                });
            }catch(Exception e){
                report(e);
                if(dialog!=null)MinecraftClient.getInstance().execute(()->dialog.failed(e.getMessage()==null?ExtraText.message("save.error"):e.getMessage()));
            }
            finally{s.exporting=false;}
        });
    }
    private static Path export(Session s,String name)throws Exception{
        Path result=s.source.resolveSibling(name+".litematic");
        boolean overwrite=s.newSchematic&&s.sourceWritten&&name.equals(sourceName(s));
        if(!overwrite&&Files.exists(result))throw new IOException(ExtraText.message("save.exists"));
        for(var entity:s.world.iterateEntities())if(!(entity instanceof PlayerEntity))expand(s.bounds,entity.getBlockPos());
        int[] b=s.bounds;
        long volume=(long)(b[3]-b[0]+1)*(b[4]-b[1]+1)*(b[5]-b[2]+1);
        if(volume>SchematicDocument.MAX_VOLUME)throw new IOException(ExtraText.message("world.export_limit"));
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
        if(output==null)throw new IOException(ExtraText.message("world.export_read"));
        if(!SchematicFiles.write(output,s.source.getParent(),name,overwrite))throw new IOException(ExtraText.message("world.export_write"));
        if(result.equals(s.source))s.sourceWritten=true;
        lastResult=result;persist(s);return result;
    }
    static boolean validName(String name){
        return !name.isBlank()&&!name.startsWith(".")&&!name.endsWith(".")&&!name.endsWith(" ")
                &&!name.matches(".*[\\p{Cntrl}<>:\"/\\\\|?*].*")
                &&!name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?");
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
            LoggerFactory.getLogger("Litematica extra").warn("Could not delete temporary world {}",s.folder,e);
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
        persist(s,s.physics);
    }
    private static void persist(Session s,CreativePhysics physics)throws IOException{
        Properties p=new Properties();p.setProperty("source",s.source.toString());p.setProperty("name",s.name);
        p.setProperty("environment",s.environment.id);
        physics.write(p);
        if(s.newSchematic){
            p.setProperty("newSchematic","true");p.setProperty("starterVisible",Boolean.toString(s.starterVisible));
            p.setProperty("blockCount",Long.toString(s.blockCount));
        }
        p.setProperty("ox",""+s.offset.getX());p.setProperty("oy",""+s.offset.getY());p.setProperty("oz",""+s.offset.getZ());
        for(int i=0;i<6;i++)p.setProperty("b"+i,""+s.bounds[i]);
        Path path=s.server.getSavePath(WorldSavePath.ROOT).resolve(LINK);
        Path temporary=Files.createTempFile(path.getParent(),LINK,".tmp");
        try{
            try(var out=Files.newOutputStream(temporary)){p.store(out,"Litematica extra local creative world");}
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void report(Exception e){
        String message=e.getMessage();LoggerFactory.getLogger("Litematica extra").error("Local creative world export failed",e);
        MinecraftClient.getInstance().execute(()->{var client=MinecraftClient.getInstance();if(client.player!=null)client.player.sendMessage(Text.literal("Litematica extra: "+message),false);});
    }
}
