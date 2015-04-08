// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
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

import gui.ProgressMonitor;

import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JFrame;

import common.ThreadPool;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;

/**
 * An internal module to compute the norm of a vector
 * This should not be accessible by the user, since every vector is either defined during import or by
 * other processing modules. In each case the vector should be prepared accordingly 
 */
public class VectorNormModule implements ProcessingModule{
	private DataColumnInfo firstVectorComponent;	
	
	public VectorNormModule(DataColumnInfo firstVectorComponent) {
		assert(firstVectorComponent.isFirstVectorComponent());
		this.firstVectorComponent = firstVectorComponent;
	}
	
	@Override
	public String getShortName() {
		return "Compute norm of value-vector";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes the norm of a vectorial value.";
	}

	@Override
	public String getRequirementDescription() {
		return "A vectorial value per atom";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		//Will always be false, since it is not intended to be  by the user
		return false;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, final AtomData data) {
		//Will always be false, since it is not intended to be  by the user
		return false;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{null};
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final int colV1 = data.getIndexForCustomColumn(firstVectorComponent.getVectorComponents()[0]);
		final int colV2 = data.getIndexForCustomColumn(firstVectorComponent.getVectorComponents()[1]);
		final int colV3 = data.getIndexForCustomColumn(firstVectorComponent.getVectorComponents()[2]);
		final int colN = data.getIndexForCustomColumn(firstVectorComponent.getVectorComponents()[3]);
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						if ((i-start)%10000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(10000);
						
						Atom a = data.getAtoms().get(i);
						float n = (float)Math.sqrt(a.getData(colV1)*a.getData(colV1)
								                  +a.getData(colV2)*a.getData(colV2)
								                  +a.getData(colV3)*a.getData(colV3)); 
						
						a.setData(n, colN);
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%10000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		
		return null;
	}

}
