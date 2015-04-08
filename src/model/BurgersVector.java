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

package model;

import common.CommonUtils;
import common.Vec3;
import crystalStructures.CrystalStructure;

public class BurgersVector {	
	
	/**
	 * Different types of Burgers vectors, each defining their own color for rendering
	 */
	public enum BurgersVectorType {
		PARTIAL(0f, 0.5f, 1f), PERFECT(0f, 1f, 0f), STAIR_ROD(1f, 0f, 0f), 
		FRANK_PARTIAL(1f, 0f, 1f), SUPER(0f, 0f, 1f),
		OTHER(0.5f, 0.5f, 0.5f), UNDEFINED(0.5f, 0.5f, 0.5f), ZERO(0.2f, 0.2f, 0.2f), //<-Some artificial types
		DONT_SHOW(0.0f,0.0f,0.0f); // <-Ignored during visualization
		
		private float[] color;
		private BurgersVectorType(float r, float g, float b){
			color = new float[]{r,g,b};
		}
		
		public float[] getColor(){
			return color;
		}
	}
	
	private CrystalStructure cs;
	private BurgersVectorType type;
	private int[] direction;	
	private int fraction;	
	
	/**
	 * Undefined BurgersVector
	 */
	public BurgersVector(CrystalStructure cs) {
		this(0, 0, 0, 0, cs, BurgersVectorType.UNDEFINED);
	}
	
	private BurgersVector(BurgersVector bv){
		this.fraction = bv.fraction;
		this.direction = bv.direction.clone();
		this.type = bv.type;
	}

	public BurgersVector(int fraction, int d0, int d1, int d2, CrystalStructure cs) {
		this.cs = cs;
		this.fraction = fraction;
		this.direction = new int[] { d0, d1, d2 };
		this.type = cs.identifyBurgersVectorType(this);
	}
	
	public BurgersVector(int fraction, int d0, int d1, int d2, CrystalStructure cs, BurgersVectorType type) {
		this.cs = cs;
		this.fraction = fraction;
		this.direction = new int[] { d0, d1, d2 };
		this.type = type;
	}
	
	public void add(BurgersVector bv) {
		if (bv.type == BurgersVectorType.UNDEFINED) return;
		if (this.type == BurgersVectorType.UNDEFINED) {
			this.fraction = bv.fraction;
			this.direction[0] = bv.direction[0];
			this.direction[1] = bv.direction[1];
			this.direction[2] = bv.direction[2];
		} else {
			int scm = CommonUtils.scm(this.fraction, bv.fraction);
			direction[0] = this.direction[0] * (scm / this.fraction) + bv.direction[0] * (scm / bv.fraction);
			direction[1] = this.direction[1] * (scm / this.fraction) + bv.direction[1] * (scm / bv.fraction);
			direction[2] = this.direction[2] * (scm / this.fraction) + bv.direction[2] * (scm / bv.fraction);
			int gcd = CommonUtils.gcd(direction);
			if (gcd == 0){
				//undefined
				this.fraction = 1;
				this.direction[0] = 0; this.direction[1] = 0; this.direction[2] = 0;
			} else {
				if (gcd>scm) this.fraction = 1;
				else {
					this.fraction = scm/gcd;
					direction[0] /= gcd;
					direction[1] /= gcd;
					direction[2] /= gcd;
				}
			}
		}
		this.type = cs.identifyBurgersVectorType(this);
	}
	
	public boolean isFullyDefined(){
		return (fraction!=0);
	}
	
	public void sub(BurgersVector bv) {
		BurgersVector clone = bv.clone();
		clone.invert();
		this.add(clone);
	}

	public BurgersVector clone() {
		return new BurgersVector(this);
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof BurgersVector)) return false;
		BurgersVector bv = (BurgersVector) o;
		if (this.fraction != bv.fraction) return false;
		if (this.direction[0] != bv.direction[0]) return false;
		if (this.direction[1] != bv.direction[1]) return false;
		if (this.direction[2] != bv.direction[2]) return false;
		return true;
	}
	
	public int[] getDirection(){
		return direction;
	}
	
	public int getFraction(){
		return fraction;
	}
	
	public Vec3 getInXYZ(CrystalRotationTools crt){
		return crt.getInXYZ(getInCrystalCoordinates());
	}
	
	public Vec3 getInCrystalCoordinates(){
		if (fraction == 0) return new Vec3();
		Vec3 v = new Vec3((float)direction[0]/fraction,(float)direction[1]/fraction, (float)direction[2]/fraction);
		return v;
	}
	
	public float getLength() {
		Vec3 v = new Vec3( (float)direction[0]/fraction, (float)direction[1]/fraction, (float)direction[2]/fraction );
		return v.getLength();
	}

	public BurgersVectorType getType() {
		return type;
	}
	
	public CrystalStructure getCrystalStructure() {
		return cs;
	}

	public void invert(){
		direction[0] *= -1;
		direction[1] *= -1;
		direction[2] *= -1;
	}
	
	public String toString() {
		if (type == BurgersVectorType.UNDEFINED) return "Unknown";
		return ("("+type.toString()+") 1/" + fraction + " [" + direction[0] + " " + direction[1] + " " + direction[2] + "]");
	}
}
