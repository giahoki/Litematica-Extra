package me.giahoki.litematicaextra;

import java.util.Set;
import java.util.function.Function;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.BiomeKeys;

final class WorldApi {
    static net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig flatConfig(DynamicRegistryManager lookup){
        return net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig.getDefaultConfig(
                lookup.getWrapperOrThrow(RegistryKeys.BIOME),lookup.getWrapperOrThrow(RegistryKeys.STRUCTURE_SET),
                lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE));
    }
    static GameRules rules(){return new GameRules();}
    static net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome> voidBiome(DynamicRegistryManager lookup){return lookup.get(RegistryKeys.BIOME).getEntry(BiomeKeys.THE_VOID).orElseThrow();}
    static Entity loadEntity(NbtCompound nbt,ServerWorld world,Function<Entity,Entity> transform){return EntityType.loadEntityWithPassengers(nbt,world,transform);}
    static void teleport(ServerPlayerEntity player,ServerWorld world,double x,double y,double z){player.teleport(world,x,y,z,Set.of(),180,20);}
    static boolean opaque(BlockState state,BlockPos pos){return state.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE,pos);}
    static BlockState parseBlock(String value)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(),value,false).blockState();}
    static void configure(GameRules rules){
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false,null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false,null);
        rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
        rules.get(GameRules.RANDOM_TICK_SPEED).set(0,null);
        rules.get(GameRules.DO_FIRE_TICK).set(false,null);
        rules.get(GameRules.SPAWN_RADIUS).set(0,null);
    }
    static void freeze(ServerWorld world){
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0,world.getServer());
        world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false,world.getServer());
    }
}
