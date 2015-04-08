package model.polygrain.grainDetection;

import java.util.ArrayList;
import java.util.List;

import model.Atom;
import model.NearestNeighborBuilder;

public class GrainDetector {
	
	public static List<List<Atom>> identifyGrains(List<Atom> atoms, final GrainDetectionCriteria gdc){
		final List<List<Atom>> allDetectedGrainSets = new ArrayList<List<Atom>>();
		
		GrainObject.init();
		final NearestNeighborBuilder<AtomToGrainObject> nnb = new NearestNeighborBuilder<AtomToGrainObject>( 
				gdc.getNeighborDistance());
		ArrayList<AtomToGrainObject> allAtoms = new ArrayList<AtomToGrainObject>(atoms.size());
		
		for (int i=0; i<atoms.size();i++){
			if (gdc.includeAtom(atoms.get(i))){
				AtomToGrainObject atog = new AtomToGrainObject(atoms.get(i));
				nnb.add(atog);
				allAtoms.add(atog);
			}
		}
		
		final AtomListHandler alh = new AtomListHandler(allAtoms);
	
		AtomToGrainObject atog = null;
		while ( (atog = alh.getNextStartAtom()) != null) {
			
			ArrayList<AtomToGrainObject> neighA = nnb.getNeigh(atog);
			if (!gdc.acceptAsFirstAtomInGrain(atog.getAtom(), neighA)) continue;
			
			GrainObject grainObj = new GrainObject();
			
			ArrayList<AtomToGrainObject> stack = new ArrayList<AtomToGrainObject>();
			//From each item in the grain array the bfs is continued
			//There is no need to keep a second list, the index of processed
			//atoms is enough
			int queuePosition = 0;
			GrainObject oldGrain = atog.setGrainObject(grainObj);
			stack.add(atog);	

			while (queuePosition < stack.size()) {
				AtomToGrainObject c = stack.get(queuePosition++);
				
				ArrayList<AtomToGrainObject> neighC = nnb.getNeigh(c);
				
				if (gdc.includeAtom(c, neighC)) {
					for (int i = 0; i < neighC.size(); i++) {
						oldGrain = neighC.get(i).setGrainObject(grainObj);
						if (oldGrain == null)
							stack.add(neighC.get(i));
					}
				}
			}
			
			if (stack.size() > gdc.getMinNumberOfAtoms()){
				ArrayList<Atom> at = new ArrayList<Atom>();
				for (AtomToGrainObject a : stack)
					at.add(a.getAtom());
				
				allDetectedGrainSets.add(at);
			} else {
				for (AtomToGrainObject a : stack)
					a.setGrainObject(null);
			}
		}
		
		return allDetectedGrainSets;
	}
		
	private static class AtomListHandler{
		ArrayList<AtomToGrainObject> allAtoms;
		int i = 0;
		
		AtomListHandler(ArrayList<AtomToGrainObject> allAtoms) {
			this.allAtoms = allAtoms;
		}
		
		synchronized AtomToGrainObject getNextStartAtom(){
			AtomToGrainObject nextNullObject = null;
			while (i<allAtoms.size() && nextNullObject == null){
				if (allAtoms.get(i).isGrainObjectNull()){
					nextNullObject = allAtoms.get(i);
				}
				i++;
			}
			
			return nextNullObject;
		}
	}
}
