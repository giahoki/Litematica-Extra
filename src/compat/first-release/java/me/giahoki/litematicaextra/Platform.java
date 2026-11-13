package me.giahoki.litematicaextra;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class Platform {
    public static net.minecraft.world.World world(net.minecraft.entity.Entity entity) { return entity.getWorld(); }
    static net.minecraft.client.texture.Sprite sprite(MinecraftClient client, Identifier atlas, Identifier id) {
        return client.getBakedModelManager().getAtlas(atlas).getSprite(id);
    }
    static void spawn(ServerWorld world, net.minecraft.util.math.BlockPos pos) { world.setSpawnPos(pos, 180); }

    static void mesh(DrawContext context, ScreenRect bounds, Identifier texture, GuiMesh vertices) {
        var layer = texture == null ? RenderLayer.getGui() : RenderLayer.getText(texture);
        var matrix = context.getMatrices().peek().getPositionMatrix();
        context.enableScissor(bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom());
        try {
            context.draw(() -> vertices.draw(context.getVertexConsumers().getBuffer(layer), matrix, 0));
        } finally {
            context.disableScissor();
        }
    }

    static void texture(DrawContext context, Identifier id, int x, int y, int u, int v, int width,
                        int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        context.drawTexture(id, x, y, width, height, u, v,
                regionWidth, regionHeight, textureWidth, textureHeight);
    }

    static void nextLayer(DrawContext context) {
        context.draw();
    }

    static void readBlockEntity(BlockEntity entity, NbtCompound data, ServerWorld world) {
        entity.read(data, world.getRegistryManager());
    }

    static void disconnect(MinecraftClient client, Text message) {
        client.world.disconnect();
        client.disconnect(new MessageScreen(message));
        client.setScreen(new TitleScreen());
    }
    static net.minecraft.util.math.Vec3d cameraPosition(net.minecraft.client.render.Camera camera){return camera.getPos();}
    static void texturedVertex(net.minecraft.client.render.VertexConsumer vertices,org.joml.Matrix4f matrix,
                               float x,float y,float depth,float u,float v,int color){
        vertices.vertex(matrix,x,y,depth).texture(u,v).color(color).light(0x00f000f0);
    }
}
