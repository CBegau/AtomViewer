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
import model.Atom;
import model.AtomData;
import model.Configuration;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchainable;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

//TODO handle reference in Toolchain
@ToolchainSupport()
public class DeltaVectorModule extends ClonableProcessingModule implements Toolchainable{
	
	private static HashMap<DataColumnInfo, DataColumnInfo> existingDeltaColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	private AtomData referenceAtomData = null;
	private DataColumnInfo toDeltaColumn;
	//This is the indicator used for import from a toolchain, since the column
	//the file is referring to might not exist at that moment 
	private String toDeltaID;
	
	@Override
	public String getShortName() {
		return "Compute difference for vectors";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes the difference for a selected vector to a reference file.";
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
		
		if ((data.getNext() == null && data.getPrevious() == null) || this.toDeltaColumn == null)
			return false;
		for (DataColumnInfo dci: data.getDataColumnInfos())
			if (dci.isFirstVectorComponent()) return true; 
		
		return false;
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
		
		List<DataColumnInfo> common = new ArrayList<DataColumnInfo>();
		for (DataColumnInfo dci: data.getDataColumnInfos())
			if (dci.isFirstVectorComponent()) common.add(dci);
		
		common.retainAll(((AtomData)referenceComboBox.getSelectedItem()).getDataColumnInfos());
		for (DataColumnInfo dci : common)
			dataComboBox.addItem(new DataColumnInfo.VectorDataColumnInfo(dci));
		
		referenceComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dataComboBox.removeAllItems();
				List<DataColumnInfo> common = new ArrayList<DataColumnInfo>();
				for (DataColumnInfo dci: data.getDataColumnInfos())
					if (dci.isFirstVectorComponent()) common.add(dci);
				common.retainAll(((AtomData)referenceComboBox.getSelectedItem()).getDataColumnInfos());
				
				for (DataColumnInfo dci : common)
					dataComboBox.addItem(new DataColumnInfo.VectorDataColumnInfo(dci));
				dataComboBox.revalidate();
			}
		});
		
		dialog.addLabel("Select vector common in both files");
		dialog.addComponent(dataComboBox);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.referenceAtomData = (AtomData)referenceComboBox.getSelectedItem(); 
			this.toDeltaColumn = ((DataColumnInfo.VectorDataColumnInfo)dataComboBox.getSelectedItem()).getDci();
		}
		if (this.toDeltaColumn == null) return false;
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (existingDeltaColumns.containsKey(toDeltaColumn)){
			return existingDeltaColumns.get(toDeltaColumn).getVectorComponents();
		} else {
			String name = toDeltaColumn.getVectorName()+"(delta)";
			DataColumnInfo[] vec = toDeltaColumn.getVectorComponents();
			DataColumnInfo deltaX = new DataColumnInfo("", vec[0].getId()+"_delta",vec[0].getUnit());
			DataColumnInfo deltaY = new DataColumnInfo("", vec[1].getId()+"_delta", vec[0].getUnit());
			DataColumnInfo deltaZ = new DataColumnInfo("", vec[2].getId()+"_delta", vec[0].getUnit());
			DataColumnInfo deltaA = new DataColumnInfo("", vec[3].getId()+"_delta", vec[0].getUnit());
			
			deltaX.setAsFirstVectorComponent(deltaY, deltaZ, deltaA, name);
			
			DataColumnInfo deltaColumn = deltaX;
			return existingDeltaColumns.put(toDeltaColumn, deltaColumn).getVectorComponents();
		}
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final AtomicBoolean mismatchWarningShown = new AtomicBoolean(false);
		
		if (data == referenceAtomData) return null;
		
		if (data.getAtoms().size() != referenceAtomData.getAtoms().size()){ 
			JLogPanel.getJLogPanel().addLog(String.format("Warning: Differences may inaccurate. Number of atoms in %s and reference %s mismatch.", 
					data.getName(), referenceAtomData.getName()));
		}
		
		
		final HashMap<Integer, Atom> atomsMap = new HashMap<Integer, Atom>();
		for (Atom a : referenceAtomData.getAtoms())
			atomsMap.put(a.getNumber(), a);
		
		if (atomsMap.size() != referenceAtomData.getAtoms().size())
			throw new RuntimeException(
					String.format("Cannot compute difference values: IDs of atoms in %s are non-unique.",
							referenceAtomData.getName()));
		
		final int colValueX = data.getIndexForCustomColumn(toDeltaColumn.getVectorComponents()[0]);
		final int colValueY = data.getIndexForCustomColumn(toDeltaColumn.getVectorComponents()[1]);
		final int colValueZ = data.getIndexForCustomColumn(toDeltaColumn.getVectorComponents()[2]);
		
		final int colValueRefX = data.getIndexForCustomColumn(toDeltaColumn.getVectorComponents()[0]);
		final int colValueRefY = data.getIndexForCustomColumn(toDeltaColumn.getVectorComponents()[1]);
		final int colValueRefZ = data.getIndexForCustomColumn(toDeltaColumn.getVectorComponents()[2]);
		
		DataColumnInfo d = existingDeltaColumns.get(toDeltaColumn);
		final int deltaColX = data.getIndexForCustomColumn(d.getVectorComponents()[0]);
		final int deltaColY = data.getIndexForCustomColumn(d.getVectorComponents()[1]);
		final int deltaColZ = data.getIndexForCustomColumn(d.getVectorComponents()[2]);
		final int deltaColA = data.getIndexForCustomColumn(d.getVectorComponents()[3]);
		
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
							float x = a.getData(colValueX)-a_ref.getData(colValueRefX);						
							float y = a.getData(colValueY)-a_ref.getData(colValueRefY);
							float z = a.getData(colValueZ)-a_ref.getData(colValueRefZ);
							
							a.setData(x, deltaColX);
							a.setData(y, deltaColY);
							a.setData(z, deltaColZ);
							a.setData((float)Math.sqrt(x*x + y*y +z*z), deltaColA);
						} else {
							if (!mismatchWarningShown.getAndSet(true)){
								JLogPanel.getJLogPanel().addLog(String.format("Warning: Some differences are inaccurate. "
										+ "Some atoms could not be found in reference file %s.", referenceAtomData.getName()));
							}
							a.setData(0f, deltaColX);
							a.setData(0f, deltaColY);
							a.setData(0f, deltaColZ);
							a.setData(0f, deltaColA);
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
		String id = reader.getAttributeValue(null, "id");
		
		List<DataColumnInfo> dci = Configuration.getCurrentAtomData().getDataColumnInfos();
		for (DataColumnInfo d : dci){
			if (d.getId().equals(id)){
				this.toDeltaColumn = d;
				break;
			}
		}
	}
}
