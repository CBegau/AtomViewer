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

package model;

import gui.JLogPanel;
import gui.ProgressMonitor;

import java.util.*;

import processingModules.*;
import processingModules.atomicModules.AtomClassificationModule;
import processingModules.otherModules.DeleteColumnModule;
import processingModules.otherModules.VectorNormModule;
import processingModules.toolchain.Toolchain;
import model.ImportConfiguration.ImportStates;
import model.io.MDFileLoader;
import model.polygrain.*;
import common.*;
import crystalStructures.CrystalStructure;
import gnu.trove.list.array.TFloatArrayList;

public class AtomData {
	/**
	 * Simulation box size, origin is at (0,0,0)
	 * Atoms outside this box are permitted, but this causes reduced performance in many routines based 
	 * on nearest neighbors.
	 */
	private BoxParameter box;
	
	/**
	 * All information on the level of individual atoms (position/velocities...)
	 * is stored in this container class
	 */
	private final AtomicData atomicData;
	
	/**
	 * Only used in polycrystalline / polyphase material:
	 * Each subgrain is assigned a number, each atom is assigned a number of the grain its belongs to
	 */
	private HashMap<Integer, Grain> grains = new HashMap<>();
	private boolean grainsImported = false;
	
	/**
	 * The largest (virtual) elements number
	 */
	private int maxNumElements = 1;
	
	private String[] elementNames;
	
	/**
	 * Meta data found in the file header
	 */
	private Map<String, Object> fileMetaData = null;
	/**
	 * Identifier of the simulation, usually the filename
	 */
	private String name;
	
	/**
	 * Full path and filename from which the file is read
	 */
	private String fullPathAndFilename;
	
	/**
	 * Data of any type, providing their own rendering routines and
	 * control panels are stored in this list.
	 */
	private ArrayList<DataContainer> additionalData = new ArrayList<>();
	
	/**
	 * Container storing resultant Burgers vectors
	 * TODO: This should be replaced by a derived class of the generic DataContainer
	 * For historic reasons, this module is still somewhat privileged
	 */
	private RBVStorage rbvStorage = new RBVStorage();
	
	private int[] atomsPerElement = new int[0];
	private int[] atomsPerType;
	private AtomData next, previous;
	
	private CrystalStructure defaultCrystalStructure;
	private CrystalRotationTools crystalRotation;

	private Toolchain toolchain = new Toolchain();
	
	//Flag that this file is the reference for a processing module
	//There is always only maximum one reference 
	private boolean isReferenceForProcessingModule = false;
	
	public AtomData(AtomData previous, MDFileLoader.ImportDataContainer idc) throws Exception{
		this.atomicData = new AtomicData(idc.atoms, idc.dataArrays, ImportConfiguration.getInstance().getDataColumns());
		
		this.box = idc.box;
		this.maxNumElements = idc.maxElementNumber;
		this.rbvStorage = idc.rbvStorage;
		this.grainsImported = idc.grainsImported;
		this.fileMetaData = idc.fileMetaData;
		this.name = idc.name;
		this.fullPathAndFilename = idc.fullPathAndFilename;
		
		this.defaultCrystalStructure = ImportConfiguration.getInstance().getCrystalStructure();
		this.crystalRotation = new CrystalRotationTools(defaultCrystalStructure, 
				ImportConfiguration.getInstance().getCrystalOrientation());
		
		//Assign the names of elements if provided in the input file
		this.elementNames = new String[maxNumElements];
		for (int i=0; i<maxNumElements;i++){
			if (idc.elementNames.containsKey(i))
				this.elementNames[i] = idc.elementNames.get(i);
		}
		//Alternatively use names provided by the crystal structure
		if (this.defaultCrystalStructure.getNamesOfElements() != null){
			String[] names = this.defaultCrystalStructure.getNamesOfElements();
			int elements = this.defaultCrystalStructure.getNumberOfElements();
			for (int i=0; i<maxNumElements;i++){
				this.elementNames[i] = names[i%elements];
			}
		}
		
		this.setPrevious(previous);
		this.processInputData(idc);
	}
	
	/**
	 * After the raw data is read from a file and copied into this instance of AtomData
	 * additional processing steps are executed in this  
	 * @param idc
	 * @throws Exception
	 */
	private void processInputData(MDFileLoader.ImportDataContainer idc) throws Exception{
		//Scale the data columns values of the remaining atoms
		for (int i=0; i < atomicData.dataColumns.size(); i++){
			float scale = atomicData.dataColumns.get(i).getScalingFactor();
			if (scale != 1f){
				TFloatArrayList values = atomicData.dataArrays.get(i);
				for (int j=0; j<values.size(); j++)
					values.setQuick(j, values.getQuick(j)*scale);
			}
		}
		
		for (DataColumnInfo dci : atomicData.dataColumns){
			if (dci.isFirstVectorComponent()){
				ProgressMonitor.getProgressMonitor().setActivityName("Compute norm of imported vectors");
				new VectorNormModule(dci).process(this);
			}
		}
		
		Toolchain t = defaultCrystalStructure.getToolchainToApplyAtBeginningOfAnalysis();
		if (t != null){
			for (ProcessingModule pm : t.getProcessingModules())
				this.applyProcessingModule(pm);
		}
		
		//Bond Angle Analysis
		if (!idc.atomTypesAvailable){
			this.applyProcessingModule(new AtomClassificationModule());
		} else {
			countAtomTypes();
		}
		
		if (isGrainsImported()){
			ProgressMonitor.getProgressMonitor().setActivityName("Processing grains");
			final List<Grain> gr = defaultCrystalStructure.identifyGrains(this, 0f);
			for (Grain g : gr){
				this.addGrain(g);
				g.getMesh(); //ensure the mesh is created in the worker thread
			}
		}
		
		ProgressMonitor.getProgressMonitor().setActivityName("Finalizing file");
		
		if (ImportStates.DISPOSE_DEFAULT.isActive()){ //Dispose perfect atoms
			final int defaultType = defaultCrystalStructure.getDefaultType();
			this.removeAtoms(new Filter<Atom>() {
				@Override
				public boolean accept(Atom a) {
					return a.getType() != defaultType;
				}
			});
			countAtomTypes();
		}
	}

	public List<DataContainer> getAdditionalData() {
		return Collections.unmodifiableList(additionalData);
	}
	
	/**
	 * Adds a set of DataColumnInfos to the data. Adds only non-existing columns.
	 * @param dci
	 */
	private void addDataColumnInfo(DataColumnInfo ... dci){
		atomicData.addDataColumnInfo(dci);
	}
	
	/**
	 * Removes a DataColumnInfo
	 * This method should only be called internally
	 * For public access an instance of {@link DeleteColumnModule} should be created
	 * and passed to {@link #applyProcessingModule(ProcessingModule) applyProcessingModule} method.
	 * This way it will be correctly recorded in a toolchain if needed 
	 * @param dci
	 */
	public void removeDataColumnInfo(DataColumnInfo dci){
		atomicData.removeDataColumnInfo(dci);
	}
	
	public FastTFloatArrayList getDataArray(int index){
		assert(index < atomicData.dataArrays.size());
		return atomicData.dataArrays.get(index);
	}
	
	/**
	 * Apply a processing module
	 * If a toolchain is currently recording, this step will automatically being added
	 * @param pm
	 * @throws Exception
	 */
	public void applyProcessingModule(ProcessingModule pm) throws Exception{
		if (pm.isApplicable(this)){
			ProgressMonitor.getProgressMonitor().setCurrentFilename(this.getName());
			ProgressMonitor.getProgressMonitor().setActivityName(pm.getShortName());

			//Store step in Toolchain
			this.toolchain.addModule(pm);
			this.addDataColumnInfo(pm.getDataColumnsInfo());
			
			ProcessingResult pr = pm.process(this);
			
			if (pr != null){
				if (pr.getDataContainer() != null)
					this.addAdditionalData(pr.getDataContainer());
				
				if (pr.getResultInfoString()!=null && !pr.getResultInfoString().isEmpty())
					JLogPanel.getJLogPanel().addInfo(String.format("Results: %s (%s)", pm.getShortName(), this.getName()), 
							pr.getResultInfoString());
			}
			if (pm.getDataColumnsInfo() != null){
				for (DataColumnInfo dci : pm.getDataColumnsInfo())
					if (!dci.isInitialized())
						dci.findRange(this, false);
			}
		}
	}
	
	

	public void countAtomTypes() {
		this.atomsPerType = new int[defaultCrystalStructure.getNumberOfTypes()];
		this.atomsPerElement = new int[maxNumElements];
		int warnings = 0;
		
		for (Atom a : atomicData.atoms){
			if (a.getType() >= atomsPerType.length || a.getType()<0){
				a.setType(0);
				warnings++;
			}
			atomsPerType[a.getType()]++;		
			atomsPerElement[a.getElement()]++;
		}
		
		if (warnings > 0){
			JLogPanel.getJLogPanel().addWarning("Type ID exceeds number of defined types.", 
					String.format("%d atoms had a type ID that was larger than the number of types defined for the structure. "
							+ "These atoms were reassigned to type 0.", warnings));
		}
	}
	
	/**
	 * Build a nearest neighbor graph and return the neighbor configuration
	 * @param atomsToPlot
	 * @return The nearest neighbor graph, formatted using html 
	 */
	public StringBuilder plotNeighborsGraph(final Atom... atomsToPlot){
		StringBuilder sb = new StringBuilder();
		final float d = defaultCrystalStructure.getNearestNeighborSearchRadius();
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<>(box, d, true);
		Filter<Atom> filter = new Filter<Atom>() {
			@Override
			public boolean accept(Atom a) {
				for (Atom b : atomsToPlot){
					if (box.getPbcCorrectedDirection(a, b).getLength()<=d)
						return true;
				}
				return false;
			}
		};
		nnb.addAll(atomicData.atoms, filter);
		
		sb.append("<html><body>");
		for (Atom a : atomsToPlot){
			ArrayList<Tupel<Atom, Vec3>> t =nnb.getNeighAndNeighVec(a);
			sb.append(String.format("Neighbor configuration of atom %d"
					+ "<br>Number of neighbors: %d", a.getNumber(), t.size()));
			
			sb.append("<table>");
			sb.append("<tr> <td>number</td> <td>distance x</td> <td> distance y</td> "
					+ "<td> distance z</td> <td>distance</td> <td>element</td> </tr>");

			for (int i=0; i< t.size(); i++){
				String s = String.format("<tr><td>%d</td><td>%f</td><td>%f</td><td>%f</td><td>%f</td><td>%d</td></tr>"
						, t.get(i).o1.getNumber(),t.get(i).o2.x, t.get(i).o2.y, t.get(i).o2.z, 
						t.get(i).o2.getLength(), t.get(i).o1.getElement());
				sb.append(s);
			}
			sb.append("</table>");
			
			int k=0;
			float[][] angles = new float[t.size()*(t.size()-1)/2][6];
			sb.append("<p>");
			sb.append("Bond angles: "+angles.length);
			sb.append("</p>");
			
			sb.append("<table>");
			sb.append("<tr> <td>number1</td> <td>number2</td> <td>angle</td>"
					+ " <td>element1</td><td>center</td><td>element2</td> </tr>");
			for (int i=0; i<t.size(); i++){
				for (int j=i+1; j< t.size(); j++){
					angles[k][0] = (float)Math.toDegrees(t.get(i).o2.getAngle(t.get(j).o2));
					angles[k][1] = t.get(i).o1.getNumber();
					angles[k][2] = t.get(j).o1.getNumber();
					
					angles[k][3] = t.get(i).o1.getElement();
					angles[k][4] = a.getElement();
					angles[k][5] = t.get(j).o1.getElement();
					k++;
				}	
			}
			Arrays.sort(angles, new Comparator<float[]>() {
				@Override
				public int compare(float[] o1, float[] o2) {
					if (o1[0] < o2[0]) return 1;
					if (o1[0] == o2[0]) return 0;
					return -1;
				}
			});
			
			for (int i=0; i<angles.length; i++){
				String s = String.format("<tr> <td>%d</td> <td>%d</td> <td>%f°</td> "
						+ "<td>%d</td> <td>%d</td> <td>%d</td> </tr>", (int)angles[i][1], (int)angles[i][2],
						angles[i][0], (int)angles[i][3], (int)angles[i][4], (int)angles[i][5]);
				sb.append(s);
			}
			sb.append("</table>");
		}
		sb.append("</body></html>");
		
		return sb;
	}
	
	public BoxParameter getBox() {
		return box;
	}
	
	public List<Atom> getAtoms(){
		return atomicData.atoms;
	}
	
	public String getName() {
		return name;
	}
	
	public String getFullPathAndFilename() {
		return fullPathAndFilename;
	}
	
	public AtomData getNext() {
		return next;
	}
	
	public AtomData getPrevious() {
		return previous;
	}
	
	/**
	 * Change the previous AtomData in the double linked list
	 * Automatically updates the next reference in previous 
	 * @param previous
	 */
	public void setPrevious(AtomData previous) {
		this.previous = previous;
		if (previous != null)
			previous.next = this;
	}
	
	public void setNextToNull() {
		this.next = null;
	}
	
	public CrystalStructure getCrystalStructure() {
		return defaultCrystalStructure;
	}
	
	public boolean isReferenceForProcessingModule() {
		return isReferenceForProcessingModule;
	}
	
	public void setAsReferenceForProcessingModule() {
		Configuration.getAtomDataIterable(this).forEach(data->isReferenceForProcessingModule = false);
		this.isReferenceForProcessingModule = true;
	}
	
	public CrystalRotationTools getCrystalRotation() {
		return crystalRotation;
	}
	
	public Toolchain getToolchain() {
		return toolchain;
	}
	
	/**
	 * Return the index at which the values for DataColumnInfo are stored
	 * or -1 if not present
	 * @param dci
	 * @return
	 */
	public int getDataColumnIndex(DataColumnInfo dci){
		for (int i=0; i < atomicData.dataColumns.size(); i++)
			if (dci.equals(atomicData.dataColumns.get(i)))
				return i;
		return -1;
	}
	
	/**
	 * Returns the first index of DataColumnInfo having the given component.
	 * If the component is not found, -1 is returned 
	 * @param dci
	 * @return
	 */
	public int getComponentIndex(DataColumnInfo.Component component){
		for (int i=0; i < atomicData.dataColumns.size(); i++)
			if (atomicData.dataColumns.get(i).getComponent().equals(component))
				return i;
		return -1;
	}
	
	public List<DataColumnInfo> getDataColumnInfos(){
		return atomicData.dataColumns;
	}
	
	public int getNumberOfAtomsWithType(int i){
		if (i>atomsPerType.length) return 0;
		return atomsPerType[i];
	}
	
	/**
	 * Return the number of different virtual elements types used in this set 
	 * @return
	 */
	public int getNumberOfElements() {
		return maxNumElements;
	}
	
	public int getNumberOfAtomsOfElement(int i){
		if (i>=atomsPerElement.length) return 0;
		return atomsPerElement[i];
	}
	
	public String getNameOfElement(int i){
		if (i>=elementNames.length) return "";
		return elementNames[i] == null ? "" : elementNames[i];
	}
	
	public Collection<Grain> getGrains() {
		return grains.values();
	}
	
	public void addGrain(Grain g) {
		grains.put(g.getGrainNumber(), g);
	}
	
	public Grain getGrains(int grain) {
		return grains.get(grain);
	}
	
	public boolean isGrainsImported() {
		return grainsImported;
	}
	
	public boolean isPolyCrystalline(){
		return grains.size() != 0;
	}
	
	public DataContainer getDataContainer(Class<? extends DataContainer> clazz){
		for (DataContainer dc : additionalData)
			if (dc.getClass().isAssignableFrom(clazz))
				return dc;
		return null;
	}
	
	public Object getFileMetaData(String s) {
		if (fileMetaData == null) return null;
		return fileMetaData.get(s);
	}

	public void removeAtoms(Filter<Atom> filter){
		atomicData.removeAtoms(filter);
	}
	
	/**
	 * Removes the references to other instances of AtomData and the internal container
	 * storing possibly very large amount of data, permitting the GC to do its work 
	 * Calling this method is useful before other files are loaded to prevent memory shortcomings
	 */
	public void clear(){
		this.atomicData.clear();
		this.additionalData.clear();
		if (this.previous != null) previous.next = this.next;
		this.next = null;
		this.previous = null;
	}
	
	public void setRbvStorage(RBVStorage rbvStorage) {
		this.rbvStorage = rbvStorage;
	}
	
	public RBVStorage getRbvStorage() {
		return rbvStorage;
	}
	
	public boolean isRbvAvailable() {
		return !rbvStorage.isEmpty();
	}
	
	private void addAdditionalData(DataContainer dc){
		boolean replaced = false;
		for (int i=0; i<this.additionalData.size(); i++){
			if (this.additionalData.get(i).getClass() == dc.getClass()){
				this.additionalData.set(i,dc);
				replaced = true;
				break;
			}
		}
		if (!replaced) this.additionalData.add(dc);
	}
	
	/**
	 * Removes a data container
	 * @param dc
	 */
	public void removeDataContainer(DataContainer dc){
        this.additionalData.remove(dc);
        Configuration.setCurrentAtomData(this, true, false);
    }
	
	
	@Override
	public String toString() {
		return getName();
	}
	
	
	/**
	 * This is a ugly dirty workaround to access the defect marking used for labeling
	 * @return
	 */
	public DefectMarking getDefectMarking() {
		Object o = this.getFileMetaData("marks");
		if (o == null)
			return null;
		
		DefectMarking marks = (DefectMarking)o;
		return marks;
	}
	
	/**
	 * A simple container class that stores all data related to individual atoms
	 */
	private class AtomicData{
		/**
		 * All atoms are stored in this list 
		 */
		final FastDeletableArrayList<Atom> atoms;
		/**
		 * Additional data per atom that is either imported from file or dynamically created
		 * using processing modules is store within these lists
		 * The data associated to a specific atom is stored at the same index as the atom itself in 
		 * the list of atoms 
		 */
		final List<FastTFloatArrayList> dataArrays;
		/**
		 * The metadata containing information what kind of data is stored in dataArrays
		 * dataArrays and dataColumns are of same size
		 */
		final List<DataColumnInfo> dataColumns = new ArrayList<DataColumnInfo>();
		
		public AtomicData(FastDeletableArrayList<Atom> atoms, List<FastTFloatArrayList> dataArrays,
				List<DataColumnInfo> dataColumns) {
			this.atoms = atoms;
			this.dataArrays = dataArrays;
			this.dataColumns.addAll(dataColumns);
		
			for (int i=0; i<this.atoms.size(); i++){
				this.atoms.get(i).setID(i);
			}	
			
			//Create arrays for atomic data filled with zeros if this specific information was not contained
			//in the input file. This can be the case if different files are read at the same time and some files
			//are missing information
			for (int i=0; i<this.dataArrays.size(); i++){
				if (this.dataArrays.get(i).isEmpty())
					this.dataArrays.set(i, new FastTFloatArrayList(this.atoms.size(), true));
			}
			
			cleanup();
		}
		
		private void cleanup(){
			//(Re-)Initialize the IDs for each atom to match the position in the arrays
			for (int j=0; j<atoms.size(); j++)
				atoms.get(j).setID(j);
			//Shrink down the arrays
			atoms.trimToSize();
			for (TFloatArrayList f: this.dataArrays){
				f.trimToSize();
				//Atoms and data arrays should be of same size
				assert (f.size() == atoms.size());
			}
			
			assert(dataColumns.size() == dataArrays.size());
		}
		
		void clear(){
			atoms.clear();
			atoms.trimToSize();
			dataArrays.clear();
			dataColumns.clear();
		}
		
		void addDataColumnInfo(DataColumnInfo ... dci){
			if (dci == null) return;
			for (DataColumnInfo d : dci)
				if (!dataColumns.contains(d)) {
					dataColumns.add(d);
					dataArrays.add(new FastTFloatArrayList(this.atoms.size(),true));
				}
		}
		
		void removeDataColumnInfo(DataColumnInfo dci){
			if (dci.isVectorComponent()){
				//Delete a complete vector component
				for (DataColumnInfo d : dci.getVectorComponents()){
					int index = dataColumns.indexOf(d);
					dataColumns.remove(index);
					dataArrays.remove(index);
				}
			} else { 
				//Delete a scalar value
				if (dataColumns.contains(dci)){
					int index = dataColumns.indexOf(dci);
					dataColumns.remove(index);
					dataArrays.remove(index);
				}
			}
		}
		
		void removeAtoms(Filter<Atom> filter){
			if (filter == null) return;
			int size = atoms.size();
			int i=0;
		
			while (i<size){
				if (filter.accept(atoms.get(i))){
					i++;
				} else {
					//These are fast removes. The last element is copied to
					//the position i and the number of elements in the
					//lists are reduced by one. Order is not preserved, but
					//deleting any number of element from linear lists is in total an O(n) operation
					size--;
					Atom rmAtom = atoms.remove(i);
					for (FastTFloatArrayList f: dataArrays)
						f.removeFast(i);
					if (rbvStorage != null)
						rbvStorage.removeAtom(rmAtom);
				}
			}
			
			cleanup();
			countAtomTypes();
		}
	}
}