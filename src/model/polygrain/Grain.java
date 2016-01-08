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

import common.CommonUtils;
import common.ThreadPool;
import common.Tupel;
import common.Vec3;
import crystalStructures.CrystalStructure;
import model.*;
import model.mesh.*;

public class Grain implements Pickable{

	private Mesh mesh;
	private CrystalStructure cs;
	private CrystalRotationTools rotTools;
	private List<Atom> atomsInGrain;
	private int grainNumber, numAtoms;
	private Future<Void> f;
	
	public Grain(Mesh mesh, List<Atom> atomsInGrain, int grainNumber, CrystalStructure cs, BoxParameter box) {
		this.cs = cs;
		this.atomsInGrain = atomsInGrain;
		this.mesh = mesh;
		this.grainNumber = grainNumber;
		this.numAtoms = atomsInGrain.size();
		
		Vec3[] rotation = identifyGrainRotation(atomsInGrain, box);
		if (rotation == null){
			rotation = new Vec3[3];
			rotation[0] = new Vec3(1f, 0f, 0f);
			rotation[1] = new Vec3(0f, 1f, 0f);
			rotation[2] = new Vec3(0f, 0f, 1f);
		}
		
		this.rotTools = new CrystalRotationTools(cs, rotation);
		
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
	
	
	public static void processGrains(final AtomData data, float filterDistance){
		if (filterDistance==0f || data.isGrainsImported()) return; 
		
		//The BSPTree implementation is often slower
		final float threshold = filterDistance;
		final ClosestTriangleSearchAlgorithm<Atom> ctsa = 
//				new ClosestTriangleSearchBruteForce<Atom>(threshold, data.getBox(), data.getCrystalStructure().getDistanceToNearestNeighbor());		 
				new GridRasterTriangleSearch<Atom>(threshold, data.getBox());
		
		
		ArrayList<FinalizedTriangle> trias = new ArrayList<FinalizedTriangle>();
		for (Grain g : data.getGrains()){
			trias.addAll(g.getMesh().getTriangles());
		}
		
		Collections.shuffle(trias);
		for (FinalizedTriangle t : trias)
			ctsa.add(t);
		
		List<Atom> closeToMesh = ctsa.getElementsWithinThreshold(data.getAtoms());
		
		for (Atom a : closeToMesh){
			if (a.getGrain() != Atom.DEFAULT_GRAIN && a.getGrain() != Atom.IGNORED_GRAIN){
				data.getGrains(a.getGrain()).decreaseAtomCount();
				a.setGrain(Atom.IGNORED_GRAIN);
				data.getRbvStorage().addRBV(a, null, null);
			}
		}
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
	 * Returns atoms in the grains
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
	 * Assign a new grain number
	 * @param i
	 * @return
	 */
	public void renumberGrain(int i){
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
		
		String orientation = String.format("%.3f, %.3f, %.3f <br> %.3f, %.3f, %.3f <br> %.3f, %.3f, %.3f"
				, o[0][0], o[1][0], o[2][0], o[0][1], o[1][1], o[2][1], o[0][2], o[1][2], o[2][2]);
		
		String[] keys = {"Grain", "Volume", "Surface", "Atoms", "Orientation"};
		String[] values = {
				Integer.toString(grainNumber),
				Double.toString(mesh.getVolume()),
				Double.toString(mesh.getArea()),
				Integer.toString(numAtoms),
				orientation
				};
		
		return CommonUtils.buildHTMLTableForKeyValue(keys, values);
	}
	
	private Vec3[] identifyGrainRotation(List<Atom> atoms, BoxParameter box){
		//find an perfect lattice site atom surrounded by only perfect lattice site atoms
		//This will be the reference orientation for the whole grain.
		int defaultNeigh = cs.getNumberOfNearestNeighbors();
		
		final int samplingSize = 20;
		
		if (atoms == null || atoms.size()<cs.getNumberOfNearestNeighbors())
			return null;
		
		NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(
				box, cs.getNearestNeighborSearchRadius(), true);
		nnb.addAll(atoms);
		
		Random r = new Random(42);
		
		int samples = 0; int trials = 0;
		Vec3[][] rots = new Vec3[samplingSize][];
		List<List<Vec3>> neighs = new ArrayList<List<Vec3>>();
		
		//Gather a few crystal rotations at random sampling sites in the grain
		//Due to crystal symmetries, it is likely that the matrices differ numerically significantly
		//although they might be crystallographic equivalents
		while (trials++ < 5*samplingSize && samples < samplingSize){
			Atom a = atoms.get(r.nextInt(atoms.size()));
			if (nnb.getNeigh(a).size() == defaultNeigh) {
				Vec3[] r1 = this.cs.identifyRotation(a, nnb);
				if (r1 != null){
					rots[samples++] = r1;
					neighs.add(nnb.getNeighVec(a));
				}
			}
		}
		
		//Not a single fitting configuration found, skip
		if (samples == 0)
			return null;
		
		//Default parameters for the grain
		Vec3[] neighPerf = cs.getPerfectNearestNeighborsUnrotated();
		float lc = cs.getLatticeConstant();
		
		//From the matrices found, select the one that overall minimizes the
		//deviation for all configurations
		int bestConfig = 0;
		float bestFit = Float.POSITIVE_INFINITY;
		for (int i=0; i<samples; i++){
			//Rotate the default neighbors into the best fit for sampling site i
			Vec3[] neighRot = new Vec3[neighPerf.length];  
			Vec3[] rot = rots[i];
			
			for (int k=0; k<neighPerf.length;k++){
				Vec3 n = new Vec3();
				n.x = (neighPerf[k].x * rot[0].x + neighPerf[k].y * rot[0].y + neighPerf[k].z * rot[0].z) * lc; 
				n.y = (neighPerf[k].x * rot[1].x + neighPerf[k].y * rot[1].y + neighPerf[k].z * rot[1].z) * lc;
				n.z = (neighPerf[k].x * rot[2].x + neighPerf[k].y * rot[2].y + neighPerf[k].z * rot[2].z) * lc;
				neighRot[k] = n;
			}
			
			//Estimate how well the orientation fits overall by computing the deviations
			//between atom positions in all selected configurations
			float fit = 0f;
			for (int j = 0; j<samples; j++){
				float bestBond = Float.POSITIVE_INFINITY;
				for (Vec3 v : neighRot){
					for (Vec3 v2 : neighs.get(j)){
						if (v.getDistTo(v2) < bestBond)
							bestBond = v.getDistTo(v2);
					}
					fit += bestBond;
				}
			}
			//Select the one that fits best as the grain orientation
			if (fit<bestFit){
				bestFit = fit;
				bestConfig = i;
			}
			
		}		
		
		return rots[bestConfig];
	}
	
	@Override
	public Tupel<String,String> printMessage(InputEvent ev, AtomData data) {
		return new Tupel<String,String>("Grain",toString());
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		if(mesh == null) return null;
		return mesh.getCenterOfObject();
	}
}
