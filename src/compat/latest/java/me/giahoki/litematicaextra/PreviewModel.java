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
    static int[] vertices(BakedQuad quad){int[] data=new int[32];
        for(int i=0;i<4;i++){
            var p=quad.getPosition(i);
            int base=i*8;
            data[base]=Float.floatToRawIntBits(p.x());
            data[base+1]=Float.floatToRawIntBits(p.y());
            data[base+2]=Float.floatToRawIntBits(p.z());
            long uv=quad.getTexcoords(i);
            data[base+4]=Float.floatToRawIntBits(net.minecraft.client.util.math.Vector2f.getX(uv));
            data[base+5]=Float.floatToRawIntBits(net.minecraft.client.util.math.Vector2f.getY(uv));
        }
        return data;}
    static int tint(BakedQuad quad){return quad.tintIndex();}
    static BakedQuad quad(int[] data,Direction side,Sprite sprite){return new BakedQuad(position(data,0),position(data,8),position(data,16),position(data,24),
                uv(data,0),uv(data,8),uv(data,16),uv(data,24),-1,side,sprite,true,0);}
    private static org.joml.Vector3f position(int[] data,int i){
        return new org.joml.Vector3f(Float.intBitsToFloat(data[i]),Float.intBitsToFloat(data[i+1]),Float.intBitsToFloat(data[i+2]));
    }
    private static long uv(int[] data,int i){return net.minecraft.client.util.math.Vector2f.toLong(Float.intBitsToFloat(data[i+4]),Float.intBitsToFloat(data[i+5]));}
}