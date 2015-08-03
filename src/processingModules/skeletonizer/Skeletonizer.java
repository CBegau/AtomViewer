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

package processingModules.skeletonizer;

import gui.RenderRange;
import gui.ViewerGLJPanel;
import gui.ViewerGLJPanel.RenderOption;
import gui.glUtils.*;
import gui.glUtils.Shader.BuiltInShader;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import common.FastDeletableArrayList;
import common.ThreadPool;
import common.UniqueIDCounter;
import common.Vec3;
import model.*;
import model.BurgersVector.BurgersVectorType;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.skeletonizer.Dislocation.BurgersVectorInformation;
import processingModules.skeletonizer.JDislocationMenuPanel.Option;
import processingModules.skeletonizer.processors.*;

/**
 * Creates a dislocation skeleton from given set of dislocation core atoms 
 * and additionally, if possible, a set of stacking fault planes 
 */
public class Skeletonizer extends DataContainer {
	private static JDislocationMenuPanel dataPanel;
	private static final float CORE_THICKNESS = 5f;
	
	private FastDeletableArrayList<SkeletonNode> nodes = new FastDeletableArrayList<SkeletonNode>();
	private ArrayList<Dislocation> dislocations = new ArrayList<Dislocation>();
	private ArrayList<PlanarDefect> planarDefects = new ArrayList<PlanarDefect>();
	
	private UniqueIDCounter dislocationIDSource = UniqueIDCounter.getNewUniqueIDCounter();
	
	private AtomData data;
	
	private float meshingThreshold = -1;
	private boolean skeletonizeOverGrains;
	
	public Skeletonizer(float meshingThreshold, boolean skeletonizeOverGrains){
		this.meshingThreshold = meshingThreshold;
		this.skeletonizeOverGrains = skeletonizeOverGrains;
	}
	
	/**
	 * Creates a dislocation skeleton from dislocation core atoms and planes of stacking faults
	 */
	public boolean processData(final AtomData data) {
		this.data = data;
		this.meshingThreshold *= data.getCrystalStructure().getNearestNeighborSearchRadius(); 

		List<Atom> defectAtoms = data.getCrystalStructure().getDislocationDefectAtoms(data);
		//create a initial mesh to skeletonize
		//Each atom becomes a node, edges are derived from nearest neighborhood relationship
		int id = 0;
		for (Atom a : defectAtoms) {
			nodes.add(new SkeletonNode(a, id++));
		}
		
		//The skeletonizer does not need modifications for polycrystalline materials
		//If atoms are placed in different grains, no neighborhood will be created in "c.buildNeigh(nnb)", thus 
		//the basic algorithm works exactly the same in single- and polycrystals
		final NearestNeighborBuilder<SkeletonNode> nnb = 
				new NearestNeighborBuilder<SkeletonNode>(data.getBox(), meshingThreshold, true);
		nnb.addAll(nodes);
		final boolean sameGrainsOnly = data.isPolyCrystalline() && !skeletonizeOverGrains;
		//Parallel build
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)(((long)nodes.size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)nodes.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++)
						nodes.get(i).buildNeigh(nnb, sameGrainsOnly);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
		
		this.transform(data);
		return true;
	}

	/**
	 * Returns all SkeletonNodes in the skeleton
	 * The list is null after the skeleton is finished
	 * @return all SkeletonNodes
	 */
	public List<SkeletonNode> getNodes() {
		return nodes;
	}
	
	/**
	 * Returns all identified stacking fault and twin boundary planes in the skeleton
	 * @return all stacking fault and twin boundary planes
	 */
	public ArrayList<PlanarDefect> getPlanarDefects() {
		return planarDefects;
	}
	
	/**
	 * Returns all identified dislocations in the skeleton
	 * @return all dislocations
	 */
	public ArrayList<Dislocation> getDislocations() {
		return dislocations;
	}
	
	/**
	 * Nearest neighborhood threshold distance applied to create the skeleton 
	 * @return Nearest neighborhood threshold distance
	 */
	public float getNearestNeigborDist() {
		return meshingThreshold;
	}
	
	/**
	 * Indicates if a skeleton is to be created across multiple grains or phases
	 * @return
	 */
	public boolean skeletonizeOverGrains() {
		return skeletonizeOverGrains;
	}
	
	/**
	 * Creates a skeleton with a default set of post- and preprocessors
	 */
	private void transform(AtomData data){
		ArrayList<SkeletonPreprocessor> preprocessors = new ArrayList<SkeletonPreprocessor>();
		
		preprocessors.addAll(data.getCrystalStructure().getSkeletonizerPreProcessors());
		
		ArrayList<SkeletonMeshPostprocessor> meshPostprocessors = new ArrayList<SkeletonMeshPostprocessor>();
		meshPostprocessors.add(new PruneProcessor());
		
		ArrayList<SkeletonDislocationPostprocessor> dislocationPostProcessors = new ArrayList<SkeletonDislocationPostprocessor>();
		dislocationPostProcessors.add(new DislocationFixingPostprocessor(meshingThreshold));
		dislocationPostProcessors.add(new DislocationSmootherPostprocessor());
		
		transform(preprocessors, meshPostprocessors, dislocationPostProcessors, data);
	}
	
	/**
	 * Creates a skeleton with given sets of post- and preprocessors
	 * @param preprocessors Preprocessors are executed on the initial created mesh in the order in which they are inserted in the list 
	 * @param meshPostprocessors These postprocessors are executed on the final shrinked mesh, before it is transformed to dislocations.
	 * They are applied in the order in which they are inserted in the list
	 * @param dislocationPostprocessors These postprocessors are executed after the mesh is transformed to dislocations.
	 * They are applied in the order in which they are inserted in the list
	 */
	private void transform(List<SkeletonPreprocessor> preprocessors, 
			List<SkeletonMeshPostprocessor> meshPostprocessors, 
			List<SkeletonDislocationPostprocessor> dislocationPostprocessors, AtomData data){
		if( this.nodes.size()==0 ) return;
		
		int iterations = 0;
		boolean updated = false;
		
		//Apply pre-processors
		if (preprocessors!=null)
			for (SkeletonPreprocessor pre : preprocessors)
				pre.preProcess(this);


		//The skeletonization core process. Alternate contraction and merging until the process converged
		do {
			updated = false;
			if (contractMesh()) updated = true;
			if (mergeNodes(0.1f*meshingThreshold) && !Thread.interrupted()) updated = true;
			iterations++;
	
		} while (updated && iterations<10000 && !Thread.interrupted());
		if (Thread.interrupted()) return;
		
		//Apply mesh post-processors
		if(meshPostprocessors != null)
			for (SkeletonMeshPostprocessor post : meshPostprocessors)
				post.postProcessMesh(this);
		
		//transform thinned mesh into dislocations
		convertToDislocations();
		
		//Apply dislocation post-processors
		if(dislocationPostprocessors != null)
			for (SkeletonDislocationPostprocessor post : dislocationPostprocessors)
				post.postProcessDislocations(this);
		
		//detect hexagonally shaped stacking faults
		if (data.getCrystalStructure().hasStackingFaults()){
			//this map will later contain the relation between atoms and the planes they are part of
			TreeMap<PlanarDefectAtom, PlanarDefect> planarDefectMap = new TreeMap<PlanarDefectAtom, PlanarDefect>();
			
			this.planarDefects = PlanarDefect.createPlanarDefects(data, planarDefectMap);
		
			NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), meshingThreshold);
			for (PlanarDefectAtom a : planarDefectMap.keySet())
				nnb.add(a.getAtom());
			
			//Copy map
			HashMap<Atom, PlanarDefect> defectAtomMap = new HashMap<Atom, PlanarDefect>();
			for (Map.Entry<PlanarDefectAtom, PlanarDefect> e : planarDefectMap.entrySet()){
				defectAtomMap.put(e.getKey().getAtom(), e.getValue());
			}
			
			//Create links between stacking faults and dislocations
			for (Dislocation d : dislocations) d.findAdjacentStackingFaults(defectAtomMap, nnb);
			for (PlanarDefect s : planarDefects) s.findAdjacentDislocations(dislocations);
		}
		
		//Store dislocation junction information in the junction nodes
		for (Dislocation d : dislocations){
			d.getStartNode().addDislocation(d);
			d.getEndNode().addDislocation(d);
		}
		
		//Analyze the skeleton and find Burgers vectors
		//TODO: Extension required to identify Burgersvectors in poly-phase material 
		//with completely different crystal structures
		if(data.isRbvAvailable())
			new BurgersVectorAnalyzer(data.getCrystalStructure()).analyse(this);
		
		// Remove dislocation with length less than two nodes
		Iterator<Dislocation> disIter = dislocations.iterator();
		while(disIter.hasNext()){
			Dislocation d = disIter.next();
			if (d.getLine().length < 2) 
				disIter.remove();
		}
		
		for (SkeletonNode n : nodes)
			n.setNeighborsToNull();
		
		nodes = null;
	}
	
	private boolean contractMesh() {
		CyclicBarrier syncBarrier = new CyclicBarrier(ThreadPool.availProcessors());
		Vector<ContractionCallable> tasks = new Vector<ContractionCallable>();
		//Evaluate all new positions (in parallel)
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			int start = (int)((long)nodes.size() * i)/ThreadPool.availProcessors();
			int end = (int)((long)nodes.size() * (i+1))/ThreadPool.availProcessors();

			tasks.add(this.new ContractionCallable(start, end, nodes, syncBarrier));
		}
		
		List<Future<Integer>> results = ThreadPool.executeParallel(tasks);

		int nodesToMove = 0;
		//Retrieve sum of all moved nodes
		for (Future<Integer> c : results){
			try {
				nodesToMove += c.get();
			} catch (CancellationException ce){
				//do nothing, jobs have been stopped by interrupt
				return false;
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		//If less than 0.5% of all remaining nodes have moved, the mesh is considered as converged  
		return nodesToMove*200 > nodes.size();
	}
	
	private boolean mergeNodes(float mergeTol){
		mergeTol = mergeTol*mergeTol;
		boolean deleted = false;
		
		for (int j=0; j<nodes.size(); j++){
			SkeletonNode t = nodes.get(j);
			if (!t.isCriticalNode()){
				//Find the nearest neighbor for t with a common neighbor
				int nearestIndex = -1;
				float minDist = Float.POSITIVE_INFINITY;
				for (int i=0; i<t.getNeigh().size(); i++){
					SkeletonNode n = t.getNeigh().get(i); 
					if (!n.isCriticalNode()){
						float dist = data.getBox().getPbcCorrectedDirection(n, t).getLengthSqr(); 
						if (dist < minDist && t.hasCommonNeighbor(n)){
							minDist = dist;
							nearestIndex = i;
						}
					}
				}
				//If another atom is the merging tolerance range
				//merge the node into the other and delete it afterwards
				if (minDist<mergeTol){
					t.getNeigh().get(nearestIndex).mergeSkeletonNode(t);
					deleted = true;
					nodes.remove(j);
					//During deletion, the last element in the list is copied to the
					//position of the element to be deleted. To continue at the same place in the list
					//with the shifted order, decrease the index by one.
					j--;
				}
			}
		}
		return deleted;
	}
	
	/**
	 * Transform the set of nodes into sets of polylines as dislocations
	 */
	private void convertToDislocations() {
		ArrayList<Dislocation> dislocations = new ArrayList<Dislocation>();
		TreeSet<Edge> edges = new TreeSet<Edge>();
		
		for (SkeletonNode a : nodes){
			//Center the skeletonNode
			a.centerToMappedAtoms(data.getBox());
			//get a unique set of all edges
			for (int j = 0; j < a.getNeigh().size(); j++) {
				SkeletonNode b = a.getNeigh().get(j);
				edges.add(new Edge(a, b));
			}
		}

		while (!edges.isEmpty()) {
			Edge e = edges.first();
			//Find a node with more than two neighbors -> start of a polyline
			while (e!=null && e.a.getNeigh().size() == 2 && e.b.getNeigh().size() == 2) {
				e = edges.higher(e);
			}
			//if e is null there are only closed simple loops in the set left
			//take the first element in the set as a random start
			if (e==null){
				e = edges.first();
			}
			// Create the dislocation as a polyline
			ArrayList<SkeletonNode> polyline = new ArrayList<SkeletonNode>();
			SkeletonNode current = null, next = null, start = null;
			if (e.a.getNeigh().size() != 2) {
				current = e.a;
				start = e.a;
				next = e.b;
			} else {
				current = e.b;
				start = e.b;
				next = e.a;
			}
			polyline.add(current);
			edges.remove(e);
			
			//Traverse along the edges until another node with more than two neighbors is found
			while (next.getNeigh().size() == 2 && next!=start) {
				// Get the next edge
				SkeletonNode overnext = (current == next.getNeigh().get(0)) ? next.getNeigh().get(1) : next.getNeigh().get(0);
				current = next;
				next = overnext;
				edges.remove(new Edge(current, next));
				polyline.add(current);
			}
			//Create and store the polyline as a dislocation
			polyline.add(next);
			dislocations.add(new Dislocation(polyline.toArray(new SkeletonNode[polyline.size()]), this));
		}
		this.dislocations = dislocations;
	}
	
	/**
	 * 
	 * @param f
	 * @return true if the file is written without errors
	 * @throws FileNotFoundException
	 */
	public boolean writeDislocationSkeleton(File f) throws FileNotFoundException{
		PrintWriter pw = new PrintWriter(f);
		
		TreeMap<Integer, SkeletonNode> nodeMap = new TreeMap<Integer,SkeletonNode>();
		for (Dislocation d : dislocations)
			for (SkeletonNode n : d.getLine())
				nodeMap.put(n.getID(), n);
		
		pw.println("#total number of nodes");
		pw.println(nodeMap.size());
		pw.println("#number x y z");
		for (SkeletonNode n : nodeMap.values())
			pw.println(n.getID()+" "+n.x+" "+n.y+" "+n.z);
		
		pw.println("#total number of dislocations");
		pw.println(dislocations.size());
		pw.println("#number numberOfNodes n_1 n_2 ... n_n BV_x BV_y BV_z BV_identified");
		pw.println("#BV_identified: (y) if Burgers vector is identified, (n) if just a numerical average is known");
		for (Dislocation d : dislocations){
			pw.print(d.getID()+" "+d.getLine().length+" ");
			for (SkeletonNode n : d.getLine())
				pw.print(n.getID()+" ");
			if (d.getBurgersVectorInfo() != null){
				BurgersVectorInformation bvInfo = d.getBurgersVectorInfo();
				if (bvInfo.getBurgersVector().getType() == BurgersVector.BurgersVectorType.UNDEFINED) {
					CrystalRotationTools crt;
					if (data.isPolyCrystalline()) crt = d.getGrain().getCystalRotationTools();
					else crt = data.getCrystalRotation();
						
					Vec3 abv = crt.getInCrystalCoordinates(bvInfo.getAverageResultantBurgersVector());
					pw.println(String.format("%.4f %.4f %.4f n", abv.x, abv.y, abv.z));
					
				} else {
					Vec3 bv = bvInfo.getBurgersVector().getInCrystalCoordinates();
					pw.println(String.format("%.4f %.4f %.4f y", bv.x, bv.y, bv.z));
				}
			} else {
				pw.println("0.0 0.0 0.0 n");
			}
		}
		
		boolean error = pw.checkError();
		pw.close();
		return error;
	}
	
	/**
	 * 
	 * An edge between two points A and B that are tagged by different unique IDs.
	 * The class implements the equal method that treats edges A-B and B-A as equal.
	 * Provided that ID as unique for each point in a set of edges, the edges can be sorted
	 */
	public class Edge implements Comparable<Edge>{
		public SkeletonNode a, b;
		
		/**
		 * Create an edge between two
		 * @param a
		 * @param b
		 */
		public Edge(SkeletonNode a, SkeletonNode b){
			if (a.getID() == b.getID()) 
				throw new IllegalArgumentException("Needs two unique atoms");
			if (a.getID() < b.getID()){
				this.a = a;
				this.b = b;
			} else {
				this.b = a;
				this.a = b;
			}
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (!(obj instanceof Edge)) return false;
			Edge e = (Edge)obj;
			if (this.a.getID()==e.a.getID() && this.b.getID()==e.b.getID()) return true;
			else return false;
		}
		
		@Override
		public int compareTo(Edge o) {
			if (a.getID()<o.a.getID()) return 1;
			if (a.getID()>o.a.getID()) return -1;
			if (b.getID()<o.b.getID()) return 1;
			if (b.getID()>o.b.getID()) return -1;
			return 0;
		}
	}
	
	private class ContractionCallable implements Callable<Integer> {
		private int start, end;
		private List<SkeletonNode> nodes;
		private CyclicBarrier syncBarrier;
		
		
		public ContractionCallable(int start, int end, List<SkeletonNode> nodes, CyclicBarrier barrier) {
			this.start = start;
			this.end = end;
			this.nodes = nodes;
			this.syncBarrier = barrier;
		}

		@Override
		public Integer call() throws Exception {
			List<SkeletonNode> nodesMoved = new ArrayList<SkeletonNode>();
			List<Vec3> nodesMovedTo = new ArrayList<Vec3>();
			
			Vec3[] dirs = new Vec3[2 * SkeletonNode.MAX_MERGED_ATOMS];
			float[] dirsLenght = new float[2 * SkeletonNode.MAX_MERGED_ATOMS];
			
			for (int j=start; j<end; j++){
				if (Thread.interrupted()) return null;
				SkeletonNode a = nodes.get(j);
				if (!a.isCriticalNode()) {
					//Calculate the new position with an inverse distance weighting scheme
					//Do not contract towards critical nodes where the mesh is thinned out
					//to prevent singularities
					
					int dirsSize = 0;
					float sumLength = 0f;
					
					for (SkeletonNode n : a.getNeigh()) {
						if (!n.isCriticalNode()){
							Vec3 dir = data.getBox().getPbcCorrectedDirection(n, a);
							float l = 1f / dir.getLengthSqr();
							dirsLenght[dirsSize] = l;
							dirs[dirsSize++] = dir;
							sumLength += l;
						}
					}
					sumLength = 0.33f / sumLength;
					
					Vec3 move = new Vec3();
					for (int i = 0; i < dirsSize; i++) {
						Vec3 dir = dirs[i];
						float l = dirsLenght[i] * sumLength;
						move.add(dir.multiply(l));
					}

					//Store new coordinate if it differs significantly from the current  
					if (move.getLengthSqr()> 1e-6f) {
						nodesMoved.add(a);
						nodesMovedTo.add(move);
					}
				}
			}
		
			syncBarrier.await();
			
			for (int i=0; i<nodesMoved.size(); i++){
				SkeletonNode n = nodesMoved.get(i);
				//Move node
				n.add(nodesMovedTo.get(i));
				data.getBox().backInBox(n);
			}
			
			return nodesMoved.size();
		}
	}

	@Override
	public boolean isTransparenceRenderingRequired() {
		return Option.STACKING_FAULT.isEnabled();
	}

	@Override
	public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		if (Option.DISLOCATIONS.isEnabled())
			drawCores(viewer, gl, renderRange, picking, box);
	}

	@Override
	public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		if (Option.STACKING_FAULT.isEnabled())
			drawSurfaces(viewer, gl, renderRange, picking, box);
	}

	@Override
	public JDataPanel getDataControlPanel() {
		if (dataPanel == null)
			dataPanel = new JDislocationMenuPanel();
		return dataPanel;
	}
	
	private void drawSurfaces(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box){
		Shader shader = BuiltInShader.VERTEX_ARRAY_COLOR_UNIFORM.getShader();
		shader.enable(gl);
		int colorUniform = gl.glGetUniformLocation(shader.getProgram(), "Color");
		
		gl.glDisable(GL.GL_CULL_FACE);
		for (int i=0; i<this.getPlanarDefects().size(); i++) {
			PlanarDefect s = this.getPlanarDefects().get(i);
			
			VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, s.getFaces().length, 3, 0, 0, 0, 0, 0, 0, 0);
			int numElements = 0;
			vds.beginFillBuffer(gl);
			if (picking){
				float[] color = viewer.getNextPickingColor(s);
				gl.glUniform4f(colorUniform, color[0], color[1], color[2], color[3]);
			}
			else {
				if (data.getCrystalStructure().hasMultipleStackingFaultTypes()){
					float[] c = data.getCrystalStructure().getGLColor(s.getPlaneComposedOfType());
					if (viewer.getHighLightObjects().contains(s))
						gl.glUniform4f(colorUniform, c[0], c[1], c[2], 0.85f);
					else gl.glUniform4f(colorUniform, c[0], c[1], c[2], 0.35f);
				} else {
					if (viewer.getHighLightObjects().contains(s))
						gl.glUniform4f(colorUniform, 0.8f,0.0f,0.0f,0.35f);
					else {
						if (RenderOption.PRINTING_MODE.isEnabled()) gl.glUniform4f(colorUniform, 0.0f,0.0f,0.0f,0.35f);
						else gl.glUniform4f(colorUniform, 0.5f,0.5f,0.5f,0.35f);
					}
				}
			}
			
			for (int j = 0; j < s.getFaces().length; j+=3) {
				if (renderRange.isInInterval(s.getFaces()[j]) && 
						box.isVectorInPBC(s.getFaces()[j].subClone(s.getFaces()[j+1])) && 
						box.isVectorInPBC(s.getFaces()[j].subClone(s.getFaces()[j+2])) && 
						box.isVectorInPBC(s.getFaces()[j+1].subClone(s.getFaces()[j+2]))) {
					vds.setVertex(s.getFaces()[j].x, s.getFaces()[j].y, s.getFaces()[j].z);
					vds.setVertex(s.getFaces()[j+1].x, s.getFaces()[j+1].y, s.getFaces()[j+1].z);
					vds.setVertex(s.getFaces()[j+2].x, s.getFaces()[j+2].y, s.getFaces()[j+2].z);
					numElements += 3;
				}
			}
			
			vds.endFillBuffer(gl);
			vds.setNumElements(numElements);
			vds.draw(gl, GL.GL_TRIANGLES);
			vds.dispose(gl);
		}
		gl.glEnable(GL.GL_CULL_FACE);
	}
		
	private void drawCores(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		//Check for object to highlight
		if (!picking){
			int numEle = data.getCrystalStructure().getNumberOfElements();
			ArrayList<RenderableObject<Atom>> objectsToRender = new ArrayList<RenderableObject<Atom>>();
			ArrayList<Atom> atomsToRender = new ArrayList<Atom>();
			
			float[] sphereSize = data.getCrystalStructure().getSphereSizeScalings();
			float maxSphereSize = 0f;
			for (int i=0; i<sphereSize.length; i++){
				sphereSize[i] *= viewer.getSphereSize();
				if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
			}
			
			for (int i=0; i<this.getDislocations().size(); i++) {
				Dislocation dis = this.getDislocations().get(i);
				if (viewer.getHighLightObjects().contains(dis)) {
					for (int  j=0; j<dis.getLine().length; j++) {
						SkeletonNode sn = dis.getLine()[j];
						for (Atom a : sn.getMappedAtoms()){
							float[] color;
							if (j==0) color = new float[]{0f, 1f, 0f, 1f};
							else if (j==dis.getLine().length-1) color = new float[]{0f, 0f, 1f, 1f};
							else color = new float[]{1f, 0f, 0f, 1f};
							objectsToRender.add(new RenderableObject<Atom>(a, color, sphereSize[a.getElement()%numEle]));
							atomsToRender.add(a);
						}
					}
				}
			}
					
			ObjectRenderData<?> ord = new ObjectRenderData<Atom>(atomsToRender, false, box);
			ObjectRenderData<?>.Cell c = ord.getRenderableCells().get(0);
			for(int i=0; i<objectsToRender.size(); i++){
				c.getColorArray()[3*i+0] = objectsToRender.get(i).color[0];
				c.getColorArray()[3*i+1] = objectsToRender.get(i).color[1];
				c.getColorArray()[3*i+2] = objectsToRender.get(i).color[2];
				c.getSizeArray()[i] = objectsToRender.get(i).size;
				c.getVisibiltyArray()[i] = true;
			}
			ord.reinitUpdatedCells();
			viewer.drawSpheres(gl, ord, false);
			gl.glDisable(GL.GL_BLEND);
		}
		
		//Render the dislocation core elements
		Shader s = BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader();
		int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
		s.enable(gl);
		
		for (int i=0; i<this.getDislocations().size(); i++) {
			Dislocation dis = this.getDislocations().get(i);
			//Disable some dislocations if needed
			if (dis.getBurgersVectorInfo().getBurgersVector().getType() == BurgersVectorType.DONT_SHOW) continue;
			if (picking){
				float[] col = viewer.getNextPickingColor(dis);
				gl.glUniform4f(colorUniform, col[0], col[1], col[2], col[3]);
			}
			else if (dis.getBurgersVectorInfo()!=null) {
				float[] col = dis.getBurgersVectorInfo().getBurgersVector().getType().getColor();
				gl.glUniform4f(colorUniform, col[0], col[1], col[2], 1f);
			} else gl.glUniform4f(colorUniform, 0.5f, 0.5f, 0.5f, 1f);
			
			ArrayList<Vec3> path = new ArrayList<Vec3>();
			
			for (int j = 0; j < dis.getLine().length; j++) {
				SkeletonNode c = dis.getLine()[j];
				if (renderRange.isInInterval(c)) {
					path.add(c);
				} else {
					if (path.size()>1) 
						TubeRenderer.drawTube(gl, path, CORE_THICKNESS);
					path.clear();
				}
				//Check PBC and restart tube if necessary
				if (j<dis.getLine().length-1){
					SkeletonNode c1 = dis.getLine()[j+1];
					if (!box.isVectorInPBC(c.subClone(c1))){
						if (path.size()>1)
							TubeRenderer.drawTube(gl, path, CORE_THICKNESS);
						path.clear();
					}
				}
			}
			if (path.size()>1)
				TubeRenderer.drawTube(gl, path, CORE_THICKNESS);
		}
		
		//Draw Burgers vectors on cores
		if (!picking && Option.BURGERS_VECTORS_ON_CORES.isEnabled() && data.isRbvAvailable()){
			for (int i = 0; i < this.getDislocations().size(); i++) {
				Dislocation dis = this.getDislocations().get(i);
				
				Vec3 f;
				float[] col;
				if (dis.getBurgersVectorInfo().getBurgersVector().isFullyDefined()) {
					col = new float[]{ 0f, 1f, 0f, 1f};
					if (dis.getGrain() == null)
						f = dis.getBurgersVectorInfo().getBurgersVector().getInXYZ(data.getCrystalRotation());
					else
						f = dis.getBurgersVectorInfo().getBurgersVector().getInXYZ(dis.getGrain().getCystalRotationTools());
				} else {
					col = new float[]{1f, 0f, 0f, 1f};
					f = dis.getBurgersVectorInfo().getAverageResultantBurgersVector();
				}

				f.multiply(CORE_THICKNESS);
				SkeletonNode c = dis.getLine()[dis.getLine().length / 2]; // node "in the middle"
				if (renderRange.isInInterval(c))
					ArrowRenderer.renderArrow(gl, c, f, 0.4f, col, true);
			}
		}
	}
	
	public AtomData getAtomData(){
		return data;
	}
	
	public UniqueIDCounter getDislocationIDSource() {
		return dislocationIDSource;
	}
}