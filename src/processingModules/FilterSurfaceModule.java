// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
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

package processingModules;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import common.ThreadPool;
import model.Atom;
import model.AtomData;
import model.Configuration;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;

public class FilterSurfaceModule implements ProcessingModule {

	private final int cycles;
	private final int minSurfaceNeighbors;
	
	public FilterSurfaceModule() {
		cycles = 1;             
		minSurfaceNeighbors = 2;
	}
	
	public FilterSurfaceModule(int cycles, int minSurfaceNeighbors) {
		this.cycles = cycles;
		this.minSurfaceNeighbors = minSurfaceNeighbors;
	}

	@Override
	public String getShortName() {
		return "Filter surface";
	}

	@Override
	public String getFunctionDescription() {
		return "Adds further layers tagged as surface to filter surface artifacts more efficient";
	}

	@Override
	public String getRequirementDescription() {
		return "Surface type must be specified";
	}

	@Override
	public boolean isApplicable() {
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public void process(final AtomData data) throws Exception {
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(
				Configuration.getCrystalStructure().getNearestNeighborSearchRadius());
		
		for (int i=0; i<data.getAtoms().size();i++)
			nnb.add(data.getAtoms().get(i));

		final int surfaceType = Configuration.getCrystalStructure().getSurfaceType();
		final CyclicBarrier cb = new CyclicBarrier(ThreadPool.availProcessors());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					//parallel filtering of atoms in contact with the surface
					ArrayList<Atom> filteredAtoms = new ArrayList<Atom>();
					int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++){
						Atom a = data.getAtoms().get(i);
						if (a.getType() != surfaceType){
							ArrayList<Atom> n = nnb.getNeigh(a);
							int count = 0;
							for (int j=0; j<n.size(); j++){	
								if (n.get(j).getType() == surfaceType) {
									count++;
								}
							}
							if (count>=minSurfaceNeighbors) filteredAtoms.add(a);
						}
					}
					//wait if all processes are finished with the identification of 
					//surface atoms, then relabel them and delete the RBV is applicable 
					cb.await();
					for (Atom a: filteredAtoms){
						a.setType(surfaceType);
						a.setRBV(null, null);
					}
					return null;
				}
			});
		}
		
		for (int i=0; i<cycles;i++)
			ThreadPool.executeParallel(parallelTasks);	
	}

}
