package me.giahoki.litematicaextra;

/** Orthographic orbit camera. Depth increases toward the viewer. */
public final class OrbitCamera {
    public double yaw = Math.toRadians(40), pitch = Math.toRadians(25), zoom = 1, panX, panY;
    public double centerX, centerY, centerZ, scale = 10, screenX, screenY;
    public void orbitPixels(double dx,double dy) {
        yaw -= dx*0.012;
        pitch = Math.max(-1.5,Math.min(1.5,pitch+dy*0.012));
    }
    public void panPixels(double dx,double dy) {panX+=dx;panY+=dy;}
    public void zoomSteps(double steps) {zoom=Math.max(0.1,Math.min(80,zoom*Math.pow(1.15,steps)));}
    public record Point(float x, float y, double depth) {}
    public Point project(double x, double y, double z) {
        x -= centerX; y -= centerY; z -= centerZ;
        double rx = Math.cos(yaw) * x - Math.sin(yaw) * z;
        double rz = Math.sin(yaw) * x + Math.cos(yaw) * z;
        double ry = Math.cos(pitch) * y - Math.sin(pitch) * rz;
        return new Point((float)(screenX + panX + rx * scale * zoom),
                (float)(screenY + panY - ry * scale * zoom), Math.sin(pitch) * y + Math.cos(pitch) * rz);
    }
    public static int regionMin(int origin, int signedSize) { return origin + Math.min(0, signedSize + 1); }
    public static boolean contains(float[] xy, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            double ax = xy[i*2], ay = xy[i*2+1], bx = xy[j*2], by = xy[j*2+1];
            if (((ay > y) != (by > y)) && x < (bx-ax)*(y-ay)/(by-ay)+ax) inside = !inside;
        }
        return inside;
    }
}
