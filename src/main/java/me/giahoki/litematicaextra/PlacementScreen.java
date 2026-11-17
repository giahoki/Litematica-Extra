package me.giahoki.litematicaextra;

import com.mojang.authlib.GameProfile;
import me.giahoki.litematicaextra.mixin.PlacementFovAccess;
import me.giahoki.litematicaextra.mixin.PlacementMouseAccess;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;
import java.util.UUID;

public final class PlacementScreen extends PlatformScreen {
    private static final Vec3d[] AXES={new Vec3d(1,0,0),new Vec3d(0,1,0),new Vec3d(0,0,1)};
    private static final int[] COLORS={0xffff776c,0xffb4eb70,0xff51bfff};
    private static final String[] LABELS={"X","Y","Z"};
    private final SchematicPlacement placement;
    private final InteractiveSelection selection;
    private InteractiveSelection.Region dragRegion;
    private static final class CameraPlayer extends OtherClientPlayerEntity {
        float previousYaw;
        CameraPlayer(net.minecraft.client.world.ClientWorld world){super(world,new GameProfile(UUID.randomUUID(),"ExtraCamera"));}
        // LivingEntity interpolates headYaw by default; this detached entity never receives head packets.
        @Override public float getYaw(float tickProgress){return MathHelper.lerpAngleDegrees(tickProgress,previousYaw,getYaw());}
    }
    private CameraPlayer camera;
    private Entity previousCamera;
    private Perspective previousPerspective;
    private Vec3d launchFrom,launchTo,velocity=Vec3d.ZERO;
    private float launchPitch;
    private int launchTick;
    private boolean cursorShown,altDown,finished;
    private int dragAxis=-1;
    private BlockPos dragOrigin;
    private double dragParameter;
    private double speed=.45;
    private String feedback="";
    private Projection projection;
    private net.minecraft.client.font.TextRenderer hudFont;
    private final Point[] tips=new Point[3];
    private Point pivotPoint;
    private Vec3d pivot;
    private boolean rDown;
    private Vec3d dragPivot;
    private record Point(double x,double y){}
    private record Projection(Vec3d eye,Quaternionf rotation,double focal,int width,int height){
        private Vector3f cameraSpace(Vec3d p){
            Vector3f v=new Vector3f((float)(p.x-eye.x),(float)(p.y-eye.y),(float)(p.z-eye.z));
            return new Quaternionf(rotation).conjugate().transform(v);
        }
        private Point screenPoint(Vector3f v){
            return new Point(width*.5+v.x*focal/-v.z,height*.5-v.y*focal/-v.z);
        }
        Point project(Vec3d p){
            Vector3f v=cameraSpace(p);
            return v.z>=-.05f?null:screenPoint(v);
        }
        Point projectResizeTip(Vec3d start,Vec3d end){
            Vector3f tip=cameraSpace(end);
            if(tip.z<-.05f)return screenPoint(tip);
            Vector3f origin=cameraSpace(start);
            if(origin.z>=-.05f)return null;
            origin.lerp(tip,(-.05f-origin.z)/(tip.z-origin.z));
            origin.z=-.05f;
            return screenPoint(origin);
        }
        Vec3d ray(double x,double y){
            Vector3f v=new Vector3f((float)((x-width*.5)/focal),(float)(-(y-height*.5)/focal),-1).normalize();
            rotation.transform(v);return new Vec3d(v.x,v.y,v.z).normalize();
        }
    }
    private PlacementScreen(SchematicPlacement placement){
        super(ExtraText.label(placement==null?"selection.title":"placement"));
        this.placement=placement;
        selection=placement==null?new InteractiveSelection():null;
    }
    public static void openSelection(){
        var client=MinecraftClient.getInstance();
        if(client.world!=null&&client.player!=null)client.setScreen(new PlacementScreen(null));
    }
    public static PlacementScreen current(){return MinecraftClient.getInstance().currentScreen instanceof PlacementScreen s?s:null;}
    public SchematicPlacement placement(){return placement;}
    public boolean cursorShown(){return cursorShown;}
    public OtherClientPlayerEntity camera(){return camera;}
    public static void open(LitematicaSchematic schematic){
        var mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null)return;
        var manager=DataManager.getSchematicPlacementManager();
        SchematicHolder.getInstance().addSchematic(schematic,true);
        var p=SchematicPlacement.createFor(schematic,mc.player.getBlockPos(),schematic.getMetadata().getName(),true,true);
        manager.addSchematicPlacement(p,true);manager.setSelectedSchematicPlacement(p);
        mc.setScreen(new PlacementScreen(p));
    }
    @Override protected void init(){
        hudFont=studioFont(textRenderer);
        if(finished&&camera!=null){
            finished=false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.setCameraEntity(camera);
        }
        if(camera==null){
            previousCamera=client.getCameraEntity();previousPerspective=client.options.getPerspective();
            camera=new CameraPlayer(client.world);
            camera.copyPositionAndRotation(client.player);camera.noClip=true;
            launchFrom=new Vec3d(camera.getX(),camera.getY(),camera.getZ());launchPitch=camera.getPitch();
            launchTo=launchFrom.subtract(camera.getRotationVec(1).multiply(5)).add(0,1.5,0);
            syncPrevious();client.options.setPerspective(Perspective.FIRST_PERSON);client.setCameraEntity(camera);
            KeyBinding.unpressAll();
        }
        if(selection!=null){
            if(selection.regions.isEmpty())addRegion();
            control("save.submit",12,this::saveSelection);
            control("selection.add",35,this::addRegion);
            control("selection.next",58,()->{cancelDrag();selection.selectNext();});
            control("selection.remove",81,()->{cancelDrag();selection.remove();});
            control("cancel",104,this::close);
            applyCursor();
            return;
        }
        addDrawableChild(ButtonWidget.builder(ExtraText.label("done"),b->close()).dimensions(width-180,16,78,20).build());
        addDrawableChild(ButtonWidget.builder(ExtraText.label("cancel_action"),b->cancelPlacement()).dimensions(width-96,16,80,20).build());
        applyCursor();
    }
    private void control(String key,int y,Runnable action){
        addDrawableChild(ButtonWidget.builder(ExtraText.label(key),b->action.run()).dimensions(width-116,y,104,20).build());
    }
    private void addRegion(){
        cancelDrag();
        Entity eye=selection.regions.isEmpty()?client.player:camera;
        var hit=eye.raycast(16,1,false);
        Vec3d target=hit.getType()==net.minecraft.util.hit.HitResult.Type.MISS
                ?eye.getEyePos().add(eye.getRotationVec(1).multiply(7)):hit.getPos().subtract(eye.getRotationVec(1).multiply(.01));
        selection.add(BlockPos.ofFloored(target).add(-1,0,-1),client.world.getBottomY(),
                client.world.getBottomY()+client.world.getHeight());
    }
    private void saveSelection(){
        cancelDrag();
        client.setScreen(new SaveSchematicScreen(this,ExtraText.message("selection.filename"),
                name->selection.save(client,name)));
    }
    private void syncPrevious(){
        camera.resetPosition();
        camera.previousYaw=camera.getYaw();
        camera.lastRenderX=camera.getX();camera.lastRenderY=camera.getY();camera.lastRenderZ=camera.getZ();
    }
    public void look(double dx,double dy){
        if(!cursorShown){if(Math.abs(dx)+Math.abs(dy)>.1)launchTick=12;camera.changeLookDirection(dx,dy);}
    }
    public void handleAlt(boolean down){
        if(PlacementSettings.HOLD_ALT.getBooleanValue())setCursorShown(down);
        else if(down&&!altDown)setCursorShown(!cursorShown);
        altDown=down;
    }
    public void setCursorShown(boolean shown){
        if(cursorShown==shown)return;
        dragAxis=-1;cursorShown=shown;if(shown)launchTick=12;velocity=Vec3d.ZERO;applyCursor();
    }
    private void applyCursor(){
        for(var child:children())if(child instanceof ButtonWidget button)button.visible=cursorShown;
        if(cursorShown){client.mouse.unlockCursor();return;}
        if(!client.isWindowFocused())return;
        // Mouse.lockCursor() closes the current screen. Capture without leaving this input surface.
        var access=(PlacementMouseAccess)client.mouse;
        double x=client.getWindow().getWidth()/2.0,y=client.getWindow().getHeight()/2.0;
        access.extra$x(x);access.extra$y(y);access.extra$cursorLocked(true);access.extra$resetDelta(true);
        GLFW.glfwSetCursorPos(client.getWindow().getHandle(),x,y); GLFW.glfwSetInputMode(client.getWindow().getHandle(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_DISABLED);
        KeyBinding.unpressAll();
    }
    private void rotate(BlockRotation target,BlockPos origin){
        if(placement.getRotation()==target&&placement.getOrigin().equals(origin))return;
        feedback="";
        placement.setRotation(target,new IMessageConsumer(){
            @Override public void addMessage(MessageType type,String key,Object... args){
                feedback=Text.translatable(key,args).getString();
            }
            @Override public void addMessage(MessageType type,int duration,String key,Object... args){
                addMessage(type,key,args);
            }
        });
        if(placement.getRotation()==target)placement.setOrigin(origin,s->feedback=s);
    }
    private Vec3d center(){
        if(selection!=null)return selection.active().center();
        BlockPos min=null,max=null;
        for(var box:placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.ANY).values()){
            var a=box.getPos1();var b=box.getPos2();
            var low=BlockPos.ofFloored(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
            var high=BlockPos.ofFloored(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
            min=min==null?low:BlockPos.ofFloored(Math.min(min.getX(),low.getX()),Math.min(min.getY(),low.getY()),Math.min(min.getZ(),low.getZ()));
            max=max==null?high:BlockPos.ofFloored(Math.max(max.getX(),high.getX()),Math.max(max.getY(),high.getY()),Math.max(max.getZ(),high.getZ()));
        }
        return min==null?Vec3d.ofCenter(placement.getOrigin()):Vec3d.ofCenter(min).lerp(Vec3d.ofCenter(max),.5);
    }
    private boolean held(KeyBinding binding){
        InputUtil.Key k=InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if(k.getCode()<0)return false;
        long window=client.getWindow().getHandle();
        return k.getCategory()==InputUtil.Type.MOUSE?GLFW.glfwGetMouseButton(window,k.getCode())==GLFW.GLFW_PRESS:GLFW.glfwGetKey(window,k.getCode())==GLFW.GLFW_PRESS;
    }
    @Override public void tick(){
        if(client.world==null||client.player==null||Platform.world(camera)!=client.world||client.player.isDead()){close();return;}
        if(client.getCameraEntity()!=camera){close();return;}
        syncPrevious();
        boolean focused=client.isWindowFocused();long window=client.getWindow().getHandle();
        handleAlt(focused&&(GLFW.glfwGetKey(window,GLFW.GLFW_KEY_LEFT_ALT)==GLFW.GLFW_PRESS||GLFW.glfwGetKey(window,GLFW.GLFW_KEY_RIGHT_ALT)==GLFW.GLFW_PRESS));
        if(!focused){velocity=Vec3d.ZERO;dragAxis=-1;rDown=false;return;}
        if(launchTick<12){
            double t=++launchTick/12.0;t=t*t*(3-2*t);
            camera.setPosition(launchFrom.lerp(launchTo,t));
            camera.setPitch(MathHelper.lerp((float)t,launchPitch,MathHelper.clamp(launchPitch+15,-60,65)));
            return;
        }
        if(cursorShown)return;
        if(!client.mouse.isCursorLocked())applyCursor();
        var o=client.options;
        double f=(held(o.forwardKey)?1:0)-(held(o.backKey)?1:0),r=(held(o.rightKey)?1:0)-(held(o.leftKey)?1:0);
        double up=(held(o.jumpKey)?1:0)-(held(o.sneakKey)?1:0);
        double yaw=Math.toRadians(camera.getYaw());
        Vec3d forward=camera.getRotationVec(1),right=new Vec3d(-Math.cos(yaw),0,-Math.sin(yaw));
        Vec3d direction=forward.multiply(f).add(right.multiply(r)).add(0,up,0);
        if(direction.lengthSquared()>1)direction=direction.normalize();
        double boost=held(o.sprintKey)?3:1;
        velocity=velocity.lerp(direction.multiply(speed*boost),.35);
        camera.setPosition(new Vec3d(camera.getX(),camera.getY(),camera.getZ()).add(velocity));
    }
    @Override public void renderBackground(DrawContext g,int x,int y,float delta){} // world remains visible; no blur
    @Override public boolean shouldPause(){return false;}
    private void updateProjection(float delta){
        var c=client.gameRenderer.getCamera();
        double fov=((PlacementFovAccess)client.gameRenderer).extra$fov(c,delta,true);
        double focal=height/(2*Math.tan(Math.toRadians(MathHelper.clamp(fov,1,179))/2));
        projection=new Projection(Platform.cameraPosition(c),new Quaternionf(c.getRotation()),focal,width,height);
        pivot=center();pivotPoint=projection.project(pivot);
        double length=MathHelper.clamp(Platform.cameraPosition(c).distanceTo(pivot)*.22,1,128);
        boolean resizing=selection!=null&&selection.resize;
        for(int i=0;i<3;i++){
            double reach=length*.85;
            if(resizing){
                var max=selection.active().max();
                double edge=switch(i){case 0->max.getX()+1-pivot.x;case 1->max.getY()+1-pivot.y;default->max.getZ()+1-pivot.z;};
                reach+=edge;
            }
            Vec3d end=pivot.add(AXES[i].multiply(reach));
            if(resizing&&pivotPoint!=null){
                Point tip=projection.projectResizeTip(pivot,end);
                double fraction=PlacementMath.resizeTipFraction(pivotPoint.x,pivotPoint.y,tip.x,tip.y,width,height,21);
                tips[i]=new Point(MathHelper.lerp(fraction,pivotPoint.x,tip.x),MathHelper.lerp(fraction,pivotPoint.y,tip.y));
            }else tips[i]=projection.project(end);
        }
    }
    @Override public void render(DrawContext g,int mx,int my,float delta){
        updateProjection(delta);
        int hover=cursorShown?hitAxis(mx,my):-1;
        BlockHitResult target=selection==null?null:selectionTarget(mx,my);
        boolean inside=target!=null&&selection.contains(target.getBlockPos());
        int targetColor=inside?StudioTheme.ACCENT:0xffffb568;
        if(pivotPoint!=null&&onScreen(pivotPoint,160)){
            var mesh=new SmoothGui();
            if(selection!=null)drawRegions(mesh,target,targetColor);
            for(int i=0;i<3;i++){
                Point a=pivotPoint,b=tips[i];boolean active=dragAxis==i||hover==i;
                int ink=axisInk(i,active);
                if(ink==0)continue;
                if(selection!=null&&selection.resize){
                    mesh.resizeHandle(new SmoothGui.P(a.x,a.y),new SmoothGui.P(b.x,b.y),ink,active);
                }else mesh.arrow(new SmoothGui.P(a.x,a.y),new SmoothGui.P(b.x,b.y),ink,active);
                Point label=labelPoint(a,b);
                mesh.roundedBox(label.x,label.y,14,13,3,ink);
                mesh.roundedBox(label.x,label.y,12,11,2,(ink&0xff000000)|0x1a242c);
            }
            mesh.disc(pivotPoint.x,pivotPoint.y,4.2,0xff152029);
            mesh.disc(pivotPoint.x,pivotPoint.y,3.2,0xffe1efef);
            mesh.disc(pivotPoint.x,pivotPoint.y,2.2,0xff263a46);
            mesh.disc(pivotPoint.x,pivotPoint.y,.8,StudioTheme.ACCENT);
            mesh.submit(g,new net.minecraft.client.gui.ScreenRect(0,0,width,height));
            for(int i=0;i<3;i++){
                int ink=axisInk(i,dragAxis==i||hover==i);
                if(ink==0)continue;
                Point label=labelPoint(pivotPoint,tips[i]);
                Platform.drawAxisLabel(g,hudFont,LABELS[i],(float)label.x,(float)label.y,1.0f,ink);
            }
        }
        if(selection!=null){
            if(pivotPoint==null||!onScreen(pivotPoint,160)){
                var mesh=new SmoothGui();
                drawRegions(mesh,target,targetColor);
                mesh.submit(g,new net.minecraft.client.gui.ScreenRect(0,0,width,height));
            }
            var region=selection.active();
            int card=Math.min(width-132,236);
            StudioTheme.surface(g,12,12,card,target==null?49:60,0xe61a242c,0xb0809aa3);
            g.drawText(hudFont,hudFont.trimToWidth(ExtraText.message("selection.title"),card-12),18,18,StudioTheme.ACCENT,false);
            g.drawText(hudFont,ExtraText.message("selection.region",selection.index+1,selection.regions.size(),
                    region.max().getX()-region.min().getX()+1,region.max().getY()-region.min().getY()+1,
                    region.max().getZ()-region.min().getZ()+1),18,32,StudioTheme.TEXT,false);
            g.drawText(hudFont,ExtraText.message(selection.resize?"selection.resize":"selection.move"),18,46,StudioTheme.TEXT,false);
            if(target!=null){
                String status=ExtraText.message(inside?"selection.block_inside":"selection.block_outside");
                g.drawText(hudFont,hudFont.trimToWidth(status,card-12),18,57,targetColor,false);
            }
            drawShortcuts(g,ExtraText.message("placement.fly"),ExtraText.message("selection.shortcuts"));
            super.render(g,mx,my,delta);
            return;
        }
        BlockPos p=placement.getOrigin();
        int angle=switch(placement.getRotation()){
            case NONE -> 0;
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
        };
        var coordinates=Text.empty();
        int[] values={p.getX(),p.getY(),p.getZ()};
        for(int i=0;i<3;i++){
            int ink=COLORS[i]&0xffffff;
            coordinates.append(Text.literal(LABELS[i]).styled(style->style.withColor(ink)))
                    .append(" "+values[i]+"  ");
        }
        coordinates.append("↻ "+angle+"°");
        String title=ExtraText.message("placement.title");
        int textWidth=Math.min(Math.max(1,width-40),Math.max(hudFont.getWidth(coordinates),
                Math.max(hudFont.getWidth(title)+16,Math.min(164,hudFont.getWidth(placement.getName())))));
        var coordinateLines=hudFont.wrapLines(coordinates,textWidth);
        int panelBottom=47+coordinateLines.size()*11;
        StudioTheme.surface(g,12,12,textWidth+16,panelBottom-12,0xe61a242c,0xb0809aa3);
        g.drawItem(new net.minecraft.item.ItemStack(net.minecraft.block.Blocks.STRUCTURE_BLOCK),18,16);
        g.drawText(hudFont,title,36,20,StudioTheme.ACCENT,false);
        g.drawText(hudFont,hudFont.trimToWidth(placement.getName(),textWidth),20,34,StudioTheme.TEXT,false);
        for(int i=0;i<coordinateLines.size();i++)g.drawText(hudFont,coordinateLines.get(i),20,47+i*11,StudioTheme.TEXT,false);
        String mode=PlacementSettings.HOLD_ALT.getBooleanValue()?ExtraText.message("placement.hold"):ExtraText.message("placement.alt");
        String first=dragAxis>=0?ExtraText.message("placement.drag",LABELS[dragAxis]):cursorShown
                ?ExtraText.message("placement.move")
                :ExtraText.message("placement.fly");
        first+=" · R — "+ExtraText.message("placement.switch_rotate")+" 90°";
        String second=cursorShown?(PlacementSettings.HOLD_ALT.getBooleanValue()?ExtraText.message("placement.release"):ExtraText.message("placement.capture")):mode+ExtraText.message("placement.speed");
        int top=drawShortcuts(g,first,second);
        if(!feedback.isEmpty())g.drawCenteredTextWithShadow(hudFont,hudFont.trimToWidth(feedback,width-32),width/2,panelBottom+8,0xffffca89);
        if(pivotPoint==null||!onScreen(pivotPoint,0))g.drawCenteredTextWithShadow(hudFont,ExtraText.message("placement.offscreen"),width/2,top-15,0xffc9d5df);
        super.render(g,mx,my,delta);
    }

    private int drawShortcuts(DrawContext g,String first,String second){
        var shortcuts=new java.util.ArrayList<String[]>();
        int total=0;
        for(String part:(first+" · "+second).split(" · ")){
            String[] pair=part.split(" — ",2);
            if(pair.length!=2){total=width;break;}
            shortcuts.add(pair);
            total+=hudFont.getWidth(pair[0])+hudFont.getWidth(pair[1])+18;
        }
        if(total+12<=width-24){
            int top=height-33,x=(width-total-12)/2;
            StudioTheme.surface(g,x,top,total+12,21,0xe61a242c,0xb0809aa3);
            x+=6;
            for(String[] pair:shortcuts){
                int keyWidth=hudFont.getWidth(pair[0])+8;
                StudioTheme.surface(g,x,top+4,keyWidth,13,0xff29363e,0xff60747d);
                g.drawText(hudFont,pair[0],x+4,top+7,StudioTheme.TEXT,false);
                g.drawText(hudFont,pair[1],x+keyWidth+4,top+7,0xffa5eddf,false);
                x+=keyWidth+hudFont.getWidth(pair[1])+10;
            }
            return top;
        }
        int wrapWidth=Math.max(1,width-36);
        var firstLines=hudFont.wrapLines(Text.literal(first),wrapWidth);
        var secondLines=hudFont.wrapLines(Text.literal(second),wrapWidth);
        int widest=0;
        for(var line:firstLines)widest=Math.max(widest,hudFont.getWidth(line));
        for(var line:secondLines)widest=Math.max(widest,hudFont.getWidth(line));
        int box=Math.min(width-24,widest+24),x=(width-box)/2,top=height-12-16-(firstLines.size()+secondLines.size())*11;
        StudioTheme.surface(g,x,top,box,height-12-top,0xe61a242c,0xb0809aa3);
        int y=top+8;
        for(var line:firstLines){g.drawText(hudFont,line,(width-hudFont.getWidth(line))/2,y,StudioTheme.TEXT,false);y+=11;}
        for(var line:secondLines){g.drawText(hudFont,line,(width-hudFont.getWidth(line))/2,y,0xffa5eddf,false);y+=11;}
        return top;
    }
    private boolean onScreen(Point p,int margin){return p.x>=-margin&&p.x<=width+margin&&p.y>=-margin&&p.y<=height+margin;}
    private BlockHitResult selectionTarget(int mouseX,int mouseY){
        if(client.world==null||camera==null)return null;
        Vec3d ray=projection.ray(cursorShown?mouseX:width*.5,cursorShown?mouseY:height*.5);
        var hit=client.world.raycast(new RaycastContext(projection.eye,projection.eye.add(ray.multiply(128)),
                RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.ANY,camera));
        return hit.getType()==HitResult.Type.BLOCK?hit:null;
    }
    private SmoothGui.P[] boxCorners(BlockPos min,BlockPos max){
        var corners=new SmoothGui.P[8];
        for(int i=0;i<8;i++){
            Point p=projection.project(new Vec3d((i&1)==0?min.getX():max.getX()+1,
                    (i&2)==0?min.getY():max.getY()+1,(i&4)==0?min.getZ():max.getZ()+1));
            if(p!=null)corners[i]=new SmoothGui.P(p.x,p.y);
        }
        return corners;
    }
    private static void drawBoxFace(SmoothGui mesh,SmoothGui.P[] corners,int axis,int side,int color){
        int u=axis==1?2:1,v=7^axis^u;
        SmoothGui.P a=corners[side],b=corners[side|u],c=corners[side|u|v],d=corners[side|v];
        if(a!=null&&b!=null&&c!=null&&d!=null)mesh.quad(a,b,c,d,color);
    }
    private static void drawEdge(SmoothGui mesh,SmoothGui.P a,SmoothGui.P b,double halfWidth,int color){
        if(a==null||b==null)return;
        double length=Math.hypot(b.x()-a.x(),b.y()-a.y());
        if(length<.01)return;
        double dx=(b.y()-a.y())*halfWidth/length,dy=(a.x()-b.x())*halfWidth/length;
        mesh.quad(a.add(dx,dy),b.add(dx,dy),b.add(-dx,-dy),a.add(-dx,-dy),color);
    }
    private void drawRegions(SmoothGui mesh,BlockHitResult target,int targetColor){
        for(int i=0;i<selection.regions.size();i++){
            var region=selection.regions.get(i);
            var min=region.min();var max=region.max();
            var corners=boxCorners(min,max);
            if(i==selection.index){
                int tint=(StudioTheme.ACCENT&0xffffff)|0x26000000;
                var eye=projection.eye;
                if(eye.x<min.getX()||eye.x>max.getX()+1)drawBoxFace(mesh,corners,1,eye.x<min.getX()?0:1,tint);
                if(eye.y<min.getY()||eye.y>max.getY()+1)drawBoxFace(mesh,corners,2,eye.y<min.getY()?0:2,tint);
                if(eye.z<min.getZ()||eye.z>max.getZ()+1)drawBoxFace(mesh,corners,4,eye.z<min.getZ()?0:4,tint);
            }
            int ink=i==selection.index?StudioTheme.ACCENT:0xc0afb9ca;
            for(int j=0;j<8;j++)for(int bit=1;bit<=4;bit*=2)
                if((j&bit)==0)drawEdge(mesh,corners[j],corners[j|bit],1,ink);
        }
        if(target!=null){
            var pos=target.getBlockPos();
            var corners=boxCorners(pos,pos);
            var side=target.getSide();
            int axis=1<<side.getAxis().ordinal(),face=side.getDirection()==Direction.AxisDirection.POSITIVE?axis:0;
            drawBoxFace(mesh,corners,axis,face,(targetColor&0xffffff)|0x44000000);
            for(int j=0;j<8;j++)if((j&axis)==face)
                for(int bit=1;bit<=4;bit*=2)if(bit!=axis&&(j&bit)==0)
                    drawEdge(mesh,corners[j],corners[j|bit],.85,targetColor);
        }
    }
    private void cancelDrag(){
        if(dragAxis>=0){
            if(selection!=null)selection.regions.set(selection.index,dragRegion);
            else placement.setOrigin(dragOrigin,s->feedback=s);
        }
        dragAxis=-1;
    }
    private void rotateClockwise(){
        BlockPos origin=placement.getOrigin();
        Vec3d offset=center().subtract(Vec3d.ofCenter(origin));
        Vec3d shift=offset.subtract(offset.rotateY((float)(-Math.PI/2)));
        rotate(placement.getRotation().rotate(BlockRotation.CLOCKWISE_90),
                origin.add((int)Math.round(shift.x),0,(int)Math.round(shift.z)));
    }
    private int hitAxis(double x,double y){
        if(pivotPoint==null)return -1;
        int best=-1;double distance=9;
        for(int i=0;i<3;i++)if(tips[i]!=null){
            Point a=pivotPoint,b=tips[i];if(Math.hypot(a.x-b.x,a.y-b.y)<12)continue;
            double d;
            if(selection!=null&&selection.resize)d=Math.hypot(x-b.x,y-b.y);
            else{
                b=labelPoint(a,b);
                d=PlacementMath.segmentDistance(x,y,a.x,a.y,b.x,b.y);
            }
            if(d<distance){distance=d;best=i;}
        }
        return best;
    }
    private static Point labelPoint(Point a,Point b){
        double offset=11/Math.hypot(b.x-a.x,b.y-a.y);
        return new Point(b.x+(b.x-a.x)*offset,b.y+(b.y-a.y)*offset);
    }
    private int axisInk(int axis,boolean active){
        Point tip=tips[axis];
        if(tip==null||!onScreen(tip,240))return 0;
        int alpha=PlacementMath.axisAlpha(Math.hypot(tip.x-pivotPoint.x,tip.y-pivotPoint.y));
        return alpha==0?0:(alpha<<24)|(active?0xffd083:COLORS[axis]&0xffffff);
    }
    private static PlacementMath.V v(Vec3d a){return new PlacementMath.V(a.x,a.y,a.z);}
    private double parameter(double x,double y,int axis){return PlacementMath.axisParameter(v(projection.eye),v(projection.ray(x,y)),v(pivot),v(AXES[axis]));}
    @Override protected boolean onMouseClicked(double x,double y,int button,BooleanSupplier vanilla){
        if(!cursorShown)return true;
        if(button==1&&dragAxis>=0){cancelDrag();return true;}
        if(vanilla.getAsBoolean())return true;
        if(button==0&&projection!=null){
            int axis=hitAxis(x,y);
            if(axis>=0){double t=parameter(x,y,axis);if(Double.isFinite(t)){
                if(selection!=null)dragRegion=selection.active();
                else dragOrigin=placement.getOrigin();
                dragPivot=pivot;dragParameter=t;dragAxis=axis;return true;
            }}
        }
        return true;
    }
    @Override protected boolean onMouseDragged(double x,double y,int button,double dx,double dy,BooleanSupplier vanilla){
        if(button==0&&dragAxis>=0){
            var ray=projection.ray(x,y);
            double t=PlacementMath.axisParameter(v(projection.eye),v(ray),v(dragPivot),v(AXES[dragAxis]));
            if(Double.isFinite(t)){
                int amount=PlacementMath.snapped(t-dragParameter);
                if(selection!=null){
                    var box=dragRegion.adjust(AXES[dragAxis],amount,selection.resize);
                    if(box.min().getX()>=-29_999_984&&box.max().getX()<=29_999_984
                            &&box.min().getZ()>=-29_999_984&&box.max().getZ()<=29_999_984
                            &&box.min().getY()>=client.world.getBottomY()
                            &&box.max().getY()<client.world.getBottomY()+client.world.getHeight())
                        selection.regions.set(selection.index,box);
                    return true;
                }
                BlockPos pos=dragOrigin.add((int)AXES[dragAxis].x*amount,(int)AXES[dragAxis].y*amount,(int)AXES[dragAxis].z*amount);
                if(Math.abs((long)pos.getX())<=29_999_984&&Math.abs((long)pos.getZ())<=29_999_984)placement.setOrigin(pos,s->feedback=s);
            }
            return true;
        }
        return vanilla.getAsBoolean();
    }
    @Override protected boolean onMouseReleased(double x,double y,int button,BooleanSupplier vanilla){if(button==0){dragAxis=-1;}return vanilla.getAsBoolean();}
    @Override public boolean mouseScrolled(double x,double y,double h,double vertical){speed=MathHelper.clamp(speed*Math.pow(1.2,vertical),.05,4);return true;}
    @Override protected boolean onKeyPressed(int key,int scan,int mods,BooleanSupplier vanilla){
        if(key==GLFW.GLFW_KEY_LEFT_ALT||key==GLFW.GLFW_KEY_RIGHT_ALT){handleAlt(true);return true;}
        if(key==GLFW.GLFW_KEY_R){if(!rDown){
            cancelDrag();
            if(selection!=null)selection.resize=!selection.resize;
            else rotateClockwise();
            rDown=true;
        }return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE&&dragAxis>=0){cancelDrag();return true;}
        if(key==GLFW.GLFW_KEY_ENTER){if(selection!=null)saveSelection();else close();return true;}
        return vanilla.getAsBoolean();
    }
    @Override protected boolean onKeyReleased(int key,int scan,int mods,BooleanSupplier vanilla){if(key==GLFW.GLFW_KEY_R){rDown=false;return true;}if(key==GLFW.GLFW_KEY_LEFT_ALT||key==GLFW.GLFW_KEY_RIGHT_ALT){handleAlt(false);return true;}return vanilla.getAsBoolean();}
    private void cancelPlacement(){DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);close();}
    @Override public void close(){client.setScreen(null);KeyBinding.unpressAll();}
    @Override public void removed(){
        if(finished)return;finished=true;
        if(client.getCameraEntity()==camera)client.setCameraEntity(previousCamera!=null&&Platform.world(previousCamera)==client.world?previousCamera:client.player);
        if(previousPerspective!=null)client.options.setPerspective(previousPerspective);
        client.mouse.unlockCursor();KeyBinding.unpressAll();
    }
}
