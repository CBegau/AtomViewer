// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universit√§t Bochum
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
import gui.PrimitiveProperty.*;

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
import common.VoronoiVolume;

@ToolchainSupport()
public class AtomicVolumeModule extends ClonableProcessingModule {

	private static DataColumnInfo volumeColumn = 
			new DataColumnInfo("Atomic volume" , "atomVol" ,"");
	private static DataColumnInfo densityColumn = 
			new DataColumnInfo("Atomic density" , "atomDens" ,"");
	
	@ExportableValue
	private float radius = 5f;
	@ExportableValue
	private float scalingFactor = 1f;
	@ExportableValue
	private boolean computeDensity = false;
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		if (computeDensity) return new DataColumnInfo[]{densityColumn};
		else return new DataColumnInfo[]{volumeColumn};
	}
	
	@Override
	public String getShortName() {
		return "Atomic volume / density";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the volume of particles using a Voronoi cell construction. Particles at surfaces which are"
				+ "not properly enclosed by a voronoi cell will have a volume equal to zero. "
				+ "Alternatively, the atomic density as the inverse of the volume can be computed. "
				+ "Choosing the neighbor search distance as small as possible is needed to reduce the execution time. "
				+ "Warning: Current implementation is really slow!";
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
		final float sphereVolume = (radius*radius*radius)*((float)Math.PI)*(4f/3f);
		//Get the array where to store results
		final int v = computeDensity ? 
				data.getDataColumnIndex(densityColumn) : data.getDataColumnIndex(volumeColumn);
		final float[] vArray = data.getDataArray(v).getData();
		//Build nearest neighbor graph
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), radius, true);
		nnb.addAll(data.getAtoms());
		
		//Parallel calculation of volume/density, iterate over all indices in a stream
		ThreadPool.executeAsParallelStream(data.getAtoms().size(), i -> {
			Atom a = data.getAtoms().get(i);
			float value = VoronoiVolume.getVoronoiVolume(nnb.getNeighVec(a));
			
			//Volume could not be computed/is not plausible
			if (value > sphereVolume)
				value = 0f;
			else if (computeDensity && value > 1e-8f) value = 1f/value;
			if (computeDensity && value < 0f) value = 0f;
			vArray[i] = value*scalingFactor;	//Store result);
		});
		
		return null;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Compute atomic volume/density");
		dialog.addLabel(getFunctionDescription());
		dialog.addLabel("The result of the volume has the unit of '(length unit)≥', the density '1/(length unit)≥'");
		dialog.add(new JSeparator());
		FloatProperty avRadius = dialog.addFloat("avRadius", "Radius of a sphere to find neighbors for a voronoi cell construction."
				, "", radius, 0f, 1000f);
		BooleanProperty density = dialog.addBoolean("compDensity", "Compute density instead of volume", "", computeDensity);
		FloatProperty scaling = dialog.addFloat("scalingFactor", "Scaling factor for the result (e.g. to nm≥)", "", scalingFactor, 0f, 1e20f);
		boolean ok = dialog.showDialog();
		if (ok){
			this.computeDensity = density.getValue();
			this.radius = avRadius.getValue();
			this.scalingFactor = scaling.getValue();
		}
		return ok;
	}
}
