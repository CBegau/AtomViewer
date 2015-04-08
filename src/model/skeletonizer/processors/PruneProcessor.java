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

package model.skeletonizer.processors;

import common.FastDeletableVector;

import model.skeletonizer.SkeletonNode;
import model.skeletonizer.Skeletonizer;

/**
 * PruneProcessor removes all minor branches with only one node length from a mesh
 * It can be applied as a pre- or postprocessor.
 * @author begauc9f
 *
 */
public class PruneProcessor implements SkeletonMeshPostprocessor, SkeletonPreprocessor{

	@Override
	public void preProcess(Skeletonizer skel) {
		prune(skel);
	}
	
	@Override
	public void postProcessMesh(Skeletonizer skel) {
		prune(skel);
	}
	
	private void prune(Skeletonizer skel){
		FastDeletableVector<SkeletonNode> nodes = skel.getNodes();
		
		for (int i=0; i<nodes.size(); i++){
			SkeletonNode a = nodes.get(i);
			if (a.getNeigh().size() == 1){
			//Delete branches with a length of one node out of a junction
				if (a.getNeigh().get(0).getNeigh().size()>=3){
					a.prepareDeleting();
					nodes.remove(i);
					i--;
				}
			}
		}
	}
}
