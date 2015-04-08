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

package model.skeletonizer;

import java.util.*;

import model.*;
import common.Vec3;

/**
 * Skeleton nodes are derived from atoms forming a nearest neighborhood mesh.
 * During skeletonization, nodes are merged to reduce the mesh complexity and to
 * derive one-dimensional curves (->Dislocations).
 * @author begauc9f
 *
 */
public class SkeletonNode extends Vec3 implements Comparable<SkeletonNode>{
	
	public static final int MAX_MERGED_ATOMS = 150;
	
	private final int id;
	
	private ArrayList<Atom> mappedAtoms = new ArrayList<Atom>(5);
	private ArrayList<SkeletonNode> neigh = new ArrayList<SkeletonNode>(3);
	
	//State variables, costly operations to identify topology are
	//only executed if the lists of neighbors is changes in the meantime
	private boolean neighListTouched = true;
	private boolean isCriticalNode = false;
	
	private ArrayList<Dislocation> joiningDislocation;
	
	/**
	 * Create a SkeletonNode as a representation of an atom 
	 * @param atom
	 */
	public SkeletonNode(Atom atom, int id){
		this.x = atom.x;
		this.y = atom.y;
		this.z = atom.z;
		this.mappedAtoms.add(atom);
		this.id = id;
	}
	
	/**
	 * Merges the given skeleton node into this one
	 * @return
	 */
	protected void mergeSkeletonNode(SkeletonNode b){
		this.add(b).multiply(0.5f);
		
		this.neigh.remove(b);
		for (int i=0; i < b.neigh.size(); ++i){
			SkeletonNode nei = b.neigh.get(i);
			if (nei != this){
				if (!nei.neigh.contains(this)){
					nei.replaceNeigh(b, this);
					this.neigh.add(nei);
				}
				else nei.removeNeigh(b);
			}
		}
		
		this.mappedAtoms.addAll(b.getMappedAtoms());
		
		this.neighListTouched = true;
	}
	
	/**
	 * The nearest neighbors of this atom
	 * WARNING: The list has to be created via buildNeigh(NearestNeighborBuilder) before. Otherwise it may be null.
	 * @return
	 */
	public ArrayList<SkeletonNode> getNeigh() {
		return neigh;
	}
	
	/**
	 * The list contains all atoms that have been mapped onto this node during skeletonization
	 * @return a list of mapped atoms
	 */
	public ArrayList<Atom> getMappedAtoms() {
		return mappedAtoms;
	}
	
	/**
	 * a unique identifier for this node 
	 * @return a unique identifier for this node
	 */
	public int getID(){
		return id;
	}
	
	public void buildNeigh(NearestNeighborBuilder<SkeletonNode> root, boolean sameGrainsOnly){
		this.neigh = root.getNeigh(this);
		if (sameGrainsOnly){
			ArrayList<SkeletonNode> sameGrainNeigh = new ArrayList<SkeletonNode>();
			for (int i=0; i<neigh.size(); i++){
				if (this.getMappedAtoms().get(0).getGrain() == neigh.get(i).getMappedAtoms().get(0).getGrain()){
					sameGrainNeigh.add(neigh.get(i));
				}
			}
			this.neigh = sameGrainNeigh;
		}
		 
	}
	
	/**
	 * 
	 * @param pbc
	 * @param size
	 */
	protected void centerToMappedAtoms(BoxParameter box){
		Vec3 shift = new Vec3();
		Vec3 ref = mappedAtoms.get(0);
		Vec3 size = box.getHeight().multiplyClone(0.5f);
		boolean[] pbc = box.getPbc();
		
		for (int i=0; i<mappedAtoms.size(); i++){
			Atom c = mappedAtoms.get(i);
			
			if (pbc[0] && c.x - ref.x < -size.x)
				shift.add(box.getBoxSize()[0]);
			else if (pbc[0] && c.x - ref.x > size.x)
				shift.sub(box.getBoxSize()[0]);
			
			if (pbc[1] && c.y - ref.y < -size.y)
				shift.add(box.getBoxSize()[1]);
			else if (pbc[1] && c.y - ref.y > size.y)
				shift.sub(box.getBoxSize()[1]);
			
			if (pbc[2] && c.z - ref.z < -size.z)
				shift.add(box.getBoxSize()[2]);
			else if (pbc[2] && c.z - ref.z > size.z)
				shift.sub(box.getBoxSize()[2]);
			
			shift.add(c);
		}
		shift.multiply(1f/mappedAtoms.size());
		
		box.backInBox(shift);
		
		this.x = shift.x;
		this.y = shift.y;
		this.z = shift.z;
	}
	
	/**
	 * All mapped atoms in this nodes are transfered to its nearest neighbor.
	 * All shared edges with its neighbors are removed.
	 * References in a list to this node can afterwards safely by removed.   
	 */
	public void prepareDeleting(){
		if (neigh.size()==0)
			return;
		float nearestDist = Float.POSITIVE_INFINITY;
		SkeletonNode nearest = null;
		
		for (int i=0; i<neigh.size(); ++i){
			SkeletonNode n = neigh.get(i);
			n.removeNeigh(this);
			if (this.getSqrDistTo(n) < nearestDist){
				nearestDist = this.getSqrDistTo(n);
				nearest = n;
			}
		}
		nearest.mappedAtoms.addAll(this.mappedAtoms);
		
		this.neigh.clear();
	}
	
	/**
	 * All shared edges with its neighbors are removed.
	 * All mapped atoms are lost permanently after calling this method
	 * References in a list to this node can afterwards safely by removed.
	 */
	public void prepareKilling(){
		for (int i=0; i<neigh.size(); ++i)
			neigh.get(i).removeNeigh(this);
		this.mappedAtoms = null;
		this.neigh = null;
	}
	
	/**
	 * Removes a node from the neighbor list
	 * Has smaller overhead compared to call neigh.remove(a)
	 * @param a
	 */
	public void removeNeigh(SkeletonNode a){
		for (int i=0; i<this.neigh.size();++i){
			if (this.neigh.get(i) == a){
				this.neigh.set(i, this.neigh.get(this.neigh.size()-1));
				this.neigh.remove(this.neigh.size()-1);
				this.neighListTouched = true;
				return;
			}
		}
	}
	
	void setNeighborsToNull(){
		neigh = null;
	}
	
	/**
	 * Replaces a neighbor node with another node
	 * @param replace
	 * @param with
	 */
	private void replaceNeigh(SkeletonNode replace, SkeletonNode with){
		for (int i=0; i<this.neigh.size();++i){
			if (this.neigh.get(i) == replace){
				this.neigh.set(i, with);
				this.neighListTouched = true;
				return;
			}
		}
	}
	
	/**
	 * A node is a critical node if its removal changes the topology,
	 * Here it is by definition a critical node if any node two hops away is to be reached by a unique path.
	 * @return
	 */
	public boolean isCriticalNode(){
//		if (isCriticalNode) return true; //Once a critical node, forever a critical node
		
		if (mappedAtoms.size()>MAX_MERGED_ATOMS)
			return true;
		
		if (neighListTouched){
			neighListTouched = false;
			isCriticalNode = true;
			for (int i=0; i<neigh.size(); i++){
				if (hasCommonNeighbor(neigh.get(i))) {
					isCriticalNode = false;
					return false;
				}
			}
		}
		return isCriticalNode; 
	}
	
	/**
	 * Test if this node and the given node are neighbored at most by two hops
	 * excluding a direct neighborhood 
	 * @param a another SkeletonNode
	 * @return true: common neighbor exists
	 */
	public boolean hasCommonNeighbor(SkeletonNode a){
		//Brute force search over up to two hops with immediate return once
		//a path is found
		//usually much faster in average compared to BFS-like schemes
		for (int i=0; i<this.neigh.size(); i++){
			SkeletonNode n = this.neigh.get(i);
			if (n!=a){
				for (int j=0; j<n.neigh.size(); j++){
					SkeletonNode n2 = n.getNeigh().get(j);
					if (n2 != this){
						if (n2 == a) return true;
						for (int k=0; k<n2.neigh.size(); k++){
							if (n2.neigh.get(k) == a) return true;
						}
					}
				}
			}
		}
		
		return false;
	}

	public ArrayList<Dislocation> getJoiningDislocations(){
		return joiningDislocation;
	}
	
	public void addDislocation(Dislocation d){
		if (joiningDislocation == null)
			joiningDislocation = new ArrayList<Dislocation>();
		
		if (d.getLine()[0]==this || d.getLine()[d.getLine().length-1]== this){
			if (joiningDislocation == null) joiningDislocation = new ArrayList<Dislocation>(4);
			// Do not double count closed dislocation loops
			if (!joiningDislocation.contains(d))
				joiningDislocation.add(d);
		} else throw new IllegalArgumentException("Can't add this dislocation");
	}
	
	@Override
	public int compareTo(SkeletonNode o) {
		return this.id - o.id;
	}
	
	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}
}
