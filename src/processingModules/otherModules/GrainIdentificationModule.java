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

package processingModules.otherModules;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.*;

import javax.swing.JFrame;

import model.*;
import model.polygrain.Grain;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport()
public class GrainIdentificationModule extends ClonableProcessingModule {

	@ExportableValue
	private boolean orderGrainsBySize = false;
	@ExportableValue
	private float meshSize;
	@ExportableValue
	private float filterGrainsDistance;
	
	@Override
	public String getShortName() {
		return "Grain identification";
	}

	@Override
	public String getFunctionDescription() {
		return "Performs a grain or phase identification. Functionality depends on the crystal structure";
	}

	@Override
	public String getRequirementDescription() {
		return null;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, getShortName());
		dialog.addLabel(getFunctionDescription());
		
		BooleanProperty orderGrains = dialog.addBoolean("orderGrains", "Order grains by volume",
				"", false);
		
		FloatProperty initialMesh = dialog.addFloat("grainBoundaryMeshSize", "Initial grain boundary mesh size",
						"Grains are wrapped with an initial mesh that are iteratively optimized."
						+ "This value defines how accurate the initial approximation is."
						+ "Smaller values provide more detailed meshes, but increase time to be created."
						+ "Min: 1.0, Max: 10.0",
						5.0f, 1.0f, 100.0f);
		
		FloatProperty filterGrains = dialog.addFloat("gbFilterDist", "Grain boundary filter distance",
						"If grain boundaries are to be detected,"
						+ "atoms in their vicinity can be excluded from dislocation networks."
						+ "This value defines the maximum distance at which atoms are excluded."
						+ "If set to zero, the feature is disabled completely."
						+ "IMPORTANT: This feature can be very time consuming."
						+ "Min: 0, Max: 25", 0f, 0f, 25f);		
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.orderGrainsBySize = orderGrains.getValue();
			this.meshSize = initialMesh.getValue();
			this.filterGrainsDistance = filterGrains.getValue();
		}
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		
		ProgressMonitor.getProgressMonitor().setActivityName("Identifying grains");
		final List<Grain> gr = data.getCrystalStructure().identifyGrains(data, meshSize);
		
		ProgressMonitor.getProgressMonitor().setActivityName("Processing grains");
		
		if (orderGrainsBySize){
			ArrayList<Grain> sortedGrains = new ArrayList<Grain>(gr);
			for (Grain g : sortedGrains)
				g.getMesh();
			
			Collections.sort(gr, new Comparator<Grain>() {
				@Override
				public int compare(Grain o1, Grain o2) {
					double diff = o1.getMesh().getVolume() - o2.getMesh().getVolume();
					if (diff<0.) return 1;
					if (diff>0.) return -1;
					return 0;
				}
			});
			
			for (int i=0; i<sortedGrains.size(); i++){
				sortedGrains.get(i).renumberGrain(i);
				for (Atom a : sortedGrains.get(i).getAtomsInGrain())
					a.setGrain(i);
			}	
		}
		
		Grain.processGrains(data, filterGrainsDistance);
		
		for (Grain g : gr) {
			data.addGrain(g);
			// Make sure all meshes are calculated if grains are imported
			g.getMesh();
		}
		
		return null;
	}

}
