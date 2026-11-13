package me.giahoki.litematicaextra;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

@FunctionalInterface
interface GuiMesh {
    static void border(net.minecraft.client.gui.DrawContext context,int x,int y,int width,int height,int color){
        context.fill(x,y,x+width,y+1,color);
        context.fill(x,y+height-1,x+width,y+height,color);
        context.fill(x,y+1,x+1,y+height-1,color);
        context.fill(x+width-1,y+1,x+width,y+height-1,color);
    }
    void draw(VertexConsumer vertices, Matrix4f matrix, float depth);
}
