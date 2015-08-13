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

import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.ThreadPool;

@ToolchainSupport()
public class CoordinationNumberModule extends ClonableProcessingModule {

	private static DataColumnInfo coordNumColumn = 
			new DataColumnInfo("Coordination" , "coordNum" ,"");
	private static DataColumnInfo densityColumn = 
			new DataColumnInfo("Particle density" , "partDens" ,"");
	@ExportableValue
	private float radius = 5f;
	@ExportableValue
	private float scalingFactor = 1f;
	@ExportableValue
	private boolean density = false;
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (density) return new DataColumnInfo[]{densityColumn};
		return new DataColumnInfo[]{coordNumColumn};
	}
	
	@Override
	public String getShortName() {
		return "Coordination number / Particle density";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the number of neighboring particles in a given radius. "
				+ "Alternatively, the density of particles inside the spherical volume can be computed.";
	}
	
	@Override
	public String getRequirementDescription() {
		return "";
	}
	
	@Override
	public boolean isApplicable(AtomData atomData) {
		return true;
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		final float sphereVolume = radius*radius*radius*((float)Math.PI)*(4f/3f);
		
		final int v = density ? data.getIndexForCustomColumn(densityColumn): data.getIndexForCustomColumn(coordNumColumn);
		
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
					
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);	
						float neigh = nnb.getNeigh(a).size();
						
						if (density)	//The density including the central particle itself
							neigh = ((neigh+1)/sphereVolume)*scalingFactor;
						
						
						a.setData(neigh, v);
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
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Compute coordination number / particle density");
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		FloatProperty avRadius = dialog.addFloat("avRadius", "Radius of the sphere", "", 5f, 0f, 1000f);
		BooleanProperty dens = dialog.addBoolean("compDensity", "Compute density instead of volume", "", false);
		FloatProperty scaling = dialog.addFloat("scalingFactor", "Scaling factor for the result (e.g. to particles/nm³)."
				+ "Only used if densities are computed.", "", 1f, 0f, 1e20f);
		boolean ok = dialog.showDialog();
		if (ok){
			this.radius = avRadius.getValue();
			this.density = dens.getValue();
			this.scalingFactor = scaling.getValue();
		}
		return ok;
	}
}
