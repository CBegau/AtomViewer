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

package processingModules.atomicModules;

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
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchainable;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.ThreadPool;

@ToolchainSupport()
public class SpatialAveragingVectorModule extends ClonableProcessingModule implements Toolchainable {

	private static HashMap<DataColumnInfo, DataColumnInfo> existingAverageColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	private DataColumnInfo toAverageColumn;
	private DataColumnInfo averageColumn;
	
	@ExportableValue
	private float averageRadius = 0f;

	public SpatialAveragingVectorModule() {}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (existingAverageColumns.containsKey(toAverageColumn)){
			this.averageColumn = existingAverageColumns.get(toAverageColumn);
		} else {
			String name = toAverageColumn.getVectorName()+"(av.)";
			DataColumnInfo[] vec = toAverageColumn.getVectorComponents();
			DataColumnInfo avX = new DataColumnInfo("", vec[0].getId()+"_av",vec[0].getUnit());
			DataColumnInfo avY = new DataColumnInfo("", vec[1].getId()+"_av", vec[0].getUnit());
			DataColumnInfo avZ = new DataColumnInfo("", vec[2].getId()+"_av", vec[0].getUnit());
			DataColumnInfo avA = new DataColumnInfo("", vec[3].getId()+"_av", vec[0].getUnit());
			
			avX.setAsFirstVectorComponent(avY, avZ, avA, name);
			
			this.averageColumn = avX;
			existingAverageColumns.put(toAverageColumn, averageColumn);
		}
		
		return averageColumn.getVectorComponents();
	}
	
	@Override
	public String getShortName() {
		return "Spatial averaging of vectors";
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the average value of a vector in a spherical volume around each atom";
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
		for (DataColumnInfo dci: data.getDataColumnInfos())
			if (dci.isFirstVectorComponent()) return true; 
		
		return false;
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), averageRadius, true);
		
		final int vx = data.getIndexForCustomColumn(toAverageColumn.getVectorComponents()[0]);
		final int avx = data.getIndexForCustomColumn(averageColumn.getVectorComponents()[0]);
		final int vy = data.getIndexForCustomColumn(toAverageColumn.getVectorComponents()[1]);
		final int avy = data.getIndexForCustomColumn(averageColumn.getVectorComponents()[1]);
		final int vz = data.getIndexForCustomColumn(toAverageColumn.getVectorComponents()[2]);
		final int avz = data.getIndexForCustomColumn(averageColumn.getVectorComponents()[2]);
		final int ava = data.getIndexForCustomColumn(averageColumn.getVectorComponents()[3]);
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		nnb.addAll(data.getAtoms());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					float tempX, tempY, tempZ;
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						tempX = a.getData(vx);
						tempY = a.getData(vy);
						tempZ = a.getData(vz);
						
						ArrayList<Atom> neigh = nnb.getNeigh(a);
						for (Atom n : neigh){
							tempX += n.getData(vx);
							tempY += n.getData(vy);
							tempZ += n.getData(vz);
						}
						tempX /= neigh.size()+1;
						tempY /= neigh.size()+1;
						tempZ /= neigh.size()+1;
						a.setData(tempX, avx);
						a.setData(tempY, avy);
						a.setData(tempZ, avz);
						a.setData((float)Math.sqrt(tempX*tempX + tempY*tempY + tempZ*tempZ), ava);
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
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Compute spatial average of a vector");
		
		dialog.addLabel("Computes the spatial average for a vector");
		dialog.add(new JSeparator());
		
		JComboBox averageComponentsComboBox = new JComboBox();
		for (DataColumnInfo dci : data.getDataColumnInfos())
			if (dci.isFirstVectorComponent())
				averageComponentsComboBox.addItem(new DataColumnInfo.VectorDataColumnInfo(dci));
		
		dialog.addLabel("Select vector to average");
		dialog.addComponent(averageComponentsComboBox);
		FloatProperty avRadius = dialog.addFloat("avRadius", "Cutoff radius for averaging"
				, "", 5f, 0f, 1000f);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.averageRadius = avRadius.getValue();
			this.toAverageColumn = ((DataColumnInfo.VectorDataColumnInfo)averageComponentsComboBox.getSelectedItem()).getDci(); 
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
	public void importParameters(XMLStreamReader reader, Toolchain toolchain) throws XMLStreamException {
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
