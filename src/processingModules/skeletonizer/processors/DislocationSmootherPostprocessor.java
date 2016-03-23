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
import java.util.Vector;
import java.util.concurrent.Callable;

import common.ThreadPool;
import common.Vec3;
import model.BoxParameter;
import processingModules.skeletonizer.Dislocation;
import processingModules.skeletonizer.SkeletonNode;
import processingModules.skeletonizer.Skeletonizer;

/**
 * Smoothing the shape of dislocation lines using simple laplacian smoothing
 */
public class DislocationSmootherPostprocessor implements SkeletonDislocationPostprocessor {
	@Override
	public void postProcessDislocations(final Skeletonizer skel) {
		final ArrayList<Dislocation> dis = skel.getDislocations();
		final BoxParameter box = skel.getAtomData().getBox();
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)(((long)dis.size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)dis.size() * (j+1))/ThreadPool.availProcessors());
					//Iterate over each dislocation in parallel and smooth each one
					for (int j=start; j<end; j++){
						Dislocation d = dis.get(j);
						if (d.getLine().length>2){
							Vec3[] pos = new Vec3[d.getLine().length-2];
							//Ignore first and last node. Junctions are not moved 
							for (int i=1; i<d.getLine().length-1; i++){
								SkeletonNode n = d.getLine()[i];
								Vec3 n1 = box.getPbcCorrectedDirection(n, d.getLine()[i-1]);
								Vec3 n2 = box.getPbcCorrectedDirection(n, d.getLine()[i+1]);
								
								//Computing new position, depending on the current location and the vectors
								//to neighboring nodes
								pos[i-1] = n.subClone(n1.add(n2).multiply(0.25f));
							}
							for (int i=1; i<d.getLine().length-1; i++)
								d.getLine()[i].setTo(pos[i-1]);
						}
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
	}
}
