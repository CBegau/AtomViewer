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

import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import common.ThreadPool;

import model.NearestNeighborBuilder;
import processingModules.skeletonizer.SkeletonNode;
import processingModules.skeletonizer.Skeletonizer;

public class ReMeshingPreprocessor implements SkeletonPreprocessor {

	@Override
	public void preProcess(final Skeletonizer skel) {
		final NearestNeighborBuilder<SkeletonNode> nnb = 
				new NearestNeighborBuilder<SkeletonNode>(skel.getAtomData().getBox(), skel.getNearestNeigborDist(), true);
		nnb.addAll(skel.getNodes());
			
		//Parallel build
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		final List<SkeletonNode> nodes = skel.getNodes(); 
		
		final boolean sameGrainsOnly = skel.getAtomData().isPolyCrystalline() && !skel.skeletonizeOverGrains();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					int start = (int)(((long)nodes.size() * j)/ThreadPool.availProcessors());
					int end = (int)(((long)nodes.size() * (j+1))/ThreadPool.availProcessors());

					for (int i=start; i<end; i++)
						nodes.get(i).buildNeigh(nnb, sameGrainsOnly);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
	}
}
