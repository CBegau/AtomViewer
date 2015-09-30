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

package processingModules.skeletonizer;

import java.awt.event.InputEvent;
import java.util.*;
import java.util.Map.Entry;

import model.*;
import model.polygrain.Grain;
import common.Tupel;
import common.Vec3;

/**
 * This implementation of planar defect can identify
 * at this time only stacking faults and twin boundaries in FCC correctly
 * In general, it is only designed to handle defects that are limited to exactly on plane.
 * The more general case of grain- and phaseboundaries are handle in model.polygrain.Grain
 */
public class PlanarDefect implements Pickable{
	private int id;
	
	private Dislocation[] adjacentDislocations;
	private Vec3 normal;
	private Atom[] faces = new Atom[0];
	private int numberOfAtoms;
	private Grain grain;
	/**
	 * Each plane is composed of exactly one atom type. 
	 */
	private int planeComposedOfType;
	
	protected PlanarDefect(final Set<PlanarDefectAtom> planarDefectAtoms, Vec3 normal,
			Map<PlanarDefectAtom, PlanarDefect> defectMap, Grain grain, int id) {
		if (planarDefectAtoms.size() < 3) throw new IllegalArgumentException("To small set (need >=3 points)");
		this.grain = grain;
		this.id = id;
		
		this.numberOfAtoms = planarDefectAtoms.size();
		this.normal = normal.normalizeClone();
		
		for (PlanarDefectAtom a : planarDefectAtoms) {
			defectMap.put(a, this);
		}
		
		createFaces(planarDefectAtoms);
		
		if (numberOfAtoms!=0)
			this.planeComposedOfType = planarDefectAtoms.iterator().next().getAtom().getType();
	}
	
	/**
	 * Creates planar defects 
	 * nearest neighbor atoms between defects need to be created before calling this method
	 * Important note: the relationship between planar defects and atoms in returned in the defectMap!
	 * @param data the set of atom Data 
	 * @param defectMap contains the relationship between planar defects and atoms AFTER completion
	 * @return a list of all created planar defects
	 */
	public static ArrayList<PlanarDefect> createPlanarDefects(AtomData data, 
			Map<PlanarDefectAtom, PlanarDefect> defectMap){

		List<Atom> sfAtoms = data.getCrystalStructure().getStackingFaultAtoms(data);
		TreeSet<PlanarDefectAtom> planarDefectAtoms = new TreeSet<PlanarDefectAtom>();
		int id = 0;
		for (Atom a: sfAtoms)
			planarDefectAtoms.add(new PlanarDefectAtom(a, id++));
		
		NearestNeighborBuilder<PlanarDefectAtom> nnb = new NearestNeighborBuilder<PlanarDefectAtom>(
				data.getBox(), data.getCrystalStructure().getNearestNeighborSearchRadius());
		
		for (PlanarDefectAtom p : planarDefectAtoms)
			nnb.add(p);
		
		for (PlanarDefectAtom p : planarDefectAtoms)
			p.setNeigh(nnb.getNeigh(p));
		
		if (!data.isPolyCrystalline()){
			return createPlanarDefects(planarDefectAtoms, 
					data.getCrystalStructure().getStackingFaultNormals(data.getCrystalRotation()),
					defectMap, null, data);
		} else {
			//For polycrystalline material, split everything into separate grains
			HashMap<Integer, TreeSet<PlanarDefectAtom>> sfPerGrain = new HashMap<Integer, TreeSet<PlanarDefectAtom>>();
			for (PlanarDefectAtom a : planarDefectAtoms){
				if (a.getAtom().getGrain() != Atom.IGNORED_GRAIN && !sfPerGrain.containsKey(a.getAtom().getGrain())){
					sfPerGrain.put(a.getAtom().getGrain(), new TreeSet<PlanarDefectAtom>());
				}
				sfPerGrain.get(a.getAtom().getGrain()).add(a);
			}
			planarDefectAtoms.clear();	//Not needed anymore, dereference all its members
			
			ArrayList<PlanarDefect> def = new ArrayList<PlanarDefect>();
			
			for (Entry<Integer, TreeSet<PlanarDefectAtom>> e : sfPerGrain.entrySet()){
				sfPerGrain.remove(e);	//reference in the map is not needed anymore
				if (e.getKey() != Atom.DEFAULT_GRAIN  && e.getKey() != Atom.IGNORED_GRAIN){
					Grain g = data.getGrains(e.getKey()); 
					
					def.addAll(createPlanarDefects(e.getValue(), 
							g.getCrystalStructure().getStackingFaultNormals(g.getCystalRotationTools()),
							defectMap, g, data));
				}
			}
			
			return def;
		}
	}
	
	/**
	 * @param planarDefectAtoms
	 * @param planeNormals
	 * @return
	 */
	private static ArrayList<PlanarDefect> createPlanarDefects(TreeSet<PlanarDefectAtom> planarDefectAtoms, 
			Vec3[] planeNormals, Map<PlanarDefectAtom, PlanarDefect> defectMap, Grain grain, AtomData data){
		HashSet<PlanarDefectAtom> planarDefectAtomsLookup = new HashSet<PlanarDefectAtom>(planarDefectAtoms);
		ArrayList<PlanarDefect> planarDefectList = new ArrayList<PlanarDefect>();
		
		TreeSet<PlanarDefectAtom> leftOverAtoms = findDefects(planarDefectAtoms, true, 
				planarDefectList, planarDefectAtomsLookup, planeNormals, defectMap, grain, data);
		findDefects(leftOverAtoms, false, planarDefectList, planarDefectAtomsLookup, planeNormals, defectMap, grain, data);

		//wait till all planarDefects are processed in worker threads
		for (PlanarDefect pd : planarDefectList)
			pd.getFaces();
		
		return planarDefectList;
	}
	
	private static TreeSet<PlanarDefectAtom> findDefects(
			TreeSet<PlanarDefectAtom> atomSet, 
			boolean firstRun, 
			ArrayList<PlanarDefect> planarDefectList, 
			HashSet<PlanarDefectAtom> planarDefectAtomsLookup,
			Vec3[] planeNormals, 
			Map<PlanarDefectAtom, PlanarDefect> defectMap, 
			Grain grain,
			AtomData data){
		
		int id = 0;
		
		int bondThreshold = firstRun ? 3 : 2;
		PlanarDefectAtom[] fittingArray = new PlanarDefectAtom[20];
	
		TreeSet<PlanarDefectAtom> leftOverAtoms = new TreeSet<PlanarDefectAtom>();
		
		while (!atomSet.isEmpty()){
			PlanarDefectAtom c = atomSet.pollFirst();
			
			//Identify straight bonds between nearest neighbors in the set of planar defects
			//In the first run consider only atoms in the list of planar defects
			//In the second run all atoms are considered
			ArrayList<Vec3> straightBonds = new ArrayList<Vec3>(); 
			for (int i=0; i<c.getNeigh().size()-1; i++){
				if (!firstRun || planarDefectAtomsLookup.contains(c.getNeigh().get(i))){
					
					Vec3 vec1 = data.getBox().getPbcCorrectedDirection(c, c.getNeigh().get(i));
					float vec1_sqrlength = vec1.getLengthSqr();
					float v1LengthTimesThreshold = 0.9025f * vec1_sqrlength;
					
					for (int j=i+1; j<c.getNeigh().size(); j++){
						if (!firstRun || planarDefectAtomsLookup.contains(c.getNeigh().get(j))){
							
							Vec3 vec2 = data.getBox().getPbcCorrectedDirection(c, c.getNeigh().get(j));
							float p = vec1.dot(vec2);
							
							if (p < 0f && (p*p) > v1LengthTimesThreshold * vec2.getLengthSqr()){ 
								straightBonds.add(data.getBox().getPbcCorrectedDirection(c.getNeigh().get(i), c.getNeigh().get(j)));
							}
						}
					}
				}
			}
			if (straightBonds.size()>=bondThreshold){
				//Three straight bonds are found, now test if they are in the same plane or forming multiple planes.
				//If there is only one plane, start BFS to detect the whole surface.
				Vec3 normal = straightBonds.get(0).cross(straightBonds.get(1)).normalize();
				if (bondThreshold == 3){
					float thirdBondInSamePlane = Math.abs(straightBonds.get(2).dot(normal));
					if (thirdBondInSamePlane > 0.1f) continue; //Not a single plane -> do nothing
				}
				
				TreeSet<PlanarDefectAtom> atomsInSet = new TreeSet<PlanarDefectAtom>();
				Queue<PlanarDefectAtom> atomsQueue = new LinkedList<PlanarDefectAtom>();
				
				//Surface is detected, now test which glide plane fits best
				int t=0;
				float best = 0;
				for (int i=0; i<planeNormals.length;i++){
					float sim = Math.abs(normal.dot(planeNormals[i]));
					if (sim>best){
						best = sim;
						t = i;
					}
				}
				
				//BFS to find the surface atoms
				atomsInSet.add(c);
				atomsQueue.add(c);
				while (!atomsQueue.isEmpty()){
					PlanarDefectAtom b = atomsQueue.poll();
					int fittingAtoms = 0;
					int fittingArraySize = 0;
					
					for (int i=0; i<b.getNeigh().size(); i++){
						PlanarDefectAtom n = b.getNeigh().get(i);
						
						//Count nearest neighbor hcp-atoms in the same plane 
						if (planarDefectAtomsLookup.contains(n)){
							// equals (vec/|vec|)*normal < 0.2, but this method is more efficient 
							Vec3 vec = data.getBox().getPbcCorrectedDirection(b, n);
							float p = vec.dot(normal);
							if ( p*p < 0.04f * vec.getLengthSqr() && n.getAtom().getType() == c.getAtom().getType()){
								fittingAtoms++;
								if (!atomsInSet.contains(n)){
									fittingArray[fittingArraySize] = n;
									fittingArraySize++;
								}
							}
						}
					}
					//if the atom has at least three neighbors in the same plane, the surface detection can proceed at this atom.
					//If not, the detection should stop here, otherwise the plane might extend itself as a line in other planes
					//Atoms that are ignored in first step by this are attached later to their planes.
					if (fittingAtoms>=3){
						for (int i=0; i<fittingArraySize; i++){
							atomSet.remove(fittingArray[i]);
							atomsInSet.add(fittingArray[i]);
							atomsQueue.add(fittingArray[i]);
						}
					}
				}
				if (atomsInSet.size()>=3){
					planarDefectList.add(new PlanarDefect(atomsInSet, planeNormals[t], defectMap, grain, id++));
					planarDefectAtomsLookup.removeAll(atomsInSet);
				}
				
			}
			else leftOverAtoms.add(c);
		}
		return leftOverAtoms;
	}


	/**
	 * Returns an array of adjacent dislocations
	 * Warning: Might be null if they have not been created
	 * @return 
	 */
	public Dislocation[] getAdjacentDislocations() {
		return adjacentDislocations;
	}

	/**
	 * Each plane consists of only one type of atoms
	 * @return the type of all atoms in this planar defect
	 */
	public int getPlaneComposedOfType() {
		return planeComposedOfType;
	}

	/**
	 * Note: To find adjacent dislocations for planar defects, first find the adjacent planar defects
	 * for dislocations
	 * @param dislocations a collection of all adjacent dislocations
	 */
	protected void findAdjacentDislocations(Collection<Dislocation> dislocations) {
		ArrayList<Dislocation> contactDis = new ArrayList<Dislocation>();
		for (Dislocation dis : dislocations){
			if (dis.isPlanarDefectAdjacent(this)) contactDis.add(dis);
		}
		this.adjacentDislocations = contactDis.toArray(new Dislocation[contactDis.size()]);
	}
	
	/**
	 * The plane's surface normal in xyz-coordinates (not in crystal-coordinates) 
	 * @return
	 */
	public Vec3 getNormal() {
		return normal;
	}
	
	/**
	 * The unique ID of the stacking fault plane
	 * @return
	 */
	public int getID() {
		return id;
	}
	
	
	private void createFaces(Set<PlanarDefectAtom> planarDefectAtoms){
		ArrayList<Atom> faces = new ArrayList<Atom>();
		//Find three atoms in triangular configuration in the same plane
		//if a triangle is found and the first atom has the highest number,
		//save the triangle in the list and for later rendering
		//Creating only triangles if the first atom has the highest number 
		//ensures that the same triangle is not created twice
		for (PlanarDefectAtom a : planarDefectAtoms){
			for (int i=0; i<a.getNeigh().size(); i++){
				PlanarDefectAtom n1 = a.getNeigh().get(i);
				if (a.compareTo(n1)>0 && planarDefectAtoms.contains(a.getNeigh().get(i))){
					//Look for common neighbors
					for (int j=i; j<a.getNeigh().size(); j++){
						for (int k=0; k<n1.getNeigh().size(); k++){
							if (a.getNeigh().get(j) == n1.getNeigh().get(k) && 
									a.compareTo(a.getNeigh().get(j)) > 0 && 
									planarDefectAtoms.contains(a.getNeigh().get(j))){
								faces.add(a.getAtom());
								faces.add(n1.getAtom());
								faces.add(a.getNeigh().get(j).getAtom());
							}
						}	
					}
				}
			}
		}
		
		
		this.faces = faces.toArray(new Atom[faces.size()]);
	}
	
	
	/**
	 * Get a transformed representation of the planar defect's atoms as a list of triangles for rendering the 
	 * plane.
	 * The list contains triples of three atoms (storing the triangle's vertices) for each face. 
	 * @return
	 */
	public Atom[] getFaces(){
		return faces;
	}
	
	public float getArea(){
		float area = 0f;
		for (int i=0; i<getFaces().length/3;i++){
			Vec3 d1 = faces[i*3+1].subClone(faces[i*3]);
			Vec3 d2 = faces[i*3+2].subClone(faces[i*3]);
			area += 0.5f * d1.cross(d2).getLength();
		}
		return area;
	}
	
	@Override
	public String toString() {
		return String.format("Planar defect (%d) Normal=(%.3f,%.3f,%.3f) #Atoms=%d Area=%f", 
				id, normal.x, normal.y, normal.z, numberOfAtoms, getArea());
	}
	
	@Override
	public Collection<?> getHighlightedObjects() {
		if (adjacentDislocations == null) return null;
		ArrayList<Dislocation> d = new ArrayList<Dislocation>(adjacentDislocations.length);
		for (int i=0; i<adjacentDislocations.length; i++)
			d.add(adjacentDislocations[i]);
		return d;
	}
	
	@Override
	public boolean isHighlightable() {
		return true;
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		Vec3 centroid = new Vec3();
		for (Atom a : faces)
			centroid.add(a);
		centroid.divide(faces.length);
		return centroid;
	}
	
	//TODO format message
	@Override
	public Tupel<String,String> printMessage(InputEvent ev, AtomData data) {
		Vec3 o;
		String type;
		if (grain == null){
			o = data.getCrystalRotation().getInCrystalCoordinates(normal);			
		} else { 
			o = grain.getCystalRotationTools().getInCrystalCoordinates(normal);
		}
		type = data.getCrystalStructure().getNameForType(getPlaneComposedOfType());
		o.multiply(data.getCrystalStructure().getLatticeConstant());
		String s = String.format("Planar defect (%d) Normal=(%.3f,%.3f,%.3f) in crystal axis=(%.3f,%.3f,%.3f) #Atoms=%d Area=%f Type: %s", 
				id, normal.x, normal.y, normal.z, o.x, o.y, o.z, numberOfAtoms, getArea(), type); 
		return new Tupel<String, String>("Stacking Fault", s);
	}
}
