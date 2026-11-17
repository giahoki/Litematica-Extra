package me.giahoki.litematicaextra;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fi.dy.masa.malilib.util.nbt.NbtView;
import me.giahoki.litematicaextra.mixin.DrawContextAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class Platform {
    public static net.minecraft.world.World world(net.minecraft.entity.Entity entity) { return entity.getEntityWorld(); }
    static net.minecraft.client.texture.Sprite sprite(MinecraftClient client, Identifier atlas, Identifier id) {
        return client.getAtlasManager().getSprite(new net.minecraft.client.util.SpriteIdentifier(atlas,id));
    }
    static void spawn(ServerWorld world, net.minecraft.util.math.BlockPos pos) { world.setSpawnPoint(new net.minecraft.world.WorldProperties.SpawnPoint(net.minecraft.util.math.GlobalPos.create(world.getRegistryKey(),pos),180,0)); }

    private static final Matrix4f IDENTITY = new Matrix4f();

    private static final RenderPipeline STUDIO_ROUND = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.of("litematica_extra", "pipeline/studio_round"))
            .withVertexShader(Identifier.of("litematica_extra", "core/studio_round"))
            .withFragmentShader(Identifier.of("litematica_extra", "core/studio_round"))
            .withVertexFormat(net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.DrawMode.QUADS)
            .build();

    static void drawAxisLabel(DrawContext context, net.minecraft.client.font.TextRenderer font,
                              String label, float x, float y, float scale, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            context.drawText(font, label, -font.getWidth(label) / 2, -4, color, false);
        } finally {
            matrices.popMatrix();
        }
    }

    static void roundedRect(DrawContext context, ScreenRect bounds, int color) {
        float u = bounds.width() / 2f;
        float v = bounds.height() / 2f;
        ((DrawContextAccessor) context).studio$getState().addSimpleElement(
                new MeshState(STUDIO_ROUND, TextureSetup.empty(), bounds, (vertices, matrix, depth) -> {
                    texturedVertex(vertices, matrix, bounds.getLeft(), bounds.getTop(), depth, -u, -v, color);
                    texturedVertex(vertices, matrix, bounds.getLeft(), bounds.getBottom(), depth, -u, v, color);
                    texturedVertex(vertices, matrix, bounds.getRight(), bounds.getBottom(), depth, u, v, color);
                    texturedVertex(vertices, matrix, bounds.getRight(), bounds.getTop(), depth, u, -v, color);
                }));
    }

    static void mesh(DrawContext context, ScreenRect bounds, Identifier texture, GuiMesh vertices) {
        var setup = texture == null ? TextureSetup.empty() : GuiTexture.setup(
                MinecraftClient.getInstance().getTextureManager().getTexture(texture));
        var pipeline = texture == null ? RenderPipelines.GUI : RenderPipelines.GUI_TEXTURED;
        ((DrawContextAccessor)context).studio$getState().addSimpleElement(new MeshState(pipeline, setup, bounds, vertices));
    }

    private record MeshState(RenderPipeline pipeline, TextureSetup textureSetup, ScreenRect bounds,
                             GuiMesh mesh) implements SimpleGuiElementRenderState {
        @Override public ScreenRect scissorArea() { return bounds; }
        @Override public void setupVertices(VertexConsumer vertices) {
            mesh.draw(vertices, IDENTITY, 0);
        }
    }

    static void texture(DrawContext context, Identifier id, int x, int y, int u, int v, int width,
                        int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, width, height,
                regionWidth, regionHeight, textureWidth, textureHeight);
    }

    static void nextLayer(DrawContext context) {
        context.createNewRootLayer();
    }

    static void readBlockEntity(BlockEntity entity, NbtCompound data, ServerWorld world) {
        entity.read(NbtView.getReader(data, world.getRegistryManager()).getReader());
    }

    static void disconnect(MinecraftClient client, Text message) {
        client.disconnect(message); client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
    }
    static net.minecraft.util.math.Vec3d cameraPosition(net.minecraft.client.render.Camera camera){return CameraPosition.get(camera);}
    static void texturedVertex(net.minecraft.client.render.VertexConsumer vertices,org.joml.Matrix4f matrix,
                               float x,float y,float depth,float u,float v,int color){
        vertices.vertex(matrix,x,y,depth).texture(u,v).color(color);
    }
}
