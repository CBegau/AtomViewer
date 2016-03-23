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

import java.util.ArrayList;

import Jama.*;
import common.CommonUtils;
import common.Vec3;
import crystalStructures.CrystalStructure;

public class CrystalRotationTools {
	
	private final float[][] xyzToCrystal = new float[3][3];
	private final float[][] crystalToXyz = new float[3][3];
	private final float[][] rotationMatrix = new float[3][3];
	private final Vec3[] crystalOrientation;
	private final CrystalStructure cs;
	
	public CrystalRotationTools(CrystalStructure cs, Vec3[] crystalOrientation) {
		this.cs = cs;
		float lc = cs.getLatticeConstant();
		
		this.crystalOrientation = new Vec3[3];
		this.crystalOrientation[0] = crystalOrientation[0].clone();
		this.crystalOrientation[1] = crystalOrientation[1].clone();
		this.crystalOrientation[2] = crystalOrientation[2].clone();
		
		xyzToCrystal[0][0] = crystalOrientation[0].x/crystalOrientation[0].getLength() / lc;
		xyzToCrystal[0][1] = crystalOrientation[0].y/crystalOrientation[0].getLength() / lc;
		xyzToCrystal[0][2] = crystalOrientation[0].z/crystalOrientation[0].getLength() / lc;
		                   
		xyzToCrystal[1][0] = crystalOrientation[1].x/crystalOrientation[1].getLength() / lc;
		xyzToCrystal[1][1] = crystalOrientation[1].y/crystalOrientation[1].getLength() / lc;
		xyzToCrystal[1][2] = crystalOrientation[1].z/crystalOrientation[1].getLength() / lc;
		                   
		xyzToCrystal[2][0] = crystalOrientation[2].x/crystalOrientation[2].getLength() / lc;
		xyzToCrystal[2][1] = crystalOrientation[2].y/crystalOrientation[2].getLength() / lc;
		xyzToCrystal[2][2] = crystalOrientation[2].z/crystalOrientation[2].getLength() / lc;
		
		crystalToXyz[0][0] = xyzToCrystal[0][0] * lc * lc;
		crystalToXyz[0][1] = xyzToCrystal[1][0] * lc * lc;
		crystalToXyz[0][2] = xyzToCrystal[2][0] * lc * lc;
		                   
		crystalToXyz[1][0] = xyzToCrystal[0][1] * lc * lc;
		crystalToXyz[1][1] = xyzToCrystal[1][1] * lc * lc;
		crystalToXyz[1][2] = xyzToCrystal[2][1] * lc * lc;
		                   
		crystalToXyz[2][0] = xyzToCrystal[0][2] * lc * lc;
		crystalToXyz[2][1] = xyzToCrystal[1][2] * lc * lc;
		crystalToXyz[2][2] = xyzToCrystal[2][2] * lc * lc;
		
		rotationMatrix[0][0] = xyzToCrystal[0][0] * lc;
		rotationMatrix[0][1] = xyzToCrystal[1][0] * lc;
		rotationMatrix[0][2] = xyzToCrystal[2][0] * lc;
		
		rotationMatrix[1][0] = xyzToCrystal[0][1] * lc;
		rotationMatrix[1][1] = xyzToCrystal[1][1] * lc;
		rotationMatrix[1][2] = xyzToCrystal[2][1] * lc;
		
		rotationMatrix[2][0] = xyzToCrystal[0][2] * lc;
		rotationMatrix[2][1] = xyzToCrystal[1][2] * lc;
		rotationMatrix[2][2] = xyzToCrystal[2][2] * lc;
	}
	
	/**
	 * The rotation matrix in order to match the default atom positions of a crystalStructure
	 * into the positions of the current orientation
	 * @return
	 */
	public float[][] getDefaultRotationMatrix(){
		return rotationMatrix;
	}
	
	/**
	 * The orientation of the crystal lattice in Cartesian xyz-directions
	 * @return
	 */
	public Vec3[] getCrystalOrientation() {
		return crystalOrientation;
	}
	
	/**
	 * Transform a vector in XYZ-coordinates in crystal coordinates
	 * @param v
	 * @return
	 */
	public Vec3 getInCrystalCoordinates(Vec3 v){
		Vec3 f = new Vec3();
		f.x = xyzToCrystal[0][0]*v.x+xyzToCrystal[1][0]*v.y+xyzToCrystal[2][0]*v.z;
		f.y = xyzToCrystal[0][1]*v.x+xyzToCrystal[1][1]*v.y+xyzToCrystal[2][1]*v.z;
		f.z = xyzToCrystal[0][2]*v.x+xyzToCrystal[1][2]*v.y+xyzToCrystal[2][2]*v.z;
		return f;
	}
	
	/**
	 * Transform a vector in crystal coordinates in XYZ-coordinates
	 * @param v
	 * @return
	 */
	public Vec3 getInXYZ(Vec3 v){
		Vec3 f = new Vec3();
		f.x = crystalToXyz[0][0]*v.x+crystalToXyz[1][0]*v.y+crystalToXyz[2][0]*v.z;
		f.y = crystalToXyz[0][1]*v.x+crystalToXyz[1][1]*v.y+crystalToXyz[2][1]*v.z;
		f.z = crystalToXyz[0][2]*v.x+crystalToXyz[1][2]*v.y+crystalToXyz[2][2]*v.z;
		return f;
	} 
	
	public Vec3[] getThompsonTetraeder() {
		final Vec3[] thompsonTetraeder = new Vec3[4];
		thompsonTetraeder[3] = new Vec3(0f,0f,0f);
		thompsonTetraeder[2] = getInXYZ(new Vec3(1f,0f,1f)).normalize();
		thompsonTetraeder[1] = getInXYZ(new Vec3(0f,1f,1f)).normalize();
		thompsonTetraeder[0] = getInXYZ(new Vec3(1f,1f,0f)).normalize();
		return thompsonTetraeder;
	}
	
	/**
	 * Return a least square rotation matrix for a most similar mapping to the default crystal orientation
	 * and the given atom and its neighbors
	 * @param a
	 * @param neighVec
	 * @param cs
	 * @return
	 */
	public static Vec3[] getLocalRotationMatrix(Atom a, Vec3[] neighVec, CrystalStructure cs){
		Matrix t = getAffineTransformationMatrix(a, neighVec, cs);
		if (t == null) return null;
		SingularValueDecomposition svd = new SingularValueDecomposition(t);
		Matrix rot = svd.getU().times(svd.getV().transpose());
		
		Vec3[] f = new Vec3[3];
		f[0] = new Vec3(); f[1] = new Vec3(); f[2] = new Vec3();   
		f[0].x = (float)rot.get(0, 0); f[0].y = (float)rot.get(0, 1); f[0].z = (float)rot.get(0, 2);
		f[1].x = (float)rot.get(1, 0); f[1].y = (float)rot.get(1, 1); f[1].z = (float)rot.get(1, 2);
		f[2].x = (float)rot.get(2, 0); f[2].y = (float)rot.get(2, 1); f[2].z = (float)rot.get(2, 2);
		return f;
	}
	
	/**
	 * Pick a basis of three distinct vectors from all nearest neighbor vectors
	 * Used to identify the crystal orientation
	 * @param unsortedBonds
	 * @return
	 */
	private static Vec3[] sortBonds(Vec3[] unsortedBonds){
		Vec3[] sort = new Vec3[3];
		
		//Put all neighbors in a list 
		ArrayList<Vec3> ub = new ArrayList<Vec3>();
		for (int i=0; i<unsortedBonds.length; i++) ub.add(unsortedBonds[i]);
		
		//Search the nearest one to the center
		float closestDist = Float.POSITIVE_INFINITY;
		int num = 0;
		for (int i=1; i<ub.size(); i++){
			float dist = ub.get(i).getLengthSqr();
			if (dist < closestDist) {
				closestDist = dist;
				num = i;
			}
			
		}
		sort[0] = ub.remove(num);
		
		//Search the closest one to sort[0]
		closestDist = Float.POSITIVE_INFINITY;
		num = 0;
		for (int i=0; i<ub.size();i++){
			float dist = ub.get(i).getSqrDistTo(sort[0]);
			if (dist < closestDist) {
				closestDist = dist;
				num = i;
			}
		}
		sort[1] = ub.remove(num);
		
		//Search the closest one to sort[0] and sort[1]
		closestDist = Float.POSITIVE_INFINITY;
		num = 0;
		for (int i=0; i<ub.size();i++){
			float dist = ub.get(i).getSqrDistTo(sort[0]) + ub.get(i).getSqrDistTo(sort[1]);
			if (dist < closestDist) {
				closestDist = dist;
				num = i;
			}
		}
		sort[2] = ub.get(num);
		//Finally check if the orientation of the system is positive, otherwise swap the order of the points
		if (sort[0].cross( sort[1]).dot(sort[2]) < 0f){
			Vec3 tmp = sort[1];
			sort[1] = sort[2];
			sort[2] = tmp;
		}
		
		return sort;
	}
	
	private static Matrix getAffineTransformationMatrix(Atom a, Vec3[] neighVec, CrystalStructure cs){
		if (neighVec == null || a.getType() != cs.getDefaultType() || neighVec.length < 3)
			return null;
		
		Vec3[] neigh = new Vec3[neighVec.length];
		
		for (int i=0; i<neighVec.length; i++){
			neigh[i] = neighVec[i]; 
		}
		
		Matrix trans = null;
		neigh = CrystalRotationTools.sortBonds(neigh);
		Vec3[] def = CrystalRotationTools.sortBonds(cs.getPerfectNearestNeighborsUnrotated());
		
		double[][] defM = new double[def.length][3];
		for (int i=0; i<def.length; i++){
			Vec3 v = def[i].normalizeClone();
			defM[i][0] = v.x;
			defM[i][1] = v.y;
			defM[i][2] = v.z;
		}
		
		double[][] neighM = new double[def.length][3];
		for (int i=0; i<neigh.length; i++){
			Vec3 v = neigh[i].normalizeClone();
			neighM[i][0] = v.x;
			neighM[i][1] = v.y;
			neighM[i][2] = v.z;
		}
		
		trans = new Matrix(neighM).solve(new Matrix(defM));
		
		return trans;
	}
	
	/**
	 * Return the most likely true Burgers vector from the resultant burgers vector
	 * that is possible in the current crystal system
	 * @param rbv
	 * @return
	 */
	public BurgersVector rbvToBurgersVector(RBV rbv){
		return rbvToBurgersVector(rbv.bv);
	}

	/**
	 * Return the most likely true Burgers vector from the resultant burgers vector
	 * that is possible in the current crystal system
	 * @param rbv
	 * @return
	 */
	public BurgersVector rbvToBurgersVector(Vec3 rbv){
		if (rbv.x == 0f && rbv.y == 0f && rbv.z == 0f) return new BurgersVector(cs);
		Vec3 rbv_c = this.getInCrystalCoordinates(rbv);
		float l = rbv_c.getLength();
		
		int[] bv = new int[3];
		
		float max = Math.max(Math.max(Math.abs(rbv_c.x), Math.abs(rbv_c.y)), Math.abs(rbv_c.z));
		
		rbv_c.multiply(1f/max);
		
		for (int i=0; i<3; i++){
			float r;
			if (i==0) r = rbv_c.x;
			else if (i==1) r = rbv_c.y;
			else r = rbv_c.z;
			if (Math.abs(r)>0.75f) bv[i] = 6*(int)Math.signum(r);
			else if (Math.abs(r)>0.412f) bv[i] = 3*(int)Math.signum(r);
			else if (Math.abs(r)>0.25f) bv[i] = 2*(int)Math.signum(r);
			else if (Math.abs(r)>0.0833f) bv[i] = (int)Math.signum(r);
			else bv[i] = 0;
		}
		
		int gcd = CommonUtils.greatestCommonDivider(bv);
		
		bv[0] /= gcd;
		bv[1] /= gcd;
		bv[2] /= gcd;
		int f = (int)Math.round(Math.sqrt(bv[0]*bv[0] + bv[1]*bv[1] + bv[2]*bv[2]) / l);
		if (f==0) f=1;
		
		return new BurgersVector(f, bv[0], bv[1], bv[2], cs);
	}
}
