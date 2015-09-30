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

package model.mesh;

import java.awt.event.InputEvent;
import java.util.*;
import java.util.concurrent.*;

import common.ThreadPool;
import common.Tupel;
import common.Vec3;
import model.*;

public class Mesh implements Callable<Void>, Pickable{
	private Vec3 upperBounds = new Vec3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);
	private Vec3 lowerBounds = new Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);

	//will be nulled as soon it is not needed anymore
	private AbstractCollection<Triangle> triangles = new HashSet<Triangle>();
	private ArrayList<Vertex> vertices = new ArrayList<Vertex>();
//	private KDTree<Vec3> tree = new KDTree<Vec3>();
	
	private NearestNeighborBuilder<Vec3> nearestVertex;
	
	private List<? extends Vec3> atomsInGrain;
	private float defaultSimplyMeshRate;
	
	private final float CELL_SIZE;
	private final float HALF_CELL_SIZE;

	private int id_source = 0;
	
	private FinalMesh finalMesh;
	
	/**
	 * Initializes the object, but does not create the mesh
	 * Create the mesh either by createMesh() or by call() afterwards
	 * @param atomsInGrain
	 * @param cellSize
	 * @param simplifyMeshRate
	 * @param box
	 * @throws IllegalArgumentException atomsInGrain is null or empty, mesh cannot be created
	 */
	public Mesh(List<? extends Vec3> atomsInGrain, float cellSize, float simplifyMeshRate, BoxParameter box) throws IllegalArgumentException{
		this.CELL_SIZE = cellSize;
		this.defaultSimplyMeshRate = simplifyMeshRate;
		this.HALF_CELL_SIZE = CELL_SIZE * 0.5f;
		if (atomsInGrain == null || atomsInGrain.size()==0){
			throw new IllegalArgumentException("Cannot create a mesh from an empty set");
		}
		this.atomsInGrain = atomsInGrain;
		
		this.nearestVertex = new NearestNeighborBuilder<Vec3>(box, 2*cellSize);
	}
	
	/**
	 * Import, directly create a finalized mesh
	 * @param triangles
	 * @param vertices
	 */
	public Mesh(int[] triangles, float[] vertices){
		this.CELL_SIZE = 0f;
		this.HALF_CELL_SIZE = 0f;
		this.finalMesh = new FinalMesh(triangles, vertices);
	}

	public synchronized void createMesh() {
		if (isFinalized()) return;
		final float invCellSize = 1f/CELL_SIZE;
		int gridX, gridY, gridZ;
		
		for (int i=0; i<atomsInGrain.size(); i++){
			Vec3 a = atomsInGrain.get(i);
			
			if (a.x>upperBounds.x) upperBounds.x = a.x;
			if (a.y>upperBounds.y) upperBounds.y = a.y;
			if (a.z>upperBounds.z) upperBounds.z = a.z;
			
			if (a.x<lowerBounds.x) lowerBounds.x = a.x;
			if (a.y<lowerBounds.y) lowerBounds.y = a.y;
			if (a.z<lowerBounds.z) lowerBounds.z = a.z;
		}
		lowerBounds.x -= HALF_CELL_SIZE; lowerBounds.y -= HALF_CELL_SIZE; lowerBounds.z -= HALF_CELL_SIZE;
		upperBounds.x += HALF_CELL_SIZE; upperBounds.y += HALF_CELL_SIZE; upperBounds.z += HALF_CELL_SIZE;
		
		gridX = (int)(((upperBounds.x-lowerBounds.x)*invCellSize)+1)+2;
		gridY = (int)(((upperBounds.y-lowerBounds.y)*invCellSize)+1)+2;
		gridZ = (int)(((upperBounds.z-lowerBounds.z)*invCellSize)+1)+2;
		
		final byte[][][] grid = new byte[gridX][gridY][gridZ];
		
		//Mark all cells containing a atom
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)(((long)atomsInGrain.size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)atomsInGrain.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++){
						Vec3 a = atomsInGrain.get(i);
						
						int x = (int)((a.x-lowerBounds.x)*invCellSize)+1;
						int y = (int)((a.y-lowerBounds.y)*invCellSize)+1;
						int z = (int)((a.z-lowerBounds.z)*invCellSize)+1;
						grid[x][y][z] = (byte)1;
					}
					
					return null;
				}
			});
		}
		ThreadPool.executeParallelSecondLevel(parallelTasks);
				
		//Identify cells inside the grid which are completely surrounded with other filled cells
		//Mark these cells, because the atoms stored there can be ignored during mesh refinement
		for (int x=1; x<gridX-1; x++){
			for (int y=1; y<gridY-1; y++){
				for (int z=1; z<gridZ-1; z++){
						if (grid[x-1][y-1][z-1] != 0 && grid[x-1][y-1][z+0] != 0 && grid[x-1][y-1][z+1] != 0 &&
							grid[x-1][y+0][z-1] != 0 && grid[x-1][y+0][z+0] != 0 && grid[x-1][y+0][z+1] != 0 &&
							grid[x-1][y+1][z-1] != 0 && grid[x-1][y+1][z+0] != 0 && grid[x-1][y+1][z+1] != 0 &&
							grid[x+0][y-1][z-1] != 0 && grid[x+0][y-1][z+0] != 0 && grid[x+0][y-1][z+1] != 0 &&
							grid[x+0][y+0][z-1] != 0 && grid[x+0][y+0][z+0] != 0 && grid[x+0][y+0][z+1] != 0 &&
							grid[x+0][y+1][z-1] != 0 && grid[x+0][y+1][z+0] != 0 && grid[x+0][y+1][z+1] != 0 &&
							grid[x+1][y-1][z-1] != 0 && grid[x+1][y-1][z+0] != 0 && grid[x+1][y-1][z+1] != 0 &&
							grid[x+1][y+0][z-1] != 0 && grid[x+1][y+0][z+0] != 0 && grid[x+1][y+0][z+1] != 0 &&
							grid[x+1][y+1][z-1] != 0 && grid[x+1][y+1][z+0] != 0 && grid[x+1][y+1][z+1] != 0) 
						    	grid[x][y][z] = (byte)2;
				}
			}
		}
		
		//Include all atoms in boundary cells in a kd-tree
		for (int i=0; i<atomsInGrain.size(); i++){
			Vec3 a = atomsInGrain.get(i);
			
			int x = (int)((a.x-lowerBounds.x)*invCellSize)+1;
			int y = (int)((a.y-lowerBounds.y)*invCellSize)+1;
			int z = (int)((a.z-lowerBounds.z)*invCellSize)+1;
			
			if (grid[x][y][z] == (byte) 1)
				nearestVertex.add(a);
		}
		
		TreeMap<int[],Vertex> vertexMap = new TreeMap<int[],Vertex>(new Comparator<int[]>() {
			@Override
			public int compare(int[] o1, int[] o2) {
				if (o1[0]<o2[0]) return -1;
				if (o1[0]>o2[0]) return 1;
				
				if (o1[1]<o2[1]) return -1;
				if (o1[1]>o2[1]) return 1;
				
				if (o1[2]<o2[2]) return -1;
				if (o1[2]>o2[2]) return 1;
				return 0;
			}
		});
		
		TreeMap<Edge, HalfEdge> edgeToHalfEdgeMap = new TreeMap<Edge, HalfEdge>();
		
		int[][] cubeCoord = new int[8][3];
		
		//Marching Tetrahedrons
		for (int x=0; x<gridX-1; ++x){
			cubeCoord[0][0] = x  ; cubeCoord[4][0] = x  ;
			cubeCoord[1][0] = x  ; cubeCoord[5][0] = x  ;
			cubeCoord[2][0] = x+1; cubeCoord[6][0] = x+1;
			cubeCoord[3][0] = x+1; cubeCoord[7][0] = x+1;
			for (int y=0; y<gridY-1; ++y){
				cubeCoord[0][1] = y  ; cubeCoord[4][1] = y  ;
				cubeCoord[1][1] = y+1; cubeCoord[5][1] = y+1;
				cubeCoord[2][1] = y+1; cubeCoord[6][1] = y+1;
				cubeCoord[3][1] = y  ; cubeCoord[7][1] = y  ;
				for (int z=0; z<gridZ-1; ++z){
					cubeCoord[0][2] = z  ; cubeCoord[4][2] = z+1;
					cubeCoord[1][2] = z  ; cubeCoord[5][2] = z+1;
					cubeCoord[2][2] = z  ; cubeCoord[6][2] = z+1;
					cubeCoord[3][2] = z  ; cubeCoord[7][2] = z+1;
					
					polyTetra(cubeCoord[0],cubeCoord[2],cubeCoord[3],cubeCoord[7],grid,vertexMap, edgeToHalfEdgeMap);
			     	polyTetra(cubeCoord[0],cubeCoord[6],cubeCoord[2],cubeCoord[7],grid,vertexMap, edgeToHalfEdgeMap);
			     	polyTetra(cubeCoord[0],cubeCoord[4],cubeCoord[6],cubeCoord[7],grid,vertexMap, edgeToHalfEdgeMap);
			     	polyTetra(cubeCoord[0],cubeCoord[6],cubeCoord[1],cubeCoord[2],grid,vertexMap, edgeToHalfEdgeMap);
			     	polyTetra(cubeCoord[6],cubeCoord[0],cubeCoord[1],cubeCoord[4],grid,vertexMap, edgeToHalfEdgeMap);
					polyTetra(cubeCoord[6],cubeCoord[5],cubeCoord[4],cubeCoord[1],grid,vertexMap, edgeToHalfEdgeMap);
				}
			}	
		}
		atomsInGrain = null;
	}
	
	public FinalMesh getFinalMesh(){
		if (finalMesh != null) return finalMesh;
		else throw new RuntimeException("Mesh is not finalized");
	}
	
	@Override
	/**
	 * will create the mesh and performs a standard simplification at the same time
	 */
	public synchronized Void call() throws Exception {
		if (isFinalized()) return null;
		createMesh();
		standardSimplification(defaultSimplyMeshRate);
		
		finalizeMesh();
		return null;
	}
	
	public List<FinalizedTriangle> getTriangles(){
		if (finalMesh != null) return finalMesh.getTriangles();
		else throw new RuntimeException("Mesh is not finalized");
	}
	
	private void polyTetra(int[] c0, int[] c1, int[] c2, int[] c3, byte[][][] grid, 
			TreeMap<int[], Vertex> vertexMap, TreeMap<Edge,HalfEdge> edgeMap){
		int triindex = 0;
		if (grid[c0[0]][c0[1]][c0[2]] >= 1) triindex |= 1;
		if (grid[c1[0]][c1[1]][c1[2]] >= 1) triindex |= 2;
		if (grid[c2[0]][c2[1]][c2[2]] >= 1) triindex |= 4;
		if (grid[c3[0]][c3[1]][c3[2]] >= 1) triindex |= 8;
		
		Vertex a, b, c;
		switch (triindex) {
		case 0x00: break;
		case 0x0F: break;
		
		case 0x01:
			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c0, c2, vertexMap);
			c = getVertex(c0, c3, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
		case 0x02:
			a = getVertex(c1, c0, vertexMap);
			b = getVertex(c1, c3, vertexMap);
			c = getVertex(c1, c2, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
		case 0x04:
			a = getVertex(c2, c0, vertexMap);
			b = getVertex(c2, c1, vertexMap);
			c = getVertex(c2, c3, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
		case 0x08:
			a = getVertex(c3, c0, vertexMap);
			b = getVertex(c3, c2, vertexMap);
			c = getVertex(c3, c1, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
			
			
		case 0x0D:
			a = getVertex(c1, c0, vertexMap);
			b = getVertex(c1, c3, vertexMap);
			c = getVertex(c1, c2, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
			break;	
		case 0x0E:
			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c0, c2, vertexMap);
			c = getVertex(c0, c3, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
			break;
		case 0x0B:
			a = getVertex(c2, c0, vertexMap);
			b = getVertex(c2, c1, vertexMap);
			c = getVertex(c2, c3, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
			break;	
		case 0x07:
			a = getVertex(c3, c0, vertexMap);
			b = getVertex(c3, c2, vertexMap);
			c = getVertex(c3, c1, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
			break;
		
			
		case 0x0C:
			a = getVertex(c0, c3, vertexMap);
			b = getVertex(c0, c2, vertexMap);
			c = getVertex(c1, c3, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
//			c = getVertex(c0, c2, vertexMap);
//			a = getVertex(c1, c3, vertexMap);
			a = c;
			c = b;
			b = getVertex(c1, c2, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
		
			break;
		case 0x03:
			a = getVertex(c0, c3, vertexMap);
			b = getVertex(c0, c2, vertexMap);
			c = getVertex(c1, c3, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
//			a = getVertex(c1, c3, vertexMap);
			a = c;
//			c = getVertex(c0, c2, vertexMap);
			c = b;
			b = getVertex(c1, c2, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
			
		case 0x0A:
			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c2, c3, vertexMap);
			c = getVertex(c0, c3, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
//			a = getVertex(c0, c1, vertexMap);
//			c = getVertex(c2, c3, vertexMap);
			c = b;
			b = getVertex(c1, c2, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
			break;
		case 0x05:
			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c2, c3, vertexMap);
			c = getVertex(c0, c3, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
//			a = getVertex(c0, c1, vertexMap);
//			c = getVertex(c2, c3, vertexMap);
			c = b;
			b = getVertex(c1, c2, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
			
		case 0x09:
			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c1, c3, vertexMap);
			c = getVertex(c2, c3, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
//			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c0, c2, vertexMap);
//			c = getVertex(c2, c3, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
			break;
		case 0x06:
			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c1, c3, vertexMap);
			c = getVertex(c2, c3, vertexMap);
			triangles.add(createTriangle(c, b, a, edgeMap));
//			a = getVertex(c0, c1, vertexMap);
			b = getVertex(c0, c2, vertexMap);
//			c = getVertex(c2, c3, vertexMap);
			triangles.add(createTriangle(a, b, c, edgeMap));
			break;
		}

	}
	
	private Vertex getVertex(int[] c1, int[] c2, TreeMap<int[],Vertex> map){
		int[] d = new int[]{
			(c1[0]+c2[0]),
			(c1[1]+c2[1]),
			(c1[2]+c2[2]),
		};
				
		Vertex v = map.get(d);
		if (v == null){
			v = new Vertex(
					new Vec3(
						lowerBounds.x+(d[0]-1)*HALF_CELL_SIZE,
						lowerBounds.y+(d[1]-1)*HALF_CELL_SIZE,
						lowerBounds.z+(d[2]-1)*HALF_CELL_SIZE), 
					id_source++);
			vertices.add(v);
			map.put(d,v);
		}

		return v;
	}
	
	private Triangle createTriangle(Vertex a, Vertex b, Vertex c, TreeMap<Edge, HalfEdge> edgeToHalfEdgeMap){
		HalfEdge he1 = new HalfEdge();
		he1.vertexEnd = b;
		HalfEdge he2 = new HalfEdge();
		he2.vertexEnd = c;
		HalfEdge he3 = new HalfEdge();
		he3.vertexEnd = a;
		
		a.neighborEdge = he1; b.neighborEdge = he2; c.neighborEdge = he3;   
		he1.next = he2; he2.next = he3; he3.next = he1;
		
		//Pairing half edges
		Edge e1 = new Edge(a,b);
		HalfEdge pair1 = edgeToHalfEdgeMap.remove(e1);
		if (pair1!=null) { 
			pair1.pair = he1; he1.pair = pair1;
		} else edgeToHalfEdgeMap.put(e1, he1);
		
		Edge e2 = new Edge(b,c);
		HalfEdge pair2 = edgeToHalfEdgeMap.remove(e2);
		if (pair2!=null) { 
			pair2.pair = he2; he2.pair = pair2;
		} else edgeToHalfEdgeMap.put(e2, he2);
		
		Edge e3 = new Edge(c,a);
		HalfEdge pair3 = edgeToHalfEdgeMap.remove(e3);
		if (pair3!=null) { 
			pair3.pair = he3; he3.pair = pair3;
		} else edgeToHalfEdgeMap.put(e3, he3);
		
		Triangle t = new Triangle();
		t.neighborEdge=he1;
		he1.triangle = t; he2.triangle = t; he3.triangle = t;
		return t;
	}
	
	/**
	 * Collapsing an half edge and effectively remove the
	 * end vertex from the mesh. The vertex is not deleted from the
	 * list of vertices immediately, instead its edge is set to null
	 * to indicate removal. After the mesh is simplified, all vertices with
	 * nulled edges can be deleted in linear time.
	 * @param he
	 * @param moveTo
	 */
	private void edgeCollapse(HalfEdge he, Vec3 moveTo){
		HalfEdge hePair = he.pair;
		
		Vertex v1 = hePair.vertexEnd;
		Vertex v2 = he.vertexEnd;
		
		//Move remaining vertex
		v1.setTo(moveTo);
		
		//Delete Faces
		triangles.remove(he.triangle);
		triangles.remove(hePair.triangle);
		
		Vertex v3 = he.next.vertexEnd;
		Vertex v4 = hePair.next.vertexEnd;
		
		HalfEdge v4v1 = v4.neighborEdge;
		do{
			v4v1 = v4v1.pair.next;
		} while (v4v1.vertexEnd!=v1);
		
		HalfEdge v1v3 = v1.neighborEdge;
		do{
			v1v3 = v1v3.pair.next;
		} while (v1v3.vertexEnd!=v3);
		
		HalfEdge v3v2 = v3.neighborEdge;
		do{
			v3v2 = v3v2.pair.next;
		} while (v3v2.vertexEnd!=v2);
		
		HalfEdge v2v4 = v2.neighborEdge;
		do{
			v2v4 = v2v4.pair.next;
		} while (v2v4.vertexEnd!=v4);
		
		//Transfer all incoming halfEdges in v2 to v1		
		HalfEdge a = v2.neighborEdge;
		do{
			a.pair.vertexEnd = v1;
			a = a.pair.next;
		} while (v2.neighborEdge!=a);
		
		//Recreate pairing on halfEdges adjacent to deleted faces
		v4v1.pair = v2v4;
		v2v4.pair = v4v1;
		v1v3.pair = v3v2;
		v3v2.pair = v1v3;
		
		v1.neighborEdge = v1v3;
		v3.neighborEdge = v3v2;
		v4.neighborEdge = v4v1;
		
		//Mark vertex as deleted
		v2.neighborEdge = null;
	}
	
	public void shrink(final float offset){
		if (finalMesh != null) return;

		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)((long)(vertices.size() * j)/ThreadPool.availProcessors());
					int end = (int)((long)(vertices.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++)
						vertices.get(i).shrink(nearestVertex, offset);
					
					return null;
				}
			});
		}
		ThreadPool.executeParallelSecondLevel(parallelTasks);
	}
	
	public void cornerPreservingSmooth(){
		if (finalMesh != null) return;
		
		final Vec3[] smooth = new Vec3[vertices.size()];
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)((long)(vertices.size() * j)/ThreadPool.availProcessors());
					int end = (int)((long)(vertices.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++){
						smooth[i] = vertices.get(i).getCurvatureDependentLaplacianSmoother();
						smooth[i].multiply(0.5f);
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallelSecondLevel(parallelTasks);
		
		for (int i=0; i<vertices.size(); i++)
			vertices.get(i).add(smooth[i]); 
	}
	
	public void smooth(){
		if (finalMesh != null) return;
		
		final Vec3[] smooth = new Vec3[vertices.size()];
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)((long)(vertices.size() * j)/ThreadPool.availProcessors());
					int end = (int)((long)(vertices.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++){
						smooth[i] = vertices.get(i).getLaplacianSmoother();
						smooth[i].multiply(0.5f);
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallelSecondLevel(parallelTasks);
		
		for (int i=0; i<vertices.size(); i++)
			vertices.get(i).add(smooth[i]); 
	}
	
	public void simplifyMesh(float maxCosts){
		if (maxCosts <= 0f) return;
		if (finalMesh != null) return;
		
		final SortedMap<Vertex,float[]> vertexQs = Collections.synchronizedSortedMap(new TreeMap<Vertex, float[]>());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)((long)(vertices.size() * j)/ThreadPool.availProcessors());
					int end = (int)((long)(vertices.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++){
						Vertex v = vertices.get(i);
						float[] q = new float[10];
						ArrayList<Triangle> t = v.getAdjacentFaces();
						for (int j=0; j<t.size(); j++){
							Vec3 n = t.get(j).getUnitNormalVector();
							float d = -n.dot(v);
							
							q[0] += n.x*n.x;	//a²
							q[1] += n.x*n.y;	//ab
							q[2] += n.x*n.z;	//ac
							q[3] += n.x*d;		//ad
							q[4] += n.y*n.y;	//b²
							q[5] += n.y*n.z;	//bc
							q[6] += n.y*d;		//bd
							q[7] += n.z*n.z;	//c²
							q[8] += n.z*d;		//cd
							q[9] += d*d;		//d²
						}
						vertexQs.put(v, q);
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallelSecondLevel(parallelTasks);
		
		TreeSet<HalfEdgePair> sortedCosts = new TreeSet<HalfEdgePair>();
		
		for (Triangle t : triangles){
			HalfEdge n = t.neighborEdge;
			if (n.isContractable()) sortedCosts.add(new HalfEdgePair(n, vertexQs));
			n = n.next;
			if (n.isContractable()) sortedCosts.add(new HalfEdgePair(n, vertexQs));
			n = n.next;
			if (n.isContractable()) sortedCosts.add(new HalfEdgePair(n, vertexQs));
		}
//		float[] m = new float[16];
		
		HalfEdgePair collapse = sortedCosts.pollFirst();
		while (collapse != null && collapse.cost<maxCosts) {
			HalfEdge n = collapse.a;
			if (n.isContractable()){
				Vertex a = collapse.a.vertexEnd;
				Vertex b = collapse.b.vertexEnd;
				
				//Calculate new vertex costs
				float[] q_sum = new float[10];
				float[] v1_q = vertexQs.get(a);
				float[] v2_q = vertexQs.get(b);
				q_sum[0] = v1_q[0] + v2_q[0]; q_sum[1] = v1_q[1] + v2_q[1];
				q_sum[2] = v1_q[2] + v2_q[2]; q_sum[3] = v1_q[3] + v2_q[3];
				q_sum[4] = v1_q[4] + v2_q[4]; q_sum[5] = v1_q[5] + v2_q[5];
				q_sum[6] = v1_q[6] + v2_q[6]; q_sum[7] = v1_q[7] + v2_q[7];
				q_sum[8] = v1_q[8] + v2_q[8]; q_sum[9] = v1_q[9] + v2_q[9];
				
				//Something is wrong in this calculation, use center of the two points to contract instead
//				//Calculate optimal contraction point
//				m[0] = q_sum[0]; m[1] = q_sum[1]; m[2] = q_sum[2]; m[3] = q_sum[3];
//				m[4] = q_sum[1]; m[5] = q_sum[4]; m[6] = q_sum[5]; m[7] = q_sum[6];
//				m[8] = q_sum[2]; m[9] = q_sum[5]; m[10] = q_sum[7]; m[11] = q_sum[8];
////				m[12] = 0f; m[13] = 0f; m[14] = 0f; 
//				m[15] = 1f;
//				
//				Vec3 contractionPoint = calculateContractionPoint(m);
//				if (contractionPoint == null) //Matrix is not invertable
//					contractionPoint = a.addClone(b).multiply(0.5f);
				
				Vec3 contractionPoint = a.addClone(b).multiply(0.5f);
				
				if (n.isContractableForGivenPoint(contractionPoint)){
					//remove all affected edges from costs
					do {
						sortedCosts.remove(new HalfEdgePair(n, vertexQs));
						n = n.pair.next;
					} while (n!=collapse.a);
					
					n = collapse.b;
					do {
						sortedCosts.remove(new HalfEdgePair(n, vertexQs));
						n = n.pair.next;
					} while (n!=collapse.b);
					
					edgeCollapse(collapse.a, contractionPoint);
					
					//Update vertex costs
					vertexQs.remove(a);
					vertexQs.put(b, q_sum);
					
					//reinsert edges to costs
					n = collapse.b.vertexEnd.neighborEdge;
					do {
						if (n.isContractable()) 
							sortedCosts.add(new HalfEdgePair(n, vertexQs));
						n = n.pair.next;
					} while (n!=collapse.b.vertexEnd.neighborEdge);
				}
			}
			collapse = sortedCosts.pollFirst();
		}
		
		//Remove all vertices marked as deleted from the list
		ArrayList<Vertex> newVertices = new ArrayList<Vertex>();
		for (Vertex v : vertices)
			if (v.neighborEdge!=null) newVertices.add(v);
		vertices = newVertices;
	}
	
	private float calculateVertexCost(Vec3 v, float[] q){
		float c = (q[0]*v.x + q[1]*v.y + q[2]*v.z + q[3])*v.x;
			 c += (q[1]*v.x + q[4]*v.y + q[5]*v.z + q[6])*v.y;
			 c += (q[2]*v.x + q[5]*v.y + q[7]*v.z + q[8])*v.z;
			 c +=  q[3]*v.x + q[6]*v.y + q[8]*v.z + q[9];
		return c;
	}
	
	private float calculateEdgeCost(HalfEdge he, SortedMap<Vertex,float[]> vertexQs){
		Vertex a = he.vertexEnd;
		Vertex b = he.pair.vertexEnd;
		Vec3 v_average = a.addClone(b).multiply(0.5f);
		float[] q_sum = new float[10];
		float[] v1_q = vertexQs.get(a);
		float[] v2_q = vertexQs.get(b);
		q_sum[0] = v1_q[0] + v2_q[0]; q_sum[1] = v1_q[1] + v2_q[1];
		q_sum[2] = v1_q[2] + v2_q[2]; q_sum[3] = v1_q[3] + v2_q[3];
		q_sum[4] = v1_q[4] + v2_q[4]; q_sum[5] = v1_q[5] + v2_q[5];
		q_sum[6] = v1_q[6] + v2_q[6]; q_sum[7] = v1_q[7] + v2_q[7];
		q_sum[8] = v1_q[8] + v2_q[8]; q_sum[9] = v1_q[9] + v2_q[9];
		float cost = calculateVertexCost(v_average, q_sum);
		return cost;
	}	
	
	public synchronized void standardSimplification(float simplificationRate){
		shrink(0f);
		cornerPreservingSmooth();
		shrink(0f);
		cornerPreservingSmooth();		
		shrink(0f);
		smooth();
		smooth();
		
		simplifyMesh(6f*(float)Math.pow(simplificationRate,3.));
	}
	
	public synchronized double getVolume(){
		if (finalMesh != null) return finalMesh.getVolume();
		else {
			double volume = 0.;
			for (Triangle t : triangles)
				volume += t.getArea()*t.getUnitNormalVector().dot(t.getVertex());
			volume /= 3.;
			return volume;
		}
	}
	
	public synchronized double getArea(){
		if (finalMesh != null) return finalMesh.getArea();
		else {
			double area = 0.;
			for (Triangle t : triangles)
				area += t.getArea();
			
			return area;
		}
	}
	
	public Vec3 getCentroid(){
		if (finalMesh != null) return finalMesh.getCentroid();
		Vec3 centroid = new Vec3();
		
		for (Triangle t : triangles){
			Vec3 n = t.getNormalVector();
			Vertex[] vert = t.getVertices();
			centroid.x += n.x * ( (vert[0].x+vert[1].x)*(vert[0].x+vert[1].x) +
					(vert[1].x+vert[2].x)*(vert[1].x+vert[2].x) +
					(vert[2].x+vert[0].x)*(vert[2].x+vert[0].x));
			centroid.y += n.y * ( (vert[0].y+vert[1].y)*(vert[0].y+vert[1].y) +
					(vert[1].y+vert[2].y)*(vert[1].y+vert[2].y) +
					(vert[2].y+vert[0].y)*(vert[2].y+vert[0].y));
			centroid.z += n.z * ( (vert[0].z+vert[1].z)*(vert[0].z+vert[1].z) +
					(vert[1].z+vert[2].z)*(vert[1].z+vert[2].z) +
					(vert[2].z+vert[0].z)*(vert[2].z+vert[0].z));
		}
		
		centroid.divide(24f);
		centroid.divide(2*(float)getVolume());
		
		return centroid;
	}
	
	/**
	 * Finalizes the mesh simplification.
	 * No more simplification operations are possible, for the benefit of less memory consumption.
	 */
	public synchronized void finalizeMesh(){
		if (this.finalMesh != null) return;
		this.finalMesh = new FinalMesh(vertices, triangles);
		
		this.nearestVertex = null;
		this.vertices = null;
		this.triangles = null;
		this.atomsInGrain = null;
	}
	
	public boolean isFinalized() {
		return (finalMesh != null);
	}
	
	private class HalfEdgePair implements Comparable<HalfEdgePair>{
		public HalfEdge a,b;
		public float cost;
		
		public HalfEdgePair(HalfEdge a, SortedMap<Vertex, float[]> vertexQs){
			HalfEdge b = a.pair;
			if (a.vertexEnd.compareTo(b.vertexEnd)<0){
				this.a = a;
				this.b = b;
			} else {
				this.a = b;
				this.b = a;
			}
			
			this.cost = calculateEdgeCost(a, vertexQs);
		}

		@Override
		public boolean equals(Object obj) {
			HalfEdgePair hep = (HalfEdgePair) obj;
			return this.a.vertexEnd == hep.a.vertexEnd && this.b.vertexEnd == hep.b.vertexEnd;
		}
		
		@Override
		public int compareTo(HalfEdgePair o) {
			if (this.cost < o.cost) return -1;
			if (this.cost > o.cost) return 1;
			if (this.a.vertexEnd.compareTo(o.a.vertexEnd)<0) return 1; 
			if (this.a.vertexEnd.compareTo(o.a.vertexEnd)>0) return -1;
			if (this.b.vertexEnd.compareTo(o.b.vertexEnd)<0) return 1; 
			if (this.b.vertexEnd.compareTo(o.b.vertexEnd)>0) return -1;
			return 0;
		}
	}
	
	@SuppressWarnings("unused")
	private Vec3 calculateContractionPoint(float[] mat) {
		//this is a 4x4 matrix inverter, but only the entries 3,7 and 11
		//are needed for the contraction point, so not the full
		//inverted matrix (full algorithm see MatrixOps) is to be calculated
		Vec3 dst = new Vec3();
		
		/* calculate pairs for first 8 elements (cofactors) */		
		float tmp1 = (mat[10] * mat[15] - mat[14] * mat[11]);
		float tmp2 = (mat[6] * mat[15] - mat[14] * mat[7]);
		float tmp3 = (mat[6] * mat[11] - mat[10] * mat[7]);
		float tmp4 = (mat[2] * mat[15] - mat[14] * mat[3]);
		float tmp5 = (mat[2] * mat[11] - mat[10] * mat[3]);
		float tmp6 = (mat[2] * mat[7] - mat[6] * mat[3]);
		/* calculate first 8 elements (cofactors) */
		float detPart0 =  tmp1 * mat[5] - tmp2 * mat[9] + tmp3 * mat[13];
		float detPart1 = -tmp1 * mat[1] + tmp4 * mat[9] - tmp5 * mat[13];
		float detPart2 =  tmp2 * mat[1] - tmp4 * mat[5] + tmp6 * mat[13];
		dst.x = -tmp3 * mat[1] + tmp5 * mat[5] - tmp6 * mat[9];
		dst.y =  tmp3 * mat[0] - tmp5 * mat[4] + tmp6 * mat[8];
		
		/* calculate pairs for second 8 elements (cofactors) */
		tmp3 = (mat[4] * mat[9] - mat[8] * mat[5]);
		tmp5 = (mat[0] * mat[9] - mat[8] * mat[1]);
		tmp6 = (mat[0] * mat[5] - mat[4] * mat[1]);
		
		/* calculate second 8 elements (cofactors) */
		dst.z = -tmp3 * mat[3] + tmp5 * mat[7] - tmp6 * mat[11];
		/* calculate determinant */
		float det = mat[0] * detPart0 + mat[4] * detPart1 + mat[8] * detPart2 + mat[12] * dst.x;
		if (Math.abs(det)<0.0001f) return null;
		
		/* calculate matrix inverse */
		det = 1f / det;
		dst.multiply(det);
		return dst;
	}
	
	private class Edge implements Comparable<Edge>{
		public Vertex a, b;
		
		public Edge(Vertex a, Vertex b) {
			assert a != b : "Illegal edge, vertex a and b identical";
			if (a.compareTo(b) < 0) {
				this.a = a; this.b = b;
			} else {
				this.a = b; this.b = a;
			}
		}

		@Override
		public int compareTo(Edge o) {
			if (this.a.compareTo(o.a) < 0) return 1; 
			if (this.a.compareTo(o.a) > 0) return -1;
			if (this.b.compareTo(o.b) < 0) return 1; 
			if (this.b.compareTo(o.b) > 0) return -1;
			return 0;
		}
	}

	@Override
	public Collection<?> getHighlightedObjects() {
		return null;
	}

	@Override
	public boolean isHighlightable() {
		return false;
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		return this.getCentroid();
	}

	//TODO format message
	@Override
	public Tupel<String,String> printMessage(InputEvent ev, AtomData data) {
		return new Tupel<String,String>("Mesh", String.format("Mesh volume=%g Surface Area=%g", getVolume(), getArea()));
	}
}