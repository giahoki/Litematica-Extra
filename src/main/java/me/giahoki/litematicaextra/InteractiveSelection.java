package me.giahoki.litematicaextra;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class InteractiveSelection {
    record Region(BlockPos min,BlockPos max) {
        Vec3d center(){return Vec3d.ofCenter(min).lerp(Vec3d.ofCenter(max),.5);}
        boolean contains(BlockPos pos){
            return pos.getX()>=min.getX()&&pos.getX()<=max.getX()
                    &&pos.getY()>=min.getY()&&pos.getY()<=max.getY()
                    &&pos.getZ()>=min.getZ()&&pos.getZ()<=max.getZ();
        }
        Region adjust(Vec3d axis,int amount,boolean resize){
            int x=(int)axis.x*amount,y=(int)axis.y*amount,z=(int)axis.z*amount;
            if(!resize)return new Region(min.add(x,y,z),max.add(x,y,z));
            return new Region(min,new BlockPos(Math.max(min.getX(),max.getX()+x),
                    Math.max(min.getY(),max.getY()+y),Math.max(min.getZ(),max.getZ()+z)));
        }
    }

    final List<Region> regions=new ArrayList<>();
    int index;
    boolean resize;
    Region active(){return regions.get(index);}
    boolean contains(BlockPos pos){return regions.stream().anyMatch(region->region.contains(pos));}
    void selectNext(){index=(index+1)%regions.size();}
    void remove(){
        if(regions.size()==1)return;
        regions.remove(index);
        index=Math.min(index,regions.size()-1);
    }
    void add(BlockPos pos,int bottom,int top){
        int y=Math.max(bottom,Math.min(top-3,pos.getY()));
        BlockPos start=new BlockPos(pos.getX(),y,pos.getZ());
        regions.add(new Region(start,start.add(2,2,2)));
        index=regions.size()-1;
    }

    String save(MinecraftClient client,String input){
        String name=input.trim().replaceFirst("(?i)\\.litematic$","");
        if(!NativeCreativeWorld.validName(name))return ExtraText.message("save.name_error");
        var world=client.world;
        if(world==null)return ExtraText.message("world.unavailable");
        AreaSelection area=new AreaSelection();
        area.setName(name);
        long total=0;
        for(int i=0;i<regions.size();i++){
            Region box=regions.get(i);
            BlockPos min=box.min(),max=box.max();
            long volume=(max.getX()-min.getX()+1L)*(max.getY()-min.getY()+1L)*(max.getZ()-min.getZ()+1L);
            total+=volume;
            if(total>SchematicDocument.MAX_VOLUME)return ExtraText.message("world.export_limit");
            for(int x=min.getX()>>4;x<=max.getX()>>4;x++)
                for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
                    if(!world.getChunkManager().isChunkLoaded(x,z))return ExtraText.message("selection.unloaded");
            area.addSubRegionBox(new Box(min,max,"Region "+(i+1)),false);
        }
        try{
            var directory=SchematicFiles.directory();
            Files.createDirectories(directory);
            if(Files.exists(directory.resolve(name+".litematic")))return ExtraText.message("save.exists");
            var handler=SchematicWorldHandler.INSTANCE;
            var previous=handler.getRegistryManager();
            var registries=world.getRegistryManager();
            LitematicaSchematic output;
            try{
                handler.setDynamicRegistryManager(registries.getOptional(RegistryKeys.BLOCK).isPresent()
                        ?registries.toImmutable():DynamicRegistryManager.of(Registries.REGISTRIES));
                output=LitematicaSchematic.createFromWorld(world,area,new LitematicaSchematic.SchematicSaveInfo(false,false),
                        client.getSession().getUsername(),message->{});
            }finally{
                handler.setDynamicRegistryManager(previous.toImmutable());
            }
            if(output==null)return ExtraText.message("world.export_read");
            if(!SchematicFiles.write(output,directory,name,false))return ExtraText.message("world.export_write");
            client.setScreen(null);
            if(client.player!=null)client.player.sendMessage(ExtraText.label("save.saved",name+".litematic"),false);
            return "";
        }catch(Exception failure){
            org.slf4j.LoggerFactory.getLogger("Litematica extra").warn("Selection export failed",failure);
            return ExtraText.message("world.export_write");
        }
    }
}
