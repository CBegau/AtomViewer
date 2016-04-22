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

package processingModules.otherModules.dislocationDensity;

import com.jogamp.opengl.GL3;

import common.Vec3;

public class HalfShellSector implements VolumeElement {

	private final Vec3 center;
	private final Vec3 center2;
	private final float innerradius, outerradius;
	private final Vec3 refVec;
	
	/**
	 * A volume element that can computed dislocation densities in the "plastic zones" defined as a semispherical
	 * volume at the contact radius of spherical indenters.
	 * @param firstContactPos The point at which the indenter had first contact with the sample at the free surface 
	 * @param indenterPos The indenter position
	 * @param innerRadius The indenter radius
	 * @param outerRadius The radius of the (half-)sphere defining the plastic zone
	 * @param dirVec direction in which the indenter moves (e.g. in negative z-direction -> {0, 0, -1})
	 */
	public HalfShellSector(Vec3 firstContactPos, Vec3 indenterPos, float innerRadius, float outerRadius, Vec3 dirVec) {
		this.center = firstContactPos.clone();
		this.center2 = indenterPos.clone();
		this.innerradius = innerRadius;
		this.outerradius = outerRadius;
		this.refVec = dirVec.normalizeClone();
	}
	
	/**
	 * More convenient constructor to define the plastic zone during indentation
	 * @param surfacePos coordinate of the surface plane
	 * @param indenterPos the center of a spherical indenter
	 * @param indenterRadius its radius
	 * @param plasticZoneFactor the factor for a plastic zone 
	 * @param dirVec a vector defining the direction in which the indenter moves into the surface. Two of the three values
	 * must be zero, the last one +-1.
	 */
	public HalfShellSector(float surfacePos, Vec3 indenterPos, float indenterRadius, float plasticZoneFactor, Vec3 dirVec) {
		float h = indenterPos.dot(dirVec)+indenterRadius+surfacePos;
		if (h<0) h = 0;
		if (h>indenterRadius) h = indenterRadius;
		
		float pz = plasticZoneFactor*(float)(Math.sqrt(indenterRadius*indenterRadius - 
				(indenterRadius-h)*(indenterRadius-h) ));
		
		Vec3 absDir = new Vec3();
		absDir.x = Math.abs(dirVec.x);
		absDir.y = Math.abs(dirVec.y);
		absDir.z = Math.abs(dirVec.z);
		
		Vec3 tmp = absDir.multiply(surfacePos);
		Vec3 tmp2 = indenterPos.multiplyClone(absDir); 
		this.center = tmp.add(indenterPos.subClone(tmp2));
			
		this.center2 = indenterPos;
		this.innerradius = indenterRadius;
		this.outerradius = pz;
		this.refVec = dirVec.normalizeClone();
	}
	
	
	@Override
	public boolean isInVolume(Vec3 c) {
		Vec3 dir = c.subClone(center);
		Vec3 dir2 = c.subClone(center2);
		
		float l = dir.getLengthSqr();
		float l2 = dir2.getLengthSqr();
		if (l>outerradius*outerradius) return false;
		if (l2<innerradius*innerradius) return false;
		
		float pos = dir.dot(refVec);
		if (pos<0f) return false;
		return true;
	}
	@Override
	public float getVolume() {
		double volume = Math.PI * 2./3.*outerradius*outerradius*outerradius; //Total outer halfsphere
		
		double h = innerradius - (center2.z-center.z);
		volume -= (h*h*Math.PI/3.) * (3*innerradius-h); //minus the inner spherical cap
		
		return (float)Math.abs(volume);
	}
	
	@Override
	public void render(GL3 gl, boolean picking, float[] color4) {
		throw new RuntimeException("rendering half shell sectors is currently not supported");
	}
}
