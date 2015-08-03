package model.polygrain.grainDetection;

import java.util.List;

import crystalStructures.CrystalStructure;
import model.Atom;

public class DefaultGrainDetectionCriteria implements GrainDetectionCriteria {

	private CrystalStructure cs;
	private int surfaceType;
	private int defaultType;
	private int defaultNumAtoms;
	private int tolerance;
	
	public DefaultGrainDetectionCriteria(CrystalStructure cs){
		this.cs = cs;
		this.surfaceType = cs.getSurfaceType();
		this.defaultType = cs.getDefaultType();
		this.defaultNumAtoms = cs.getNumberOfNearestNeighbors();
		this.tolerance = (int) Math.round(this.defaultNumAtoms/4.);
	}
	
	@Override
	public float getNeighborDistance() {
		return cs.getNearestNeighborSearchRadius();
	}

	@Override
	public int getMinNumberOfAtoms() {
		return 20;
	}

	@Override
	public boolean includeAtom(Atom atom) {
		return atom.getType() != surfaceType;
	}

	@Override
	public boolean includeAtom(AtomToGrainObject atom, List<AtomToGrainObject> neighbors) {
		int count = 0;
		for (int j=0; j<neighbors.size(); j++){
			if (neighbors.get(j).getAtom().getType() == defaultType) count++;
		}
		
		return count>=this.defaultNumAtoms-tolerance;
	}
	
	@Override
	public boolean acceptAsFirstAtomInGrain(Atom atom, List<AtomToGrainObject> neighbors) {
		return neighbors.size()>this.defaultNumAtoms-tolerance;
	}

}
