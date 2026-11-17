package me.giahoki.litematicaextra;

public final class PlacementMath {
    public record V(double x,double y,double z){
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        public V mul(double n){return new V(x*n,y*n,z*n);}
        public double dot(V b){return x*b.x+y*b.y+z*b.z;}
    }
    // Parameter on the axis closest to the pointer ray. NaN when viewed end-on.
    public static double axisParameter(V origin,V ray,V pivot,V axis){
        V w=origin.sub(pivot);double b=axis.dot(ray),den=1-b*b;
        return den<1e-5?Double.NaN:(axis.dot(w)-b*ray.dot(w))/den;
    }
    public static double segmentDistance(double x,double y,double ax,double ay,double bx,double by){
        double dx=bx-ax,dy=by-ay,len=dx*dx+dy*dy;
        double t=len<1e-8?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/len));
        return Math.hypot(x-ax-t*dx,y-ay-t*dy);
    }
    public static int snapped(double delta){return (int)Math.max(-30_000_000,Math.min(30_000_000,Math.round(delta)));}
    static int axisAlpha(double projectedLength){
        double progress=Math.max(0,Math.min(1,(projectedLength-4)/20));
        int alpha=(int)Math.round(255*progress*progress*(3-2*progress));
        // Minecraft's text renderer treats alpha below 4 as fully opaque.
        return alpha<4?0:alpha;
    }
    static double resizeTipFraction(double startX,double startY,double tipX,double tipY,int width,int height,int inset){
        double fraction=1;
        if(tipX<inset&&tipX<startX)fraction=Math.min(fraction,(inset-startX)/(tipX-startX));
        if(tipX>width-inset&&tipX>startX)fraction=Math.min(fraction,(width-inset-startX)/(tipX-startX));
        if(tipY<inset&&tipY<startY)fraction=Math.min(fraction,(inset-startY)/(tipY-startY));
        if(tipY>height-inset&&tipY>startY)fraction=Math.min(fraction,(height-inset-startY)/(tipY-startY));
        // Keep the original projection if the segment never reaches the inset viewport.
        double x=startX+(tipX-startX)*fraction,y=startY+(tipY-startY)*fraction;
        return fraction>=0&&x>=inset-1e-6&&y>=inset-1e-6&&x<=width-inset+1e-6&&y<=height-inset+1e-6?fraction:1;
    }
    private PlacementMath(){}
}
