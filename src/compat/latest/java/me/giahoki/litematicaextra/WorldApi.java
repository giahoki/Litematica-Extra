package me.giahoki.litematicaextra;

import java.util.Set;
import java.util.function.Function;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.rule.GameRules;
import net.minecraft.world.biome.BiomeKeys;

final class WorldApi {
    static GameRules rules(){return new GameRules(DataConfiguration.SAFE_MODE.enabledFeatures());}
    static net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome> voidBiome(RegistryWrapper.WrapperLookup lookup){return lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.THE_VOID);}
    static Entity loadEntity(NbtCompound nbt,ServerWorld world,Function<Entity,Entity> transform){return EntityType.loadEntityWithPassengers(nbt,world,SpawnReason.LOAD,transform::apply);}
    static void teleport(ServerPlayerEntity player,ServerWorld world,double x,double y,double z){player.teleport(world,x,y,z,Set.of(),180,20,true);}
    static boolean opaque(BlockState state,BlockPos pos){return state.isOpaqueFullCube();}
    static BlockState parseBlock(String value)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return BlockArgumentParser.block(Registries.BLOCK,value,false).blockState();}
    static void configure(GameRules rules){
        rules.setValue(GameRules.ADVANCE_TIME,false,null);
        rules.setValue(GameRules.ADVANCE_WEATHER,false,null);
        rules.setValue(GameRules.DO_MOB_SPAWNING,false,null);
        rules.setValue(GameRules.RANDOM_TICK_SPEED,0,null);
        rules.setValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,0,null);
        rules.setValue(GameRules.RESPAWN_RADIUS,0,null);
    }
    static void freeze(ServerWorld world){
        world.getGameRules().setValue(GameRules.RANDOM_TICK_SPEED,0,world.getServer());
        world.getGameRules().setValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,0,world.getServer());
    }
}