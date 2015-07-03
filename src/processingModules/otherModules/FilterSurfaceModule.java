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

package processingModules.otherModules;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.JPrimitiveVariablesPropertiesDialog.BooleanProperty;
import gui.JPrimitiveVariablesPropertiesDialog.IntegerProperty;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import javax.swing.JFrame;

import common.ThreadPool;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport()
public class FilterSurfaceModule extends ClonableProcessingModule {

	@ExportableValue
	private int cycles;
	@ExportableValue
	private int minSurfaceNeighbors;
	@ExportableValue
	private boolean halfSpaceAnalysis = true;
	
	public FilterSurfaceModule() {
		cycles = 1;             
		minSurfaceNeighbors = 2;
	}

	@Override
	public String getShortName() {
		return "Filter surface";
	}

	@Override
	public String getFunctionDescription() {
		return "Surface atoms can be identified as an atom where all neighbors are located approximately in a halfspace. "
				+ "Additionally, it is possible to mark iteratively all atoms which are neighboring to a number of surface "
				+ "atoms as being part of the surface as well. <br><br>"
				+ "<i>WARNING: This feature is not fully integrated. The result is not revertible</i>";
	}

	@Override
	public String getRequirementDescription() {
		return "Surface type must be specified";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[0];
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), 
				data.getCrystalStructure().getNearestNeighborSearchRadius(), true);
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size()*(cycles+(halfSpaceAnalysis?1:0)));
		
		nnb.addAll(data.getAtoms());

		final int surfaceType = data.getCrystalStructure().getSurfaceType();
		
		if (halfSpaceAnalysis)
			halfSpaceAnalysis(data, nnb);
		
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
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
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
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		
		for (int i=0; i<cycles;i++)
			ThreadPool.executeParallel(parallelTasks);
		
		data.countAtomTypes();
		
		ProgressMonitor.getProgressMonitor().stop();
		
		return null;
	}

	
	private void halfSpaceAnalysis(final AtomData data, final NearestNeighborBuilder<Atom> nnb) throws Exception {
		final int surfaceType = data.getCrystalStructure().getSurfaceType();
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					//parallel filtering of atoms in contact with the surface
					int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						Atom a = data.getAtoms().get(i);
						
						if (a.getType() != surfaceType){
							ArrayList<Vec3> neigh = nnb.getNeighVec(a);
								
							//Test if all atoms are located almost in a half-space of the center atom
							//Compute the sum of all neighbor vectors and negate 
							Vec3 con = new Vec3();
							for (Vec3 n : neigh)
								con.sub(n);
							//Normalize this vector --> this normal splits the volume into two half-spaces 
							con.normalize();
							boolean surface = true;
							//Test if all neighbors either on one side of the halfplane (dot product < 0) or only 
							//slightly off
							for (Vec3 n : neigh){
								if (con.dot(n.normalizeClone())>0.35f){
									surface = false;
									break;
								}
							}
							if (surface){
								a.setType(surfaceType);
								a.setRBV(null, null);
							}
						}
					}
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		
		ThreadPool.executeParallel(parallelTasks);
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Filter surface");
		dialog.addLabel(getFunctionDescription());
		
		BooleanProperty halfSpace = dialog.addBoolean("halfSpaceAnalysis", "Perform half-space analysis:"
				+ "atoms with all neighbors approximately located in half-space will be assigned as surface", 
				"If more neighbors are found, the farthest once are excluded", false);
		IntegerProperty minSurfaceNeigh = dialog.addInteger("minNeigh", "Minimum number of neighbors to mark an atom as surface as well.",
				"", 2, 0, 10000);
		IntegerProperty cycles = dialog.addInteger("cycles", "Number of cycles to mark atoms as surface",
				"", 1, 0, 100000);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.halfSpaceAnalysis = halfSpace.getValue();
			this.cycles = cycles.getValue();
			this.minSurfaceNeighbors = minSurfaceNeigh.getValue();
		}
		return ok;
	}
}
