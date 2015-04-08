// Part of AtomVieloat[]wer: AtomViewer is a tool to display and analyse
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

package gui;

import java.util.ArrayList;
import java.util.List;

import model.Atom;
import model.Filter;
import common.Vec3;

public class RenderRange implements Filter<Atom> {
	
	private float[] currentLimit = new float[6];
	private float[] globalLimit = new float[6];
	private boolean[] defaultClippingPlanesActive = new boolean[6];
	private boolean noLimiting = true;
	private ArrayList<float[]> customClippingPlanes;
	
	//TODO implement non-ortho
	public RenderRange(Vec3 upperLimit) {
		this.globalLimit[0] = 0f;
		this.globalLimit[1] = 0f;
		this.globalLimit[2] = 0f;
		this.globalLimit[3] = upperLimit.x;
		this.globalLimit[4] = upperLimit.y;
		this.globalLimit[5] = upperLimit.z;
		reset();
	}
	
	/**
	 * 0-2: lower limits xyz, 3-5: upper limits xyz
	 * @param i
	 * @return
	 */
	public float getCurrentLimit(int i) {
		return currentLimit[i];
	}

	/**
	 * 0-2: lower limits xyz, 3-5: upper limits xyz
	 * @param i
	 * @return
	 */
	public void setCurrentLimit(int i, float limit) {
		this.currentLimit[i] = limit;
		defaultClippingPlanesActive[i] = true;
		this.noLimiting = false;
	}

	
	/**
	 * 0-2: lower limits xyz, 3-5: upper limits xyz
	 * @param i
	 * @return
	 */
	public void setGlobalLimit(int i, float limit) {
		this.globalLimit[i] = limit;
	}
	
	
	/**
	 * 0-2: lower limits xyz, 3-5: upper limits xyz
	 * @param i
	 * @return
	 */
	public float getGlobalLimit(int i) {
		return globalLimit[i];
	}
	
	/**
	 * 0-2: lower limits xyz, 3-5: upper limits xyz
	 * @param i
	 * @return
	 */
	public void setLimitActive(int i, boolean active) {
		defaultClippingPlanesActive[i] = active;
		
		if (!active){	//Test if any clipping plane is currently active  
			noLimiting = true;
			if (this.customClippingPlanes!=null && this.customClippingPlanes.size()>0) noLimiting = false;
			else {
				for (int j=0; j<6;j++)
					if (this.defaultClippingPlanesActive[j]) noLimiting = false;
			}
		}
	}

	/**
	 * 0-2: lower limits xyz, 3-5: upper limits xyz
	 * @param i
	 * @return
	 */
	public boolean getLimitActive(int i) {
		return defaultClippingPlanesActive[i];
	}
	
	public List<float[]> getCustomClippingPlanes(){
		return customClippingPlanes;
	}
	
	public void setCustomClippingPlanes(List<float[]> customClippingPlanes) {
		if (this.customClippingPlanes == null)
			this.customClippingPlanes = new ArrayList<float[]>();
		this.customClippingPlanes.clear();
		
		for (float[] p : customClippingPlanes){
			Vec3 n = new Vec3(p[3],p[4],p[5]);
			if (n.dot(n) > 0f){
				this.customClippingPlanes.add(
						new float[]{p[0], p[1], p[2], p[3],p[4],p[5], n.x*p[0]+n.y*p[1]+n.z*p[2]});
			}
		}
		
		//Test if any clipping plane is currently active
		if (this.customClippingPlanes.size()>0) noLimiting = false;
		else {
			noLimiting = true;
			for (int i=0; i<6;i++)
				if (this.defaultClippingPlanesActive[i]) noLimiting = false;
		}
	}
	
	public boolean isNoLimiting() {
		return noLimiting;
	}
	
	public void reset(){
		for (int i=0; i<6; i++){
			defaultClippingPlanesActive[i] = false;
			currentLimit[i] = globalLimit[i];
		}
		this.noLimiting = true;
		this.customClippingPlanes = null;
	}
	
	public boolean isInInterval(Vec3 coord){
		if (noLimiting) return true;
		if (defaultClippingPlanesActive[0] && coord.x<currentLimit[0]) return false;
		if (defaultClippingPlanesActive[1] && coord.y<currentLimit[1]) return false;
		if (defaultClippingPlanesActive[2] && coord.z<currentLimit[2]) return false;
		if (defaultClippingPlanesActive[3] && coord.x>currentLimit[3]) return false;
		if (defaultClippingPlanesActive[4] && coord.y>currentLimit[4]) return false;
		if (defaultClippingPlanesActive[5] && coord.z>currentLimit[5]) return false;
		if (customClippingPlanes != null)
			for (float[] p : customClippingPlanes)
				if (p[3]*coord.x + p[4]*coord.y + p[5]*coord.z > p[6]) return false;
		
		return true;
	}
	
	@Override
	public boolean accept(Atom a) {
		return isInInterval(a);
	}
}
