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

package model.io;

import java.io.File;

import javax.swing.filechooser.FileFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gui.PrimitiveProperty;
import gui.ProgressMonitor;

import javax.swing.SwingWorker;

import common.FastDeletableArrayList;
import common.FastTFloatArrayList;
import common.Vec3;
import model.*;
import model.ImportConfiguration.ImportStates;

public abstract class MDFileLoader{
	protected File[] filesToRead;
	
	public SwingWorker<AtomData, String> getNewSwingWorker(){
		return new Worker();
	}
	
	private class Worker extends  SwingWorker<AtomData, String>{
		@Override
		protected final AtomData doInBackground() throws Exception{
			ProgressMonitor progressMonitor = ProgressMonitor.createNewProgressMonitor(this);
			
			progressMonitor.setActivityName("Reading file");
			
			AtomData toReturn = null;
			
			AtomData previous = null;
			//If new files are to be appended on the current file set, get the
			//last in the set of currently opened files
			if (ImportStates.APPEND_FILES.isActive()){
				previous = Configuration.getCurrentAtomData();
				while (previous.getNext()!=null) previous = previous.getNext();
			}
			
			for (File f : filesToRead){
				ProgressMonitor.getProgressMonitor().setCurrentFilename(f.getName());
				Filter<Atom> filter = ImportConfiguration.getInstance().getCrystalStructure().getIgnoreAtomsDuringImportFilter();
				toReturn = readInputData(f, previous, filter); 
				previous = toReturn;
			}
					
			//Set ranges for customColums
			for (DataColumnInfo cci : toReturn.getDataColumnInfos())
				if (!cci.isInitialRangeGiven()) cci.findRange(toReturn, true);
			
			progressMonitor.destroy();
			filesToRead = null;
			return toReturn;
		}
	}
	
	
	
	public void setFilesToRead(File[] files){
		this.filesToRead = files;
	}
	
	/**
	 * Creates a single instance of AtomData from {@code f}. 
	 * @param f the file containing the atomic data
	 * @param previous an instance of AtomData that is the previous data in a linked list.
	 * May be null if this is the first file in a list.
	 * @param atomFilter A filter that ignores certain atoms already during import.
	 * Can be null, in which case no atoms are filtered 
	 * @return An instance of AtomData read from file, possibly linking to a previous data sets
	 * @throws IOException
	 */
	public abstract AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws Exception;
	
	public abstract FileFilter getDefaultFileFilter();
	
	/**
	 * Reads the header from a file and returns an array containing the
	 * names of all optionally importable values and their units (if available)
	 * @param f The file to read the header from
	 * @return array with Strings of optional values first entry is the value name, second the unit
	 * @throws IOException
	 */
	public abstract String[][] getColumnNamesUnitsFromHeader(File f) throws IOException;
	
	/**
	 * Provides a name for this type of file reader
	 * @return
	 */
	public abstract String getName();
	
	public abstract Map<String, DataColumnInfo.Component> getDefaultNamesForComponents();
	
	public List<PrimitiveProperty<?>> getOptions(){
		return new ArrayList<PrimitiveProperty<?>>();
	}
	
	/**
	 * Data from MD input must be stored in this container and then passed into 
	 */
	public static class ImportDataContainer{
		public Vec3 boxSizeX = new Vec3();
		public Vec3 boxSizeY = new Vec3();
		public Vec3 boxSizeZ = new Vec3();
		
		public Vec3 offset = new Vec3();
		public boolean[] pbc = ImportConfiguration.getInstance().getPeriodicBoundaryConditions().clone();
		
		public RBVStorage rbvStorage = new RBVStorage();
		
		/**
		 * All atoms are stored in this list 
		 */
		public FastDeletableArrayList<Atom> atoms = new FastDeletableArrayList<Atom>();
		
		public List<FastTFloatArrayList> dataValues = new ArrayList<FastTFloatArrayList>();
		
		/**
		 * The largest (virtual) elements number found in all imported files
		 */
		public byte maxElementNumber = 1;
		
		/**
		 * A map for the element names
		 */
		public Map<Integer, String> elementNames = new HashMap<Integer, String>();
		
		//Some flags for imported or calculated values
		//Set to true if values are imported from files
		public boolean rbvAvailable = false;
		public boolean atomTypesAvailable = false;
		public boolean grainsImported = false;
		
		/**
		 * Meta data found in the file header
		 */
		public Map<String, Object> fileMetaData = null;
		/**
		 * Identifier of the simulation, usually the filename
		 */
		public String name;
		
		/**
		 * The fully qualified filename for the file
		 */
		public String fullPathAndFilename;
		
		public BoxParameter box;
		
		public ImportDataContainer() {
			for (int i=0; i<ImportConfiguration.getInstance().getDataColumns().size(); i++)
				dataValues.add(new FastTFloatArrayList());
		}
		
		public void makeBox(){
			box = new BoxParameter(boxSizeX, boxSizeY, boxSizeZ, pbc[0], pbc[1], pbc[2]);
			box.setOffset(offset);
		}
	}
}
