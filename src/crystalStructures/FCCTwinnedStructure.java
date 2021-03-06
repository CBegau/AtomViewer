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

package crystalStructures;

import gui.ProgressMonitor;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import common.*;
import model.*;
import model.polygrain.Grain;
import model.polygrain.grainDetection.AtomToGrainObject;
import model.polygrain.grainDetection.GrainDetectionCriteria;
import model.polygrain.grainDetection.GrainDetector;
import model.mesh.Mesh;

public class FCCTwinnedStructure extends FCCStructure {

	private static final float[][] ATOM_COLORS = {
		{1f, 0.5f, 0f},
		{0.2f, 0.4f, 1f},
		{1f, 0f, 0f}, 
		{1f, 0.4f, 0.7f}, 
		{1f, 1f, 0f}, 
		{0f, 1f, 0f}, 
		{0f, 1f, 1f}, 
		{0f, 1f, 0.5f},
		{1f, 1f, 1f}
	};
	
	@Override
	public float[][] getDefaultColors(){
		float[][] f = new float[ATOM_COLORS.length][3];
		for (int i=0; i< f.length;i++){
			f[i][0] = ATOM_COLORS[i][0];
			f[i][1] = ATOM_COLORS[i][1];
			f[i][2] = ATOM_COLORS[i][2];
		}
		
		return f;
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new FCCTwinnedStructure();
	}
	
	@Override
	protected String getIDName() {
		return "FCC_Twinned";
	}
	
	@Override
	public String getNameForType(int i) {
			switch (i) {
			case 0: return "bcc";
			case 1: return "fcc";
			case 2: return "hcp";
			case 3: return "twin";
			case 4: return "10-11 neighbors";
			case 5: return "defect";
			case 6: return "<10 neighbors";
			default: return "unknown";
		}
	}
	
	@Override
	public int getNumberOfTypes() {
		return 7;
	}
	
	@Override
	public void identifyDefectAtoms(List<Atom> atoms, NearestNeighborBuilder<Atom> nnb, 
			int start, int end, CyclicBarrier barrier) {
		for (int i=start; i<end; i++){
			if (Thread.interrupted()) return;
			
			if ((i-start)%10000 == 0)
				ProgressMonitor.getProgressMonitor().addToCounter(10000);
			
			Atom a = atoms.get(i);
			int type = identifyAtomType(a, nnb); 
			//Relabel
			if (type == 3 || type == 7) type = 5;
			a.setType(type);
		}
		
		ProgressMonitor.getProgressMonitor().addToCounter((end-start)%10000);
		try {
			barrier.await();
		} catch (Exception e) {
			if (Thread.interrupted()) return;
		}
		
		Vec3 v1 = null;
		Vec3 normal = null;
		
		for (int i=start; i<end; i++){
			if (Thread.interrupted()) return;
			Atom a = atoms.get(i);
			
			if (a.getType() == 2){
				int countHCP = 0;
				boolean twin = true;
				ArrayList<Tupel<Atom, Vec3>> neiAndVec = nnb.getNeighAndNeighVec(a);
				for (Tupel<Atom, Vec3> n : neiAndVec){
					if (n.o1.getType() == 2 || n.o1.getType() == 3){
						if (countHCP == 0) {
							countHCP++;
							v1 = n.o2;
						} else if (countHCP == 1) {
							countHCP++;
							normal = v1.createNormal(n.o2);
						} else {
							Vec3 d = n.o2;
							float dev = normal.dot(d);
							if (Math.abs(dev)>0.2) {
								twin = false;
								break;
							}
							countHCP++;
						}
						
					}
				}
				
				if (twin && countHCP >= 3) a.setType(3);
			}
		}
	}
	
	@Override
	public List<Grain> identifyGrains(final AtomData data, float meshSize) {
		if (!data.isGrainsImported()){
			List<Grain> grains = new Vector<Grain>();
			//Reset grain ID
			for (Atom a : data.getAtoms())
				a.setGrain(Atom.DEFAULT_GRAIN);
			
			final List<List<Atom>> grainSets = GrainDetector.identifyGrains(data.getAtoms(), 
					this.getGrainDetectionCriteria(), data.getBox());

			int id=0;
			for (List<Atom> g: grainSets){
				for (Atom a : g){
					a.setGrain(id);
				}
				id++;
			}
			
			final NearestNeighborBuilder<Atom> nnb = 
					new NearestNeighborBuilder<Atom>(data.getBox(),getNearestNeighborSearchRadius());
			
			for (int i=0; i<data.getAtoms().size();i++){
				if (data.getAtoms().get(i).getType() != 6){
					nnb.add(data.getAtoms().get(i));
				}
			}
			
			//Second step, find the most frequent grain assignment of neighbors, 
			//then assign this type to the previously unassigned atom
			Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
			final CyclicBarrier cb = new CyclicBarrier(ThreadPool.availProcessors());
			for (int i=0; i<ThreadPool.availProcessors(); i++){
				final int start = (int)(((long)data.getAtoms().size() * i)/ThreadPool.availProcessors());
				final int end = (int)(((long)data.getAtoms().size() * (i+1))/ThreadPool.availProcessors());
				
				parallelTasks.add(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						ArrayList<Tupel<Atom,Integer>> assigns = new ArrayList<Tupel<Atom, Integer>>();
						for (int j=start; j<end; j++){
							Atom a = data.getAtoms().get(j);
							if (a.getGrain() == Atom.DEFAULT_GRAIN && a.getType() != 6){
								ArrayList<Atom> nei = nnb.getNeigh(a);
								if (nei.size() != 0){
									int[] ng = new int[nei.size()];
									for (int i=0; i<nei.size();i++){
										ng[i] = nei.get(i).getGrain();
									}
									Arrays.sort(ng);
									int maxElement = 1; int maxIndex = ng[0];
									int currentElement = 1; int currentIndex = ng[0];
									
									for (int i=1; i<ng.length; i++){
										if (ng[i] == currentIndex){
											currentElement++;
										} else {
											if (currentElement > maxElement && currentIndex < Atom.DEFAULT_GRAIN) {
												maxElement = currentElement;
												maxIndex = currentIndex;
											}
											currentElement = 1;
											currentIndex = ng[i];
										}
									}
									
									if (maxIndex < Atom.DEFAULT_GRAIN){
										assigns.add(new Tupel<Atom, Integer>(a, maxIndex));
									}
								}
							}
						}
						
						cb.await();
						
						synchronized(grainSets){
							for (Tupel<Atom, Integer> a : assigns){
								a.o1.setGrain(a.o2);
								grainSets.get(a.o2).add(a.o1);
							}
						}
						
						return null;
					}
				});
			};
			ThreadPool.executeParallel(parallelTasks);
			
			
			CrystalStructure cs = this.getCrystalStructureOfDetectedGrains();
			int grainIndex = 0;
			for (List<Atom> s : grainSets){
				Mesh mesh = new Mesh(s, meshSize, cs.nearestNeighborSearchRadius, data.getBox());
				Grain g = new Grain(mesh, s, grainIndex++, cs, data.getBox());
				grains.add(g);
			}
			
			return grains;
		} else {
			return super.identifyGrains(data, meshSize);
		}
		
	}
	
	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		if (a.getGrain() == Atom.IGNORED_GRAIN) return false;
		
		int type = a.getType();
		if (type == 4 || type == 5) return true;
		return false;
	}
	
	@Override
	public List<Atom> getStackingFaultAtoms(AtomData data){
		List<Atom> sfAtoms = new ArrayList<Atom>();
		for (int i=0; i<data.getAtoms().size(); i++){
			Atom a = data.getAtoms().get(i);
			if ((a.getType() == 2 || a.getType() == 3) && a.getGrain() != Atom.IGNORED_GRAIN)
				sfAtoms.add(a);
		}
		return sfAtoms;
	}
	
	@Override
	public GrainDetectionCriteria getGrainDetectionCriteria() {
		return new TwinGrainDetectionCriteria(this);
	}
	
	public static class TwinGrainDetectionCriteria implements GrainDetectionCriteria {

		private CrystalStructure cs;
		private int surfaceType;
		private int defaultType;
		
		public TwinGrainDetectionCriteria(CrystalStructure cs){
			this.cs = cs;
			this.surfaceType = cs.getSurfaceType();
			this.defaultType = cs.getDefaultType();
		}
		
		@Override
		public float getNeighborDistance() {
			return cs.getNearestNeighborSearchRadius();
		}

		@Override
		public int getMinNumberOfAtoms() {
			return 20;
		}

		@Override
		public boolean includeAtom(Atom atom) {
			return atom.getType() != surfaceType;
		}

		@Override
		public boolean includeAtom(AtomToGrainObject atom, List<AtomToGrainObject> neighbors) {
			int count = 0;
			for (int j=0; j<neighbors.size(); j++){
				if (neighbors.get(j).getAtom().getType() == defaultType) count++;
			}
			
			return count>=10;
		}
		
		@Override
		public boolean acceptAsFirstAtomInGrain(Atom atom, List<AtomToGrainObject> neighbors) {
			return neighbors.size()>9;
		}

	}
}
