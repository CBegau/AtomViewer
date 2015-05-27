package delaunay;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import quickhull3d.Point3d;
import quickhull3d.QuickHull3D;
import common.FastDeletableArrayList;
import common.Vec3;
 
public class Delaunay {
 
    List<Tetrahedron> tetras;
 
    public List<Line> edges;
 
    public List<Line> surfaceEdges;
    public List<Triangle> triangles;
 
 
    public Delaunay() {
        tetras = new ArrayList<Tetrahedron>();
        edges = new ArrayList<Line>();
        surfaceEdges = new ArrayList<Line>();
        triangles = new ArrayList<Triangle>();
    }
 
    public Delaunay(List<Vec3> points){
    	this();
    	Vec3 origin = new Vec3();
    	ArrayList<Vec3> copy = new ArrayList<Vec3>(points);
    	copy.add(origin);
    	this.setData(copy);
    }
    
    public void setData(List<Vec3> seq) {
 
        // 1    : 点群を包含する四面体を求める
        //   1-1: 点群を包含する球を求める
        Vec3 vMax = new Vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        Vec3 vMin = new Vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        for(Vec3 v : seq) {
            if (vMax.x < v.x) vMax.x = v.x;
            if (vMax.y < v.y) vMax.y = v.y;
            if (vMax.z < v.z) vMax.z = v.z;
            if (vMin.x > v.x) vMin.x = v.x;
            if (vMin.y > v.y) vMin.y = v.y;
            if (vMin.z > v.z) vMin.z = v.z;
        }
 
        Vec3 center = new Vec3();     // 外接球の中心座標
        center.x = 0.5f * (vMax.x - vMin.x);
        center.y = 0.5f * (vMax.y - vMin.y);
        center.z = 0.5f * (vMax.z - vMin.z);
        float r = -1;                       // 半径
        for(Vec3 v : seq) {
            if (r < center.getDistTo(v)) r = center.getDistTo(v);
        }
        r += 0.1f*center.getLength();
 
        //   1-2: 球に外接する四面体を求める
        Vec3 v1 = new Vec3();
        v1.x = center.x;
        v1.y = center.y + 3.0f*r;
        v1.z = center.z;
 
        Vec3 v2 = new Vec3();
        v2.x = center.x - 2.0f*(float)Math.sqrt(2)*r;
        v2.y = center.y - r;
        v2.z = center.z;
 
        Vec3 v3 = new Vec3();
        v3.x = center.x + (float)Math.sqrt(2)*r;
        v3.y = center.y - r;
        v3.z = center.z + (float)Math.sqrt(6)*r;
 
        Vec3 v4 = new Vec3();
        v4.x = center.x + (float)Math.sqrt(2)*r;
        v4.y = center.y - r;
        v4.z = center.z - (float)Math.sqrt(6)*r;
 
        Vec3[] outer = {v1, v2, v3, v4};
        tetras.add(new Tetrahedron(v1, v2, v3, v4));
 
        for(Vec3 v : seq) {
        	ArrayList<Tetrahedron> tmpTList = new ArrayList<Tetrahedron>();
            ArrayList<Tetrahedron> newTList = new ArrayList<Tetrahedron>();
            
            for (Tetrahedron t : tetras) {
                if((t.o != null) && (t.r > v.getDistTo(t.o))) {
                    tmpTList.add(t);
                }
            }
 
            for (Tetrahedron t1 : tmpTList) {
                // まずそれらを削除
                tetras.remove(t1);
 
                v1 = t1.vertices[0];
                v2 = t1.vertices[1];
                v3 = t1.vertices[2];
                v4 = t1.vertices[3];
                newTList.add(new Tetrahedron(v1, v2, v3, v));
                newTList.add(new Tetrahedron(v1, v2, v4, v));
                newTList.add(new Tetrahedron(v1, v3, v4, v));
                newTList.add(new Tetrahedron(v2, v3, v4, v));
            }
 
            boolean[] isRedundancy = new boolean[newTList.size()];
//            for (int i = 0; i < isRedundancy.length; i++) isRedundancy[i] = false;
            for (int i = 0; i < newTList.size()-1; i++) {
                for (int j = i+1; j < newTList.size(); j++) {
                    if(newTList.get(i).equals(newTList.get(j))) {
                        isRedundancy[i] = isRedundancy[j] = true;
                    }
                }
            }
            for (int i = 0; i < isRedundancy.length; i++) {
                if (!isRedundancy[i]) {
                    tetras.add(newTList.get(i));
                }
 
            }
             
        }
 
         
//        boolean isOuter = false;
//        for (Tetrahedron t4 : tetras) {
//            isOuter = false;
//            for (Vec3 p1 : t4.vertices) {
//                for (Vec3 p2 : outer) {
//                    if (p1.x == p2.x && p1.y == p2.y && p1.z == p2.z) {
//                        isOuter = true;
//                    }
//                }
//            }
//            if (isOuter) {
//                tetras.remove(t4);
//            }
//        }
        
        
        ArrayList<Tetrahedron> nonOuterTetras = new ArrayList<Tetrahedron>();
        for (int i=0; i<tetras.size(); i++) {
        	boolean isOuter = false;
            Tetrahedron t4 = tetras.get(i);
            for (Vec3 p1 : t4.vertices) {
                for (Vec3 p2 : outer) {
//                    if (p1.x == p2.x && p1.y == p2.y && p1.z == p2.z) {
                	if (p1 == p2) {
                        isOuter = true;
                    }
                }
            }
            if (!isOuter)
                nonOuterTetras.add(t4);
        }
        tetras = nonOuterTetras;
 
        for (Tetrahedron t : tetras) {
            for (Line l1 : t.getLines()) {
            	boolean isSame = false;
                for (Line l2 : edges) {
                    if (l2.equals(l1)) {
                        isSame = true;
                        break;
                    }
                }
                if (!isSame) {
                    edges.add(l1);
                }
            }
        }
        
//        ArrayList<Triangle> triList = new ArrayList<Triangle>();
//        for (Tetrahedron t : tetras) {
//            v1 = t.vertices[0];
//            v2 = t.vertices[1];
//            v3 = t.vertices[2];
//            v4 = t.vertices[3];
// 
//            Triangle tri1 = new Triangle(v1, v2, v3);
//            Triangle tri2 = new Triangle(v1, v3, v4);
//            Triangle tri3 = new Triangle(v1, v4, v2);
//            Triangle tri4 = new Triangle(v4, v3, v2);
// 
//            Vec3 n;
//            // 面の向きを決める
//            n = tri1.getNormal();
//            if(n.dot(v1) > n.dot(v4)) tri1.turnBack();
// 
//            n = tri2.getNormal();
//            if(n.dot(v1) > n.dot(v2)) tri2.turnBack();
// 
//            n = tri3.getNormal();
//            if(n.dot(v1) > n.dot(v3)) tri3.turnBack();
// 
//            n = tri4.getNormal();
//            if(n.dot(v2) > n.dot(v1)) tri4.turnBack();
// 
//            triList.add(tri1);
//            triList.add(tri2);
//            triList.add(tri3);
//            triList.add(tri4);
//        }
//        boolean[] isSameTriangle = new boolean[triList.size()];
//        for(int i = 0; i < triList.size()-1; i++) {
//            for(int j = i+1; j < triList.size(); j++) {
//                if (triList.get(i).equals(triList.get(j))) isSameTriangle[i] = isSameTriangle[j] = true;
//            }
//        }
//        for(int i = 0; i < isSameTriangle.length; i++) {
//            if (!isSameTriangle[i]) triangles.add(triList.get(i));
//        }
// 
//        surfaceEdges.clear();
//        ArrayList<Line> surfaceEdgeList = new ArrayList<Line>();
//        for(Triangle tri : triangles) {
//            surfaceEdgeList.addAll(Arrays.asList(tri.getLines()));
//        }
//        boolean[] isRedundancy = new boolean[surfaceEdgeList.size()];
//        for(int i = 0; i < surfaceEdgeList.size()-1; i++) {
//            for (int j = i+1; j < surfaceEdgeList.size(); j++) {
//                if (surfaceEdgeList.get(i).equals(surfaceEdgeList.get(j))) isRedundancy[j] = true;
//            }
//        }
// 
//        for (int i = 0; i < isRedundancy.length; i++) {
//            if (!isRedundancy[i]) surfaceEdges.add(surfaceEdgeList.get(i));
//        }
    }
    
    public static List<Vec3> getSurroundingVertices(List<Vec3> points) {
    	ArrayList<Vec3> v = new ArrayList<Vec3>();
    	for (Line e : d.edges){
    		if (e.end == origin)
    			v.add(e.start);
    		else if (e.start == origin)
    			v.add(e.end);
    	}  	
    	
    	return v;
    }
    
    
    
    public List<Vec3> getVoronoiVertices(List<Vec3> points) {
    	ArrayList<Vec3> v = new ArrayList<Vec3>();
    	for (Tetrahedron t : d.tetras){
    		if (t.vertices[0] == origin || t.vertices[1] == origin ||
    				t.vertices[2] == origin || t.vertices[3].equals(origin)){
    			Vec3 center = t.vertices[0].clone();
    			center.add(t.vertices[1]).add(t.vertices[2]).add(t.vertices[3]).multiply(0.25f);
    			v.add(center);
    		}
    	}
    	
    	return v;
    }
    
}