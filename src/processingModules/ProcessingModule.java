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

package processingModules;

import javax.swing.JFrame;

import model.AtomData;
import model.DataColumnInfo;
import processingModules.toolchain.Toolchain.ReferenceMode;

public interface ProcessingModule extends Cloneable {
	
	/**
	 * A short name for the module that can be displayed in a list
	 * @return
	 */
	public String getShortName();
	
	/**
	 * Describes what the processing module is doing
	 * @return
	 */
	public String getFunctionDescription();
	
	/**
	 * Describes what the processing module requires to work
	 * @return
	 */
	public String getRequirementDescription();
	
	/**
	 * Test if the module can be applied in an instance of AtomData
	 * @return
	 */
	public boolean isApplicable(final AtomData data);
	
	/**
	 * Return if the module can be applied to multiple files at once.
	 * Processing modules that are dependent on e.g. external files that
	 * differ for each set of AtomData will return false 
	 * @return
	 */
	public boolean canBeAppliedToMultipleFilesAtOnce();
	
	/**
	 * Creates a dialog to set parameters necessary for processing by a user
	 * @return true if the parameters are properly set and the data can be processed
	 * false if the processing is cancelled for any reason
	 * @param frame the parent frame to show dialogs
	 * @param data The current Atom data
	 */
	public boolean showConfigurationDialog(JFrame frame, AtomData data);
	
	/**
	 * Return the data columns that this module will produce.
	 * Can be null if no extra column is created
	 * If the array is not null, the same instances of DataColumnInfo must be
	 * returned on every call
	 * @return
	 */
	public DataColumnInfo[] getDataColumnsInfo();
	
	/**
	 * Apply the module on data
	 * @param data
	 * @return An instance of ProcessingResults if any information needs to be displayed on screen
	 * if no results are needed, returning null is possible
	 * @throws Exception
	 */
	public ProcessingResult process(final AtomData data) throws Exception;
	
	/**
	 * Creates a clone of this module
	 * @return
	 */
	public ProcessingModule clone();
	
	/**
	 * Return which reference mode the processing module is using.
	 * If null is returned, no references are needed. 
	 * @return
	 */
	public ReferenceMode getReferenceModeUsed();
}
