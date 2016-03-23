// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2013  ICAMS, Ruhr-Universität Bochum
//
// AtomViewer is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// AtomViewer is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with AtomViewer. If not, see <http://www.gnu.org/licenses/> 

package gui.glUtils;

import common.Tupel;
import common.Vec3;
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * Tessalator for a sphere
 */
public class SphereTesselator {

	private static Tupel<int[], float[]> sphereLOD0, sphereLOD1;
	
	static {
		sphereLOD0 = getSphericalVBOData(0);
		sphereLOD1 = getSphericalVBOData(1);
	}
	
	/**
	 * Creates a unique sphere approximated as a icosahedron, higher values for lod get better approximations with more trianlges
	 * Arrays can be used in VBOs. Float-Array contains vertices, the Int-Array the indices for triangles 
	 * @param lod level of detail: 0: icosahedron 20 triangles, 1: first tessallation of icosahedron 80 triangles 2:  320 triangles...
	 * @return
	 */
	public static Tupel<int[], float[]> getSphericalVBOData(int lod){
		if (lod == 0){
			if (sphereLOD0 != null) return sphereLOD0;
		} else if (lod == 1){
			if (sphereLOD1 != null) return sphereLOD1;
		}
		//vertices of an unit size icosahedron
		float[] v = {	 0.0f,         0.0f,         1.0f, 
						 0.8944272f,   0.0f,         0.4472136f,
						 0.27639318f,  0.85065085f,  0.4472136f,
						-0.7236068f,   0.5257311f,   0.4472136f,
						-0.72360677f, -0.5257312f,   0.4472136f,
						 0.27639332f, -0.8506508f,   0.4472136f,
						 0.7236068f,   0.52573115f, -0.4472136f,
						-0.27639326f,  0.8506508f,  -0.4472136f,
						-0.8944272f,   0.0f,        -0.4472136f,
						-0.2763933f,  -0.8506508f,  -0.4472136f,
						 0.723607f,   -0.52573085f, -0.4472136f,
						 0.0f,         0.0f,        -1.0f};

		//triangle indices for the icosahedron
		int[] t = { 0,1,2, 0,2,3, 0,3,4, 0,4,5, 0,5,1,
				11,7,6, 11,8,7, 11,9,8, 11,10,9, 11,6,10,
				1,6,2, 2,6,7, 2,7,3, 3,8,4, 4,9,5, 5,10,1,
				7,8,3, 8,9,4, 9,10,5, 10,6,1};
		for (int i=0; i<lod; i++){
			Tupel<int[], float[]> tessalation = SphereTesselator.tesselate(t, v);
			t = tessalation.o1;
			v = tessalation.o2;
		}
		return new Tupel<int[], float[]>(t, v);
	}
	
	public static Tupel<int[], float[]> tesselate(int[] t, float[] v){
		int[] t2 = new int[t.length*4]; 
		//find all unique edges
		TreeSet<Edge> edges = new TreeSet<Edge>();
		
		for (int i=0; i<t.length; i+=3){
			Edge e = new Edge(t[i], t[i+1]);
			edges.add(e);
			e = new Edge(t[i], t[i+2]);
			edges.add(e);
			e = new Edge(t[i+1], t[i+2]);
			edges.add(e);
		}
		
		//Copy existing vertices
		float[] v2 = new float[v.length + edges.size()*3];
		for (int i=0; i<v.length; i++) v2[i] = v[i]; 
		
		//Create the new vertices and build create a map between edges and new vertex at their center
		TreeMap<Edge, Integer> edgeToVertexMap = new TreeMap<Edge, Integer>();
		int count = v.length/3;
		for (Edge e: edges){
			Vec3 p = new Vec3();
			p.x = v[e.a1*3] + v[e.a2*3];
			p.y = v[e.a1*3+1] + v[e.a2*3+1];
			p.z = v[e.a1*3+2] + v[e.a2*3+2];
			p.normalize();
			
			v2[count*3] = p.x;
			v2[count*3+1] = p.y;
			v2[count*3+2] = p.z;
			edgeToVertexMap.put(e, count);
			count++;
		}
		
		//Tessallate triangles
		count = 0;
		for (int i=0; i<t.length; i+=3){
			int vert0 = t[i];
			int vert1 = t[i+1];
			int vert2 = t[i+2];
			int vert3 = edgeToVertexMap.get(new Edge(vert0, vert1));
			int vert4 = edgeToVertexMap.get(new Edge(vert1, vert2));
			int vert5 = edgeToVertexMap.get(new Edge(vert0, vert2));
			
			t2[count++] = vert0; t2[count++] = vert3; t2[count++] = vert5; 
			t2[count++] = vert3; t2[count++] = vert1; t2[count++] = vert4;
			t2[count++] = vert3; t2[count++] = vert4; t2[count++] = vert5;
			t2[count++] = vert4; t2[count++] = vert2; t2[count++] = vert5;
		}
		
		return new Tupel<int[], float[]>(t2, v2);
	}
	
	private static class Edge implements Comparable<Edge>{
		public int a1, a2;
		public Edge(int a1, int a2){
			if (a1 == a2) 
				throw new IllegalArgumentException("Needs two unique nodes");
			if (a1 < a2){
				this.a1 = a1;
				this.a2 = a2;
			} else {
				this.a2 = a1;
				this.a1 = a2;
			}
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (!(obj instanceof Edge)) return false;
			Edge e = (Edge)obj;
			if (this.a1==e.a1 && this.a2==e.a2) return true;
			else return false;
		}
		
		@Override
		public int compareTo(Edge o) {
			if (a1<o.a1) return 1;
			if (a1>o.a1) return -1;
			if (a2<o.a2) return 1;
			if (a2>o.a2) return -1;
			return 0;
		}
	}
}