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

package model;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import javax.swing.JOptionPane;
import gui.JLogPanel;

import processingModules.*;
import model.dataContainer.*;
import model.io.MDFileLoader;
import model.polygrain.*;
import model.polygrain.mesh.Mesh;
import model.skeletonizer.*;
import common.*;

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
	//Some flags for imported or calculated values
	private boolean rbvAvailable = false;
	private boolean atomTypesAvailable = false;
	private boolean grainsImported = false;
	private boolean meshImported = false;
	
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
	
	/**
	 * Skeletonizer: stores the dislocation network
	 */
	private Skeletonizer skeletonizer = null;
	
	private int[] atomsPerElement = new int[0];
	private int[] atomsPerType;
	private AtomData next, previous;
	
	public AtomData(AtomData previous, MDFileLoader.ImportDataContainer idc){
		this.atomsPerType = new int[Configuration.getCrystalStructure().getNumberOfTypes()];
		this.box = idc.box;
		this.atoms = idc.atoms;
		this.grains = idc.grains;
		this.meshImported = idc.meshImported;
		this.maxNumElements = idc.maxElementNumber;
		this.rbvAvailable = idc.rbvAvailable;
		this.atomTypesAvailable = idc.atomTypesAvailable;
		this.grainsImported = idc.grainsImported;
		this.fileMetaData = idc.fileMetaData;
		this.name = idc.name;
		this.fullPathAndFilename = idc.fullPathAndFilename;
		Configuration.setCurrentAtomData(this);
		
		if (Configuration.getNumElements()<idc.maxElementNumber)
			Configuration.setNumElements(idc.maxElementNumber);
		
		if (previous!=null){
			previous.next = this;
			this.previous = previous;
		}
		
		try {
			processInputData();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ArrayList<DataContainer> getAdditionalData() {
		return additionalData;
	}
	
	private void processInputData() throws Exception{
		//Short circuit for empty files
		if (this.atoms.size() == 0) return;
		
		{
			List<DataContainer> data = Configuration.getCrystalStructure().getDataContainerToApplyAtBeginningOfAnalysis();
			if (data != null){
				for (DataContainer dc : data){
					Configuration.currentFileLoader.getProgressMonitor().setActivityName("Process: "+dc.getName());
					this.addAdditionalData(dc);
				}
			}
		}
		
		//Bond Angle Analysis
		if (!atomTypesAvailable){
			Configuration.currentFileLoader.getProgressMonitor().setActivityName("Classifying atoms");
			StructuralAnalysisBuilder.performStructureAnalysis(this);
		}
		
		if (ImportStates.FILTER_SURFACE.isActive()){
			Configuration.currentFileLoader.getProgressMonitor().setActivityName("Filtering surface");
			new FilterSurfaceModule(3,3).process(this);
		}
		
		if (ImportStates.POLY_MATERIAL.isActive()){
			if (ImportStates.BURGERS_VECTORS.isActive() && 
					Configuration.getCrystalStructure().createRBVbeforeGrains() && !rbvAvailable){
				Configuration.currentFileLoader.getProgressMonitor().setActivityName("Computing burgers vectors");
				RbvBuilder.createRBV(this);
				rbvAvailable = true;
			}
			Configuration.currentFileLoader.getProgressMonitor().setActivityName("Identifying grains");
			final List<Grain> gr = Configuration.getCrystalStructure().identifyGrains(this);
			for (Grain g : gr){
				this.grains.put(g.getGrainNumber(), g);
			}
		
			if (ImportStates.BURGERS_VECTORS.isActive() && !rbvAvailable) {
				Configuration.currentFileLoader.getProgressMonitor().setActivityName("Computing burgers vectors");
				// RBV poly-crystal
				for (Grain g : gr)
					RbvBuilder.createRBV(g, getBox());
				rbvAvailable = true;
			}
			Configuration.currentFileLoader.getProgressMonitor().setActivityName("Processing grains");
			
			if (!Configuration.getCrystalStructure().orderGrainsBySize()){
				for (Grain g : gr) {
					Configuration.addGrainIndex(g.getGrainNumber());
				}
			} else {
				ArrayList<Grain> sortedGrains = new ArrayList<Grain>(this.getGrains());
				for (Grain g : sortedGrains)
					g.getMesh();
				
				Collections.sort(sortedGrains, new Comparator<Grain>() {
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
					Configuration.addGrainIndex(i);
					for (Atom a : sortedGrains.get(i).getAtomsInGrain()){
						a.setGrain(i);
					}
					sortedGrains.get(i).getAtomsInGrain().clear();	//Atoms in the grain are not needed anymore
				}
				
				grains.clear();
				for (Grain g : sortedGrains)
					grains.put(g.getGrainNumber(), g);
			}
			
			Grain.processGrains(this);
			
			for (Grain g : gr) {
				// Make sure all meshes are calculated if grains are imported
				Mesh m = g.getMesh();
				if (m != null){	//Can only happen in case of CancellationException 
					m.finalizeMesh();
					if (!Configuration.getCrystalStructure().orderGrainsBySize())
						g.getAtomsInGrain().clear();	//Atoms are not needed anymore if there is no need for reordering
				}
			}
			
		} else if (ImportStates.BURGERS_VECTORS.isActive() && !rbvAvailable){
			//RBV single-crystal
			Configuration.currentFileLoader.getProgressMonitor().setActivityName("Computing burgers vectors");
			RbvBuilder.createRBV(this);
			rbvAvailable = true;
		}
		
		if (ImportStates.SKELETONIZE.isActive()){
			Configuration.currentFileLoader.getProgressMonitor().setActivityName("Creating dislocation networks");
			this.getSkeletonizer().transform(this);
		}
		
		//Post-Processors
		for (ProcessingModule pm : Configuration.getProcessingModules()){
			Configuration.currentFileLoader.getProgressMonitor().setActivityName(pm.getShortName());
			pm.process(this);
		}
		
		{
			List<DataContainer> data = Configuration.getCrystalStructure().getDataContainerToApplyAtEndOfAnalysis();
			if (data != null){
				for (DataContainer dc : data){
					Configuration.currentFileLoader.getProgressMonitor().setActivityName("Process: "+dc.getName());
					this.addAdditionalData(dc);
				}
			}
		}
		
		Configuration.currentFileLoader.getProgressMonitor().setActivityName("Finalizing file");
		
		if (ImportStates.KILL_ALL_ATOMS.isActive()) 			//Dispose all atoms
			atoms.clear();
		else if (ImportStates.DISPOSE_DEFAULT.isActive()){ //Dispose perfect atoms
			final int defaultType = Configuration.getCrystalStructure().getDefaultType();
			new FilteringModule(new AtomFilter() {
				@Override
				public boolean accept(Atom a) {
					return a.getType() != defaultType;
				}
			}).process(this);
		}

		int maxType = Configuration.getCrystalStructure().getNumberOfTypes();
		int warnings = 0;
		for (int i=0; i<atoms.size();i++){
			if (atoms.get(i).getType()>=maxType){
				atoms.get(i).setType(0);
				warnings++;
			}
		}
		if (warnings > 0)
			JLogPanel.getJLogPanel().addLog(String.format("%d Atoms with a type ID exceeding the total number of types defined"
					+ "for this structure are detected. These atoms were reassigned to type 0.", warnings));
		
		//Count all atoms types
		for (int i=0; i<atoms.size();i++){
			if (atoms.get(i).getType() >= 0 && atoms.get(i).getType() < atomsPerType.length)
				atomsPerType[atoms.get(i).getType()]++;			
		}
		
		//Count atoms of different (virtual) elements
		atomsPerElement = new int[maxNumElements];
		if (maxNumElements>1){
			for (int i = 0; i< atoms.size(); i++){
				atomsPerElement[atoms.get(i).getElement()]++;
			}
		} else atomsPerElement[0] = atoms.size();
		
		this.atoms.trimToSize();		
		
		//Scale the data columns values of the remaining atoms
		for (int i=0; i<Configuration.getSizeDataColumns(); i++){
			DataColumnInfo cci = Configuration.getDataColumnInfo(i);
			int c = cci.getColumn();
			float scale = cci.getScalingFactor();
			if (!cci.isSpecialColoumn())
				for (Atom a : this.atoms)
					a.setData(a.getData(c)*scale, c); 
		}
		
	}
	
	/**
	 * Build a nearest neighbor graph and return the neighbor configuration
	 * @param atomsToPlot
	 * @return
	 */
	public StringBuilder plotNeighborsGraph(Atom... atomsToPlot){
		StringBuilder sb = new StringBuilder();
		final float d = Configuration.getCrystalStructure().getNearestNeighborSearchRadius();
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(d);
		
		for (int i=0; i<atoms.size();i++)
			nnb.add(atoms.get(i));
		
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
	
	public AtomData getPrevious() {
		return previous;
	}
	
	public int getNumberOfAtomsWithType(int i){
		if (i>atomsPerType.length) return 0;
		return atomsPerType[i];
	}
	
	public int getNumberOfAtomsWithElement(int i){
		if (i>=atomsPerElement.length) return 0;
		return atomsPerElement[i];
	}
	
	public Collection<Grain> getGrains() {
		return grains.values();
	}
	
	public Grain getGrains(int grain) {
		return grains.get(grain);
	}
	
	public boolean isGrainsImported() {
		return grainsImported;
	}
	
	public boolean isMeshImported() {
		return meshImported;
	}
	
	public Skeletonizer getSkeletonizer(){
		if (skeletonizer == null)
			skeletonizer = new Skeletonizer(this);
		return skeletonizer;
	}
	
	public Object getFileMetaData(String s) {
		if (fileMetaData == null) return null;
		return fileMetaData.get(s);
	}

	/**
	 * Frees the atom list to make more memory available and removes references to the skeletonizer
	 * and next and previous AtomDatas
	 * Helps if you want to load another large file, without memory shortcomings 
	 */
	public void clear(){
		maxNumElements = 1;
		this.atoms.clear();
		this.atoms.trimToSize();
		this.skeletonizer = null;
		this.next = null;
		this.previous = null;
	}
	
	public boolean isRbvAvailable() {
		return rbvAvailable;
	}
	
	public void addAdditionalData(DataContainer dc){
		dc = dc.deriveNewInstance();
		
		boolean success = false;
		try {
			File f = null;
			if (dc.isExternalFileRequired()) {
				f = dc.selectFile(Configuration.getLastOpenedFolder());
				if (f == null) return;
			}
			if (dc.showOptionsDialog()) 
				success = dc.processData(f, this);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (success) {
			AtomData a = this;
			boolean replaced = false;
			for (int i=0; i<a.additionalData.size(); i++){
				if (a.additionalData.get(i).getClass() == dc.getClass()){
					a.additionalData.set(i,dc);
					replaced = true;
					break;
				}
			}
			if (!replaced) a.additionalData.add(dc);
		}
		else JOptionPane.showMessageDialog(null, "Creating additional data failed");
	}
	
	public void printToFile(File out, boolean binary, boolean compressed) throws IOException{
		DataOutputStream dos;
		if (compressed)
			dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(out), 4096*16),4096*64));
		else dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out), 4096*64));
		
		int numData = 1+Configuration.getSizeDataColumns()+(ImportStates.POLY_MATERIAL.isActive()?1:0);
		
		if (binary){
			dos.writeBytes(String.format("#F b 1 1 0 3 0 %d\n", numData));
		} else dos.writeBytes(String.format("#F A 1 1 0 3 0 %d\n", numData));
		dos.writeBytes("#C number type x y z ada_type");
		
		if (Configuration.getSizeDataColumns()!=0){
			for (int i=0; i<Configuration.getSizeDataColumns(); i++){
				String id = " "+Configuration.getDataColumnInfo(i).getId();
				dos.writeBytes(id);
			}
		}
		if (ImportStates.POLY_MATERIAL.isActive())
			dos.writeBytes(" grain");
		
		//rbv always at the end
		if (rbvAvailable)
			dos.writeBytes(" rbv_data");
		dos.writeBytes("\n");
		
		Vec3[] b = box.getBoxSize();
		dos.writeBytes(String.format("#X %.8f %.8f %.8f\n", b[0].x, b[0].y, b[0].z));
		dos.writeBytes(String.format("#Y %.8f %.8f %.8f\n", b[1].x, b[1].y, b[1].z));
		dos.writeBytes(String.format("#Z %.8f %.8f %.8f\n", b[2].x, b[2].y, b[2].z));
		
		dos.writeBytes("##META\n");
		if (ImportStates.POLY_MATERIAL.isActive()){
			for (Grain g: getGrains()){
				float[][] rot = g.getCystalRotationTools().getDefaultRotationMatrix();
				dos.writeBytes(String.format("##grain %d %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f\n",
						g.getGrainNumber(), rot[0][0], rot[1][0], rot[2][0]
						, rot[0][1], rot[1][1], rot[2][1]
						, rot[0][2], rot[1][2], rot[2][2]));
				dos.writeBytes(String.format("##atomsInGrain %d %d\n",
						g.getGrainNumber(), g.getNumberOfAtoms()));
				g.getMesh().getFinalMesh().printMetaData(dos, g.getGrainNumber());
			}
		}
		if (fileMetaData != null && fileMetaData.containsKey("extpot")){
			float[] indent = (float[]) fileMetaData.get("extpot");
			dos.writeBytes(String.format("##extpot %.4f %.4f %.4f %.4f %.4f\n", indent[0], indent[1], indent[2], indent[3], indent[4]));
		}
		if (fileMetaData != null && fileMetaData.containsKey("wall")){
			float[] wall = (float[]) fileMetaData.get("wall");
			dos.writeBytes(String.format("##wall %.4f %.4f %.4f\n", wall[0], wall[1], wall[2]));
		}
		if (fileMetaData != null && fileMetaData.containsKey("timestep")){
			float[] timestep = (float[])fileMetaData.get("timestep");
			dos.writeBytes(String.format("##timestep %f\n", timestep[0]));
		}
		
		dos.writeBytes("##METAEND\n");
		dos.writeBytes("#E\n");
		
		if (binary){
			for (Atom a : atoms){
				//enable highest filtering
//				if ( (a.getRBV() == null || a.getRBV().getSquaredRBVLengh()<0.36) && a.getType()!=6) continue;
//				if (a.getRBV() == null && a.getType()!=6) continue;
				
				dos.writeInt(a.getNumber()); dos.writeInt(a.getElement());
				dos.writeFloat(a.x); dos.writeFloat(a.y); dos.writeFloat(a.z);
				dos.writeInt(a.getType());
				
				for (int i=0; i<Configuration.getSizeDataColumns();i++)
					dos.writeFloat(a.getData(i));
				if (ImportStates.POLY_MATERIAL.isActive())
					dos.writeInt(a.getGrain());
				
				if (rbvAvailable){
					if (a.getRBV()!=null){
						dos.writeInt(1);
						dos.writeFloat(a.getRBV().lineDirection.x);
						dos.writeFloat(a.getRBV().lineDirection.y);
						dos.writeFloat(a.getRBV().lineDirection.z);
						dos.writeFloat(a.getRBV().bv.x); dos.writeFloat(a.getRBV().bv.y); dos.writeFloat(a.getRBV().bv.z);
					} else {
						dos.writeInt(0);
					}
				}
			}
		} else {
			for (Atom a : atoms){
				dos.writeBytes(String.format("%d %d %.8f %.8f %.8f %d", a.getNumber(), a.getElement(), 
						a.x, a.y, a.z, a.getType()));
				
				if (Configuration.getSizeDataColumns()!=0)
					for (int i=0; i<Configuration.getSizeDataColumns();i++)
						dos.writeBytes(String.format(" %.8f",a.getData(i)));
				
				if (ImportStates.POLY_MATERIAL.isActive())
					dos.writeBytes(" "+Integer.toString(a.getGrain()));
				
				if (rbvAvailable){
					if (a.getRBV()==null){
						dos.writeBytes(" 0");
					} else {
						dos.writeBytes(String.format(" 1 %.4f %.4f %.4f %.4f %.4f %.4f",
								a.getRBV().lineDirection.x, a.getRBV().lineDirection.y, a.getRBV().lineDirection.z,
								a.getRBV().bv.x, a.getRBV().bv.y, a.getRBV().bv.z));
					}
				}
				
				dos.writeBytes("\n");
			}
		}
		dos.close();
	}	
}