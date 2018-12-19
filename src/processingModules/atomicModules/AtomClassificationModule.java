// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2013  ICAMS, Ruhr-Universität Bochum
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

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;

import java.util.*;

import javax.swing.JFrame;

import common.ThreadPool;
import crystalStructures.CrystalStructure;

public class AtomClassificationModule extends ClonableProcessingModule {

	@Override
	public String getShortName() {
		return "Classify atoms";
	}

	@Override
	public String getFunctionDescription() {
		return "Performs a classification for atoms";
	}

	@Override
	public String getRequirementDescription() {
		return "";
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
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		final CrystalStructure cs = data.getCrystalStructure();
		
		NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), 
				cs.getStructuralAnalysisSearchRadius(), true);
		List<Atom> atoms = data.getAtoms();
		
		nnb.addAll(atoms, cs.getFilterForAtomsNotNeedingClassificationByNeighbors());
		
		ThreadPool.executeAsParallelStream(atoms.size(), i-> {
			Atom a = atoms.get(i);
			a.setType(cs.identifyAtomType(a, nnb));	
		});
	
		cs.identifyDefectAtomsPostProcessing(data, nnb);
		
		data.countAtomTypes();
		return null;
	}
}
