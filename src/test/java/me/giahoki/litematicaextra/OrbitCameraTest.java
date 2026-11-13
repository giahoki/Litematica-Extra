package me.giahoki.litematicaextra;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbitCameraTest {
    @Test void orbitFollowsPointerInsteadOfMirroringHorizontalMovement() {
        OrbitCamera c=new OrbitCamera();
        double x=Math.sin(c.yaw)*3,z=Math.cos(c.yaw)*3;
        var before=c.project(x,0,z);c.orbitPixels(12,0);var after=c.project(x,0,z);
        assertTrue(after.x()>before.x(),"The near side must follow a drag to the right");
        c=new OrbitCamera();x=Math.sin(c.yaw)*3;z=Math.cos(c.yaw)*3;
        before=c.project(x,0,z);c.orbitPixels(0,12);after=c.project(x,0,z);
        assertTrue(after.y()>before.y(),"The near side must follow a downward drag");
    }
    @Test void panAndZoomFollowMouseDirectionAndRemainBounded() {
        OrbitCamera c=new OrbitCamera();var before=c.project(1,2,3);c.panPixels(15,-8);var after=c.project(1,2,3);
        assertEquals(before.x()+15,after.x(),1e-4);assertEquals(before.y()-8,after.y(),1e-4);
        c.zoomSteps(1);assertTrue(c.zoom>1);c.zoomSteps(-10000);assertEquals(0.1,c.zoom);c.zoomSteps(10000);assertEquals(80,c.zoom);
    }
    @Test void negativeRegionsUseMinimumCornerAndInclusiveSize() {
        assertEquals(7,OrbitCamera.regionMin(10,-4));
        assertEquals(10,OrbitCamera.regionMin(10,4));
        assertEquals(10,OrbitCamera.regionMin(10,-1));
        assertEquals(-13,OrbitCamera.regionMin(-10,-4));
    }
    @Test void cameraCenterRemainsAtScreenCenter() {
        OrbitCamera c=new OrbitCamera();c.centerX=3;c.centerY=-5;c.centerZ=11;c.screenX=150;c.screenY=80;
        var p=c.project(3,-5,11);assertEquals(150,p.x());assertEquals(80,p.y());assertEquals(0,p.depth(),1e-9);
    }
    @Test void zoomAndPanDoNotAlterDepthOrOrbitPivot() {
        OrbitCamera c=new OrbitCamera();var p=c.project(1,2,3);c.zoom=2;c.panX=20;c.panY=-10;
        var q=c.project(1,2,3);assertEquals(p.x()*2+20,q.x(),1e-4);assertEquals(p.y()*2-10,q.y(),1e-4);assertEquals(p.depth(),q.depth());
    }
    @Test void pickingUsesQuadRatherThanBoundingBox() {
        float[] diamond={0,5,5,0,10,5,5,10};
        assertTrue(OrbitCamera.contains(diamond,5,5));
        assertFalse(OrbitCamera.contains(diamond,1,1));
        assertFalse(OrbitCamera.contains(diamond,-2,5));
        assertTrue(OrbitCamera.contains(new float[]{5,10,10,5,5,0,0,5},5,5));
    }
}
