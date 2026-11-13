package me.giahoki.litematicaextra;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import fi.dy.masa.litematica.util.FileType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.tick.OrderedTick;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

public final class SchematicDocument {
    public static final long MAX_VOLUME = 2_000_000;
    public final LitematicaSchematic schematic;
    public final Path source;
    public final List<Cell> cells = new ArrayList<>();
    public final Map<BlockPos, Cell> occupied = new HashMap<>();
    private final Map<BlockPos,Cell> slots=new HashMap<>();
    private final Map<BlockPos,BlockState> additions=new HashMap<>();
    private long originalVolume;
    private final Deque<Edit> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private long revision, savedRevision, nextRevision;
    private int blockCount;
    public record Cell(String region, int x, int y, int z, BlockPos position) {}
    private record Change(Cell cell, BlockState before, BlockState after, NbtCompound entity,
                          OrderedTick<Block> blockTick, OrderedTick<Fluid> fluidTick) {}
    private record Edit(long before, long after, List<Change> changes) {}

    public SchematicDocument(Path file) throws Exception {
        source = file;
        if (Files.size(file) > 32L * 1024 * 1024) throw new IllegalArgumentException("Файл больше 32 МБ — пока не поддерживается");
        schematic = loadSchematic(file);
        if (schematic == null) throw new IllegalArgumentException("Litematica не смогла прочитать схему");
        long volume = 0;
        for (String name : schematic.getAreaSizes().keySet()) {
            Vec3i s = schematic.getSubRegionContainer(name).getSize();
            volume += (long)s.getX()*s.getY()*s.getZ();
            if (volume > MAX_VOLUME) throw new IllegalArgumentException("Лимит редактора: 2 млн ячеек");
        }
        for (String name : new TreeSet<>(schematic.getAreaSizes().keySet())) {
            var c = schematic.getSubRegionContainer(name);
            Vec3i s = c.getSize(), signed = schematic.getAreaSize(name);
            BlockPos p = schematic.getSubRegionPosition(name);
            int ox = OrbitCamera.regionMin(p.getX(), signed.getX());
            int oy = OrbitCamera.regionMin(p.getY(), signed.getY());
            int oz = OrbitCamera.regionMin(p.getZ(), signed.getZ());
            for (int y=0;y<s.getY();y++) for (int z=0;z<s.getZ();z++) for (int x=0;x<s.getX();x++) {
                if (!c.get(x,y,z).isAir()) cells.add(new Cell(name,x,y,z,new BlockPos(ox+x,oy+y,oz+z)));
            }
        }
        blockCount = cells.size();
        originalVolume=volume;
        for(Cell cell:cells)slots.put(cell.position,cell);
        reindex();
    }
    public static LitematicaSchematic loadSchematic(Path file) {
        return withBlockRegistry(()->SchematicFiles.read(file));
    }
    private static <T> T withBlockRegistry(Supplier<T> action) {
        // Palette loading and resizing expect a world registry, including in the title screen.
        // Use registered client blocks for this synchronous operation, then restore Litematica's state.
        var handler=SchematicWorldHandler.INSTANCE;
        var previous=handler.getRegistryManager();
        boolean fallback=previous.getOptional(RegistryKeys.BLOCK).isEmpty();
        try {
            if(fallback)handler.setDynamicRegistryManager(DynamicRegistryManager.of(Registries.REGISTRIES));
            return action.get();
        } finally {
            if(fallback)handler.setDynamicRegistryManager(previous.toImmutable());
        }
    }
    public BlockState state(Cell c) { return c.region==null?additions.getOrDefault(c.position,net.minecraft.block.Blocks.AIR.getDefaultState()):container(c).get(c.x,c.y,c.z); }
    public BlockState stateAt(BlockPos pos) { Cell c=occupied.get(pos);return c==null?net.minecraft.block.Blocks.AIR.getDefaultState():state(c); }
    private LitematicaBlockStateContainer container(Cell c) { return schematic.getSubRegionContainer(c.region); }
    public boolean dirty() { return revision != savedRevision; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public int blockCount() { return blockCount; }
    private void reindex() {
        occupied.clear();
        for (Cell c : cells) if (!state(c).isAir()) occupied.put(c.position,c);
    }
    public void replace(Cell selected, BlockState replacement, boolean all) {
        if (selected == null || state(selected).equals(replacement)) return;
        Block block = state(selected).getBlock();
        List<Change> changes = new ArrayList<>();
        for (Cell c : all ? cells : List.of(selected)) {
            BlockState before = state(c);
            if (before.isAir() || before.getBlock() != block || before.equals(replacement)) continue;
            BlockPos local = new BlockPos(c.x,c.y,c.z);
            var entities = schematic.getBlockEntityMapForRegion(c.region);
            var ticks = schematic.getScheduledBlockTicksForRegion(c.region);
            var fluids = schematic.getScheduledFluidTicksForRegion(c.region);
            changes.add(new Change(c,before,replacement,
                    entities == null ? null : SchematicData.vanilla(entities.get(local)), ticks == null ? null : ticks.get(local),
                    fluids == null ? null : fluids.get(local)));
        }
        if (changes.isEmpty()) return;
        if (changes.size() > 100_000) throw new IllegalArgumentException("За раз можно изменить до 100 000 блоков");
        commit(changes);
    }
    private void commit(List<Change> changes) {
        withBlockRegistry(()->{for (Change change : changes) apply(change,true);return null;});
        long after=++nextRevision;
        undo.push(new Edit(revision,after,changes)); while (undo.size()>24) undo.removeLast();
        redo.clear(); revision=after; reindex();
    }
    public void setBlock(BlockPos position,BlockState value) {
        Cell cell=occupied.get(position);
        if(cell==null)cell=slots.get(position);
        if(cell==null&&value.isAir())return;
        if(cell==null){
            for(String name:new TreeSet<>(schematic.getAreaSizes().keySet())){
                BlockPos origin=schematic.getSubRegionPosition(name),signed=schematic.getAreaSize(name);
                int x=position.getX()-OrbitCamera.regionMin(origin.getX(),signed.getX());
                int y=position.getY()-OrbitCamera.regionMin(origin.getY(),signed.getY());
                int z=position.getZ()-OrbitCamera.regionMin(origin.getZ(),signed.getZ());
                var size=schematic.getSubRegionContainer(name).getSize();
                if(x>=0&&y>=0&&z>=0&&x<size.getX()&&y<size.getY()&&z<size.getZ()){cell=new Cell(name,x,y,z,position.toImmutable());break;}
            }
            if(cell==null)cell=new Cell(null,position.getX(),position.getY(),position.getZ(),position.toImmutable());
            slots.put(position.toImmutable(),cell);cells.add(cell);
        }
        BlockState before=state(cell);if(before.equals(value))return;
        if(cell.region==null&&!value.isAir()){
            int[] b=extraBounds(position);long volume=(long)(b[3]-b[0]+1)*(b[4]-b[1]+1)*(b[5]-b[2]+1);
            if(originalVolume+volume>MAX_VOLUME)throw new IllegalArgumentException("Достройка превышает лимит 2 млн ячеек");
        }
        BlockPos local=new BlockPos(cell.x,cell.y,cell.z);
        var entities=schematic.getBlockEntityMapForRegion(cell.region);var ticks=schematic.getScheduledBlockTicksForRegion(cell.region);var fluids=schematic.getScheduledFluidTicksForRegion(cell.region);
        commit(List.of(new Change(cell,before,value,entities==null?null:SchematicData.vanilla(entities.get(local)),ticks==null?null:ticks.get(local),fluids==null?null:fluids.get(local))));
    }
    private int[] extraBounds(BlockPos include){
        int[] b={include.getX(),include.getY(),include.getZ(),include.getX(),include.getY(),include.getZ()};
        for(var e:additions.entrySet())if(!e.getValue().isAir()){
            var p=e.getKey();b[0]=Math.min(b[0],p.getX());b[1]=Math.min(b[1],p.getY());b[2]=Math.min(b[2],p.getZ());b[3]=Math.max(b[3],p.getX());b[4]=Math.max(b[4],p.getY());b[5]=Math.max(b[5],p.getZ());
        }
        return b;
    }
    private void apply(Change c, boolean forward) {
        BlockState next = forward ? c.after : c.before;
        BlockState old = state(c.cell);
        blockCount += (old.isAir()?0:-1) + (next.isAir()?0:1);
        if(c.cell.region==null){if(next.isAir())additions.remove(c.cell.position);else additions.put(c.cell.position,next);}
        else container(c.cell).set(c.cell.x,c.cell.y,c.cell.z,next);
        if (c.cell.region!=null && c.before.getBlock() != c.after.getBlock()) {
            BlockPos pos = new BlockPos(c.cell.x,c.cell.y,c.cell.z);
            SchematicData.restore(schematic,c.cell.region,pos,forward?null:c.entity);
            restore(schematic.getScheduledBlockTicksForRegion(c.cell.region),pos,forward?null:c.blockTick);
            restore(schematic.getScheduledFluidTicksForRegion(c.cell.region),pos,forward?null:c.fluidTick);
        }
    }
    private static <T> void restore(Map<BlockPos,T> map, BlockPos pos,T value) {
        if (map != null) { if (value == null) map.remove(pos); else map.put(pos,value); }
    }
    public void undo() { if (!undo.isEmpty()) { var c=undo.pop(); withBlockRegistry(()->{c.changes.forEach(v->apply(v,false));return null;}); redo.push(c); revision=c.before; reindex(); } }
    public void redo() { if (!redo.isEmpty()) { var c=redo.pop(); withBlockRegistry(()->{c.changes.forEach(v->apply(v,true));return null;}); undo.push(c); revision=c.after; reindex(); } }
    public Path saveCopy() throws Exception {
        String stem = source.getFileName().toString().replaceFirst("(?i)\\.litematic$", "");
        Path directory = source.getParent();
        String name = stem + "_edited";
        int suffix = 2;
        while (Files.exists(directory.resolve(name+".litematic"))) name = stem+"_edited_"+suffix++;
        schematic.getMetadata().setTotalBlocks(blockCount);
        schematic.getMetadata().setTimeModifiedToNow();
        LitematicaSchematic output=schematic;
        if(!additions.isEmpty()){
            int[] b=extraBounds(additions.keySet().iterator().next());
            NbtCompound root=schematic.writeToNBT(),regions=SchematicFiles.regions(root);
            long addedVolume=0;
            // Split at original region boundaries: no new region's air may cover an old region.
            for(var group:partitionAdditions().values()){
                int[] bounds=boundsOf(group.keySet());int sx=bounds[3]-bounds[0]+1,sy=bounds[4]-bounds[1]+1,sz=bounds[5]-bounds[2]+1;
                addedVolume+=(long)sx*sy*sz;
                LitematicaBlockStateContainer added=new LitematicaBlockStateContainer(sx,sy,sz);
                withBlockRegistry(()->{group.forEach((p,s)->added.set(p.getX()-bounds[0],p.getY()-bounds[1],p.getZ()-bounds[2],s));return null;});
                NbtCompound region=new NbtCompound();region.put("Position",vectorTag(bounds[0],bounds[1],bounds[2]));region.put("Size",vectorTag(sx,sy,sz));
                region.put("BlockStatePalette",added.getPalette().writeToNBT());region.put("BlockStates",new NbtLongArray(added.getBackingLongArray()));
                region.put("TileEntities",new NbtList());region.put("Entities",new NbtList());region.put("PendingBlockTicks",new NbtList());region.put("PendingFluidTicks",new NbtList());
                String regionName="Extra_Build";int index=2;while(regions.contains(regionName))regionName="Extra_Build_"+index++;
                regions.put(regionName,region);
            }
            root.put("Regions",regions);
            output=withBlockRegistry(()->new LitematicaSchematic(source,root,FileType.LITEMATICA_SCHEMATIC));
            output.getMetadata().setRegionCount(regions.getKeys().size());
            output.getMetadata().setTotalVolume((int)(originalVolume+addedVolume));
            for(var e:schematic.getAreaSizes().entrySet()){
                BlockPos p=schematic.getSubRegionPosition(e.getKey()),s=e.getValue();
                int x=OrbitCamera.regionMin(p.getX(),s.getX()),y=OrbitCamera.regionMin(p.getY(),s.getY()),z=OrbitCamera.regionMin(p.getZ(),s.getZ());
                b[0]=Math.min(b[0],x);b[1]=Math.min(b[1],y);b[2]=Math.min(b[2],z);b[3]=Math.max(b[3],x+Math.abs(s.getX())-1);b[4]=Math.max(b[4],y+Math.abs(s.getY())-1);b[5]=Math.max(b[5],z+Math.abs(s.getZ())-1);
            }
            output.getMetadata().setEnclosingSize(new Vec3i(b[3]-b[0]+1,b[4]-b[1]+1,b[5]-b[2]+1));
        }
        if (!SchematicFiles.write(output,directory,name,false)) throw new java.io.IOException("Не удалось сохранить копию");
        savedRevision = revision;
        return directory.resolve(name+".litematic");
    }
    private Map<BlockPos,Map<BlockPos,BlockState>> partitionAdditions(){
        List<TreeSet<Integer>> cuts=List.of(new TreeSet<>(),new TreeSet<>(),new TreeSet<>());
        for(var e:schematic.getAreaSizes().entrySet()){
            BlockPos p=schematic.getSubRegionPosition(e.getKey()),s=e.getValue();int[] pos={p.getX(),p.getY(),p.getZ()},size={s.getX(),s.getY(),s.getZ()};
            for(int axis=0;axis<3;axis++){int min=OrbitCamera.regionMin(pos[axis],size[axis]);cuts.get(axis).add(min);cuts.get(axis).add(min+Math.abs(size[axis]));}
        }
        List<List<Integer>> boundaries=cuts.stream().map(c->new ArrayList<>(c)).map(c->(List<Integer>)c).toList();
        Map<BlockPos,Map<BlockPos,BlockState>> groups=new LinkedHashMap<>();
        additions.forEach((p,s)->{
            BlockPos key=new BlockPos(band(boundaries.get(0),p.getX()),band(boundaries.get(1),p.getY()),band(boundaries.get(2),p.getZ()));
            groups.computeIfAbsent(key,k->new LinkedHashMap<>()).put(p,s);
        });
        return groups;
    }
    private static int band(List<Integer> cuts,int value){int result=Collections.binarySearch(cuts,value);return result>=0?result+1:-result-1;}
    private static int[] boundsOf(Collection<BlockPos> positions){
        int[] b={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
        for(BlockPos p:positions){b[0]=Math.min(b[0],p.getX());b[1]=Math.min(b[1],p.getY());b[2]=Math.min(b[2],p.getZ());b[3]=Math.max(b[3],p.getX());b[4]=Math.max(b[4],p.getY());b[5]=Math.max(b[5],p.getZ());}return b;
    }
    private static NbtCompound vectorTag(int x,int y,int z){NbtCompound n=new NbtCompound();n.putInt("x",x);n.putInt("y",y);n.putInt("z",z);return n;}
}
