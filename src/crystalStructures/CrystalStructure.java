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

package crystalStructures;

import gui.JLogPanel;
import gui.PrimitiveProperty;
import gui.ProgressMonitor;

import java.awt.Color;
import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.CyclicBarrier;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import common.ColorTable;
import common.Vec3;
import model.*;
import model.BurgersVector.BurgersVectorType;
import model.polygrain.Grain;
import model.polygrain.grainDetection.*;
import model.mesh.Mesh;
import processingModules.skeletonizer.Skeletonizer;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer;
import processingModules.skeletonizer.processors.SkeletonPreprocessor;
import processingModules.toolchain.Toolchain;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.ClassificationPattern;

/**
 * All crystal structure depended subroutines are collected here

 */
public abstract class CrystalStructure{

	private static final float[][] DEFAULT_ATOM_COLORS = {
		{1f, 0.5f, 0f},
		{0.2f, 0.4f, 1f}, 
		{1f, 0f, 0f}, 
		{0f, 1f, 0f}, 
		{1f, 1f, 0f}, 
		{1f, 0f, 1f}, 
		{0f, 1f, 1f}, 
		{0f, 1f, 0.5f},
		{1f, 1f, 1f}
	};
	
	float latticeConstant;
	float nearestNeighborSearchRadius;
	ArrayList<PrimitiveProperty<?>> crystalProperties = new ArrayList<PrimitiveProperty<?>>();
	float[][] currentColors;
	
	private static final ArrayList<CrystalStructure> structures = new ArrayList<CrystalStructure>();
	/**
	 * Initialize a list of all supported crystal structures
	 * all CrystalStructures in this list must implement deriveNewInstance() and getIDName() correctly
	 */
	static {
		structures.add(new FCCStructure());
		structures.add(new FCCTwinnedStructure());
		structures.add(new BCCStructure());
		structures.add(new B2NiTi());
		structures.add(new B2());
		structures.add(new L10_Structure());
		structures.add(new L12_Ni3AlStructure());
		structures.add(new FeCStructure());
		structures.add(new FeC_virtStructure());
		structures.add(new DiamondCubicStructure());
		structures.add(new SiliconStructure());
		structures.add(new UndefinedCrystalStructure());
		
		structures.add(new YSZStructure());
		
		//Get the path for plugins 
		File dir;
		if (Configuration.RUN_AS_STICKWARE){
			try {
				dir = new File(CrystalStructure.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			} catch (URISyntaxException e) {
				dir = new File(CrystalStructure.class.getProtectionDomain().getCodeSource().getLocation().getPath());
			}
			dir = dir.getParentFile();
		} else {
			String userHome = System.getProperty("user.home");
			dir = new File(userHome+"/.AtomViewer");
			if (!dir.exists()) dir.mkdir();
		}
		
		File pluginFolder = new File(dir,"/plugins/crystalStructures/");
		boolean exists = pluginFolder.exists(); 
		if (!exists)
			exists = pluginFolder.mkdirs();
		
		if (exists){
			File[] files = pluginFolder.listFiles();
			for (File f : files){
				if (f.getName().endsWith(".java")){
					JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
					if (compiler == null) JLogPanel.getJLogPanel().addLog("No compiler installed, cannot compile "+f.getName());
					else {
						int compilationResult = compiler.run(null, System.out, System.err, f.getAbsolutePath());
					        if(compilationResult != 0){
					            JLogPanel.getJLogPanel().addLog("Compilation failed for file "+f.getName());
					        }
					}
				}
			}
			
			try {
				files = pluginFolder.listFiles();
				URL[] url = new URL[] {pluginFolder.getParentFile().toURI().toURL()};
				ClassLoader loader = URLClassLoader.newInstance(url);
				
				for (File f : files){
					if (f.getName().endsWith(".class")){
							String name = "crystalStructures."+f.getName().replace(".class", "");
							Class<?> clazz = Class.forName(name, true, loader);
							if (CrystalStructure.class.isAssignableFrom(clazz)){
								Class<? extends CrystalStructure> crystalStructureClass = clazz.asSubclass(CrystalStructure.class);
								Constructor<? extends CrystalStructure> ctor = crystalStructureClass.getConstructor();
								CrystalStructure struct = ctor.newInstance();
								structures.add(struct);
							}
						
					}
				}
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public CrystalStructure() {
		this.currentColors = ColorTable.loadDefaultColorScheme(this.getIDName());
		//if no default file exists, use default values
		if (this.currentColors == null || this.currentColors.length<this.getNumberOfTypes())
			this.currentColors = getDefaultColors();
	}
	
	public static List<CrystalStructure> getCrystalStructures(){
		return Collections.unmodifiableList(structures);
	}
	
	public static CrystalStructure createCrystalStructure(String cs, float latticeConstant, float nearestNeighborSearchRadius) {
		CrystalStructure struct = null;
		//Check if there is a structures with the same identifier
		for (CrystalStructure c : structures){
			if (c.toString().equalsIgnoreCase(cs)) struct = c;
		}
		
		//if a structure with the given identifier is found, derive a new instance of this class or create a default one
		if (struct == null) struct = new UndefinedCrystalStructure();
		else struct = struct.deriveNewInstance();
			
		if (nearestNeighborSearchRadius == 0f) 
			struct.nearestNeighborSearchRadius = struct.getDefaultNearestNeighborSearchScaleFactor() * latticeConstant;
		else struct.nearestNeighborSearchRadius = nearestNeighborSearchRadius;
		
		struct.latticeConstant = latticeConstant;
		return struct;
	}
	
	/* **********************************************************
	 * Abstract methods
	 * **********************************************************/
	/**
	 * An unique ID labeling the crystal structure
	 * MUST BE ALWAYS overridden / implemented in each subclass
	 * @return
	 */
	protected abstract String getIDName();
	
	/**
	 * Create a new instance of the same class
	 * Basically it is just cloning, but avoiding casts.
	 * Additional operations can be performed in the subclass as well.
	 * MUST BE ALWAYS overridden / implemented in each subclass
	 * @return
	 */
	protected abstract CrystalStructure deriveNewInstance();
	
	/**
	 * Sets the maximum number of classes in which atoms can be assigned to
	 * Number should correspond to the number of labels in getNameForType(i)
	 * optionally override getColor(index) and getGLColor(index) if other color schemes
	 * are needed
	 * @return number of different atom classes in this structure, at maximum 128 types are supported
	 */
	public abstract int getNumberOfTypes();
	
	/**
	 * Identifies the atom type for a single atom by Bond angle analysis, Common neighbor analysis, whatever...
	 * @see getAtomTypeKeyword(), if the types are read from file, this method is not being used
	 * @param atom atoms to assign a atom type
	 * @param nnb The nearest neighbor graph for this task
	 * @return atom type, must be smaller than getNumberOfTypes()
	 */
	public abstract int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb);
	
	/**
	 * The number of nearest neighbor atoms in a perfect lattice
	 * Used in computing grain rotations
	 * @return
	 */
	public abstract int getNumberOfNearestNeighbors();
	
	/**
	 * Test if a Burgers vector should be calculated for an atom
	 * If no custom implementation of getDislocationDefectAtoms is provided,
	 * the atoms accepted by this method and have a RBV will be included in the
	 * dislocation network
	 * @param a The atom to test
	 * @return true if it may be part of a dislocation, otherwise false
	 */
	public abstract boolean isRBVToBeCalculated(Atom a);
	
	/**
	 * Return the names for atoms of type i
	 * @param i
	 * @return
	 */
	public abstract String getNameForType(int i);
	
	/**
	 * The indicator for a atom type for atoms in (near) perfect lattice sites.
	 * This value is primarily needed to dispose atoms during loading files, that are not associated with defects
	 * @return Indicator for atoms in (near) perfect lattice sites
	 */
	//TODO change to a more abstract scheme what does not only depends on atomic type
	public abstract int getDefaultType();
	
	/**
	 * Each crystal structure has to provide a indicator to identify a class of atoms assigned as a free surface.
	 * This indicator is required to filter defect structures on free surfaces.
	 * @return Indicator for atoms associated with a free surface
	 */
	//TODO change to a more abstract scheme what does not only depends on atomic type
	public abstract int getSurfaceType();
	
	
	/**
	 * The length of a perfect Burgers vector, required to estimate a scalar value of GND densities.
	 * @return length of a perfect Burgers vector
	 */
	public abstract float getPerfectBurgersVectorLength();
	
	/**
	 * The perfect nearest neighbors in an arbitrary orientation 
	 * @return
	 */
	public abstract Vec3[] getPerfectNearestNeighborsUnrotated();
	
	/**
	 * Default scaling factor to search nearest neighbors relative to the lattice constant
	 * @return
	 */
	public abstract float getDefaultNearestNeighborSearchScaleFactor();
	
	
	/**
	 * Either include this atom as a neighbor during RBV calculation or not
	 * @param a
	 * @return
	 */
	public boolean considerAtomAsNeighborDuringRBVCalculation(Atom a){
		return true;
	}
	
	/**
	 * AToolchain to be applied at the end of the analysis process
	 * @return
	 */
	public Toolchain getToolchainToApplyAtBeginningOfAnalysis(){
		return null;
	}
	
	/* ******************************
	 * Final methods
	 ********************************/
	/**
	 * Return the color associated for the given atom class index  
	 * @param index
	 * @return
	 */
	public final Color getColor(int index){
		float[] c = getGLColor(index); 
		return new Color(c[0], c[1],c[2]);
	}
	
	@Override
	public final String toString() {
		return getIDName();
	}
	
	/**
	 * Perform an identification of the atom types (Bond angle analysis, Common neighbor analysis, whatever...)
	 * @param atoms A list of all atoms
	 * @param nnb The nearest neighbor graph for this task
	 * @param start Perform the identification for all atom within the range of start and end
	 * The calculation has to be able to performed in parallel
	 * @param barrier If the analysis consists of several phases, this barrier can be used to synchronize the threads
	 * @param end see start
	 */
	public void identifyDefectAtoms(List<Atom> atoms, NearestNeighborBuilder<Atom> nnb, int start, int end, CyclicBarrier barrier) {
		for (int i=start; i<end; i++){
			if (Thread.interrupted()) return;
			if ((i-start)%10000 == 0)
				ProgressMonitor.getProgressMonitor().addToCounter(10000);
			
			Atom a = atoms.get(i);
			a.setType(identifyAtomType(a, nnb));
		}
		
		ProgressMonitor.getProgressMonitor().addToCounter((end-start)%10000);
	}
	
	public final float getLatticeConstant() {
		return latticeConstant;
	}
	
	/**
	 * Identifies a rotation matrix from the current configuration of bonds towards a reference
	 * @param a Atom to derive the rotation from
	 * @param nnb  NearestNeighborBuilder that can provide the required neighbors
	 * @return either the rotation matrix or null if the atom
	 *  is not suited to get rotation from (atom is defect atom or similar)
	 */
	public final Vec3[] identifyRotation(Atom a, NearestNeighborBuilder<Atom> nnb){
		ArrayList<Vec3> neighList = nnb.getNeighVec(a);
		Vec3[] neighVec = neighList.toArray(new Vec3[neighList.size()]);
		
		Vec3[] rot = CrystalRotationTools.getLocalRotationMatrix(a, neighVec, this);
		return rot;
	}
	
	
	/**
	 * The perfect nearest neighbors rotated into the grain orientation 
	 * @param g
	 * @return
	 */
	public final Vec3[] getPerfectNearestNeighbors(Grain g) {
		return getPerfectNearestNeighbors(g.getCystalRotationTools());
	}
	
	/**
	 * The radius of the integrated sphere during the calculation of RBVs.
	 * Usually somewhere between the first and second nearest neighbor distance 
	 * @return
	 */
	public float getRBVIntegrationRadius(){
		return getDistanceToNearestNeighbor();
	};
	
	/**
	 * The nearest neighbors bonds (relative to a central atom) in the current crystal rotation
	 * @param crt The orientation of the crystal 
	 * @return array of Vec3, each a bond in xyz
	 */
	public final Vec3[] getPerfectNearestNeighbors(CrystalRotationTools crt){
		Vec3[] neighPerf = getPerfectNearestNeighborsUnrotated();
		Vec3[] neighPerfFloat = new Vec3[neighPerf.length];
		
		float[][] rot = crt.getDefaultRotationMatrix();
		
		for (int i=0; i<neighPerf.length;i++){
			Vec3 n = new Vec3();
			n.x = (neighPerf[i].x * rot[0][0] + neighPerf[i].y * rot[1][0] + neighPerf[i].z * rot[2][0]) * latticeConstant; 
			n.y = (neighPerf[i].x * rot[0][1] + neighPerf[i].y * rot[1][1] + neighPerf[i].z * rot[2][1]) * latticeConstant;
			n.z = (neighPerf[i].x * rot[0][2] + neighPerf[i].y * rot[1][2] + neighPerf[i].z * rot[2][2]) * latticeConstant;
			neighPerfFloat[i] = n;
		}
		
		return neighPerfFloat;
	}
	
	/**
	 * Identifies the type of a Burgers vector based on the list of pattern from 
	 * "getBurgersVectorClassificationPattern()"
	 * @param bv
	 * @return
	 */
	public final BurgersVectorType identifyBurgersVectorType(BurgersVector bv){
		if (bv.getFraction() == 0) return BurgersVectorType.UNDEFINED;
		if (bv.getDirection()[0] == 0 && bv.getDirection()[1] == 0 && bv.getDirection()[2] == 0)
			return BurgersVectorType.ZERO;
		
		for (ClassificationPattern cp : getBurgersVectorClassificationPattern())
			if (cp.typeMatch(bv)) return cp.getType();
		
		return BurgersVectorType.OTHER;
	}
	
	/**
	 * Distance in which nearest neighbors are to be searched.
	 * Usually slightly larger than the nearest neighbor distance in a perfect crystals
	 * @see getDefaultNearestNeighborSearchScaleFactor()
	 * For an atom in a perfect crystal the number of atoms in this radius should match
	 * the value returned by {@link CrystalStructure#getNumberOfNearestNeighbors()}
	 * @return
	 */
	public final float getNearestNeighborSearchRadius() {
		return nearestNeighborSearchRadius;
	}
	
	/**
	 * The minimal distance between two atoms in a perfect single crystal 
	 * @return
	 */
	public float getDistanceToNearestNeighbor() {
		Vec3[] n = this.getPerfectNearestNeighborsUnrotated();
		float min = Float.POSITIVE_INFINITY;
		for (int i=0; i<n.length; i++)
			if (n[i].getLength()<min) min = n[i].getLength();
		return min * this.latticeConstant;
	}
	
	/**
	 * Return the search radius in which neighbors are to be found
	 * to identify the structure this atom is
	 * This value can be different from the value of 
	 * {@link CrystalStructure#getNearestNeighborSearchRadius()}, which usually only
	 * includes the atoms in the range of about the first nearest neighbor distance.
	 * @return
	 */
	public float getStructuralAnalysisSearchRadius(){
		return getNearestNeighborSearchRadius();
	}
	
	/* ****************************************************************
	 * Methods that can be overridden if required, controlling details
	 * of the Nye tensor analysis, skeletonization and handling of 
	 * grain- or phase-boundaries 
	 ******************************************************************/
	
	/**
	 * A list of special options for a CrystalStructure
	 * Handle via CrystalStructureProperties 
	 * @return
	 */
	public ArrayList<PrimitiveProperty<?>> getCrystalProperties() {
		return crystalProperties;
	}
	
	/**
	 * Return the color associated for the given atom class index  
	 * @param index float[3] array to be used in OpenGl
	 * @return
	 */
	public final float[] getGLColor(int index){
		assert (index<currentColors.length && index>=0);
		return currentColors[index];		
	}	
	
	/**
	 * Provides the default color scheme for this crystal structure
	 * @return
	 */
	public float[][] getDefaultColors(){
		if (DEFAULT_ATOM_COLORS.length < this.getNumberOfTypes()){
			return ColorTable.createColorTable(this.getNumberOfTypes());
		} else {
			float[][] f = new float[DEFAULT_ATOM_COLORS.length][3];
			for (int i=0; i< f.length;i++){
				f[i][0] = DEFAULT_ATOM_COLORS[i][0];
				f[i][1] = DEFAULT_ATOM_COLORS[i][1];
				f[i][2] = DEFAULT_ATOM_COLORS[i][2];
			}
			
			return f;
		}
	}
	
	
	/**
	 * Reset the colors used for atoms to a predefined standard
	 */
	public final void resetColors(){
		this.currentColors = getDefaultColors();
		this.saveColorScheme();
	}
	
	/**
	 * Stores the currently set coloring scheme for atoms in a file,
	 * the filename is identical as the structures ID, providing automatic loading
	 * next time the same structure is used
	 */
	public final void saveColorScheme(){
		ColorTable.saveColorScheme(this.currentColors, this.getIDName());
	}
	
	/**
	 * Set a color for a certain type
	 * @param index
	 * @param color
	 */
	public final void setGLColors(int index, float[] color){
		assert (index<currentColors.length && index >=0);
		assert(color.length>=3);
		
		currentColors[index][0] = Math.min(1, Math.max(0, color[0]));
		currentColors[index][1] = Math.min(1, Math.max(0, color[1]));
		currentColors[index][2] = Math.min(1, Math.max(0, color[2]));
	}
	
	/**
	 * Number of different elements in the crystal.
	 * Monoatomic structures return 1, Biatomic structures 2...
	 * If virtual atom types are used, the same scheme as in the IMD format for n elements has to be used
	 * 0 -> element 0
	 * 1 -> element 1
	 * ...
	 * n -> element n
	 * n+1 -> element 0
	 * n+2 -> element 1
	 * ...
	 * 2n -> element n
	 * 2n+1 -> element 0
	 * ...
	 * @return
	 */
	public int getNumberOfElements(){
		return 1;
	}
	
	/**
	 * Specifies the names of different elements
	 * @return an array equal with size equal to getNumberOfElements() or null if no names are specified
	 */
	public String[] getNamesOfElements(){
		return null;
	}
	
	/**
	 * Scaling factor to display atoms of different elements with different sizes 
	 * @return scaling factors, array has the same size as the value returned by getNumberOfElements()
	 */
	public float[] getSphereSizeScalings(){
		float[] size = new float[getNumberOfElements()];
		for (int i=0; i<getNumberOfElements(); i++)
			size[i] = 1f;
		return size;
	}
	
	
	public Filter<Atom> getIgnoreAtomsDuringImportFilter(){
		return null;
	}
	
	/* **********************************
	 * skeletonization related methods
	 ************************************/
	/**
	 * Perform analysis on a finalized skeleton, e.g. mapping of Burgers vectors 
	 * @param skel
	 */
	public void analyse(Skeletonizer skel) {
		if (skel.getAtomData().isRbvAvailable())
			new BurgersVectorAnalyzer(this).analyse(skel);
	}
	
	/**
	 * Define a set of PreProcessors for the skeletonizer
	 * @return
	 */
	public List<SkeletonPreprocessor> getSkeletonizerPreProcessors(){
		return new Vector<SkeletonPreprocessor>();
	}
	
	/**
	 * Creates a subset of atoms belonging to dislocations that are considered during skeletonization
	 * @param data
	 * @return
	 */
	public final List<Atom> getDislocationDefectAtoms(AtomData data){
		ArrayList<Atom> defectAtoms = new ArrayList<Atom>();
		if (!data.isRbvAvailable()) return defectAtoms;
		
		float maxRBVLength = getPerfectBurgersVectorLength()*2.5f;
		maxRBVLength *= maxRBVLength;
		
		for (Atom a : data.getAtoms()) {
			if (a.getRBV()!=null){
				float l = a.getRBV().bv.getLengthSqr();
				if (l<maxRBVLength)
					defectAtoms.add(a);
			}
		}
		
		return defectAtoms;
	};
	
	/**
	 * Return a list of patterns to map numerical Burgers vectors to the crystallographic ones.
	 * Each desired mapping must be defined in the list 
	 * Defines at the same time, the assignment of types for the Burgers vectors in 
	 * "identifyBurgersVectorType(BurgersVector bv)"
	 * @return
	 */
	public ArrayList<ClassificationPattern> getBurgersVectorClassificationPattern() {
		return new ArrayList<ClassificationPattern>();
	}
	
	/* ****************************
	 * Stacking fault methods
	 *******************************/
	
	/**
	 * Defines if stacking faults exist in the structure.
	 * If true, the method getStackingFaultNormals requires to return the possible stacking fault normals
	 * @return
	 */
	public boolean hasStackingFaults(){
		return false;
	}
	
	/**
	 * If stacking faults exist in the crystal structure, this method provides their normals
	 * (in xyz-coordinates)
	 * @return normals of all possible stacking fault planes
	 */
	public Vec3[] getStackingFaultNormals(CrystalRotationTools crt){
		return new Vec3[0];
	}
	
	/**
	 * Creates a subset of atoms belonging to stacking faults (if not existing, the list is empty)
	 * Should be linked with the hasStackingFault() method
	 * @param data
	 * @return
	 */
	public List<Atom> getStackingFaultAtoms(AtomData data){
		return new ArrayList<Atom>();
	};
	
	/**
	 * Each stacking fault consists of atoms all classified with the same type
	 * @return If there are multiple types that can form stacking faults, the methods returns true
	 */
	public boolean hasMultipleStackingFaultTypes(){
		return false;
	}
	
	/* *************************************
	 * Polycrystalline / Polyphase related methods
	 **************************************** */
	
	/**
	 * Crystal structure specific routines to identify different grains
	 * @param data
	 * @return
	 */
	public List<Grain> identifyGrains(AtomData data, float meshSize) {
		List<Grain> grains = new Vector<Grain>();
		
		if (!data.isGrainsImported()){
			//Identify grains from scratch 
			
			List<List<Atom>> grainSets = GrainDetector.identifyGrains(data.getAtoms(), 
							this.getGrainDetectionCriteria(), data.getBox());
			
			CrystalStructure cs = this.getCrystalStructureOfDetectedGrains();
			for (List<Atom> s : grainSets){
				Mesh mesh = new Mesh(s, meshSize, nearestNeighborSearchRadius, data.getBox());
				Grain g = new Grain(mesh, s, grains.size(), cs, data.getBox());
				grains.add(g);
			}
		} else {
			//Import from file
			Object o = data.getFileMetaData("grain");
			PolygrainMetadata meta = null;
			if (o!=null && o instanceof PolygrainMetadata){
				meta = (PolygrainMetadata)o;
			}
			
			if (meta != null && meta.meshes.size() == meta.grainOrientation.size()){
				HashMap<Integer, ArrayList<Atom>> grainIndexToList = new HashMap<Integer, ArrayList<Atom>>(); 
				for (int i : meta.meshes.keySet()){
					Mesh mesh = new Mesh(meta.meshes.get(i).triangles, meta.meshes.get(i).vertices);
					Grain g;
					ArrayList<Atom> list = new ArrayList<Atom>();
					if (meta!=null && meta.grainOrientation.containsKey(i))
						g = new Grain(mesh, list, i, this, meta.grainOrientation.get(i));
					else g = new Grain(mesh, list, i, this, data.getBox());
					grains.add(g);
					grainIndexToList.put(g.getGrainNumber(), list);
					if (meta.numAtoms.get(i)!=null)
						g.setNumberOfAtoms(meta.numAtoms.get(i));
				}
				for (Atom a : data.getAtoms()){
					int grainID = a.getGrain();
					if (grainID != Atom.DEFAULT_GRAIN && grainID != Atom.IGNORED_GRAIN)
						grainIndexToList.get(grainID).add(a);
				}
			} else {
				//Grains are given in input, just process the sets
				HashMap<Integer, ArrayList<Atom>> grainsAtomLists = new HashMap<Integer, ArrayList<Atom>>();
				for (int i=0; i<data.getAtoms().size(); i++){
					Atom a = data.getAtoms().get(i);
					if (a.getGrain() != Atom.IGNORED_GRAIN){	
						if (!grainsAtomLists.containsKey(a.getGrain())){
							grainsAtomLists.put(a.getGrain(), new ArrayList<Atom>());
						}
						grainsAtomLists.get(a.getGrain()).add(a);
					}
				}
						
				//Mesh creation
				for (Integer i : grainsAtomLists.keySet()){
					ArrayList<Atom> grain = grainsAtomLists.get(i);
					if (grain!= null &&!grain.isEmpty()){
						Mesh mesh = new Mesh(grain, meshSize, nearestNeighborSearchRadius, data.getBox());
						Grain g;
						if (meta!=null && meta.grainOrientation.containsKey(i))
							g = new Grain(mesh, grain, i, this, meta.grainOrientation.get(i));
						else g = new Grain(mesh, grain, i, this, data.getBox());
						
						grains.add(g);
					}
				}
			}
		}
		return grains;
	}
	
	/**
	 * Set of rules and patterns to identify grain boundaries
	 * TODO: Note, this interface is subjected to be updated/removed in a future release
	 * @return An instance of an implementation of GrainDetectionCriteria
	 */
	public GrainDetectionCriteria getGrainDetectionCriteria(){
		return new DefaultGrainDetectionCriteria(this);
	}
	
	/**
	 * Defines which crystal structure a detected grain has, currently only used for 
	 * detecting austenite/marteniste in NiTi.
	 * TODO: Note, this method is subjected to be updated/removed in a future release
	 * @return
	 */
	public CrystalStructure getCrystalStructureOfDetectedGrains(){
		return this;
	}
	
	/**
	 * Defines if connection in the dislocation structure are possible
	 * over multiple grains (phases) or not
	 * @return
	 */
	public boolean skeletonizeOverMultipleGrains(){
		return false;
	}
}
