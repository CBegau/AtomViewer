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
package processingModules.atomicModules;

import gui.JLogPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.ReferenceModeProperty;

import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import common.ThreadPool;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchain.ReferenceMode;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport()
public class DisplacementModule extends ClonableProcessingModule {
	
	private static DataColumnInfo[] cci = { new DataColumnInfo("Displacement", "displ_x", ""),
			new DataColumnInfo("Displacement", "displ_y", ""),
			new DataColumnInfo("Displacement", "displ_z", ""),
			new DataColumnInfo("Displacement (length)" , "displ_abs" ,"")};
	
	@ExportableValue
	private ReferenceMode referenceMode = ReferenceMode.FIRST;
	
	static {
		//Define the vector components
		cci[0].setAsFirstVectorComponent(cci[1], cci[2], cci[3], "Displacement");
	}
	
	@Override
	public String getShortName() {
		return "Displacement";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes the displacement vector per atom in comparison to a reference configuration.";
	}

	@Override
	public String getRequirementDescription() {
		return "Atoms in the reference configuration must have the same ID"
				+ " as in the file the displacement is to be computed";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return (data.getNext() != null || data.getPrevious() != null);
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, getShortName());
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		
		ReferenceModeProperty rp = dialog.addReferenceMode("referenceMode", 
				"Select reference configuration", referenceMode);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.referenceMode = rp.getValue();
			if (this.referenceMode == ReferenceMode.REF)
				rp.getReferenceAtomData().setAsReferenceForProcessingModule(); 
		}
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return cci;
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final AtomicBoolean mismatchWarningShown = new AtomicBoolean(false);
		final AtomData referenceAtomData = Toolchain.getReferenceData(data, referenceMode);
		if (data == referenceAtomData) return null;
		
		if (!data.getBox().equals(referenceAtomData.getBox())){
			JLogPanel.getJLogPanel().addWarning("Inaccurate displacement vectors",  
					String.format("Box sizes of reference %s is different from %s."
							+ " Displacement vectors may be inaccurate.", referenceAtomData.getName(), data.getName()));
		}
		
		if (data.getAtoms().size() != referenceAtomData.getAtoms().size()){ 
			JLogPanel.getJLogPanel().addWarning("Inaccurate displacement vectors", 
					String.format("The number of atoms in %s and reference %s mismatch."
							+ "Computed displacements between these file may be inaccurate", 
					data.getName(), referenceAtomData.getName()));
		}
		
		
		final HashMap<Integer, Atom> atomsMap = new HashMap<Integer, Atom>();
		for (Atom a : referenceAtomData.getAtoms())
			atomsMap.put(a.getNumber(), a);
		
		if (atomsMap.size() != referenceAtomData.getAtoms().size()){
			String errorMessage = String.format("IDs of atoms in %s are non-unique", referenceAtomData.getName());
			JLogPanel.getJLogPanel().addError("IDs of atoms in are non-unique",
					String.format("Cannot compute displacement vectors from %s", referenceAtomData.getName()));
			throw new RuntimeException(errorMessage);
		}
		
		final float[] xArray = data.getDataArray(data.getDataColumnIndex(cci[0])).getData();
		final float[] yArray = data.getDataArray(data.getDataColumnIndex(cci[1])).getData();
		final float[] zArray = data.getDataArray(data.getDataColumnIndex(cci[2])).getData();
		final float[] nArray = data.getDataArray(data.getDataColumnIndex(cci[3])).getData();
		
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
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						Atom a_ref = atomsMap.get(a.getNumber());
						if (a_ref!=null){
							Vec3 displ = data.getBox().getPbcCorrectedDirection(a_ref, a);
							xArray[i] = displ.x;
							yArray[i] = displ.y;
							zArray[i] = displ.z;
							nArray[i] = displ.getLength();
						} else {
							if (!mismatchWarningShown.getAndSet(true)){
								JLogPanel.getJLogPanel().addWarning("Inaccurate displacement vectors", 
										String.format("Atom IDs in %s could not be matched to the reference %s."
												+ "Computed  displacement vectors between these file may be inaccurate", 
										data.getName(), referenceAtomData.getName()));
							}
							xArray[i] = 0f; yArray[i] = 0f; zArray[i] = 0f; nArray[i] = 0f;
						}
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		
		return null;
	}

}
