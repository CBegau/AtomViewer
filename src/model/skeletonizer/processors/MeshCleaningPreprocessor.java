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


import java.util.List;
import model.skeletonizer.*;

/**
 * MeshCleaningPreprocessor simplifies common structures in an initial mesh directly before the skeleton algorithm is applied.
 * Not really necessary, but in most cases the results are very slightly improved and runtime is not really affected   
 *
 */
public class MeshCleaningPreprocessor implements SkeletonPreprocessor{

	@Override
	public void preProcess(Skeletonizer skel) {
		List<SkeletonNode> nodes = skel.getNodes();

		for (int i=0; i<nodes.size(); i++){
			SkeletonNode a = nodes.get(i);
			if (a.getNeigh().size() == 2){
				//Remove small spikes on the mesh
				// -o____o-        -o____o-
				//    \/	  ==>
				//    °
				
				//Test if both neighbors are connected with each other
				if (a.getNeigh().get(0).getNeigh().size() > 2 &&
						a.getNeigh().get(1).getNeigh().size() > 2 &&
						a.getNeigh().get(0).getNeigh().contains(a.getNeigh().get(1))){
					
					a.prepareDeleting();
					nodes.remove(i);
					i--;
			
				}
			} else if (a.getNeigh().size() == 3){
				//The same for a small tethrahedral spike
				//Test if all three neighbors are connected with each other
				if (a.getNeigh().get(0).getNeigh().size() > 3 &&
						a.getNeigh().get(1).getNeigh().size() > 3 &&
						a.getNeigh().get(2).getNeigh().size() > 3 &&
						a.getNeigh().get(0).getNeigh().contains(a.getNeigh().get(1)) && 
						a.getNeigh().get(0).getNeigh().contains(a.getNeigh().get(2)) &&
						a.getNeigh().get(1).getNeigh().contains(a.getNeigh().get(2))){
					
					a.prepareDeleting();
					nodes.remove(i);
					i--;
				}
			}
		}
	}
}
