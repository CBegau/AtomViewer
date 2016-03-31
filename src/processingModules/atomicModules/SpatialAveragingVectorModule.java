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

import gui.JLogPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
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
import model.DataColumnInfo.Component;
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
public class SpatialAveragingVectorModule extends ClonableProcessingModule implements Toolchainable {

	private static HashMap<DataColumnInfo, DataColumnInfo> existingAverageColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	private DataColumnInfo toAverageColumn;
	private DataColumnInfo averageColumn;
	//This is the indicator used for import from a toolchain, since the column
	//the file is referring to might not exist at that moment 
	private String toAverageID;
	
	@ExportableValue
	private float averageRadius = 5f;
	
	@ExportableValue
	private boolean useSmoothingKernel = false;
	
	@ExportableValue
	private boolean weigthByMass = false;

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
		//Identify the column by its ID if imported from a toolchain
		if (toAverageColumn == null && toAverageID != null){
			for (DataColumnInfo d : data.getDataColumnInfos()){
				if (d.getId().equals(toAverageID)){
					this.toAverageColumn = d;
				}
			}
			if (toAverageColumn == null) return false;
		}
		
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
		
		final int massColumn = data.getIndexForComponent(Component.MASS);
		final boolean scaleMass = weigthByMass && massColumn != -1;
		if (weigthByMass && !scaleMass)
			JLogPanel.getJLogPanel().addWarning("Mass not found",
					String.format("Weightened averages for %s selected, but mass column is missing in %s", toAverageColumn.getName(),
							data.getName()));
		
		ProgressMonitor.getProgressMonitor().start(2*data.getAtoms().size());
		
		nnb.addAll(data.getAtoms());
		
		final CyclicBarrier barrier = new CyclicBarrier(ThreadPool.availProcessors());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					
					final float halfR = averageRadius*0.5f;
					final Vec3 temp = new Vec3();
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					if (!useSmoothingKernel){
						for (int i=start; i<end; i++){
							if ((i-start)%1000 == 0) ProgressMonitor.getProgressMonitor().addToCounter(2000);
					
							Atom a = data.getAtoms().get(i);
							temp.x = a.getData(vx);
							temp.y = a.getData(vy);
							temp.z = a.getData(vz);
							ArrayList<Atom> neigh = nnb.getNeigh(a);
							float mass = scaleMass ? a.getData(massColumn) : 1f;
							float sumMass = mass;
							
							for (Atom n : neigh){
								mass = scaleMass ? a.getData(massColumn) : 1f;
								temp.x += n.getData(vx) * mass;
								temp.y += n.getData(vy) * mass;
								temp.z += n.getData(vz) * mass;
								sumMass += mass;
							}
							temp.divide(sumMass);
							
							a.setData(temp.x, avx);          
							a.setData(temp.y, avy);          
							a.setData(temp.z, avz);          
							a.setData(temp.getLength(), ava);
						}
						ProgressMonitor.getProgressMonitor().addToCounter( 2*(end-start%1000));
					} else {
						//Compute density of all particles and store in the data column
						for (int i=start; i<end; i++){
							if ((i-start)%1000 == 0)
								ProgressMonitor.getProgressMonitor().addToCounter(1000);
							Atom a = data.getAtoms().get(i);
							ArrayList<Tupel<Atom,Vec3>> neigh = nnb.getNeighAndNeighVec(a);
							float mass = scaleMass ? a.getData(massColumn) : 1f;
							
							//Include central particle a with d = 0
							float density = mass*CommonUtils.getM4SmoothingKernelWeight(0f, halfR);
							//Estimate local density based on distance to other particles
							for (int k=0, len = neigh.size(); k<len; k++){
								Tupel<Atom,Vec3> n = neigh.get(k);
								mass = scaleMass ? n.o1.getData(massColumn) : 1f;
								density += mass * CommonUtils.getM4SmoothingKernelWeight(n.o2.getLength(), halfR);
							}
							//Temporarily store the density of the particle 
							a.setData(density, ava);
						}
						ProgressMonitor.getProgressMonitor().addToCounter((end-start)%1000);
						barrier.await();
						
						//Compute the averages 
						for (int i=start; i<end; i++){
							if ((i-start)%1000 == 0)
								ProgressMonitor.getProgressMonitor().addToCounter(1000);

							//Start with central particle with d = 0
							Atom a = data.getAtoms().get(i);
							temp.x = a.getData(vx); temp.y = a.getData(vy); temp.z = a.getData(vz);
							float mass = scaleMass ? a.getData(massColumn) : 1f;
							temp.multiply(mass * CommonUtils.getM4SmoothingKernelWeight(0f, halfR) / a.getData(ava));
							
							ArrayList<Tupel<Atom,Vec3>> neigh = nnb.getNeighAndNeighVec(a);
							for (Tupel<Atom,Vec3> n : neigh){
								mass = scaleMass ? a.getData(massColumn) : 1f;
								//Weighting based on distance and density
								float w = mass * CommonUtils.getM4SmoothingKernelWeight(n.getO2().getLength(), halfR);
								w /= n.o1.getData(ava); //Divide by local density
								temp.x += n.o1.getData(vx) * w;
								temp.y += n.o1.getData(vy) * w;
								temp.z += n.o1.getData(vz) * w;

							}							
							a.setData(temp.x, avx); a.setData(temp.y, avy); a.setData(temp.z, avz);
						}
						
						barrier.await();
						
						//Compute length of vector and overwrite the density with this value
						for (int i=start; i<end; i++){
							Atom a = data.getAtoms().get(i);
							Vec3 v = new Vec3(a.getData(avx), a.getData(avy), a.getData(avz)); 
							a.setData(v.getLength(), ava);
						}
						
						ProgressMonitor.getProgressMonitor().addToCounter((end-start)%1000);
					
					} // End of smoothing kernel
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
		
		dialog.addLabel("Computes the spatial average of a vector. <br>"
				+ "The average is computed from all neighbors within a given "
				+ "radius either as the arithmetical average or as a weigthed average using a "
				+ "cubic spline smoothing kernel as described in (Monaghan, Rep. Prog. Phys 68, 2005)");
		dialog.add(new JSeparator());
		
		JComboBox averageComponentsComboBox = new JComboBox();
		for (DataColumnInfo dci : data.getDataColumnInfos())
			if (dci.isFirstVectorComponent())
				averageComponentsComboBox.addItem(new DataColumnInfo.VectorDataColumnInfo(dci));
		
		dialog.addLabel("Select vector to average");
		dialog.addComponent(averageComponentsComboBox);
		FloatProperty avRadius = dialog.addFloat("avRadius", "Cutoff radius for averaging"
				, "", averageRadius, 0f, 1000f);
		
		ButtonGroup bg = new ButtonGroup();
		dialog.startGroup("Averaging method");
		JRadioButton smoothingButton = new JRadioButton("Cubic spline smoothing kernel");
		JRadioButton arithmeticButton = new JRadioButton("Arithmetic average");
		String smoothingTooltip = "Computes a weightend average over neighbors based on distance and density<br>"
				+ "This implementation is using the cubic spline M4 kernel<br>";
		smoothingButton.setToolTipText(CommonUtils.getWordWrappedString(smoothingTooltip, smoothingButton));
		arithmeticButton.setToolTipText("Computes the arithmetic average over all nearby neighbors without weighting.");
		
		JCheckBox considerMassButton = new JCheckBox("Weigth by particle mass", this.weigthByMass);
		considerMassButton.setToolTipText("Weigth particles by their mass (if possible)");
		if (data.getIndexForComponent(Component.MASS)==-1) considerMassButton.setEnabled(false);
		
		smoothingButton.setSelected(useSmoothingKernel);
		arithmeticButton.setSelected(!useSmoothingKernel);
		dialog.addComponent(arithmeticButton);
		dialog.addComponent(smoothingButton);
		dialog.addComponent(considerMassButton);
		bg.add(smoothingButton);
		bg.add(arithmeticButton);
		dialog.endGroup();
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.weigthByMass = considerMassButton.isEnabled() && considerMassButton.isSelected();
			this.useSmoothingKernel = smoothingButton.isSelected();
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
		this.toAverageID = reader.getAttributeValue(null, "id");
	}
}
