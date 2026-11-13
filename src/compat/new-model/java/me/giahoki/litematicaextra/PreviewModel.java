package me.giahoki.litematicaextra;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

final class PreviewModel {
    static List<BakedQuad> quads(MinecraftClient client,BlockState state){
        var model=client.getBlockRenderManager().getModel(state);
        List<BakedQuad> result=new ArrayList<>();
        for(var part:model.getParts(Random.create(42))){
            result.addAll(part.getQuads(null));
            for(Direction side:Direction.values())result.addAll(part.getQuads(side));
        }
        return result;
    }

    static Sprite particle(MinecraftClient client,BlockState state){return client.getBlockRenderManager().getModel(state).particleSprite();}
    static Direction face(BakedQuad quad){return quad.face();}
    static int[] vertices(BakedQuad quad){return quad.vertexData();}
    static int tint(BakedQuad quad){return quad.tintIndex();}
    static BakedQuad quad(int[] data,Direction side,Sprite sprite){return new BakedQuad(data,-1,side,sprite,true,0);}
}