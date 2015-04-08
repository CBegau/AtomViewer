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

package model.polygrain;

import java.awt.event.InputEvent;
import java.util.*;
import java.util.concurrent.*;

import common.ThreadPool;
import common.Tupel;
import common.Vec3;
import crystalStructures.CrystalStructure;
import model.*;
import model.polygrain.mesh.*;

public class Grain implements Pickable{

	private Mesh mesh;
	private CrystalStructure cs;
	private CrystalRotationTools rotTools;
	private List<Atom> atomsInGrain;
	private int grainNumber, numAtoms;
	private Future<Void> f;
	
	public Grain(Mesh mesh, List<Atom> atomsInGrain, int grainNumber, CrystalStructure cs) {
		this.cs = cs;
		this.atomsInGrain = atomsInGrain;
		this.mesh = mesh;
		this.grainNumber = grainNumber;
		this.numAtoms = atomsInGrain.size();
		this.rotTools = new CrystalRotationTools(cs, identifyGrainRotation(atomsInGrain));
		
		for (Atom a : atomsInGrain)
			a.setGrain(grainNumber);
		
		this.f = ThreadPool.submit(mesh);
	}
	
	public Grain(Mesh mesh, List<Atom> atomsInGrain, int grainNumber, CrystalStructure cs, Vec3[] latticeRotation) {
		this.cs = cs;
		this.atomsInGrain = atomsInGrain;
		this.grainNumber = grainNumber;
		this.mesh = mesh;
		this.grainNumber = grainNumber;
		this.rotTools = new CrystalRotationTools(cs, latticeRotation);
		
		for (Atom a : atomsInGrain)
			a.setGrain(grainNumber);
		
		this.f = ThreadPool.submit(mesh);
	}
	
	
	public static void processGrains(final AtomData data){
		if (Configuration.getCrystalStructure().getGrainBoundaryFilterDistance()==0f || 
				data.isMeshImported()) return; 
		
		//The BSPTree implementation is often slower
		final float threshold = Configuration.getCrystalStructure().getGrainBoundaryFilterDistance();
		final ClosestTriangleSearchAlgorithm ctsa = new ClosestTriangleSearch(threshold);
//		final ClosestTriangleSearchAlgorithm ctsa = new BSPTree(threshold);
		
		ArrayList<Triangle> trias = new ArrayList<Triangle>();
		for (Grain g : data.getGrains()){
//			if (g.getGrainNumber() !=0)
			
			for (Triangle t: g.getMesh().getTriangleSet()){
				trias.add(t);
			}
			g.getMesh().finalizeMesh();
		}
		
		Collections.shuffle(trias);
		for (Triangle t : trias){
			ctsa.add(t);
		}
		
//		final int defaultType = Configuration.getCrystalStructure().getDefaultType();
//		final int surfaceType = Configuration.getCrystalStructure().getSurfaceType();
		final float nnbDist = Configuration.getCrystalStructure().getNearestNeighborSearchRadius();
		
		final ArrayList<AtomMeshDistanceHelper> atomsToTest = new ArrayList<AtomMeshDistanceHelper>();
		
		final NearestNeighborBuilder<AtomMeshDistanceHelper> nnb =
				new NearestNeighborBuilder<Grain.AtomMeshDistanceHelper>(nnbDist);
		
		for (Atom a : data.getAtoms()){
//			if (a.getType() != defaultType && a.getType() != surfaceType && a.getGrain() != Atom.DEFAULT_GRAIN){
			if (a.getGrain() != Atom.DEFAULT_GRAIN && a.getGrain() != Atom.IGNORED_GRAIN){
				AtomMeshDistanceHelper amdh = new AtomMeshDistanceHelper(a);
				atomsToTest.add(amdh);
				nnb.add(amdh);
			}
		}
				
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)(((long)atomsToTest.size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)atomsToTest.size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						AtomMeshDistanceHelper a = atomsToTest.get(i);
						if (Thread.interrupted()) return null;
						
						ArrayList<Tupel<AtomMeshDistanceHelper, Vec3>> nn = nnb.getNeighAndNeighVec(a);
						
						//A (positive) distance has been set before
						if (!Float.isInfinite(a.distance) && a.distance >= threshold){
							//Update positions of neighbors as well
							for (Tupel<AtomMeshDistanceHelper, Vec3> t : nn){
								AtomMeshDistanceHelper n = t.o1;
								float minDistToMesh = a.distance-t.o2.getLength(); 
								if (minDistToMesh>threshold)
									n.distance = minDistToMesh;
							}
						} else if (a.distance+nnbDist < threshold){
							data.getGrains(a.atom.getGrain()).decreaseAtomCount();
							a.atom.setGrain(Atom.IGNORED_GRAIN);
						} else {
							float distanceToMesh = ctsa.sqrDistToMeshElement(a).o1;
							
							distanceToMesh=(float)Math.sqrt(distanceToMesh);
							if (distanceToMesh < threshold){
								data.getGrains(a.atom.getGrain()).decreaseAtomCount();
								a.atom.setGrain(Atom.IGNORED_GRAIN);
								a.distance = distanceToMesh;
							} 

							//The atom is so far away from the mesh that
							//all neighbors cannot be within the threshold
							//update their minimum distances, to avoid computing these atoms if possible
							for (Tupel<AtomMeshDistanceHelper, Vec3> t : nn){
								float dist = t.o2.getLength();
								AtomMeshDistanceHelper n = t.o1;
								float minDistToMesh = distanceToMesh-dist; 
								if (minDistToMesh>threshold)
									n.distance = minDistToMesh;
							}

						}
					}
					
					return null;
				}
			});
		};
		ThreadPool.executeParallel(parallelTasks);
	}
	
	public synchronized Mesh getMesh() {
		if (f!=null){
			try {
				f.get();
				f = null;
			} catch (InterruptedException e) {
				return null;
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		return mesh;
	}
	
	public int getGrainNumber() {
		return grainNumber;
	}
	
	public CrystalStructure getCrystalStructure() {
		return cs;
	}
	
	public CrystalRotationTools getCystalRotationTools() {
		return rotTools;
	}	
	
	@Override
	public Collection<?> getHighlightedObjects() {
		return null;
	}
	
	@Override
	public boolean isHighlightable() {
		return false;
	}
	
	/**
	 * Elements in this list are to be deleted once it is not needed anymore
	 * @return
	 */
	public List<Atom> getAtomsInGrain() {
		return atomsInGrain;
	}
	
	/**
	 * Return the number of atoms in the grain
	 * @return
	 */
	public int getNumberOfAtoms() {
		return numAtoms;
	}
	
	/**
	 * Sets number of atoms in the grain, should be used if the grain/mesh is read from
	 * a file and the original number of atoms cannot be reproduced
	 * @param numAtoms
	 */
	public void setNumberOfAtoms(int numAtoms) {
		this.numAtoms = numAtoms;
	}
	
	/**
	 * Assign a new grain number, only effective is CrystalStructure.orderBySize() is true
	 * @param i
	 * @return
	 */
	public void renumberGrain(int i){
		if (Configuration.getCrystalStructure().orderGrainsBySize())
			grainNumber = i;
	}

	/**
	 * Decreases the atom count by one, should be called if an atom's grain associated is removed elsewhere
	 */
	public synchronized void decreaseAtomCount(){
		numAtoms--;
	}
	
	@Override
	public String toString() {
		float[][] o = rotTools.getDefaultRotationMatrix();
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Grain (%d) Volume: %.4f Surface: %.4f Atoms: %d\n", 
				this.grainNumber, mesh.getVolume(), mesh.getArea(), this.numAtoms));
		sb.append(String.format("(%.3f, %.3f, %.3f)\n", o[0][0], o[0][1], o[0][2]));
		sb.append(String.format("(%.3f, %.3f, %.3f)\n", o[1][0], o[1][1], o[1][2]));
		sb.append(String.format("(%.3f, %.3f, %.3f)\n", o[2][0], o[2][1], o[2][2]));
		return sb.toString();
	}
	
	private Vec3[] identifyGrainRotation(List<Atom> atoms){
		//find an perfect lattice site atom surrounded by only perfect lattice site atoms
		//This will be the reference orientation for the whole grain.
		int defaultType = cs.getDefaultType();
		
		if (atoms == null || atoms.size()<cs.getNumberOfNearestNeighbors())
			return Configuration.getCrystalOrientation();
		
		NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>( 
				cs.getNearestNeighborSearchRadius());
		for (Atom a : atoms) {
			if (a.getType() == defaultType) nnb.add(a);
		}
		
		Vec3[] rot = null;
		for (Atom a : atoms) {
			if (a.getType() == defaultType) {
				// Identify the local grain rotation
				rot = this.cs.identifyRotation(a, nnb);
			}
			if (rot != null) break;
		}
		
		//if rot is null, then the grain is extremely small or so distorted that no orientation can be identified
		//The default orientation is used in this case
		if (rot == null){
			rot = Configuration.getCrystalOrientation();	
		} 
		
		return rot;
	}
	
	@Override
	public String printMessage(InputEvent ev) {
		return toString();
	}
	
	private static class AtomMeshDistanceHelper extends Vec3 {
		Atom atom;
		float distance;
		
		public AtomMeshDistanceHelper(Atom a) {
			this.setTo(a);
			this.atom = a;
			this.distance = Float.POSITIVE_INFINITY;	//Unknown value
		}
		
	}
}
