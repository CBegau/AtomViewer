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
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchainable;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.CommonUtils;
import common.ThreadPool;
import common.Tupel;
import common.Vec3;

@ToolchainSupport()
public class SpatialAveragingModule extends ClonableProcessingModule implements Toolchainable {

	private static HashMap<DataColumnInfo, DataColumnInfo> existingAverageColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	private DataColumnInfo toAverageColumn;
	private DataColumnInfo averageColumn;
	//This is the indicator used for import from a toolchain, since the column
	//the file is referring to might not exist at that moment 
	private String toAverageID;
	
	@ExportableValue
	private float averageRadius = 0f;
	
	@ExportableValue
	private boolean usSmoothingKernel = true;

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
		//Identify the column by its ID if imported from a toolchain
		if (toAverageColumn == null && toAverageID != null){
			for (DataColumnInfo d : data.getDataColumnInfos()){
				if (d.getId().equals(toAverageID)){
					this.toAverageColumn = d;
				}
			}
			if (toAverageColumn == null) return false;
		}
		
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
						
						if (!usSmoothingKernel){
							ArrayList<Atom> neigh = nnb.getNeigh(a);
							for (Atom n : neigh)
								temp += n.getData(v);
							temp /= neigh.size()+1;
						} else {
							ArrayList<Tupel<Atom,Vec3>> neigh = nnb.getNeighAndNeighVec(a);
							//Start with central particle with d = 0
							float density = CommonUtils.getM4SmoothingKernelWeight(0f, averageRadius);
							temp *= density; 
							
							for (Tupel<Atom,Vec3> n : neigh){
								//Estimate local density of particles
								density += CommonUtils.getM4SmoothingKernelWeight(n.getO2().getLength(), averageRadius);
							}
							
							for (Tupel<Atom,Vec3> n : neigh){
								//Weighting based on distance
								temp += n.o1.getData(v) * 
										CommonUtils.getM4SmoothingKernelWeight(n.getO2().getLength(), averageRadius);
							}
							//Scale weighted average by density  
							temp /= density;
						}
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
		
		dialog.addLabel("Computes the spatial average of a value.");
		dialog.add(new JSeparator());
		
		JComboBox averageComponentsComboBox = new JComboBox();
		for (DataColumnInfo dci : data.getDataColumnInfos())
			averageComponentsComboBox.addItem(dci);
		
		dialog.addLabel("Select value to average");
		dialog.addComponent(averageComponentsComboBox);
		FloatProperty avRadius = dialog.addFloat("avRadius", "Cutoff radius for averaging"
				, "", 5f, 0f, 1000f);
		
		ButtonGroup bg = new ButtonGroup();
		dialog.startGroup("Averaging method");
		JRadioButton smoothingButton = new JRadioButton("Cubic spline smoothing kernel");
		JRadioButton arithmeticButton = new JRadioButton("Arithmetic average");
		
		String wrappedToolTip = CommonUtils.getWordWrappedString("Computed average is the weightend average of all particles based on their distance d "
				+ "<br> (2-d)³-4(1-d)³ for d&lt;1/2r <br> (2-d)³ for 1/2r&lt;d&lt;r", smoothingButton);
		
		smoothingButton.setToolTipText(wrappedToolTip);
		arithmeticButton.setToolTipText("Computed average is the arithmetic average");
		smoothingButton.setSelected(true);
		arithmeticButton.setSelected(false);
		dialog.addComponent(smoothingButton);
		dialog.addComponent(arithmeticButton);
		bg.add(smoothingButton);
		bg.add(arithmeticButton);
		dialog.endGroup();
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.usSmoothingKernel = smoothingButton.isSelected();
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
	public void importParameters(XMLStreamReader reader, Toolchain toolchain) throws XMLStreamException {
		reader.next();
		if (!reader.getLocalName().equals("toAverageColumn")) throw new XMLStreamException("Illegal element detected");
		this.toAverageID = reader.getAttributeValue(null, "id");
	}
}
