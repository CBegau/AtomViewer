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

package common;

public class Vec3{
	public float x,y,z;
	
	public Vec3(){}
	
	public Vec3(float x, float y, float z){
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	@Override
	public final Vec3 clone(){
		return new Vec3(x,y,z);
	}
	
	public final Vec3 multiply(float c){
		this.x *= c;
		this.y *= c;
		this.z *= c;
		return this;
	}
	
	public final Vec3 multiplyClone(float c){
		return this.clone().multiply(c);
	}
	
	public final Vec3 multiply(Vec3 v){
		this.x *= v.x;
		this.y *= v.y;
		this.z *= v.z;
		return this;
	}
	
	public final Vec3 multiplyClone(Vec3 v){
		return this.clone().multiply(v);
	}
	
	public final Vec3 divide(float v){
		this.x /= v;
		this.y /= v;
		this.z /= v;
		return this;
	}
	
	public final Vec3 divideClone(float v){
		return this.clone().divide(v);
	}
	
	public final Vec3 normalize(){
		float l = 1f/getLength();
		this.x *= l;
		this.y *= l;
		this.z *= l;
		return this;
	}
	
	public final Vec3 normalizeClone(){
		return this.clone().normalize();
	}
	
	public final float getLength(){
		return (float)Math.sqrt(x*x + y*y + z*z);
	}
	
	public final float getLengthSqr(){
		return x*x + y*y + z*z;
	}
	
	public final float getSqrDistTo(Vec3 v){
		return (x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) + (z - v.z) * (z - v.z);
	}
	
	public final float getDistTo(Vec3 v){
		return (float) Math.sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) + (z - v.z) * (z - v.z));
	}
	
	public final float dot(Vec3 v){
		return this.x * v.x + this.y * v.y + this.z * v.z;
	}
	
	public final Vec3 add(float v){
		this.x += v;
		this.y += v;
		this.z += v;
		return this;
	}
	
	public final Vec3 add(Vec3 v){
		this.x += v.x;
		this.y += v.y;
		this.z += v.z;
		return this;
	}
	
	public final Vec3 addClone(Vec3 v){
		return this.clone().add(v);
	}
	
	public final Vec3 addClone(float v){
		return this.clone().add(v);
	}
	
	public final Vec3 sub(Vec3 v){
		this.x -= v.x;
		this.y -= v.y;
		this.z -= v.z;
		return this;
	}
	
	public final Vec3 sub(float v){
		this.x -= v;
		this.y -= v;
		this.z -= v;
		return this;
	}
	
	public final Vec3 subClone(Vec3 v){
		return this.clone().sub(v);
	}
	
	public final Vec3 subClone(float v){
		return this.clone().sub(v);
	}
	
	public final float[] asArray(){
		return new float[]{x,y,z};
	}
	
	public final Vec3 cross(Vec3 v){
		Vec3 u = new Vec3();
		u.x = this.y * v.z - this.z * v.y;
		u.y = this.z * v.x - this.x * v.z;
		u.z = this.x * v.y - this.y * v.x;
		return u;
	}
	
	public final double getAngle(Vec3 v){
		float a = this.dot(v)/(this.getLength() * v.getLength());
		if (a>1) a = 1;		//Rounding errors produce in some cases something like 1.00000001, acos will return NaN otherwise
		if (a<-1) a =- 1;
		return Math.acos(a);
	}
	
	public final Vec3 createNormal(Vec3 v){
		Vec3 n = this.cross(v);
		return n.normalize();
	}
	
	public final Vec3 setTo(Vec3 v){
		this.x = v.x;
		this.y = v.y;
		this.z = v.z;
		return this;
	}
	
	/**
	 * Return the value of the smallest component in the vector
	 * @return
	 */
	public final float minComponent(){
		return Math.min(x, Math.min(y, z));
	}
	
	/**
	 * Return the value of the smallest component in the vector
	 * @return
	 */
	public final float maxComponent(){
		return Math.max(x, Math.max(y, z));
	}
	
	public final Vec3 setTo(float x, float y, float z){
		this.x = x;
		this.y = y;
		this.z = z;
		return this;
	}
	
	public final static Vec3 makeNormal(Vec3 p1, Vec3 p2, Vec3 p3){
		Vec3 u = p2.subClone(p1);
		Vec3 v = p3.subClone(p1);
		return u.cross(v).normalize();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj.getClass() != this.getClass()) return false;
		Vec3 v = (Vec3)obj;
		return (v.x == x && v.y == y && v.z == z);
	}
	
	@Override
	public String toString() {
		return String.format("(%f, %f, %f)", x,y,z);
	}
}
