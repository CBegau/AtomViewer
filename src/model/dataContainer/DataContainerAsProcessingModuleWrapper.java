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
package model.dataContainer;

import javax.swing.JFrame;

import model.AtomData;
import model.DataColumnInfo;
import processingModules.ProcessingModule;
import processingModules.ProcessingResult;

public class DataContainerAsProcessingModuleWrapper implements ProcessingModule{

	private DataContainer dc;
	private boolean applicableToMultipleFiles;
	
	public DataContainerAsProcessingModuleWrapper(DataContainer dc, boolean applicableToMultipleFiles){
		this.dc = dc;
		this.applicableToMultipleFiles = applicableToMultipleFiles;
	}
	
	@Override
	public String getShortName() {
		return dc.getName();
	}

	@Override
	public String getFunctionDescription() {
		return dc.getDescription();
	}

	@Override
	public String getRequirementDescription() {
		return dc.getRequirementDescription();
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return dc.isApplicable(data);
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return applicableToMultipleFiles;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return dc.showOptionsDialog();
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		final DataContainer clonedDC = dc.deriveNewInstance();
		boolean success = clonedDC.processData(data);
		if (!success) throw new RuntimeException(String.format("Failed to create %s", dc.getName()));
		
		ProcessingResult pr = new ProcessingResult() {
			
			@Override
			public String getResultInfoString() {
				return clonedDC.getName();
			}
			
			@Override
			public DataContainer getDataContainer() {
				return clonedDC;
			}
		};
		
		return pr;
	}

}
