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
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
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
public class SpatialDerivatiesModule extends ClonableProcessingModule implements Toolchainable{

	private static HashMap<DataColumnInfo, DataColumnInfo> existingGradientColumns 
		= new HashMap<DataColumnInfo, DataColumnInfo>();
	
	@ExportableValue
	private float radius = 5f;
	@ExportableValue
	private boolean weigthByMass = false;
	
	private DataColumnInfo toDeriveColumn;
	private DataColumnInfo gradientColumn;
	//This is the indicator used for import from a toolchain, since the column
	//the file is referring to might not exist at that moment 
	private String toDeriveID;
	
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (existingGradientColumns.containsKey(toDeriveColumn)){
			this.gradientColumn = existingGradientColumns.get(toDeriveColumn);
		} else {
			String name = "grad("+toDeriveColumn.getName()+")";
			DataColumnInfo avX = new DataColumnInfo(toDeriveColumn.getName()+"_dx", toDeriveColumn.getId()+"_grad_x", "");
			DataColumnInfo avY = new DataColumnInfo(toDeriveColumn.getName()+"_dy", toDeriveColumn.getId()+"_grad_y", "");
			DataColumnInfo avZ = new DataColumnInfo(toDeriveColumn.getName()+"_dz", toDeriveColumn.getId()+"_grad_z","");
			DataColumnInfo avA = new DataColumnInfo("|grad("+toDeriveColumn.getId()+")|", toDeriveColumn.getId()+"_grad_abs", "");
			
			avX.setAsFirstVectorComponent(avY, avZ, avA, name, false);
			
			this.gradientColumn = avX;
			existingGradientColumns.put(toDeriveColumn, gradientColumn);
		}
		
		return gradientColumn.getVectorComponents();
	}
	
	@Override
	public String getShortName() {
		return "Spatial derivatives";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the spatial derivatives and its absolute of a scalar value.";
	}
	
	@Override
	public String getRequirementDescription() {
		return "";
	}
	
	@Override
	public boolean isApplicable(AtomData atomData) {
		//Identify the column by its ID if imported from a toolchain
		if (toDeriveColumn == null && toDeriveID != null){
			for (DataColumnInfo d : atomData.getDataColumnInfos()){
				if (d.getId().equals(toDeriveID)){
					this.toDeriveColumn = d;
				}
			}
			if (toDeriveColumn == null) return false;
		}
		
		return !atomData.getDataColumnInfos().isEmpty();
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final int v = data.getDataColumnIndex(toDeriveColumn);
		
		final int gx = data.getDataColumnIndex(gradientColumn.getVectorComponents()[0]);
		final int gy = data.getDataColumnIndex(gradientColumn.getVectorComponents()[1]);
		final int gz = data.getDataColumnIndex(gradientColumn.getVectorComponents()[2]);
		final int gn = data.getDataColumnIndex(gradientColumn.getVectorComponents()[3]);
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), radius, true);
		nnb.addAll(data.getAtoms());
		
		final float halfR = radius*0.5f;
		final int massColumn = data.getComponentIndex(Component.MASS);
		final boolean scaleMass = weigthByMass && massColumn != -1;
		if (weigthByMass && !scaleMass)
			JLogPanel.getJLogPanel().addWarning("Mass not found",
					String.format("Weightened spatial derivatives for %s selected, but mass column is missing in %s", 
							toDeriveColumn.getName(), data.getName()));
		
		final float[] gxArray = data.getDataArray(gx).getData();
		final float[] gyArray = data.getDataArray(gy).getData();
		final float[] gzArray = data.getDataArray(gz).getData();
		final float[] gnArray = data.getDataArray(gn).getData();
		final float[] vArray = data.getDataArray(v).getData();
		final float[] massArray = scaleMass ? data.getDataArray(massColumn).getData() : null;
		
		ThreadPool.executeAsParallelStream(data.getAtoms().size(), i->{
			Atom a = data.getAtoms().get(i);
			
			ArrayList<Tupel<Atom,Vec3>> neigh = nnb.getNeighAndNeighVec(a);

			//Estimate local density
			float mass = scaleMass ? massArray[i] : 1f;
			float density = mass * CommonUtils.getM4SmoothingKernelWeight(0f, halfR);
			
			for (int k=0, len = neigh.size(); k<len; k++){
				mass = scaleMass ? massArray[neigh.get(k).o1.getID()] : 1f;
				density += mass * CommonUtils.getM4SmoothingKernelWeight(neigh.get(k).o2.getLength(), halfR);
			}
			 
			Vec3 grad = new Vec3();
			
			float valueA = vArray[i];
			
			for (Tupel<Atom,Vec3> n : neigh){
				float valueB = vArray[n.o1.getID()];
				mass = scaleMass ? massArray[n.o1.getID()] : 1f;
				grad.add(CommonUtils.getM4SmoothingKernelDerivative(n.o2, halfR).multiply(mass*(valueB-valueA)));
			}
			
			grad.divide(density);
			
			gxArray[i] = -grad.x;
			gyArray[i] = -grad.y;
			gzArray[i] = -grad.z;
			gnArray[i] = grad.getLength();
		});
		
		return null;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, getShortName());
		dialog.addLabel(getFunctionDescription()+"<br>"
				+ "The first derivaties are approximated using a smoothing kernel summing up all particles in a given radius."
				+ "The implementation is based on the methods used in Smooth particle hydrodynamics and uses a "
				+ "cubic spline smoothing kernel as described in (Monaghan, Rep. Prog. Phys 68, 2005)");
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		FloatProperty avRadius = dialog.addFloat("avRadius", "Radius of the sphere", "", this.radius, 0f, 1000f);
		
		JComboBox<DataColumnInfo> averageComponentsComboBox = new JComboBox<>();
		for (DataColumnInfo dci : data.getDataColumnInfos())
			averageComponentsComboBox.addItem(dci);
		
		dialog.addLabel("Select value to compute gradient");
		dialog.addComponent(averageComponentsComboBox);
		
		JCheckBox considerMassButton = new JCheckBox("Weigth by particle mass", this.weigthByMass);
		considerMassButton.setToolTipText("Weigth particles by their mass (if possible)");
		if (data.getComponentIndex(Component.MASS)==-1) considerMassButton.setEnabled(false);
		dialog.addComponent(considerMassButton);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.weigthByMass = considerMassButton.isEnabled() && considerMassButton.isSelected();
			this.radius = avRadius.getValue();
			this.toDeriveColumn = (DataColumnInfo)averageComponentsComboBox.getSelectedItem();
		}
		return ok;
	}
	
	@Override
	public void exportParameters(XMLStreamWriter xmlOut)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException {
		xmlOut.writeStartElement("toDeriveColumn");
		xmlOut.writeAttribute("id", toDeriveColumn.getId());
		xmlOut.writeEndElement();
		
	}
	
	@Override
	public void importParameters(XMLStreamReader reader, Toolchain toolchain) throws XMLStreamException {
		reader.next();
		if (!reader.getLocalName().equals("toDeriveColumn")) throw new XMLStreamException("Illegal element detected");
		this.toDeriveID = reader.getAttributeValue(null, "id");
	}
}
