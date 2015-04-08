package model.polygrain.grainDetection;

import common.Vec3;

import model.Atom;

public class AtomToGrainObject extends Vec3 {

	private GrainObject grain = null;
	private Atom atom;
	
	public AtomToGrainObject(Atom atom){
		this.atom = atom;
		this.setTo(atom);
	}
	
	public Atom getAtom() {
		return atom;
	}
	
	/**
	 * Sets the grain of this object to the given object
	 * @param grain the new grain
	 * @return the previous grainObject, if not null 
	 */
	public GrainObject setGrainObject(GrainObject grain){
		GrainObject oldObject = this.grain;
		this.grain = grain;
		return oldObject;
	}
	
	public synchronized boolean isGrainObjectNull(){
		return grain == null;
	}
	
	public GrainObject getGrainObject(){
		return grain;
	}
}
