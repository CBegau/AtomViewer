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

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import common.ThreadPool;
import common.Tupel;
import model.RBV;
import processingModules.skeletonizer.SkeletonNode;
import processingModules.skeletonizer.Skeletonizer;

public class RBVAngleFilterPreprocessor implements SkeletonPreprocessor {

	private final float maxAngle;
	
	/**
	 * Removes all connections if the rbv's of the connected atoms deviate more than a given threshold
	 * @param maxAngle filtering threshold in degree
	 */
	public RBVAngleFilterPreprocessor(float maxAngle){
		this.maxAngle = maxAngle;
	}
	
	@Override
	public void preProcess(final Skeletonizer skel) {
		if (!skel.getAtomData().isRbvAvailable()) return;
		
		final float PHI_MIN = (float) Math.cos((180f-maxAngle)*Math.PI/180.);
		final float PHI_MAX = (float) Math.cos(maxAngle*Math.PI/180.);
		
		final Vector<Tupel<SkeletonNode, SkeletonNode>> toDelete = new Vector<Tupel<SkeletonNode,SkeletonNode>>();
		
		Vector<Callable<Void>> tasks = new Vector<Callable<Void>>();

		//Evaluate all new positions (in parallel)
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int start = (int)(((long)skel.getNodes().size() * i)/ThreadPool.availProcessors());
			final int end = (int)(((long)skel.getNodes().size() * (i+1))/ThreadPool.availProcessors());
			tasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					for (int j=start; j<end; j++){
						SkeletonNode sn = skel.getNodes().get(j);
						if (sn.getNeigh().size()<=2) continue;
						RBV v = sn.getMappedAtoms().get(0).getRBV();
						float l_v = v.bv.getLength();
						
						ArrayList<SkeletonNode> nei = sn.getNeigh();
						
						for (int i=0; i<nei.size();i++){
							SkeletonNode n = nei.get(i);
							//Avoid performing the test twice for both atoms
							if (n.getID() < sn.getID()){
								RBV u = n.getMappedAtoms().get(0).getRBV();
								float l_u =  u.bv.getLength();
								float angle = v.bv.dot(u.bv) / (l_u * l_v);
								
								if (angle < PHI_MAX && angle > PHI_MIN){
									toDelete.add(new Tupel<SkeletonNode,SkeletonNode>(n,sn));
								}
							}
						}
					}
					
					
					return null;
				}
			});
		}
		ThreadPool.executeParallel(tasks);
		
		//Delete Tupel
		for (Tupel<SkeletonNode,SkeletonNode> t : toDelete){
			t.o1.removeNeigh(t.o2);
			t.o2.removeNeigh(t.o1);
		}
		
		final List<SkeletonNode> nodes = skel.getNodes();
		for (int i=0; i<nodes.size(); i++){
			if (nodes.get(i).getNeigh().size() == 0) {
				nodes.remove(i);
				i--;
			}
		}
	}

}
