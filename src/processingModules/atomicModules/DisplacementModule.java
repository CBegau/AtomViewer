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

import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JSeparator;

import common.ThreadPool;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

//TODO handle reference in Toolchain
@ToolchainSupport()
public class DisplacementModule extends ClonableProcessingModule {
	
	private static DataColumnInfo[] cci = { new DataColumnInfo("Displacement", "displ_x", ""),
			new DataColumnInfo("Displacement", "displ_y", ""),
			new DataColumnInfo("Displacement", "displ_z", ""),
			new DataColumnInfo("Displacement (length)" , "displ_abs" ,"")};
	
	static {
		//Define the vector components
		cci[0].setAsFirstVectorComponent(cci[1], cci[2], cci[3], "Displacement");
	}
	
	private AtomData referenceAtomData = null;
	
	
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
		
		JComboBox referenceComboBox = new JComboBox();
		AtomData d = data;
		while (d.getPrevious()!=null) d = d.getPrevious();
		
		do {
			referenceComboBox.addItem(d);
			d = d.getNext();
		} while (d!=null);
		
		dialog.addLabel("Select reference configuration");
		dialog.addComponent(referenceComboBox);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.referenceAtomData = (AtomData)referenceComboBox.getSelectedItem(); 
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
		
		if (data == referenceAtomData) return null;
		
		if (!data.getBox().getBoxSize()[0].equals(referenceAtomData.getBox().getBoxSize()[0]) || 
				!data.getBox().getBoxSize()[1].equals(referenceAtomData.getBox().getBoxSize()[1]) || 
				!data.getBox().getBoxSize()[2].equals(referenceAtomData.getBox().getBoxSize()[2])){
			JLogPanel.getJLogPanel().addLog(String.format("WARNING: Displacement vectors may be inaccurate. "
					+ "Box sizes of reference %s is different from %s", referenceAtomData.getName(), data.getName()));
		}
		
		if (data.getAtoms().size() != referenceAtomData.getAtoms().size()){ 
			JLogPanel.getJLogPanel().addLog(String.format("WARNING: Displacement vectors may be inaccurate. Number of atoms in %s and reference %s mismatch.", 
					data.getName(), referenceAtomData.getName()));
		}
		
		
		final HashMap<Integer, Atom> atomsMap = new HashMap<Integer, Atom>();
		for (Atom a : referenceAtomData.getAtoms())
			atomsMap.put(a.getNumber(), a);
		
		if (atomsMap.size() != referenceAtomData.getAtoms().size())
			throw new RuntimeException(
					String.format("Cannot compute displacement vectors: IDs of atoms in %s are non-unique.",
							referenceAtomData.getName()));
		
		final int dx = data.getIndexForCustomColumn(cci[0]);
		final int dy = data.getIndexForCustomColumn(cci[1]);
		final int dz = data.getIndexForCustomColumn(cci[2]);
		final int da = data.getIndexForCustomColumn(cci[3]);
		
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
							a.setData(displ.x, dx);
							a.setData(displ.y, dy);
							a.setData(displ.z, dz);
							a.setData(displ.getLength(), da);
						} else {
							if (!mismatchWarningShown.getAndSet(true)){
								JLogPanel.getJLogPanel().addLog(String.format("WARNING: Some displacement vectors are inaccurate. "
										+ "Some atoms could not be found in reference file %s.", referenceAtomData.getName()));
							}
							a.setData(0f, dx); a.setData(0f, dy); a.setData(0f, dz); a.setData(0f, da);
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
