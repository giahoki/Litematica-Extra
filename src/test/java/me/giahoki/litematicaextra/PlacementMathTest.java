package me.giahoki.litematicaextra;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import me.giahoki.litematicaextra.PlacementMath.V;
class PlacementMathTest {
    private V normalized(V v){return v.mul(1/Math.sqrt(v.dot(v)));}
    @Test void draggingEachAxisFollowsPointerInBothDirections(){
        V eye=new V(10,12,14),pivot=new V(3,4,5);
        for(V axis:new V[]{new V(1,0,0),new V(0,1,0),new V(0,0,1)})for(double t:new double[]{-7,-.5,0,4,18}){
            V target=pivot.add(axis.mul(t));
            assertEquals(t,PlacementMath.axisParameter(eye,normalized(target.sub(eye)),pivot,axis),1e-8);
        }
    }
    @Test void parallelAxisCannotJumpToInfinity(){
        assertTrue(Double.isNaN(PlacementMath.axisParameter(new V(0,0,0),new V(1,0,0),new V(0,1,0),new V(1,0,0))));
    }
    @Test void snapMatchesBlockGridAndBounds(){
        assertEquals(3,PlacementMath.snapped(2.8));assertEquals(-3,PlacementMath.snapped(-2.8));
        assertEquals(0,PlacementMath.snapped(.4));assertEquals(30_000_000,PlacementMath.snapped(1e30));
    }
    @Test void handlePickingIncludesShaftAndEndpoints(){
        assertEquals(3,PlacementMath.segmentDistance(50,3,0,0,100,0),1e-8);
        assertEquals(5,PlacementMath.segmentDistance(104,3,0,0,100,0),1e-8);
        assertEquals(5,PlacementMath.segmentDistance(3,4,0,0,0,0),1e-8);
    }
}
