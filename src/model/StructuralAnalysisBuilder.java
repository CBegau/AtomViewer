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

package model;

import gui.ProgressMonitor;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import common.ThreadPool;
import crystalStructures.CrystalStructure;

public class StructuralAnalysisBuilder {
	private StructuralAnalysisBuilder(){}
	
	public static void performStructureAnalysis(AtomData atomData){
		NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(atomData.getBox(), 
				atomData.getCrystalStructure().getStructuralAnalysisSearchRadius(), true);
		List<Atom> atoms = atomData.getAtoms();
		
		nnb.addAll(atoms);
		
		Vector<AnalyseCallable> tasks = new Vector<AnalyseCallable>();
		StructuralAnalysisBuilder sab = new StructuralAnalysisBuilder();
		CyclicBarrier barrier = new CyclicBarrier(ThreadPool.availProcessors());
		
		ProgressMonitor.getProgressMonitor().start(atoms.size());
		
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			int start = (int)(((long)atoms.size() * i)/ThreadPool.availProcessors());
			int end = (int)(((long)atoms.size() * (i+1))/ThreadPool.availProcessors());
			tasks.add(sab.new AnalyseCallable(start, end, atoms, nnb, barrier, atomData.getCrystalStructure()));
		}
		
		ThreadPool.executeParallel(tasks);
		
		ProgressMonitor.getProgressMonitor().stop();
	}
	
	private class AnalyseCallable implements Callable<Void> {
		private int start, end;
		private List<Atom> atoms;
		private NearestNeighborBuilder<Atom> nnb;
		private CyclicBarrier barrier;
		private CrystalStructure cs;
		
		public AnalyseCallable(int start, int end, List<Atom> atoms, 
				NearestNeighborBuilder<Atom> nnb, CyclicBarrier barrier, CrystalStructure cs) {
			this.start = start;
			this.end = end;
			this.atoms = atoms;
			this.nnb = nnb;
			this.barrier = barrier;
			this.cs = cs;
		}

		@Override
		public Void call() throws Exception {
			cs.identifyDefectAtoms(atoms, nnb, start, end, barrier);
			return null;
		}
	}
}
