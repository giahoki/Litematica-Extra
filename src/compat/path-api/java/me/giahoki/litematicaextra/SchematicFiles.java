package me.giahoki.litematicaextra;

import java.nio.file.Path;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import net.minecraft.nbt.NbtCompound;

final class SchematicFiles {
    static Path directory(){return DataManager.getSchematicsBaseDirectory();}
    static LitematicaSchematic read(Path path){return LitematicaSchematic.createFromFile(path.getParent(),path.getFileName().toString());}
    static boolean write(LitematicaSchematic schematic,Path dir,String name,boolean overwrite){return schematic.writeToFile(dir,name,overwrite);}
    static NbtCompound regions(NbtCompound root){return root.getCompoundOrEmpty("Regions");}
}