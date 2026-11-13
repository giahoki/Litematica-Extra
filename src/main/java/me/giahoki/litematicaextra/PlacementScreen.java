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
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;
import java.util.UUID;

public final class PlacementScreen extends PlatformScreen {
    private static final Vec3d[] AXES={new Vec3d(1,0,0),new Vec3d(0,1,0),new Vec3d(0,0,1)};
    private static final int[] COLORS={0xfff16d75,0xff8cda91,0xff6ea9ff};
    private static final String[] LABELS={"X","Y","Z"};
    private final SchematicPlacement placement;
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
    private final Point[] tips=new Point[3];
    private Point pivotPoint;
    private Vec3d pivot;
    private ButtonWidget done,cancel;
    private boolean rotationMode,rotating,rDown;
    private Vec3d dragPivot,centerOffset;
    private BlockRotation dragRotation;
    private double ringRadius,previousRingAngle,rotationDelta;
    private final Point[] ring=new Point[64];
    private record Point(double x,double y){}
    private record Projection(Vec3d eye,Quaternionf rotation,double focal,int width,int height){
        Point project(Vec3d p){
            Vector3f v=new Vector3f((float)(p.x-eye.x),(float)(p.y-eye.y),(float)(p.z-eye.z));
            new Quaternionf(rotation).conjugate().transform(v);
            if(v.z>=-.05)return null;
            return new Point(width*.5+v.x*focal/-v.z,height*.5-v.y*focal/-v.z);
        }
        Vec3d ray(double x,double y){
            Vector3f v=new Vector3f((float)((x-width*.5)/focal),(float)(-(y-height*.5)/focal),-1).normalize();
            rotation.transform(v);return new Vec3d(v.x,v.y,v.z).normalize();
        }
    }
    private PlacementScreen(SchematicPlacement placement){super(Text.literal("Размещение"));this.placement=placement;}
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
        if(camera==null){
            previousCamera=client.getCameraEntity();previousPerspective=client.options.getPerspective();
            camera=new CameraPlayer(client.world);
            camera.copyPositionAndRotation(client.player);camera.noClip=true;
            launchFrom=new Vec3d(camera.getX(),camera.getY(),camera.getZ());launchPitch=camera.getPitch();
            launchTo=launchFrom.subtract(camera.getRotationVec(1).multiply(5)).add(0,1.5,0);
            syncPrevious();client.options.setPerspective(Perspective.FIRST_PERSON);client.setCameraEntity(camera);
            KeyBinding.unpressAll();
        }
        done=addDrawableChild(ButtonWidget.builder(Text.literal("Готово"),b->close()).dimensions(width-180,16,78,20).build());
        cancel=addDrawableChild(ButtonWidget.builder(Text.literal("Отменить"),b->cancelPlacement()).dimensions(width-96,16,80,20).build());
        applyCursor();
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
        dragAxis=-1;rotating=false;cursorShown=shown;if(shown)launchTick=12;velocity=Vec3d.ZERO;applyCursor();
    }
    private void applyCursor(){
        done.visible=cancel.visible=cursorShown;
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
        if(!focused){velocity=Vec3d.ZERO;dragAxis=-1;rotating=false;rDown=false;return;}
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
        pivot=rotating?dragPivot:center();pivotPoint=projection.project(pivot);
        double length=MathHelper.clamp(Platform.cameraPosition(c).distanceTo(pivot)*.22,1,128);
        ringRadius=length;
        if(rotationMode){
            for(int i=0;i<ring.length;i++){
                double angle=i*Math.TAU/ring.length;
                ring[i]=projection.project(pivot.add(Math.cos(angle)*ringRadius,0,Math.sin(angle)*ringRadius));
            }
            return;
        }
        for(int i=0;i<3;i++)tips[i]=projection.project(pivot.add(AXES[i].multiply(length)));
    }
    @Override public void render(DrawContext g,int mx,int my,float delta){
        updateProjection(delta);
        int hover=cursorShown&&!rotationMode?hitAxis(mx,my):-1;
        if(pivotPoint!=null&&onScreen(pivotPoint,160)){
            var mesh=new SmoothGui();
            if(rotationMode)drawRing(mesh,rotating||cursorShown&&hitRing(mx,my));
            for(int i=0;!rotationMode&&i<3;i++)if(tips[i]!=null&&onScreen(tips[i],240)){
                Point a=pivotPoint,b=tips[i];boolean active=dragAxis==i||hover==i;
                mesh.arrow(new SmoothGui.P(a.x,a.y),new SmoothGui.P(b.x,b.y),active?0xffffd083:COLORS[i],active);
                Point label=labelPoint(a,b);mesh.disc(label.x,label.y,5.5,0xdf172733);
            }
            mesh.disc(pivotPoint.x,pivotPoint.y,3.2,0xffe1efef);mesh.disc(pivotPoint.x,pivotPoint.y,2.1,0xff263a46);
            mesh.submit(g,new net.minecraft.client.gui.ScreenRect(0,0,width,height));
            for(int i=0;!rotationMode&&i<3;i++)if(tips[i]!=null&&onScreen(tips[i],240)){
                Point label=labelPoint(pivotPoint,tips[i]);
                g.drawText(textRenderer,LABELS[i],(int)Math.round(label.x)-3,(int)Math.round(label.y)-4,dragAxis==i||hover==i?0xffffd083:COLORS[i],false);
            }
        }
        int panelRight=Math.min(width-192,312),textWidth=Math.max(60,panelRight-36);
        BlockPos p=placement.getOrigin();
        int angle=switch(placement.getRotation()){
            case NONE -> 0;
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
        };
        var coordinates=textRenderer.wrapLines(Text.literal("X "+p.getX()+"   Y "+p.getY()+"   Z "+p.getZ()+"   ·   Поворот "+angle+"°"),textWidth);
        int panelBottom=53+coordinates.size()*11;
        g.fill(12,12,panelRight,panelBottom,0xdf111e2a);g.fill(12,12,14,panelBottom,0xff89d9cb);
        g.drawTextWithShadow(textRenderer,"РАЗМЕЩЕНИЕ",24,21,0xffa5eddf);
        g.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(placement.getName(),textWidth),24,37,0xffeeeeee);
        for(int i=0;i<coordinates.size();i++)g.drawTextWithShadow(textRenderer,coordinates.get(i),24,52+i*11,0xffa4b6c6);
        String mode=PlacementSettings.HOLD_ALT.getBooleanValue()?"Держи Alt — курсор":"Alt — курсор";
        String first=dragAxis>=0?"Ось "+LABELS[dragAxis]+" · Шаг: 1 блок · ПКМ — отменить":cursorShown
                ?(rotationMode?"ЛКМ + кольцо — поворот Y · Шаг: 90°":"ЛКМ + стрелка — двигать · Шаг: 1 блок")
                :"WASD — полёт · Space / Shift — высота";
        first+=" · R — "+(rotationMode?"перемещение":"поворот");
        String second=cursorShown?(PlacementSettings.HOLD_ALT.getBooleanValue()?"Отпусти Alt — камера · Enter — готово":"Alt — камера · Enter / Esc — готово"):mode+" · Колесо — скорость · Esc — выход";
        int wrapWidth=Math.max(60,Math.min(width-48,480));
        var firstLines=textRenderer.wrapLines(Text.literal(first),wrapWidth);
        var secondLines=textRenderer.wrapLines(Text.literal(second),wrapWidth);
        int widest=0;
        for(var line:firstLines)widest=Math.max(widest,textRenderer.getWidth(line));
        for(var line:secondLines)widest=Math.max(widest,textRenderer.getWidth(line));
        int box=Math.min(width-24,widest+24),x=(width-box)/2,top=height-12-16-(firstLines.size()+secondLines.size())*11;
        g.fill(x,top,x+box,height-12,0xdf111e2a);
        int y=top+8;
        for(var line:firstLines){g.drawTextWithShadow(textRenderer,line,(width-textRenderer.getWidth(line))/2,y,0xffd7e6f0);y+=11;}
        for(var line:secondLines){g.drawTextWithShadow(textRenderer,line,(width-textRenderer.getWidth(line))/2,y,0xff89d9cb);y+=11;}
        if(!feedback.isEmpty())g.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(feedback,width-32),width/2,panelBottom+8,0xffffca89);
        if(pivotPoint==null||!onScreen(pivotPoint,0))g.drawCenteredTextWithShadow(textRenderer,"Центр схемы вне кадра",width/2,top-15,0xffc9d5df);
        super.render(g,mx,my,delta);
    }
    private boolean onScreen(Point p,int margin){return p.x>=-margin&&p.x<=width+margin&&p.y>=-margin&&p.y<=height+margin;}
    private void drawRing(SmoothGui mesh,boolean active){
        int color=active?0xffffd083:COLORS[1];
        for(int i=0;i<ring.length;i++){
            Point a=ring[i],b=ring[(i+1)%ring.length];
            if(a==null||b==null)continue;
            double dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy);
            if(len<.01)continue;
            double nx=-dy/len*1.5,ny=dx/len*1.5;
            mesh.quad(new SmoothGui.P(a.x+nx,a.y+ny),new SmoothGui.P(b.x+nx,b.y+ny),
                    new SmoothGui.P(b.x-nx,b.y-ny),new SmoothGui.P(a.x-nx,a.y-ny),color);
        }
    }
    private boolean hitRing(double x,double y){
        for(int i=0;i<ring.length;i++){
            Point a=ring[i],b=ring[(i+1)%ring.length];
            if(a!=null&&b!=null&&PlacementMath.segmentDistance(x,y,a.x,a.y,b.x,b.y)<8)return true;
        }
        return false;
    }
    private double ringAngle(double x,double y,Vec3d center){
        Vec3d ray=projection.ray(x,y);
        if(Math.abs(ray.y)<1e-6)return Double.NaN;
        double distance=(center.y-projection.eye.y)/ray.y;
        if(distance<=0)return Double.NaN;
        Vec3d offset=projection.eye.add(ray.multiply(distance)).subtract(center);
        return Math.atan2(offset.z,offset.x);
    }
    private void cancelDrag(){
        if(rotating)rotate(dragRotation,dragOrigin);
        else if(dragAxis>=0)placement.setOrigin(dragOrigin,s->feedback=s);
        rotating=false;dragAxis=-1;
    }
    private int hitAxis(double x,double y){
        if(pivotPoint==null)return -1;
        int best=-1;double distance=9;
        for(int i=0;i<3;i++)if(tips[i]!=null){
            Point a=pivotPoint,b=tips[i];if(Math.hypot(a.x-b.x,a.y-b.y)<12)continue;
            b=labelPoint(a,b);
            double d=PlacementMath.segmentDistance(x,y,a.x,a.y,b.x,b.y);
            if(d<distance){distance=d;best=i;}
        }
        return best;
    }
    private static Point labelPoint(Point a,Point b){
        double len=Math.hypot(b.x-a.x,b.y-a.y);
        return len<1?b:new Point(b.x+(b.x-a.x)/len*11,b.y+(b.y-a.y)/len*11);
    }
    private static PlacementMath.V v(Vec3d a){return new PlacementMath.V(a.x,a.y,a.z);}
    private double parameter(double x,double y,int axis){return PlacementMath.axisParameter(v(projection.eye),v(projection.ray(x,y)),v(pivot),v(AXES[axis]));}
    @Override protected boolean onMouseClicked(double x,double y,int button,BooleanSupplier vanilla){
        if(!cursorShown)return true;
        if(button==1&&(dragAxis>=0||rotating)){cancelDrag();return true;}
        if(vanilla.getAsBoolean())return true;
        if(button==0&&projection!=null){
            if(rotationMode){
                if(pivotPoint!=null&&hitRing(x,y)){
                    double angle=ringAngle(x,y,pivot);
                    if(Double.isFinite(angle)){
                        dragPivot=pivot;dragOrigin=placement.getOrigin();dragRotation=placement.getRotation();
                        centerOffset=dragPivot.subtract(Vec3d.ofCenter(dragOrigin));
                        previousRingAngle=angle;rotationDelta=0;rotating=true;
                    }
                }
                return true;
            }
            int axis=hitAxis(x,y);
            if(axis>=0){double t=parameter(x,y,axis);if(Double.isFinite(t)){dragOrigin=placement.getOrigin();dragPivot=pivot;dragParameter=t;dragAxis=axis;return true;}}
        }
        return true;
    }
    @Override protected boolean onMouseDragged(double x,double y,int button,double dx,double dy,BooleanSupplier vanilla){
        if(button==0&&rotating){
            double angle=ringAngle(x,y,dragPivot);
            if(Double.isFinite(angle)){
                rotationDelta+=Math.IEEEremainder(angle-previousRingAngle,Math.TAU);
                previousRingAngle=angle;
                int turns=Math.floorMod((int)Math.round(rotationDelta/(Math.PI/2)),4);
                BlockRotation step=switch(turns){
                    case 1 -> BlockRotation.CLOCKWISE_90;
                    case 2 -> BlockRotation.CLOCKWISE_180;
                    case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
                    default -> BlockRotation.NONE;
                };
                Vec3d shift=centerOffset.subtract(centerOffset.rotateY((float)(-turns*Math.PI/2)));
                rotate(dragRotation.rotate(step),dragOrigin.add((int)Math.round(shift.x),0,(int)Math.round(shift.z)));
            }
            return true;
        }
        if(button==0&&dragAxis>=0){
            var ray=projection.ray(x,y);
            double t=PlacementMath.axisParameter(v(projection.eye),v(ray),v(dragPivot),v(AXES[dragAxis]));
            if(Double.isFinite(t)){
                int amount=PlacementMath.snapped(t-dragParameter);
                BlockPos pos=dragOrigin.add((int)AXES[dragAxis].x*amount,(int)AXES[dragAxis].y*amount,(int)AXES[dragAxis].z*amount);
                if(Math.abs((long)pos.getX())<=29_999_984&&Math.abs((long)pos.getZ())<=29_999_984)placement.setOrigin(pos,s->feedback=s);
            }
            return true;
        }
        return vanilla.getAsBoolean();
    }
    @Override protected boolean onMouseReleased(double x,double y,int button,BooleanSupplier vanilla){if(button==0){dragAxis=-1;rotating=false;}return vanilla.getAsBoolean();}
    @Override public boolean mouseScrolled(double x,double y,double h,double vertical){speed=MathHelper.clamp(speed*Math.pow(1.2,vertical),.05,4);return true;}
    @Override protected boolean onKeyPressed(int key,int scan,int mods,BooleanSupplier vanilla){
        if(key==GLFW.GLFW_KEY_LEFT_ALT||key==GLFW.GLFW_KEY_RIGHT_ALT){handleAlt(true);return true;}
        if(key==GLFW.GLFW_KEY_R){if(!rDown){cancelDrag();rotationMode=!rotationMode;rDown=true;}return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE&&(dragAxis>=0||rotating)){cancelDrag();return true;}
        if(key==GLFW.GLFW_KEY_ENTER){close();return true;}
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
