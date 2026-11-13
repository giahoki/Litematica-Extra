package me.giahoki.litematicaextra;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

final class SchematicData {
    static NbtCompound vanilla(NbtCompound data){return data==null?null:data.copy();}
    static NbtCompound entity(LitematicaSchematic.EntityInfo entity){return vanilla(entity.nbt);}
    static void restore(LitematicaSchematic schematic,String region,BlockPos pos,NbtCompound data){
        var map=schematic.getBlockEntityMapForRegion(region);
        if(map==null)return;
        if(data==null)map.remove(pos);
        else map.put(pos,data);
    }
}