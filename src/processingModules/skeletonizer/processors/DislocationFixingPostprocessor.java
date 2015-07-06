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

package processingModules.skeletonizer.processors;

import java.util.*;

import common.Vec3;
import model.Atom;
import model.NearestNeighborBuilder;
import processingModules.skeletonizer.*;

/**
 * Broken dislocations ending inside a crystal are fixed with a heuristic algorithm. These intersections are typically caused by 
 * misclassified dislocation core atoms. Free endings in a crystal are joined into another free ending,
 * a dislocation junction, or a node on another dislocation in its vicinity.  
 * @author begauc9f
 *
 */
public class DislocationFixingPostprocessor implements SkeletonDislocationPostprocessor {
	private final float nearestNeighborDistance;

	private final static float MAX_DISTANCE_SCALING = 3f;
	private final float invThresholdDistance;
	
	private Skeletonizer skeleton;
	
	public DislocationFixingPostprocessor(float nearestNeighborDistance) {
		this.nearestNeighborDistance = nearestNeighborDistance;
		this.invThresholdDistance = 1f / (MAX_DISTANCE_SCALING * MAX_DISTANCE_SCALING * nearestNeighborDistance * nearestNeighborDistance);
	}
	
	/**
	 * Heuristic to fix dislocations, which are intersected due to wrong classified dislocation core atoms
	 */
	@Override
	public void postProcessDislocations(Skeletonizer skel) {
		this.skeleton = skel;
		TreeMap<SkeletonNode, Dislocation> freeEndings = new TreeMap<SkeletonNode, Dislocation>();

		float minDistLength = skel.getAtomData().getCrystalStructure().getPerfectBurgersVectorLength() * MAX_DISTANCE_SCALING;
		Iterator<Dislocation> iterDis = skel.getDislocations().iterator();
		// Remove "dislocations" that are connected to nothing and are very short
		while (iterDis.hasNext()) {
			Dislocation d = iterDis.next();
			if (d.getStartNode().getNeigh().size() == 1 && d.getEndNode().getNeigh().size() == 1
					&& d.getLength() < minDistLength) iterDis.remove();
		}

		// Create a nnb between all atoms tagged as surface and atoms at dislocation ends
		// Can quite efficiently filter out the dislocations ending at a surface
		NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(
				skel.getAtomData().getBox(), skel.getAtomData().getCrystalStructure().getNearestNeighborSearchRadius());

		int surfaceType = skel.getAtomData().getCrystalStructure().getSurfaceType();
		
		for (Atom a : skel.getAtomData().getAtoms()){ //Adding the free surface atoms
			if (a.getType() == surfaceType) nnb.add(a);
		}
		
		for (Dislocation d : skel.getDislocations()){ //Adding all atoms from dislocation ends
			for (Atom a : d.getStartNode().getMappedAtoms())
				nnb.add(a);
			for (Atom a : d.getEndNode().getMappedAtoms())
				nnb.add(a);
		}

		//Identify dislocation ending in the vicinity of the free surface
		for (Dislocation d : skel.getDislocations()){
			if (d.getStartNode().getNeigh().size()==1){
				boolean adjacentToSurface = false;
				for (Atom a : d.getStartNode().getMappedAtoms()) {
					ArrayList<Atom> neigh = nnb.getNeigh(a);
					for (Atom n : neigh) {
						if (n.getType() == surfaceType) {
							adjacentToSurface = true;
							break;
						}
					}
				}
				if (!adjacentToSurface) freeEndings.put(d.getStartNode(), d);
			}
			if (d.getEndNode().getNeigh().size() == 1) {
				boolean adjacentToSurface = false;
				for (Atom a : d.getEndNode().getMappedAtoms()) {
					ArrayList<Atom> neigh = nnb.getNeigh(a);
					for (Atom n : neigh) {
						if (n.getType() == surfaceType) {
							adjacentToSurface = true;
							break;
						}
					}
				}
				if (!adjacentToSurface) freeEndings.put(d.getEndNode(), d);
			}
		}

		NearestNeighborBuilder<SkeletonNode> skelNnb = new NearestNeighborBuilder<SkeletonNode>(skel.getAtomData().getBox(), 
				nearestNeighborDistance * MAX_DISTANCE_SCALING);
		for (SkeletonNode n : skel.getNodes()) {
			skelNnb.add(n);
		}
		
		// Case 1: join two free endings if they are very close to each other
		// Remove both dislocations and replace them by the new one
		SkeletonNode n1 = freeEndings.isEmpty() ? null: freeEndings.firstKey();
		while (n1 != null) {
			Dislocation d1 = freeEndings.get(n1);
			ArrayList<SkeletonNode> nearByNodes = skelNnb.getNeigh(n1);

			SkeletonNode bestFit = null;
			float bestValue = 1f;

			for (SkeletonNode n2 : nearByNodes) {
				if (freeEndings.containsKey(n2)) {
					float fit = nodeFixingQuality(n1, d1, n2);
					if (fit < 1) {
						if (bestFit == null || fit < bestValue) {
							bestFit = n2;
							bestValue = fit;
						}
					}
				}
			}
			if (bestFit!=null){
				Dislocation d2 = freeEndings.get(bestFit);
				if (d1!=d2){ //Do not create a loop!
					freeEndings.remove(d1.getStartNode());
					freeEndings.remove(d2.getStartNode());
					freeEndings.remove(d1.getEndNode());
					freeEndings.remove(d2.getEndNode());
					Dislocation comb = this.joinDislocations(d1, n1, d2, bestFit);
					//Remove old dislocations
					skel.getDislocations().remove(d1);
					skel.getDislocations().remove(d2);
					//Add new one
					skel.getDislocations().add(comb);
					
					if (comb.getStartNode().getNeigh().size()==1) freeEndings.put(comb.getStartNode(), comb);
					if (comb.getEndNode().getNeigh().size()==1) freeEndings.put(comb.getEndNode(), comb);
				}
			}
			n1 = freeEndings.higherKey(n1);
		}

		// Case 2: join a free ending into an existing junction
		n1 = freeEndings.isEmpty() ? null: freeEndings.firstKey();
		while (n1 != null){
			Dislocation d1 = freeEndings.get(n1);

			ArrayList<SkeletonNode> nearByNodes = skelNnb.getNeigh(n1);
			// Find nearest junction in tolerance, but do not create loops in dislocations
			SkeletonNode nearest = null;
			float bestDist = 1f;

			for (SkeletonNode n2 : nearByNodes) {
				if (n2.getNeigh().size() <= 2) continue;
				if ((n1 == d1.getStartNode() && n2 == d1.getEndNode()) || (n1 == d1.getEndNode() && n2 == d1.getStartNode()))
					continue;
				float dist = nodeFixingQuality(n1, d1, n2);
				if (dist < bestDist){
					nearest = n2;
					bestDist = dist;
				}
			}

			if (bestDist < 1f){
				//join dislocation into junction
				this.joinDislocationIntoJunction(d1, n1, nearest);
				freeEndings.remove(n1);
			}
			n1 = freeEndings.higherKey(n1);
		}
		
		
		// Case 3: join a free ending into a node on another dislocation
		// This will cut the dislocation into two new dislocation with a new
		// junction
		n1 = freeEndings.isEmpty() ? null:freeEndings.firstKey();
		while (n1 != null){
			Dislocation d1 = freeEndings.get(n1);
			//Find the best none-junction node on any dislocations, but do not create loops in dislocations
			SkeletonNode nearest = null;
			Dislocation nearestDis = null;
			float bestDist = 1f;
			
			for (Dislocation dis : skel.getDislocations()){ //<--Slow, iterating over all dislocations
				if (dis == d1) continue;
				for (int i=0; i<dis.getLine().length; i++){
					if (dis.getLine()[i].getNeigh().size() == 2){
						float dist = nodeFixingQuality(n1, d1, dis.getLine()[i]);
						if (dist < bestDist){
							nearest = dis.getLine()[i];
							nearestDis = dis;
							bestDist = dist;
						}
					}
				}
			}

			if (nearest != null){
				//join dislocation into junction
				Dislocation[] newDis = this.joinDislocationIntoNode(d1, n1, nearestDis, nearest);
				freeEndings.remove(n1);
				skel.getDislocations().remove(nearestDis);
				skel.getDislocations().add(newDis[0]);
				skel.getDislocations().add(newDis[1]);
				//Update possible freeEndings of the replaced dislocation
				if (freeEndings.containsKey(newDis[0].getStartNode())) freeEndings.put(newDis[0].getStartNode(), newDis[0]);
				if (freeEndings.containsKey(newDis[0].getEndNode())) freeEndings.put(newDis[0].getEndNode(), newDis[0]);
				if (freeEndings.containsKey(newDis[1].getStartNode())) freeEndings.put(newDis[1].getStartNode(), newDis[1]);
				if (freeEndings.containsKey(newDis[1].getEndNode())) freeEndings.put(newDis[1].getEndNode(), newDis[1]);
			}
			n1 = freeEndings.higherKey(n1);
		}
	}

	/**
	 * Estimate the quality for joining a dislocation into the tested node
	 * Takes into account the gap distance, bending angle of the dislocation and the difference in angle of RBVs  
	 * @param origin
	 * @param d
	 * @param testNode
	 * @return : a value <= 1 indicates, that the node is acceptable for fixing free dislocations, the smaller the better
	 * any value >1 is unacceptable 
	 */
	private float nodeFixingQuality(SkeletonNode origin, Dislocation d, SkeletonNode testNode){
		if (skeleton.getAtomData().isPolyCrystalline() && 
				!skeleton.getAtomData().getCrystalStructure().skeletonizeOverMultipleGrains()){
			if (origin.getMappedAtoms().get(0).getGrain() != testNode.getMappedAtoms().get(0).getGrain()) return 2f;
		}
		
		//Test if the testNode is not too far away
		Vec3 o = skeleton.getAtomData().getBox().getPbcCorrectedDirection(testNode, origin);
		float distanceRatio = o.getLengthSqr() * invThresholdDistance;
		if (distanceRatio>1f) return distanceRatio;
		
		//Check if the angle between the dislocation direction and the direction to the nodes do not exceed a threshold
		//to prevent a link back onto a another dislocation in opposite direction
		Vec3 direction;
		if (d.getStartNode() == origin){
			direction = d.getLine()[0].subClone(d.getLine()[1]);
		} else direction = d.getLine()[d.getLine().length-1].subClone(d.getLine()[d.getLine().length-2]);
		
		double angle = o.getAngle(direction);
		
		if (skeleton.getAtomData().isRbvAvailable()){
			Vec3 rbv1 = new Vec3();
			Vec3 rbv2 = new Vec3();
			Vec3 ld1 = new Vec3();
			Vec3 ld2 = new Vec3();
			
			for (Atom a: origin.getMappedAtoms()){
				rbv1.add(a.getRBV().bv);
				ld1.add(a.getRBV().lineDirection);
			}
			for (Atom a: testNode.getMappedAtoms()){
				rbv2.add(a.getRBV().bv);
				ld2.add(a.getRBV().lineDirection);
			}
			
			if (ld1.dot(ld2) < 0f)
				rbv2.multiply(-1f);
			
			double angleBurgers = (rbv1.x * rbv2.x + rbv1.y * rbv2.y + rbv1.z * rbv2.z) / 
					(Math.sqrt(rbv1.x * rbv1.x + rbv1.y * rbv1.y + rbv1.z * rbv1.z) * 
					 Math.sqrt(rbv2.x * rbv2.x + rbv2.y * rbv2.y + rbv2.z * rbv2.z));
			
			if (angleBurgers>0.866){	//Prefer node strongly with a similar burgersvector
				angleBurgers = 1.-angleBurgers;
				angle *= angleBurgers;
			} else if (angleBurgers<0.5){	//And avoid too different burgers vectors
				angle*= 1./angleBurgers;
			}
		}
		
		if (angle<Math.PI*0.6) return distanceRatio;
		else return 2f;
	}
	
	private Dislocation joinDislocations(Dislocation d1, SkeletonNode sn1, Dislocation d2, SkeletonNode sn2){
		//Combine both linestrips
		SkeletonNode[] d1Line = d1.getLine();
		if (sn1 == d1Line[0]) revertArray(d1Line);
		SkeletonNode[] d2Line = d2.getLine();
		if (sn2 != d2Line[0]) revertArray(d2Line);
		
		if (sn1 != d1Line[d1Line.length-1] || sn2 != d2Line[0] || sn1.getNeigh().size()!=1 || sn2.getNeigh().size()!=1)
			throw new IllegalArgumentException("lines cannot be joined");
		
		SkeletonNode[] combinedLine = new SkeletonNode[d1Line.length+d2Line.length];
		
		for (int i=0;i<d1Line.length;i++){
			combinedLine[i] = d1Line[i];
		}
		for (int i=0;i<d2Line.length;i++){
			combinedLine[i+d1Line.length] = d2Line[i];
		}
		
		sn1.getNeigh().add(sn2);
		sn2.getNeigh().add(sn1);
		
		Dislocation combDis = new Dislocation(combinedLine, skeleton);
		return combDis;
	}
	
	private void joinDislocationIntoJunction(Dislocation d1, SkeletonNode sn1, SkeletonNode junction){
		if (sn1.getNeigh().size() != 1 || junction.getNeigh().size() <= 2)
			throw new IllegalArgumentException("joining not possible");
		
		junction.getNeigh().add(sn1);
		sn1.getNeigh().add(junction);
		
		ArrayList<SkeletonNode> nodes = new ArrayList<SkeletonNode>();
		if (d1.getStartNode()==sn1) nodes.add(junction);
		
		for (int i=0; i<d1.getLine().length; i++){
			nodes.add(d1.getLine()[i]);
		}
		if (d1.getEndNode()==sn1) nodes.add(junction);
		
		d1.replaceLine(nodes.toArray(new SkeletonNode[d1.getLine().length]));
	}
	
	private Dislocation[] joinDislocationIntoNode( Dislocation d1, SkeletonNode sn1, Dislocation d2, SkeletonNode newJunction){
		if (sn1.getNeigh().size() != 1 || newJunction.getNeigh().size() != 2)
			throw new IllegalArgumentException("joining not possible");
		
		//Split the linestrip in two parts
		int cut = 0;
		for (int i=0; i<d2.getLine().length;i++){
			if (d2.getLine()[i] == newJunction){
				cut = i;
				break;
			}
		}
		
		SkeletonNode[] p1 = new SkeletonNode[cut+1];
		SkeletonNode[] p2 = new SkeletonNode[d2.getLine().length-cut];
		
		for (int i=0; i<=cut; i++){
			p1[i] = d2.getLine()[i];
		}
		for (int i=0; i<d2.getLine().length-cut; i++){
			p2[i] = d2.getLine()[i+cut];
		}
		
		//Create two new dislocations from both parts
		Dislocation newD1 = new Dislocation(p1, skeleton);
		Dislocation newD2 = new Dislocation(p2, skeleton);
		
		//Add existing dislocation into newJunction
		newJunction.getNeigh().add(sn1);
		sn1.getNeigh().add(newJunction);
		
		ArrayList<SkeletonNode> nodes = new ArrayList<SkeletonNode>();
		if (d1.getStartNode()==sn1) nodes.add(newJunction);
		
		for (int i=0; i<d1.getLine().length; i++){
			nodes.add(d1.getLine()[i]);
		}
		if (d1.getEndNode()==sn1) nodes.add(newJunction);
		
		d1.replaceLine(nodes.toArray(new SkeletonNode[d1.getLine().length]));
			
		return new Dislocation[]{newD1, newD2};
	}
	
	private static void revertArray(Object[] o) {
		for (int left = 0, right = o.length - 1; left < right; left++, right--) {
			Object temp = o[left];
			o[left] = o[right];
			o[right] = temp;
		}
	}
}
