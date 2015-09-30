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
import processingModules.otherModules.FilteringModule;
import processingModules.otherModules.VectorNormModule;
import processingModules.toolchain.Toolchain;
import model.ImportConfiguration.ImportStates;
import model.io.MDFileLoader;
import model.polygrain.*;
import common.*;
import crystalStructures.CrystalStructure;

public class AtomData {
	/**
	 * Simulation box size, origin is at (0,0,0)
	 * Atoms outside this box, but this reduced performance in many routines based 
	 * on nearest neighbors.
	 */
	private BoxParameter box;
	
	/**
	 * All atoms are stored in this list 
	 */
	private ArrayList<Atom> atoms = new ArrayList<Atom>();
	
	/**
	 * Only used in polycrystalline / polyphase material:
	 * Each subgrain is assigned a number, each atom is assigned a number of the grain its belongs to
	 */
	private HashMap<Integer, Grain> grains = new HashMap<Integer, Grain>();
	/**
	 * The largest (virtual) elements number
	 */
	private int maxNumElements = 1;
	
	private String[] elementNames;
	
	private List<DataColumnInfo> dataColumns = new ArrayList<DataColumnInfo>();
	
	//Some flags for imported or calculated values
	private boolean rbvAvailable = false;
	private boolean grainsImported = false;
	
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
	private ArrayList<DataContainer> additionalData = new ArrayList<DataContainer>();
	
	private int[] atomsPerElement = new int[0];
	private int[] atomsPerType;
	private AtomData next, previous;
	
	private CrystalStructure defaultCrystalStructure;
	private CrystalRotationTools crystalRotation;

	private Toolchain toolchain = new Toolchain();
	
	//Flag that this file is the reference for a processing module
	//There is always only maximum one reference 
	private boolean isReferenceForProcessingModule = false;
	
	public AtomData(AtomData previous, MDFileLoader.ImportDataContainer idc){
		this.defaultCrystalStructure = ImportConfiguration.getInstance().getCrystalStructure();
		
		this.crystalRotation = new CrystalRotationTools(defaultCrystalStructure, 
				ImportConfiguration.getInstance().getCrystalOrientation());
		
		this.atomsPerType = new int[defaultCrystalStructure.getNumberOfTypes()];
		this.box = idc.box;
		this.atoms = idc.atoms;
		
		this.maxNumElements = idc.maxElementNumber;
		
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
		
		this.rbvAvailable = idc.rbvAvailable;
		this.grainsImported = idc.grainsImported;
		this.fileMetaData = idc.fileMetaData;
		this.name = idc.name;
		this.fullPathAndFilename = idc.fullPathAndFilename;
		
		this.dataColumns.addAll(ImportConfiguration.getInstance().getDataColumns());
		
		if (previous!=null){
			previous.next = this;
			this.previous = previous;
		}
		
		try {
			processInputData(idc);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public ArrayList<DataContainer> getAdditionalData() {
		return additionalData;
	}
	
	/**
	 * Adds a set of DataColumnInfos to the data. Adds only non-existing columns.
	 * @param dci
	 */
	private void addDataColumnInfo(DataColumnInfo ... dci){
		if (dci == null) return;
		int added = 0;
		for (DataColumnInfo d : dci)
			if (!dataColumns.contains(d)) {
				dataColumns.add(d);
				added++;
			}
		
		if (added > 0)
			for (Atom a : atoms)
				a.extendDataValuesFields(added);
	}
	
	/**
	 * Removes a DataColumnInfo
	 * This method should only be called internally
	 * For public access an instance of {@link DeleteColumnModule} should be created
	 * and passed to {@link #applyProcessingModule(ProcessingModule) applyProcessingModule} method.
	 * This way it will be correctly recorded in a toolchain is needed 
	 * @param dci
	 */
	public void removeDataColumnInfo(DataColumnInfo dci){
		if (dci.isVectorComponent()){
			//Delete a complete vector component
			for (DataColumnInfo d : dci.getVectorComponents()){
				int index = dataColumns.indexOf(d);
				dataColumns.remove(index);
				for (Atom a : atoms)
					a.deleteDataValueField(index);
			}
		} else { 
			//Delete a scalar value
			if (dataColumns.contains(dci)){
				int index = dataColumns.indexOf(dci);
				dataColumns.remove(index);
				for (Atom a : atoms)
					a.deleteDataValueField(index);
			}
		}
	}
	
	/**
	 * Apply a processing module
	 * If a toolchain is currently recording, this step will automatically being added
	 * @param pm
	 * @throws Exception
	 */
	public void applyProcessingModule(ProcessingModule pm) throws Exception{
		if (pm.isApplicable(this)){
			this.addDataColumnInfo(pm.getDataColumnsInfo());
			
			ProcessingResult pr = pm.process(this);
			
			if (pr != null){
				if (pr.getDataContainer() != null)
					this.addAdditionalData(pr.getDataContainer());
				
				if (pr.getResultInfoString()!=null && !pr.getResultInfoString().isEmpty())
					JLogPanel.getJLogPanel().addInfo(String.format("Results: %s", pm.getShortName()), 
							pr.getResultInfoString());
			}
			if (pm.getDataColumnsInfo() != null){
				for (DataColumnInfo dci : pm.getDataColumnsInfo())
					if (!dci.isInitialized())
						dci.findRange(this, false);
			}
			//Store sucessful step in Toolchain
			this.toolchain.addModule(pm);
		}
	}
	
	private void processInputData(MDFileLoader.ImportDataContainer idc) throws Exception{
		//Short circuit for empty files
		if (this.atoms.size() == 0) return;
		
		{
			Toolchain t = defaultCrystalStructure.getToolchainToApplyAtBeginningOfAnalysis();
			if (t != null){
				for (ProcessingModule pm : t.getProcessingModules())
					this.applyProcessingModule(pm);
			}
		}
		
		//Bond Angle Analysis
		if (!idc.atomTypesAvailable){
			ProcessingModule pm = new AtomClassificationModule();
			ProgressMonitor.getProgressMonitor().setActivityName(pm.getShortName());
			this.applyProcessingModule(pm);
		}
		
		if (isGrainsImported()){
			ProgressMonitor.getProgressMonitor().setActivityName("Processing grains");
			final List<Grain> gr = defaultCrystalStructure.identifyGrains(this, 0f);
			for (Grain g : gr){
				this.addGrain(g);
				g.getMesh(); //ensure the mesh is created in the worker thread
			}
		}
			
		for (DataColumnInfo dci : dataColumns){
			if (dci.isFirstVectorComponent()){
				ProgressMonitor.getProgressMonitor().setActivityName("Compute norm of vectors");
				new VectorNormModule(dci).process(this);
			}
		}
		
		ProgressMonitor.getProgressMonitor().setActivityName("Finalizing file");
		
		if (ImportStates.DISPOSE_DEFAULT.isActive()){ //Dispose perfect atoms
			final int defaultType = defaultCrystalStructure.getDefaultType();
			new FilteringModule(new Filter<Atom>() {
				@Override
				public boolean accept(Atom a) {
					return a.getType() != defaultType;
				}
			}).process(this);
		}
		
		atomsPerElement = new int[maxNumElements];
		countAtomTypes();
		
		this.atoms.trimToSize();		
		
		//Scale the data columns values of the remaining atoms
		for (int i=0; i < dataColumns.size(); i++){
			float scale = dataColumns.get(i).getScalingFactor();
			for (Atom a : this.atoms)
				a.setData(a.getData(i)*scale, i); 
		}
	}

	public void countAtomTypes() {
		int maxType = defaultCrystalStructure.getNumberOfTypes();
		int warnings = 0;
		
		for (int i=0; i<atomsPerType.length;i++)
			atomsPerType[i] = 0; 
		
		for (int i=0; i<atomsPerElement.length;i++)
			atomsPerElement[i] = 0; 
		
		for (int i=0; i<atoms.size();i++){
			if (atoms.get(i).getType()>=maxType || atoms.get(i).getType()<0){
				atoms.get(i).setType(0);
				warnings++;
			}
		}
		if (warnings > 0){
			JLogPanel.getJLogPanel().addWarning("Type ID exceeds number of defined types.", 
					String.format("%d atoms had a type ID that was larger than the number of types defined for the structure. "
							+ "These atoms were reassigned to type 0.", warnings));
		}
		
		for (int i=0; i<atoms.size();i++){
			if (atoms.get(i).getType() >= 0 && atoms.get(i).getType() < atomsPerType.length)
				atomsPerType[atoms.get(i).getType()]++;		
			atomsPerElement[atoms.get(i).getElement()]++;
		}
	}
	
	/**
	 * Build a nearest neighbor graph and return the neighbor configuration
	 * @param atomsToPlot
	 * @return
	 */
	public StringBuilder plotNeighborsGraph(final Atom... atomsToPlot){
		StringBuilder sb = new StringBuilder();
		final float d = defaultCrystalStructure.getNearestNeighborSearchRadius();
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(box, d, true);
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
		nnb.addAll(atoms, filter);
		
		for (Atom a : atomsToPlot){
			ArrayList<Tupel<Atom, Vec3>> t =nnb.getNeighAndNeighVec(a);
			sb.append("#Neighbor configuration ");
			sb.append(t.size());
			sb.append("\n");	
			sb.append("#number\t#dx\t#dy\t#dz\t#d\t#element");
			sb.append("\n");	
			for (int i=0; i< t.size(); i++){
				String s = String.format("%d\t%.6f\t%.6f\t%.6f\t%.6f\t%d", t.get(i).o1.getNumber(),
						t.get(i).o2.x, t.get(i).o2.y, t.get(i).o2.z, t.get(i).o2.getLength(),
						t.get(i).o1.getElement());
				sb.append(s);
				sb.append("\n");
			}
			
			int k=0;
			sb.append("#Bond angles");
			float[][] angles = new float[t.size()*(t.size()-1)/2][6];
			sb.append(angles.length);
			sb.append("\n");
			sb.append("#number1\t#number2\t#angle\t#element1\t#center\t#element2\n");
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
				String s = String.format("%d\t%d\t%.6f°\t%d\t%d\t%d", (int)angles[i][1], (int)angles[i][2],
						angles[i][0], (int)angles[i][3], (int)angles[i][4], (int)angles[i][5]);
				sb.append(s);
				sb.append("\n");
			}
			
			
		}
		return sb;
	}
	
	public BoxParameter getBox() {
		return box;
	}
	
	public List<Atom> getAtoms(){
		return atoms;
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
	
	public void setNext(AtomData next) {
		this.next = next;
	}
	
	public AtomData getPrevious() {
		return previous;
	}
	
	public void setPrevious(AtomData previous) {
		this.previous = previous;
	}
	
	public CrystalStructure getCrystalStructure() {
		return defaultCrystalStructure;
	}
	
	public boolean isReferenceForProcessingModule() {
		return isReferenceForProcessingModule;
	}
	
	public void setAsReferenceForProcessingModule() {
		AtomData a = this;
		while(a.getPrevious()!=null) a = a.getPrevious();
		while(a!=null){
			a.isReferenceForProcessingModule = false;
			a = a.next;
		}
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
	public int getIndexForCustomColumn(DataColumnInfo dci){
		for (int i=0; i < dataColumns.size(); i++)
			if (dci.equals(dataColumns.get(i)))
				return i;
		return -1;
	}
	
	public List<DataColumnInfo> getDataColumnInfos(){
		return dataColumns;
	}
	
	public int getNumberOfAtomsWithType(int i){
		if (i>atomsPerType.length) return 0;
		return atomsPerType[i];
	}
	
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

	/**
	 * Frees the atom list to make more memory available by removing all references to other 
	 * instances of AtomData
	 * Helps if you want to load another large file, without memory shortcomings 
	 */
	public void clear(){
		maxNumElements = 1;
		this.atoms.clear();
		this.atoms.trimToSize();
		this.additionalData.clear();
		this.next = null;
		this.previous = null;
	}
	
	public void setRbvAvailable(boolean rbvAvailable) {
		this.rbvAvailable = rbvAvailable;
	}
	
	public boolean isRbvAvailable() {
		return rbvAvailable;
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
	
	@Override
	public String toString() {
		return getName();
	}
}