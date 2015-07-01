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

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.JPrimitiveVariablesPropertiesDialog.FloatProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JSeparator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.Atom;
import model.AtomData;
import model.Configuration;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.Toolchainable.ToolchainSupport;
import common.ThreadPool;

@ToolchainSupport()
public class SpatialAveragingModule implements ProcessingModule, Toolchainable {

	private static HashMap<DataColumnInfo, DataColumnInfo> existingAverageColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	private DataColumnInfo toAverageColumn;
	private DataColumnInfo averageColumn;
	
	@ExportableValue
	private float averageRadius = 0f;

	public SpatialAveragingModule() {}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (existingAverageColumns.containsKey(toAverageColumn)){
			this.averageColumn = existingAverageColumns.get(toAverageColumn);
		} else {
			String name = toAverageColumn.getName()+"(av.)";
			String id = toAverageColumn.getId()+"_av";
			this.averageColumn = new DataColumnInfo(name, id, toAverageColumn.getUnit());
			SpatialAveragingModule.existingAverageColumns.put(toAverageColumn, averageColumn);
		}
		
		return new DataColumnInfo[]{averageColumn};
	}
	
	@Override
	public String getShortName() {
		return "Spatial averaging";
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the average value in a spherical volume around each atom";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getRequirementDescription() {
		return "";
	}
	
	@Override
	public boolean isApplicable(AtomData data) {
		return (!data.getDataColumnInfos().isEmpty());
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), averageRadius, true);
		
		final int v = data.getIndexForCustomColumn(toAverageColumn);
		final int av = data.getIndexForCustomColumn(averageColumn);
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		nnb.addAll(data.getAtoms());
		
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
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						temp = a.getData(v);
						ArrayList<Atom> neigh = nnb.getNeigh(a);
						for (Atom n : neigh)
							temp += n.getData(v);
						temp /= neigh.size()+1;
						a.setData(temp, av);
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		
		return null;
	};
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Compute spatial average");
		
		dialog.addLabel("Computes the spatial average of a value");
		dialog.add(new JSeparator());
		
		JComboBox averageComponentsComboBox = new JComboBox();
		for (DataColumnInfo dci : data.getDataColumnInfos())
			averageComponentsComboBox.addItem(dci);
		
		dialog.addLabel("Select value to average");
		dialog.addComponent(averageComponentsComboBox);
		FloatProperty avRadius = dialog.addFloat("avRadius", "Cutoff radius for averaging"
				, "", 5f, 0f, 1000f);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.averageRadius = avRadius.getValue();
			this.toAverageColumn = (DataColumnInfo)averageComponentsComboBox.getSelectedItem(); 
		}
		return ok;
	}
	
	@Override
	public void exportParameters(XMLStreamWriter xmlOut)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException {
		xmlOut.writeStartElement("toAverageColumn");
		xmlOut.writeAttribute("id", toAverageColumn.getId());
		xmlOut.writeEndElement();
		
	}
	
	@Override
	public void importParameters(XMLStreamReader reader) throws XMLStreamException {
		reader.next();
		if (!reader.getLocalName().equals("toAverageColumn")) throw new XMLStreamException("Illegal element detected");
		String id = reader.getAttributeValue(null, "id");
		
		
		List<DataColumnInfo> dci = Configuration.getCurrentAtomData().getDataColumnInfos();
		for (DataColumnInfo d : dci){
			if (d.getId().equals(id)){
				this.toAverageColumn = d;
				break;
			}
		}
	}
}
