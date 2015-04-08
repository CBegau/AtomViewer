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
import model.Configuration;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import common.ThreadPool;


public class SpatialAveragingModule implements ProcessingModule {

	private DataColumnInfo toAverageColumn;
	private DataColumnInfo averageColumn;
	
	public SpatialAveragingModule(DataColumnInfo toAverage) {
		this.toAverageColumn = toAverage;
	}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		String name = toAverageColumn.getName()+"(av.)"; 
		String id = toAverageColumn.getId()+"_av";
		DataColumnInfo cci = new DataColumnInfo(name, id,
				toAverageColumn.getUnit(), toAverageColumn.getScalingFactor());
		
		this.averageColumn = cci;
		return new DataColumnInfo[]{cci};
	}
	
	@Override
	public String getShortName() {
		return "Spatial averaging "+toAverageColumn.getName();
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the average value in a spherical volume around each atom";
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
		float averageDistance = toAverageColumn.getSpatiallyAveragingRadius();
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(averageDistance);
		
		final int v = toAverageColumn.getColumn();
		final int av = averageColumn.getColumn();
		
		Configuration.currentFileLoader.getProgressMonitor().start(data.getAtoms().size());
		
		//Parallel add
		for (int i=0; i<data.getAtoms().size(); i++){
			nnb.add(data.getAtoms().get(i));
		}
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					float temp = 0f;
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							Configuration.currentFileLoader.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						temp = a.getData(v);
						ArrayList<Atom> neigh = nnb.getNeigh(a);
						for (Atom n : neigh)
							temp += n.getData(v);
						temp /= neigh.size()+1;
						a.setData(temp, av);
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
