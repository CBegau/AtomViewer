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
import java.util.stream.IntStream;

import javax.swing.JFrame;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ProcessingResult;
import processingModules.ClonableProcessingModule;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.Vec3;

@ToolchainSupport()
public class CentroSymmetryModule extends ClonableProcessingModule {

	private static DataColumnInfo centroSymmetryColumn = new DataColumnInfo("Centrosymmetry" , "CSD" ,"");
	
	@ExportableValue
	private float radius = 0f;
	@ExportableValue
	private int maxBonds = 0;
	@ExportableValue
	private boolean adaptiveCentroSymmetry = false;
	@ExportableValue
	private float scaling = 1f;
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{centroSymmetryColumn};
	}
	
	@Override
	public String getShortName() {
		return "Centrosymmetry deviation";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the centrosymmetry deviation (CSD) per atom, originally described in"
				+ " Kelchner et al. \"Dislocation nucleation and defect structure during surface indentation\", "
				+ "Phys. Rev. B (58), 1998. "
				+ "<br>The implementation here is generalized in certain aspects: "
				+ "The CSD value is divided by the given radius, in order to be invariant to different lattice constant. "
				+ "Furthermore, it can compute the centrosymmetry for an arbitrary number of bonds, or a fixed number of nearest neighbors. "
				+ "<br>The results of the original algorithm can be reproduced if the scaling factor is set equal to the cut-off radius and the "
				+ "number of bonds is limited to 12.";
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
		final float[] csdArray = data.getDataArray(data.getDataColumnIndex(centroSymmetryColumn)).getData();
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), radius, true);
		nnb.addAll(data.getAtoms());		
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		final int progressBarUpdateInterval = Math.min(1, (int)(data.getAtoms().size()/200));
		//Parallel calculation of volume/density, iterate over all indices in a stream
		IntStream.range(0, data.getAtoms().size()).parallel().forEach(i -> {
			if (i%progressBarUpdateInterval == 0)
				ProgressMonitor.getProgressMonitor().addToCounter(progressBarUpdateInterval);
			
			Atom a = data.getAtoms().get(i);

			float csd = 0f;
			
			ArrayList<Vec3> neigh;
			if (adaptiveCentroSymmetry)
				neigh = nnb.getNeighVec(a, maxBonds);
			else neigh = nnb.getNeighVec(a);
			
			boolean[] paired = new boolean[neigh.size()];
			for (int j=0; j<neigh.size(); j++){
				
				if (!paired[j]){
					Vec3 inv = neigh.get(j).multiplyClone(-1f);
					int minIndex = j;
					float minDistance = 4*radius*radius;
					for (int k=j+1; k<neigh.size(); k++){
						float d = inv.getSqrDistTo(neigh.get(k));
						if (d<minDistance) {
							minIndex = k;
							minDistance = d;
						}
					}
					
					csd += minDistance;
					paired[minIndex] = true;
				}
			}
			
			csd /= radius;
			csd *= scaling;
			csdArray[i] = csd;
		});
		
		ProgressMonitor.getProgressMonitor().stop();
		
		return null;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Centrosymmetry deviation");
		dialog.addLabel(getFunctionDescription());
		float def;
		if (this.radius == 0f)
			def = data.getCrystalStructure().getNearestNeighborSearchRadius();
		else
			def = radius;
		
		FloatProperty avRadius = dialog.addFloat("avRadius", "Radius of a sphere to find neighbors."
				, "", def, 0f, 1e10f);
		BooleanProperty adaptive = dialog.addBoolean("adaptive", "Adaptive Centrosymmetry, "
				+ "only consider a fixed number of nearest neighbors", 
				"If more neighbors are found, the farthest once are excluded", adaptiveCentroSymmetry);
		IntegerProperty maxBonds = dialog.addInteger("maxBonds", "Maximum number of bonds for adaptive centrosymmetry",
				"", this.maxBonds, 0, 100000);
		FloatProperty scaling = dialog.addFloat("scalingFactor", "Scaling factor for the result."
				+ "", "", this.scaling, 0f, 1e20f);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.radius = avRadius.getValue();
			this.adaptiveCentroSymmetry = adaptive.getValue();
			this.maxBonds = maxBonds.getValue();
			this.scaling = scaling.getValue();
		}
		return ok;
	}
}
