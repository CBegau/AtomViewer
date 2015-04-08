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

import model.Atom;
import model.AtomData;
import model.AtomFilter;
import model.Configuration;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import common.ThreadPool;
import common.Vec3;


public class CentroSymmetryModule implements ProcessingModule {

	private DataColumnInfo centroSymmetryColumn;
	private AtomFilter filter;
	
	public CentroSymmetryModule(AtomFilter filter) {
		this.filter = filter;
	}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		String name = "Centrosymmetry"; 
		String id = "CSD";
		DataColumnInfo cci = new DataColumnInfo(name, id,"", 1f);
		
		this.centroSymmetryColumn = cci;
		return new DataColumnInfo[]{cci};
	}
	
	@Override
	public String getShortName() {
		return "Centrosymmetry deviation";
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the centrosymmetry deviation per atom";
	}
	
	@Override
	public String getRequirementDescription() {
		return "";
	}
	
	@Override
	public boolean isApplicable() {
		return true;
	}

	@Override
	public void process(final AtomData data) throws Exception {
		final float averageDistance = Configuration.getCrystalStructure().getNearestNeighborSearchRadius();
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(averageDistance, false);
		
		final int v = centroSymmetryColumn.getColumn();
		
		Configuration.currentFileLoader.getProgressMonitor().start(data.getAtoms().size());
		
		if (filter == null){
			//Parallel add
			for (int i=0; i<data.getAtoms().size(); i++){
				nnb.add(data.getAtoms().get(i));
			}
		} else {
			for (int i=0; i<data.getAtoms().size(); i++){
				Atom a = data.getAtoms().get(i);
				if (filter.accept(a)) nnb.add(a);
			}
		}
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							Configuration.currentFileLoader.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						if (filter != null && !filter.accept(a)) continue;

						float csd = 0f;
						
						ArrayList<Vec3> neigh = nnb.getNeighVec(a);
						
						boolean[] paired = new boolean[neigh.size()];
						for (int j=0; j<neigh.size(); j++){
							
							if (!paired[j]){
								Vec3 inv = neigh.get(j).multiplyClone(-1f);
								int minIndex = j;
								float minDistance = 4*averageDistance*averageDistance;
								for (int k=j+1; k<neigh.size(); k++){
									float d = inv.getSqrDistTo(neigh.get(k));
									if (d<minDistance) {
										minIndex = k;
										minDistance = d;
									}
								}
								
								csd += minDistance;
								paired[minIndex] = true;
							}
						}
						
						csd /= averageDistance;
						a.setData(csd, v);
					}
					
					Configuration.currentFileLoader.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		Configuration.currentFileLoader.getProgressMonitor().stop();
	};
}
