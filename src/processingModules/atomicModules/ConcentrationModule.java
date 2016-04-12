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
import gui.PrimitiveProperty.*;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.DataColumnInfo.Component;
import model.NearestNeighborBuilder;
import processingModules.ProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchainable;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.ThreadPool;
import gnu.trove.set.hash.TIntHashSet;

@ToolchainSupport()
public class ConcentrationModule implements Toolchainable, Cloneable, ProcessingModule{
	
	private static DataColumnInfo atomicConcentrationColumn = 
			new DataColumnInfo("Concentration (at%)" , "AtomicConcentration" ,"%");
	private static DataColumnInfo weigthConcentrationColumn = 
			new DataColumnInfo("Concentration (wt%)" , "WeigthConcentration" ,"%");
	
	@ExportableValue
	private float radius = 5f;
	@ExportableValue
	private boolean concAsWeightPercent = false;
	
	//Custom export
	private TIntHashSet elements = new TIntHashSet();
	
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (concAsWeightPercent)
			return new DataColumnInfo[]{weigthConcentrationColumn};
		return new DataColumnInfo[]{atomicConcentrationColumn};
	}
	
	@Override
	public String getShortName() {
		return "Atomic/Weight percent concentration";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the local atomic or weight concentration of selected elements.";
	}
	
	@Override
	public String getRequirementDescription() {
		return "Structure must be composed of more than one element";
	}
	
	@Override
	public boolean isApplicable(AtomData atomData) {
		return atomData.getCrystalStructure().getNumberOfElements()>1;
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		final int v = data.getDataColumnIndex(concAsWeightPercent?weigthConcentrationColumn:atomicConcentrationColumn);
		final int m = data.getComponentIndex(Component.MASS);
		final float[] vArray = data.getDataArray(v).getData();
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), radius, true);
		nnb.addAll(data.getAtoms());
		
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					final int numElements = data.getCrystalStructure().getNumberOfElements();
					
					if (!concAsWeightPercent){
						for (int i=start; i<end; i++){
							if ((i-start)%1000 == 0)
								ProgressMonitor.getProgressMonitor().addToCounter(1000);
							
							Atom a = data.getAtoms().get(i);	
							
							float inElement = 0f;
							if (elements.contains(a.getElement()%numElements)) inElement++;
							ArrayList<Atom> nn = nnb.getNeigh(a);
							for (Atom n : nn){
								if (elements.contains(n.getElement()%numElements)) inElement++;
							}
							vArray[i] = (inElement/(nn.size()+1.f))*100f; 
						}
					} else {
						
						if (m == -1){
							JLogPanel.getJLogPanel().addWarning("Mass not found",
								String.format("Concentration in weigth percent selected, but mass column is missing in %s", 
										data.getName()));
						} else {
							final float[] massArray = data.getDataArray(m).getData();
							
							for (int i=start; i<end; i++){
								if ((i-start)%1000 == 0)
									ProgressMonitor.getProgressMonitor().addToCounter(1000);
								
								Atom a = data.getAtoms().get(i);	
								
								float inMass = 0f;
								float totalMass = massArray[i];
								
								if (elements.contains(a.getElement()%numElements)) inMass += massArray[i];
								
								
								ArrayList<Atom> nn = nnb.getNeigh(a);
								for (Atom n : nn){
									if (elements.contains(n.getElement()%numElements)) inMass += massArray[n.getID()];
									totalMass += massArray[n.getID()];
								}
								vArray[i] = (inMass/totalMass)*100f; 
							}
						}
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter((end-start)%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		return null;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = 
				new JPrimitiveVariablesPropertiesDialog(frame, "Compute particle concentrations");
		dialog.addLabel(getFunctionDescription()+"</br>The concentration is computed per particle within a selected radius.");
		dialog.add(new JSeparator());
		FloatProperty avRadius = dialog.addFloat("avRadius", "Radius of the sphere", "", 5f, 0f, 1000f);
		BooleanProperty weighConc = dialog.addBoolean("concWeight",
				"Concentration in weight percentage instead of atomic percentage", 
				"Requires masses per particle", concAsWeightPercent);
		if (data.getComponentIndex(Component.MASS)==-1){
			weighConc.setEnabled(false);
			weighConc.setValue(false);
		}
		
		dialog.endGroup();
		
		dialog.startGroup("Select elements");
		
		int numElements = data.getCrystalStructure().getNumberOfElements();
		
		JPanel buttonPanel = new JPanel(new GridLayout(numElements, 1));
		JCheckBox[] selectElements = new JCheckBox[numElements];
		
		if (data.getCrystalStructure().getNamesOfElements() != null){
			for (int i=0; i<numElements; i++)
				selectElements[i] = new JCheckBox(data.getCrystalStructure().getNamesOfElements()[i], elements.contains(i));
		} else {
			for (int i=0; i<numElements; i++)
				selectElements[i] = new JCheckBox(Integer.toString(i), elements.contains(i));
		}
		
		for (int i=0; i<numElements; i++)
			buttonPanel.add(selectElements[i]);
		
		dialog.addComponent(buttonPanel);
		dialog.endGroup();
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.radius = avRadius.getValue();
			this.concAsWeightPercent = weighConc.getValue();
			elements.clear();
			for (int i=0; i<numElements; i++)
				if (selectElements[i].isSelected()) elements.add(i); 
		}
		return ok;
	}
	
	@Override
	public void exportParameters(XMLStreamWriter xmlOut)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException {
		xmlOut.writeStartElement("elements");
		StringBuilder sb = new StringBuilder();
		int[] e = elements.toArray();
		
		sb.append(Integer.toString(e[0]));
		for (int i=1; i<e.length;i++){
			sb.append(", ");
			sb.append(Integer.toString(e[i]));
		}
		xmlOut.writeAttribute("ids", sb.toString());
		xmlOut.writeEndElement();
	}
	
	@Override
	public void importParameters(XMLStreamReader reader, Toolchain toolchain) throws Exception {
		reader.next();
		if (!reader.getLocalName().equals("elements")) throw new XMLStreamException("Illegal element detected");
		String s = reader.getAttributeValue(null, "ids");
		String[] elementList = s.trim().split(",");
		this.elements.clear();
		
		for (String se : elementList){
			this.elements.add(Integer.parseInt(se.trim()));
		}
	}
	
	//TIntHashSet 
	@Override
	public ProcessingModule clone() {
		ConcentrationModule clone = new ConcentrationModule();
		clone.concAsWeightPercent = this.concAsWeightPercent;		
		clone.radius = this.radius;
		clone.elements.addAll(this.elements);
		return clone;
	}
}
