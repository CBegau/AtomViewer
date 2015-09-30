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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JSeparator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import common.ThreadPool;
import common.CommonUtils.KahanSum;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchainable;
import processingModules.toolchain.Toolchain.ReferenceData;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

//TODO handle reference in Toolchain
@ToolchainSupport()
public class DeltaValueModule extends ClonableProcessingModule implements Toolchainable{
	
	private static HashMap<DataColumnInfo, DataColumnInfo> existingDeltaColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	@ExportableValue
	private int referenceMode = 0;
	
	private DataColumnInfo toDeltaColumn;
	//This is the indicator used for import from a toolchain, since the column
	//the file is referring to might not exist at that moment 
	private String toDeltaID;
	
	@Override
	public String getShortName() {
		return "Compute difference for values";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes the difference for a selected data value to a reference file.";
	}

	@Override
	public String getRequirementDescription() {
		return "Atoms in the reference configuration must have the same ID"
				+ " as in the file the difference is to be computed";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		//Identify the column by its ID if imported from a toolchain
		if (toDeltaColumn == null && toDeltaID != null){
			for (DataColumnInfo d : data.getDataColumnInfos()){
				if (d.getId().equals(toDeltaID)){
					this.toDeltaColumn = d;
				}
			}
		}
		
		return (data.getNext() != null || data.getPrevious() != null);
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, final AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, getShortName());
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		
		final JComboBox referenceComboBox = new JComboBox();
		AtomData d = data;
		while (d.getPrevious()!=null) d = d.getPrevious();
		
		do {
			referenceComboBox.addItem(d);
			d = d.getNext();
		} while (d!=null);
		
		dialog.addLabel("Select reference configuration");
		dialog.addComponent(referenceComboBox);
		
		final JComboBox dataComboBox = new JComboBox();
		
		List<DataColumnInfo> common = new ArrayList<DataColumnInfo>(data.getDataColumnInfos());
		common.retainAll(((AtomData)referenceComboBox.getSelectedItem()).getDataColumnInfos());
		for (DataColumnInfo cci : common)
			dataComboBox.addItem(cci);
		
		referenceComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dataComboBox.removeAllItems();
				List<DataColumnInfo> common = new ArrayList<DataColumnInfo>(data.getDataColumnInfos());
				common.retainAll(((AtomData)referenceComboBox.getSelectedItem()).getDataColumnInfos());
				
				for (DataColumnInfo cci : common)
					dataComboBox.addItem(cci);
				dataComboBox.revalidate();
			}
		});
		
		dialog.addLabel("Select data value common in both files");
		dialog.addComponent(dataComboBox);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.referenceMode = ReferenceData.REF.getID();
			((AtomData)referenceComboBox.getSelectedItem()).setAsReferenceForProcessingModule();
			this.toDeltaColumn = (DataColumnInfo)dataComboBox.getSelectedItem();
		}
		if (this.toDeltaColumn == null) return false;
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (existingDeltaColumns.containsKey(toDeltaColumn)){
			return new DataColumnInfo[]{existingDeltaColumns.get(toDeltaColumn)};
		} else {
			String name = toDeltaColumn.getName()+"(delta)";
			String id = toDeltaColumn.getId()+"_delta";
			DataColumnInfo deltaColumn = new DataColumnInfo(name, id, toDeltaColumn.getUnit());
			existingDeltaColumns.put(toDeltaColumn, deltaColumn);
			return new DataColumnInfo[]{deltaColumn};
		}
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final AtomicBoolean mismatchWarningShown = new AtomicBoolean(false);
		final KahanSum sumOfAllDeltas = new KahanSum();
		
		final AtomData referenceAtomData = Toolchain.getReferenceData(data, referenceMode);
		if (data == referenceAtomData) return null;
		
		if (data.getAtoms().size() != referenceAtomData.getAtoms().size()){ 
			JLogPanel.getJLogPanel().addWarning(String.format("Inaccurate differences for %s", this.toDeltaColumn.getName()), 
					String.format("The number of atoms in %s and reference %s mismatch."
							+ "Computed differences between these file may be inaccurate", 
					data.getName(), referenceAtomData.getName()));
		}
		
		
		final HashMap<Integer, Atom> atomsMap = new HashMap<Integer, Atom>();
		for (Atom a : referenceAtomData.getAtoms()){
			Atom oldValue = atomsMap.put(a.getNumber(), a);
			if (oldValue != null){
				JLogPanel.getJLogPanel().addWarning("Duplicated IDs in data", 
						String.format("The atom ID is %d is duplicated in %s."+
								"The position of both atoms are (%.4f,%.4f,%.4f) and (%.4f,%.4f,%.4f)"
						+ "Computed differences between these file may be inaccurate", 
						referenceAtomData.getName(), a.getNumber(), a.x, a.y, a.y, oldValue.x, oldValue.y, oldValue.z));
			}
		}
		
		if (atomsMap.size() != referenceAtomData.getAtoms().size()){
			String errorMessage = String.format("IDs of atoms in %s are non-unique", referenceAtomData.getName());
			JLogPanel.getJLogPanel().addError(errorMessage,
					String.format("Cannot compute difference of value %s", this.toDeltaColumn.getName()));
			throw new RuntimeException(errorMessage);
		}
		
		final int colValue = data.getIndexForCustomColumn(toDeltaColumn);
		final int colValueRef = data.getIndexForCustomColumn(toDeltaColumn);
		final int deltaCol = data.getIndexForCustomColumn(existingDeltaColumns.get(toDeltaColumn));
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		final Object mutex = new Object();
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					KahanSum sum = new KahanSum();
					
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						Atom a_ref = atomsMap.get(a.getNumber());

						if (a_ref!=null){
							float value = a.getData(colValue)-a_ref.getData(colValueRef);						
							a.setData(value, deltaCol);
							sum.add(value);
						} else {
							if (!mismatchWarningShown.getAndSet(true)){
								JLogPanel.getJLogPanel().addWarning(String.format("Inaccurate differences for %s", toDeltaColumn.getName()), 
										String.format("Atom IDs in %s could not be matched to the reference %s."
												+ "Computed differences between these file may be inaccurate", 
										data.getName(), referenceAtomData.getName()));
							}
							a.setData(0f, deltaCol);
						}
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					synchronized (mutex) {
						sumOfAllDeltas.add(sum.getSum());
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		String s = String.format("Total difference between file %s and %s in %s: %f", 
				referenceAtomData.getName(), data.getName(), toDeltaColumn.getName(), sumOfAllDeltas.getSum());
		return new DataContainer.DefaultDataContainerProcessingResult(null, s);
	}

	@Override
	public void exportParameters(XMLStreamWriter xmlOut)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException {
		xmlOut.writeStartElement("toDeltaColumn");
		xmlOut.writeAttribute("id", toDeltaColumn.getId());
		xmlOut.writeEndElement();
	}
	
	@Override
	public void importParameters(XMLStreamReader reader, Toolchain toolchain) throws Exception {
		reader.next();
		if (!reader.getLocalName().equals("toDeltaColumn")) throw new XMLStreamException("Illegal element detected");
		this.toDeltaID = reader.getAttributeValue(null, "id");
	}
	
}
