package model.polygrain.grainDetection;

import java.util.List;

import model.Atom;

public interface GrainDetectionCriteria {

	public float getNeighborDistance();
	
	public int getMinNumberOfAtoms();
	
	public boolean includeAtom(Atom atom);
	
	public boolean acceptAsFirstAtomInGrain(Atom atom, List<AtomToGrainObject> neighbors);
	
	public boolean includeAtom(AtomToGrainObject atom, List<AtomToGrainObject> neighbors);
}
