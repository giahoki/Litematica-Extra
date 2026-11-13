package me.giahoki.litematicaextra;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import java.util.*;

/** Subpixel GUI geometry with an alpha fringe instead of integer rectangle stepping. */
public final class SmoothGui {
    public record P(double x,double y) {
        P add(double dx,double dy){return new P(x+dx,y+dy);}
    }
    private record V(float x,float y,int color){}
    private final List<V> vertices=new ArrayList<>();
    private void vertex(P p,int color){vertices.add(new V((float)p.x,(float)p.y,color));}
    private void coloredQuad(P a,int ac,P b,int bc,P c,int cc,P d,int dc){
        // GUI uses a Y-down projection and back-face culling: keep clockwise GPU winding.
        double area=a.x*b.y-b.x*a.y+b.x*c.y-c.x*b.y+c.x*d.y-d.x*c.y+d.x*a.y-a.x*d.y;
        if(area>0){vertex(d,dc);vertex(c,cc);vertex(b,bc);vertex(a,ac);}
        else{vertex(a,ac);vertex(b,bc);vertex(c,cc);vertex(d,dc);}
    }
    public void quad(P a,P b,P c,P d,int color){coloredQuad(a,color,b,color,c,color,d,color);}
    public void triangle(P a,P b,P c,int color){quad(a,b,c,c,color);}
    public void polygon(int color,P... points){
        for(int i=1;i<points.length-1;i++)triangle(points[0],points[i],points[i+1],color);
    }
    public void fringe(int color,double width,P... points){
        double area=0;
        for(int i=0;i<points.length;i++){P a=points[i],b=points[(i+1)%points.length];area+=a.x*b.y-b.x*a.y;}
        double sign=area>0?1:-1;
        for(int i=0;i<points.length;i++){
            P a=points[i],b=points[(i+1)%points.length];double dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy);
            if(len<1e-6)continue;
            double nx=sign*dy/len*width,ny=-sign*dx/len*width;
            coloredQuad(a,color,b,color,b.add(nx,ny),color&0xffffff,a.add(nx,ny),color&0xffffff);
        }
    }
    public void disc(double x,double y,double radius,int color){
        P center=new P(x,y);P[] edge=new P[32];
        for(int i=0;i<32;i++)edge[i]=new P(x+Math.cos(i*Math.PI/16)*radius,y+Math.sin(i*Math.PI/16)*radius);
        for(int i=0;i<32;i++)triangle(center,edge[i],edge[(i+1)%32],color);
        fringe(color,.65,edge);
    }
    public static int shade(int color,double value){
        int r=(int)Math.min(255,((color>>16)&255)*value),g=(int)Math.min(255,((color>>8)&255)*value),b=(int)Math.min(255,(color&255)*value);
        return (color&0xff000000)|(r<<16)|(g<<8)|b;
    }
    public void arrow(P start,P tip,int color,boolean active){
        double len=Math.hypot(tip.x-start.x,tip.y-start.y);if(len<7)return;
        double dx=(tip.x-start.x)/len,dy=(tip.y-start.y)/len,nx=-dy,ny=dx;
        double head=Math.min(9,len*.3),half=active?1.3:.9,wing=active?4.3:3.8;
        P base=tip.add(-dx*head,-dy*head),a=start.add(nx*half,ny*half),b=base.add(nx*half,ny*half),c=base.add(-nx*half,-ny*half),d=start.add(-nx*half,-ny*half);
        polygon(color,a,b,c,d);fringe(color,.65,a,b,c,d);
        P left=base.add(nx*wing,ny*wing),right=base.add(-nx*wing,-ny*wing);
        triangle(left,tip,base,shade(color,1.15));triangle(base,tip,right,shade(color,.78));
        fringe(color,.65,left,tip,right);
    }
    public void submit(DrawContext context,ScreenRect bounds){
        if(vertices.isEmpty())return;
        var mesh=List.copyOf(vertices);
        Platform.mesh(context,bounds,null,(consumer,matrix,depth)->{
            for(V v:mesh)consumer.vertex(matrix,v.x,v.y,depth).color(v.color);
        });
    }
}
