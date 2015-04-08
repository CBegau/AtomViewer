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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import gui.JMDFileChooser;
import gui.ProgressMonitor;

import javax.swing.SwingWorker;

import common.Vec3;
import model.*;
import model.polygrain.Grain;

public abstract class MDFileLoader extends SwingWorker<AtomData, String> {
	public enum InputFormat {IMD, LAMMPS};
	
	protected JMDFileChooser chooser;
	
	private ProgressMonitor progressMonitor;
	
	public MDFileLoader(JMDFileChooser chooser) {
		this.chooser = chooser;
	}
	
	@Override
	protected final AtomData doInBackground() throws Exception{
		progressMonitor = new ProgressMonitor(this);
		progressMonitor.setActivityName("Reading file");
		AtomData d = readInputData(); 
		
		//Set ranges for customColums
		for (int i=0; i<Configuration.getSizeDataColumns(); i++){
			DataColumnInfo cci = Configuration.getDataColumnInfo(i);
				if (!cci.isInitialRangeGiven()) cci.findRange(true);
		}
		
		progressMonitor.destroy();
		return d;
	}
	
	public ProgressMonitor getProgressMonitor() {
		if (progressMonitor == null)
			progressMonitor = new ProgressMonitor(null);
		return progressMonitor;
	}
	
	/**
	 * Reading of MD input must be implemented here
	 * The recommend way to create is first buffering the data read in an
	 * instance of ImportDataContainer and passing this container into the 
	 * AtomData constructor.
	 * Generating a set of AtomData instances should be supported as well
	 * if this option is selected in the JMDFileChooser instance.
	 * In this case the set of data are connected as a double linked list
	 * by passing the previous created instance of AtomData as an argument in
	 * the constructor. 
	 * @return An instance of AtomData read from file, possibly linking to additional data sets 
	 * @throws Exception May be any kind of error during reading and processing
	 */
	protected abstract AtomData readInputData() throws Exception;
	
	/**
	 * Creates a single instance of AtomData from {@code f}.
	 * For importing a sequence of files or more functionality use the 
	 * @param f the file to read
	 * @return an instance of AtomData
	 * @throws IOException
	 */
	public abstract AtomData readInputData(File f) throws IOException;
	
	
	/**
	 * Reads the header from a file and returns an array containing the
	 * names of all optionally importable values
	 * @param f The file to read the header from
	 * @return array with Strings of optional values
	 * @throws IOException
	 */
	public abstract String[] getColumnsNamesFromHeader(File f) throws IOException;
	
	/**
	 * Data from MD input must be stored in this container and then passed into 
	 */
	public static class ImportDataContainer{
		public Vec3 boxSizeX = new Vec3();
		public Vec3 boxSizeY = new Vec3();
		public Vec3 boxSizeZ = new Vec3();
		
		public Vec3 offset = new Vec3();
		/**
		 * All atoms are stored in this list 
		 */
		public ArrayList<Atom> atoms = new ArrayList<Atom>();
		/**
		 * Only used in polycrystalline / polyphase material:
		 * Each subgrain is assigned a number, each atom is assigned a number of the grain its belongs to
		 */
		public HashMap<Integer, Grain> grains = new HashMap<Integer, Grain>();
		/**
		 * The largest (virtual) elements number found in all imported files
		 */
		public byte maxElementNumber = 1;
		//Some flags for imported or calculated values
		//Set to true if values are imported from files
		public boolean rbvAvailable = false;
		public boolean atomTypesAvailable = false;
		public boolean grainsImported = false;
		public boolean meshImported = false;
		
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
		
		public void makeBox(){
			box = new BoxParameter(boxSizeX, boxSizeY, boxSizeZ);
			box.setOffset(offset);
		}
		
		public synchronized void addAtom(Atom a){
			atoms.add(a);
		}
	}
}
