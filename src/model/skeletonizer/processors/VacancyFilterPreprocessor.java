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

import java.util.*;

import model.skeletonizer.*;

/**
 * Vacancy detector in FCC, usually unnecessary if RBV are available
 * Currently not in use
 */
public class VacancyFilterPreprocessor implements SkeletonPreprocessor {

	@Override
	public void preProcess(Skeletonizer skel) {
		TreeSet<SkeletonNode> vacancyNodes = new TreeSet<SkeletonNode>();
		for (SkeletonNode sn : skel.getNodes()){
			List<SkeletonNode> vacancies = detectVacancy(sn);
			if (vacancies != null)
				vacancyNodes.addAll(vacancies);
		}
		
		for (SkeletonNode sn : vacancyNodes){
			sn.prepareKilling();
		}
		skel.getNodes().removeAll(vacancyNodes);
	}

	
	
	private List<SkeletonNode> detectVacancy(SkeletonNode sn){
		if (sn.getMappedAtoms().get(0).getType()!=4) return null;
		
		ArrayList<SkeletonNode> nodesAroundVacancy = new ArrayList<SkeletonNode>();
		Queue<SkeletonNode> nodeQueue = new LinkedList<SkeletonNode>();
		nodesAroundVacancy.add(sn);
		nodeQueue.add(sn);

		while (!nodeQueue.isEmpty()){
			SkeletonNode a = nodeQueue.poll();
			for (SkeletonNode b : a.getNeigh()){
				if (b.getMappedAtoms().get(0).getType() != 4 && b.getMappedAtoms().get(0).getType() != 7) 
					return null;
				else if (!nodesAroundVacancy.contains(b)){
					nodesAroundVacancy.add(b);
					nodeQueue.add(b);
				}
			}
		}
		//Test if the "vacancy" is a surface line defect instead
		//The majority of atoms should have exactly two neighbors then
		int count = 0;
		for (int i=0; i<nodesAroundVacancy.size();i++){
			if (nodesAroundVacancy.get(i).getNeigh().size()<=2) count++;
		}
		if (count/(double)nodesAroundVacancy.size()>0.8) return null;
		
		return nodesAroundVacancy;
	}
}
