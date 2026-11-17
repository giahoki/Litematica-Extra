package me.giahoki.litematicaextra;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import java.util.*;

/** Uses Minecraft baked block models and atlas textures, projected into the 1.21.8 GUI pipeline. */
public final class PreviewRenderer {
    record Face(SchematicDocument.Cell cell, float[] xyz, float[] uv, int color) {}
    private record Projected(Face face, float[] xy, double depth) {}
    private final List<Face> mesh = new ArrayList<>();
    private List<Projected> projected = List.of();
    public final OrbitCamera camera = new OrbitCamera();
    private double extent = 1;
    private double floorY;
    public int faceCount() { return mesh.size(); }
    List<Face> mesh(){return mesh;}
    public void rebuild(SchematicDocument doc, boolean resetCamera) {
        mesh.clear(); projected=List.of();
        MinecraftClient mc = MinecraftClient.getInstance();
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        Map<BlockState,List<BakedQuad>> cache = new HashMap<>();
        for (var cell : doc.occupied.values()) {
            BlockState state = doc.state(cell);
            var pos=cell.position();
            minX=Math.min(minX,pos.getX()); minY=Math.min(minY,pos.getY()); minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX()+1); maxY=Math.max(maxY,pos.getY()+1); maxZ=Math.max(maxZ,pos.getZ()+1);
            List<BakedQuad> quads = cache.computeIfAbsent(state,s -> {
                List<BakedQuad> result = PreviewModel.quads(mc,s);
                if(result.isEmpty()) {
                    var sprite=PreviewModel.particle(mc,s);
                    if(!s.getFluidState().isEmpty()) {
                        String id=s.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)?"block/lava_still":"block/water_still";
                        sprite=Platform.sprite(mc,SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,Identifier.ofVanilla(id));
                    }
                    for(Direction d:Direction.values())result.add(fallback(d,sprite));
                }
                return result;
            });
            for (BakedQuad q : quads) {
                // Cull only quads whose entire face lies on the block boundary.
                var neighbor=doc.occupied.get(pos.offset(PreviewModel.face(q)));
                if (neighbor != null && WorldApi.opaque(doc.state(neighbor),neighbor.position()) && boundary(q)) continue;
                int[] data=PreviewModel.vertices(q); int stride=data.length/4;
                float[] xyz=new float[12],uv=new float[8];
                for (int i=0;i<4;i++) {
                    xyz[i*3]=Float.intBitsToFloat(data[i*stride])+pos.getX();
                    xyz[i*3+1]=Float.intBitsToFloat(data[i*stride+1])+pos.getY();
                    xyz[i*3+2]=Float.intBitsToFloat(data[i*stride+2])+pos.getZ();
                    uv[i*2]=Float.intBitsToFloat(data[i*stride+4]); uv[i*2+1]=Float.intBitsToFloat(data[i*stride+5]);
                }
                int tint=PreviewModel.tint(q)>=0?mc.getBlockColors().getColor(state,null,null,PreviewModel.tint(q)):0xffffff;
                if(state.isOf(net.minecraft.block.Blocks.WATER)) tint=0x3f76e4;
                if (tint == -1) tint=0xffffff;
                float shade = switch(PreviewModel.face(q)) {case UP->1f;case DOWN->0.5f;case NORTH,SOUTH->0.78f;default->0.65f;};
                int r=(int)(((tint>>16)&255)*shade),g=(int)(((tint>>8)&255)*shade),b=(int)((tint&255)*shade);
                mesh.add(new Face(cell,xyz,uv,0xff000000|r<<16|g<<8|b));
                if (mesh.size()>150_000) { mesh.clear(); throw new IllegalArgumentException(ExtraText.message("editor.face_limit")); }
            }
        }
        if (resetCamera) {
            if (minX==Integer.MAX_VALUE) { minX=minY=minZ=0;maxX=maxY=maxZ=1; }
            camera.centerX=(minX+maxX)/2.0;camera.centerY=(minY+maxY)/2.0;camera.centerZ=(minZ+maxZ)/2.0;
            floorY=minY;
            extent=Math.max(1,Math.sqrt(Math.pow(maxX-minX,2)+Math.pow(maxY-minY,2)+Math.pow(maxZ-minZ,2)));
            resetView();
        }
    }
    private static BakedQuad fallback(Direction side,net.minecraft.client.texture.Sprite sprite) {
        float[][] p=switch(side) {
            case UP->new float[][]{{0,1,0},{0,1,1},{1,1,1},{1,1,0}};
            case DOWN->new float[][]{{0,0,1},{0,0,0},{1,0,0},{1,0,1}};
            case NORTH->new float[][]{{1,1,0},{1,0,0},{0,0,0},{0,1,0}};
            case SOUTH->new float[][]{{0,1,1},{0,0,1},{1,0,1},{1,1,1}};
            case WEST->new float[][]{{0,1,0},{0,0,0},{0,0,1},{0,1,1}};
            case EAST->new float[][]{{1,1,1},{1,0,1},{1,0,0},{1,1,0}};
        };
        int[] data=new int[32];
        for(int i=0;i<4;i++) {
            for(int k=0;k<3;k++)data[i*8+k]=Float.floatToRawIntBits(p[i][k]);
            data[i*8+3]=-1;
            data[i*8+4]=Float.floatToRawIntBits(i<2?sprite.getMinU():sprite.getMaxU());
            data[i*8+5]=Float.floatToRawIntBits(i==0||i==3?sprite.getMinV():sprite.getMaxV());
        }
        return PreviewModel.quad(data,side,sprite);
    }
    private static boolean boundary(BakedQuad q) {
        int[] d=PreviewModel.vertices(q);int stride=d.length/4;
        int axis=switch(PreviewModel.face(q).getAxis()){case X->0;case Y->1;case Z->2;};
        float edge=PreviewModel.face(q).getDirection()==Direction.AxisDirection.POSITIVE?1:0;
        for(int i=0;i<4;i++) if(Math.abs(Float.intBitsToFloat(d[i*stride+axis])-edge)>0.001) return false;
        return true;
    }
    public void resetView() {camera.zoom=1;camera.panX=camera.panY=0;camera.yaw=Math.toRadians(40);camera.pitch=Math.toRadians(25);}
    public SchematicDocument.Cell pick(double x,double y) {
        for(int i=projected.size()-1;i>=0;i--) if(OrbitCamera.contains(projected.get(i).xy,x,y)) return projected.get(i).face.cell;
        return null;
    }
    public void draw(DrawContext context,int x,int y,int w,int h,SchematicDocument.Cell selection,boolean grid) {
        camera.screenX=x+w/2.0;camera.screenY=y+h/2.0;camera.scale=Math.min(w,h)*0.86/extent;
        ScreenRect bounds=new ScreenRect(x,y,w,h);
        if(grid) drawFloorGrid(context,bounds);
        List<Projected> out=new ArrayList<>(mesh.size());
        for(Face face:mesh) {
            float[] xy=new float[8];double depth=0;
            float minX=Float.MAX_VALUE,minY=minX,maxX=-minX,maxY=-minX;
            for(int i=0;i<4;i++) {
                var p=camera.project(face.xyz[i*3],face.xyz[i*3+1],face.xyz[i*3+2]);
                xy[i*2]=p.x();xy[i*2+1]=p.y();depth+=p.depth();
                minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());
            }
            if(maxX<x||minX>x+w||maxY<y||minY>y+h) continue;
            out.add(new Projected(face,xy,depth/4));
        }
        out.sort(Comparator.comparingDouble(Projected::depth));projected=out;
        var faces=List.copyOf(out);
        Platform.mesh(context,bounds,SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,(vertices,matrix,depth)->{
            for(Projected p:faces){
                int color=p.face.cell.equals(selection)?0xff73ffee:p.face.color;
                for(int i=0;i<4;i++)Platform.texturedVertex(vertices,matrix,p.xy[i*2],p.xy[i*2+1],depth,p.face.uv[i*2],p.face.uv[i*2+1],color);
            }
        });
    }

    private void drawFloorGrid(DrawContext context,ScreenRect bounds) {
        SmoothGui grid=new SmoothGui();
        double radius=extent*.85;
        double floor=floorY;
        for(int i=-8;i<=8;i++){
            double offset=i*radius/8;
            gridLine(grid,camera.project(camera.centerX+offset,floor,camera.centerZ-radius),
                    camera.project(camera.centerX+offset,floor,camera.centerZ+radius));
            gridLine(grid,camera.project(camera.centerX-radius,floor,camera.centerZ+offset),
                    camera.project(camera.centerX+radius,floor,camera.centerZ+offset));
        }
        grid.submit(context,bounds);
        Platform.nextLayer(context);
    }

    private static void gridLine(SmoothGui grid,OrbitCamera.Point a,OrbitCamera.Point b) {
        double length=Math.hypot(b.x()-a.x(),b.y()-a.y());
        if(length<.01)return;
        double dx=(b.y()-a.y())*.35/length,dy=(a.x()-b.x())*.35/length;
        grid.quad(new SmoothGui.P(a.x()+dx,a.y()+dy),new SmoothGui.P(b.x()+dx,b.y()+dy),
                new SmoothGui.P(b.x()-dx,b.y()-dy),new SmoothGui.P(a.x()-dx,a.y()-dy),0xff252d38);
    }
}
