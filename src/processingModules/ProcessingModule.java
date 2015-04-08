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

import model.AtomData;
import model.DataColumnInfo;

public interface ProcessingModule {
	
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
	 * Test if the module can be applied in the currently defined toolchain
	 * @return
	 */
	public boolean isApplicable();
	
	/**
	 * Return the data columns that this module will produce.
	 * Can be null if no extra column is created 
	 * @return
	 */
	public DataColumnInfo[] getDataColumnsInfo();
	
	/**
	 * Apply the module on data
	 * @param data
	 * @throws Exception
	 */
	public void process(final AtomData data) throws Exception;
}
