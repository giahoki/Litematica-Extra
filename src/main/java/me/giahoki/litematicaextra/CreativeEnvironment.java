package me.giahoki.litematicaextra;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

enum CreativeEnvironment {
    VOID("void",World.OVERWORLD,64,320,6000),
    FLAT("flat",World.OVERWORLD,-60,320,6000),
    NETHER("nether",World.NETHER,128,256,6000),
    END("end",World.END,100,256,6000),
    VOID_NIGHT("void_night",World.OVERWORLD,64,320,18000);

    final String id;
    final RegistryKey<World> dimension;
    final int baseY,topY,time;

    CreativeEnvironment(String id,RegistryKey<World> dimension,int baseY,int topY,int time) {
        this.id=id;
        this.dimension=dimension;
        this.baseY=baseY;
        this.topY=topY;
        this.time=time;
    }

    static CreativeEnvironment fromId(String id) {
        for(var environment:values())if(environment.id.equals(id))return environment;
        return VOID;
    }
}
