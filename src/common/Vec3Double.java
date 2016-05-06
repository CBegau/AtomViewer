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

public class Vec3Double{
	public double x,y,z;
	
	public Vec3Double(){}
	
	public Vec3Double(double x, double y, double z){
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public final Vec3Double clone(){
		return new Vec3Double(x,y,z);
	}
	
	public final Vec3Double multiply(double c){
		this.x *= c;
		this.y *= c;
		this.z *= c;
		return this;
	}
	
	public final Vec3Double multiplyClone(double c){
		return this.clone().multiply(c);
	}
	
	public final Vec3Double multiply(Vec3Double v){
		this.x *= v.x;
		this.y *= v.y;
		this.z *= v.z;
		return this;
	}
	
	public final Vec3Double multiplyClone(Vec3Double v){
		return this.clone().multiply(v);
	}
	
	public final Vec3Double divide(double v){
		this.x /= v;
		this.y /= v;
		this.z /= v;
		return this;
	}
	
	public final Vec3Double divideClone(double v){
		return this.clone().divide(v);
	}
	
	public final Vec3Double normalize(){
		double l = 1f/getLength();
		this.x *= l;
		this.y *= l;
		this.z *= l;
		return this;
	}
	
	public final Vec3Double normalizeClone(){
		return this.clone().normalize();
	}
	
	public final double getLength(){
		return (double)Math.sqrt(x*x + y*y + z*z);
	}
	
	public final double getLengthSqr(){
		return x*x + y*y + z*z;
	}
	
	public final double getSqrDistTo(Vec3Double v){
		return (x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) + (z - v.z) * (z - v.z);
	}
	
	public final double getDistTo(Vec3Double v){
		return (double) Math.sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) + (z - v.z) * (z - v.z));
	}
	
	public final double dot(Vec3Double v){
		return this.x * v.x + this.y * v.y + this.z * v.z;
	}
	
	public final Vec3Double add(Vec3Double v){
		this.x += v.x;
		this.y += v.y;
		this.z += v.z;
		return this;
	}
	
	public final Vec3Double add(double v){
		this.x += v;
		this.y += v;
		this.z += v;
		return this;
	}
	
	public final Vec3Double addClone(Vec3Double c){
		return this.clone().add(c);
	}
	
	public final Vec3Double addClone(double c){
		return this.clone().add(c);
	}
	
	public final Vec3Double sub(Vec3Double src){
		this.x -= src.x;
		this.y -= src.y;
		this.z -= src.z;
		return this;
	}
	
	public final Vec3Double sub(double v){
		this.x -= v;
		this.y -= v;
		this.z -= v;
		return this;
	}
	
	public final Vec3Double subClone(Vec3Double v){
		return this.clone().sub(v);
	}
	
	public final Vec3Double subClone(double v){
		return this.clone().sub(v);
	}
	
	public final double[] asArray(){
		return new double[]{x,y,z};
	}
	
	public final Vec3Double cross(Vec3Double v){
		Vec3Double u = new Vec3Double();
		u.x = this.y * v.z - this.z * v.y;
		u.y = this.z * v.x - this.x * v.z;
		u.z = this.x * v.y - this.y * v.x;
		return u;
	}
	
	public final double getAngle(Vec3Double v){
		double a = this.dot(v)/(this.getLength() * v.getLength());
		if (a>1) a = 1;		//Rounding errors produce in some cases something like 1.00000001, acos will return NaN otherwise
		if (a<-1) a =- 1;
		return Math.acos(a);
	}
	
	/**
	 * Return the value of the smallest component in the vector
	 * @return
	 */
	public final double minComponent(){
		return Math.min(x, Math.min(y, z));
	}
	
	public final Vec3 toVec3(){
		return new Vec3((float)x, (float)y, (float)z);
	}
	
	/**
	 * Return the value of the smallest component in the vector
	 * @return
	 */
	public final double maxComponent(){
		return Math.max(x, Math.max(y, z));
	}
	
	public final Vec3Double createNormal(Vec3Double v){
		Vec3Double n = this.cross(v);
		return n.normalize();
	}
	
	public final Vec3Double setTo(Vec3Double v){
		this.x = v.x;
		this.y = v.y;
		this.z = v.z;
		return this;
	}
	
	public final static Vec3Double makeNormal(Vec3Double p1, Vec3Double p2, Vec3Double p3){
		Vec3Double u = p2.subClone(p1);
		Vec3Double v = p3.subClone(p1);
		return u.cross(v).normalize();
	}
	
    @Override
    public String toString() {
        return String.format("(%f, %f, %f)", x, y, z);
    }
}
