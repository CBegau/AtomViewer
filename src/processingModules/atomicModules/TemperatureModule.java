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

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import common.ThreadPool;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.DataColumnInfo.Component;
import processingModules.DataContainer;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import model.NearestNeighborBuilder;

@ToolchainSupport()
public class TemperatureModule extends ClonableProcessingModule{

	private static DataColumnInfo temperatureColumn = new DataColumnInfo("Temperature", "temp", "");
	@ExportableValue
	private float centerOfMassVelocityRadius = 0f;
	@ExportableValue
	private float scalingFactor = 11605f;
	@ExportableValue
	private boolean spatialAverage = true;
	@ExportableValue
    private boolean ignoreFixedAtoms = true;
	
	@Override
	public String getFunctionDescription() {
		return "Computes the temperature T per atom from a velocity vector δv and the atomic mass m as T=|δv|*m/3. "
				+ "The velocity vector δv is the difference of the atom's velocity "
				+ "and the center of mass velocity of all atoms within the given cutoff radius."
				+ " If requested, the temperature per atom is averaged within the cutoff radius."
				+ " If requested, atoms with an absolute velocity of exactly 0.0 are considered "
				+ "as fixed atoms and ignored for temperature calculations.";
	}
	
	@Override
	public String getRequirementDescription() {
		return null;
	}
	
	@Override
	public String getShortName() {
		return "Compute temperature";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		int m = -1;
		int v_x = -1;
		int v_y = -1;
		int v_z = -1;
		for (int i=0; i<data.getDataColumnInfos().size(); i++){
			DataColumnInfo cci = data.getDataColumnInfos().get(i);
			
			if (cci.getComponent() == Component.MASS)
				m = i;
			
			if (cci.getComponent() == Component.VELOCITY_X)
				v_x = i;
			
			if (cci.getComponent() == Component.VELOCITY_Y)
				v_y = i;
			
			if (cci.getComponent() == Component.VELOCITY_Z)
				v_z = i;
		}
		
		if (m==-1 || v_x==-1 || v_y==-1 || v_z==-1)
			return false;
		else return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{temperatureColumn};
	}
	
	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final NearestNeighborBuilder<Atom> nnb;
		if (centerOfMassVelocityRadius > 0)
			nnb = new NearestNeighborBuilder<Atom>(data.getBox(), centerOfMassVelocityRadius, true);
		else nnb = null;
		
		int m = data.getComponentIndex(Component.MASS);
		int v_x = data.getComponentIndex(Component.VELOCITY_X);
		int v_y = data.getComponentIndex(Component.VELOCITY_Y);
		int v_z = data.getComponentIndex(Component.VELOCITY_Z);
		
		int temperatureIndex = data.getDataColumnIndex(temperatureColumn);
		if (m == -1  || temperatureIndex == -1 || v_x == -1 || v_y == -1 || v_z == -1)
			throw new RuntimeException("Could not find all all input data");
		
		final float[] massArray = data.getDataArray(m).getData();
		final float[] vxArray = data.getDataArray(v_x).getData();
		final float[] vyArray = data.getDataArray(v_y).getData();
		final float[] vzArray = data.getDataArray(v_z).getData();
		int vAbsIndex = data.getDataColumnIndex(data.getDataColumnInfos().get(v_x).getVectorComponents()[3]);
		final float[] vAbsArray = data.getDataArray(vAbsIndex).getData();
		final float[] temperatureArray = data.getDataArray(temperatureIndex).getData();
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		if (centerOfMassVelocityRadius > 0)
			nnb.addAll(data.getAtoms());
		
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
						float vx = vxArray[i];
						float vy = vyArray[i];
						float vz = vzArray[i];
						
						float totThermalEnergy = 0f;
						
						if (centerOfMassVelocityRadius > 0){
						    if (ignoreFixedAtoms && vAbsArray[i] == 0.f){ //Fixed atoms have no temperature
						        temperatureArray[i] = 0f;
						        continue;
						    }
						    
							float av_x = vx;
							float av_y = vy;
							float av_z = vz;
							int nn = 1;
							
							ArrayList<Atom> neigh = nnb.getNeigh(a);
							for (Atom n : neigh){
								int id = n.getID();
								if (!ignoreFixedAtoms || vAbsArray[id]!=0f) {
								    //Ignore fixed atoms if requested
								    av_x += vxArray[id];
								    av_y += vyArray[id];
								    av_z += vzArray[id];
								    nn++;
								}
							}
							
							av_x /= nn;
							av_y /= nn;
							av_z /= nn;
							
							totThermalEnergy = massArray[i] *
									((vx-av_x)*(vx-av_x) + (vy-av_y)*(vy-av_y) + (vz-av_z)*(vz-av_z));
							
							if (spatialAverage){
								for (Atom n : neigh){
									int id = n.getID();
									if (!ignoreFixedAtoms || vAbsArray[id]!=0f){
    									float ax = vxArray[id];
    									float ay = vyArray[id];
    									float az = vzArray[id];
    									float am = massArray[id];
    									
    									totThermalEnergy += am *
    											((ax-av_x)*(ax-av_x) + (ay-av_y)*(ay-av_y) + (az-av_z)*(az-av_z));
									}
								}
								
								totThermalEnergy /= nn;
							}
						} else {
							totThermalEnergy = massArray[i] * (vx*vx + vy*vy + vz*vz);
						}
						
						//final scaling to (average) temperature
						totThermalEnergy /= 3f;
						totThermalEnergy *= scalingFactor;
						
						temperatureArray[i] = totThermalEnergy;
					}
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
		
		double[] tempPerElement = new double[data.getNumberOfElements()];
		int[] numPerElement = new int[data.getNumberOfElements()];
		for (Atom a : data.getAtoms()){
			tempPerElement[a.getElement()] += a.getData(temperatureIndex, data);
			numPerElement[a.getElement()]++;
		}
		
		StringBuilder s = new StringBuilder();
		s.append("Average temperatures for elements in "+data.getName()+"<br>");
		for (int i=0; i<tempPerElement.length; i++)
			s.append(String.format("Element %d: %.6f<br>", i, numPerElement[i]==0 ? 0. : tempPerElement[i]/numPerElement[i]));
				
		ProgressMonitor.getProgressMonitor().stop();

		return new DataContainer.DefaultDataContainerProcessingResult(null, s.toString());
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Compute temperature");
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		FloatProperty comRadius = dialog.addFloat("comvRadius", "Center of mass velocity cutoff"
				, "", centerOfMassVelocityRadius, 0f, 1000f);
		FloatProperty scaling = dialog.addFloat("scalingFactor", "Scaling factor (e.g. 1eV->11605K)", "", scalingFactor, 0f, 1e20f);
		BooleanProperty average = dialog.addBoolean("spacialAverage", "Enable spatial averaging", 
				"The temperatures of each particle are averaged within the cutoff radius.", spatialAverage);
		BooleanProperty ignoreFixed = dialog.addBoolean("ignoreFixed", "Identify and ignore fixed atoms", 
                "Atoms with a v=0.0 are considered as fixed and ignored.", ignoreFixedAtoms);
		boolean ok = dialog.showDialog();
		if (ok){
			this.centerOfMassVelocityRadius = comRadius.getValue();
			this.scalingFactor = scaling.getValue();
			this.spatialAverage = average.getValue();
			this.ignoreFixedAtoms = ignoreFixed.getValue();
		}
		return ok;
	}
}
