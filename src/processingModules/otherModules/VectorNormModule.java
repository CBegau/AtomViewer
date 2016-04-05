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
package processingModules.otherModules;

import gui.ProgressMonitor;

import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JFrame;

import common.ThreadPool;
import model.AtomData;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;

/**
 * An internal module to compute the norm of a vector
 * This should not be accessible by the user, since every vector is either defined during import or by
 * other processing modules. In each case the vector should be prepared accordingly 
 */
public class VectorNormModule extends ClonableProcessingModule {
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
		//Will always be false, since it is not intended to be visible to the user
		return false;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, final AtomData data) {
		//Will always be false, since it is not intended to be visible to the user
		return false;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{null};
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final int indexX = data.getDataColumnIndex(firstVectorComponent.getVectorComponents()[0]);
		final int indexY = data.getDataColumnIndex(firstVectorComponent.getVectorComponents()[1]);
		final int indexZ = data.getDataColumnIndex(firstVectorComponent.getVectorComponents()[2]);
		final int indexNorm = data.getDataColumnIndex(firstVectorComponent.getVectorComponents()[3]);
		
		final float[] arrayX = data.getDataArray(indexX).getData();
		final float[] arrayY = data.getDataArray(indexY).getData();
		final float[] arrayZ = data.getDataArray(indexZ).getData();
		final float[] arrayNorm = data.getDataArray(indexNorm).getData();
		
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
						if ((i-start)%10000 == 0) ProgressMonitor.getProgressMonitor().addToCounter(10000);
						arrayNorm[i] = (float)Math.sqrt(arrayX[i]*arrayX[i] + arrayY[i]*arrayY[i] + arrayZ[i]*arrayZ[i]);
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
