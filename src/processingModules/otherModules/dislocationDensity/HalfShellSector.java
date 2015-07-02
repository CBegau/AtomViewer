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

import javax.media.opengl.GL3;

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
		
//		if (getVolume()<=0) return;
//		
//		int scaley = 24;
//		int scalex = 24;
//		float[][] v = new float[scalex * scaley][3];
//		float[][] n = new float[scalex * scaley][3];
//		
//		if (!picking) OutdatedShader.DEFAULT_PPL_SHADER.enable(gl);;
//		
//		for (int i = 0; i < scalex; ++i) {
//			for (int j = 0; j < scaley; ++j) {
//				n[i * scaley + j][0] = (float) (Math.cos(j * 2 * Math.PI / scaley) * Math.cos(i * Math.PI / (2 * scalex)));
//				n[i * scaley + j][1] = (float) (Math.sin(j * 2 * Math.PI / scaley) * Math.cos(i * Math.PI / (2 * scalex)));
//				n[i * scaley + j][2] = -(float) (Math.sin(i * Math.PI / (2 * scalex)));
//			}
//		}
//			
//		//rotate in different orientations
//		if (refVec[2]!=-1){
//			double angle = VectorOps.angle(refVec, new float[]{0f,0f,-1f}); 
//			float[] rotAxis = VectorOps.normalize(VectorOps.crossProduct(refVec, new float[]{0f,0f,-1f}));
//			
//			float[][] rot = new float[3][3];
//			float s = (float)Math.sin(angle);
//			float c = (float)Math.cos(angle);
//			float t = 1f- c;
//			float x = rotAxis[0]; float y = rotAxis[1]; float z = rotAxis[2];
//			
//			rot[0][0] = t*x*x + c;   rot[0][1] = t*x*y - z*s;  rot[0][2] = t*x*z + y*s;
//			rot[1][0] =	t*x*y + z*s; rot[1][1] = t*y*y + c;    rot[1][2] = t*y*z - x*s;
//			rot[2][0] =	t*x*z - y*s; rot[2][1] = t*y*z + x*s;  rot[2][2] = t*z*z + c;
//			
//			for (int i = 0; i<n.length; i++){
//				float[] n1 = new float[3];
//				n1[0] = rot[0][0] * n[i][0] + rot[1][0] * n[i][1] + rot[2][0] * n[i][2];
//				n1[1] = rot[0][1] * n[i][0] + rot[1][1] * n[i][1] + rot[2][1] * n[i][2];
//				n1[2] = rot[0][2] * n[i][0] + rot[1][2] * n[i][1] + rot[2][2] * n[i][2];                                     
//			
//				n[i] = n1;
//			}
//		}
//
//		for (int i = 0; i<n.length; i++){
//			float[] v1 = new float[3];
//			v1[0] = n[i][0]*outerradius + center[0];
//			v1[1] = n[i][1]*outerradius + center[1];
//			v1[2] = n[i][2]*outerradius + center[2];                                     
//		
//			v[i] = v1;
//		}
//		
//		gl.glBegin(GL2.GL_QUADS);
//		for (int i = 0; i < scalex - 1; ++i) {
//			for (int j = 0; j < scaley; ++j) {
//				gl.glNormal3fv(n[i * scaley + (j + 1) % scaley], 0);
//				gl.glVertex3fv(v[i * scaley + (j + 1) % scaley], 0);
//				
//				gl.glNormal3fv(n[i * scaley + j], 0);
//				gl.glVertex3fv(v[i * scaley + j], 0);
//				
//				gl.glNormal3fv(n[(i + 1) * scaley + j], 0);
//				gl.glVertex3fv(v[(i + 1) * scaley + j], 0);
//				
//				gl.glNormal3fv(n[(i + 1) * scaley + (j + 1) % scaley], 0);
//				gl.glVertex3fv(v[(i + 1) * scaley + (j + 1) % scaley], 0);
//			}
//		}
//		gl.glEnd();
//		
//		gl.glBegin(GL2.GL_TRIANGLE_FAN);
//		gl.glNormal3fv(refVec, 0);
//		gl.glVertex3fv(VectorOps.add(center, VectorOps.multiply(refVec, outerradius)), 0);
//		
//		for (int i = scalex-1; i>=0; --i) {
//			gl.glNormal3fv(n[scalex*(scaley-1) + i], 0);
//			gl.glVertex3fv(v[scalex*(scaley-1) + i], 0);
//		}
//		gl.glNormal3fv(n[scalex*(scaley-1) + scalex-1], 0);
//		gl.glVertex3fv(v[scalex*(scaley-1) + scalex-1], 0);
//		
//		gl.glEnd();
//		
//		gl.glBegin(GL2.GL_TRIANGLE_FAN);
//		gl.glNormal3fv(VectorOps.invert(refVec), 0);
//		gl.glVertex3fv(center, 0);
//		
//		for (int i = 0; i<scalex; i++) {
//			gl.glNormal3fv(n[i], 0);
//			gl.glVertex3fv(v[i], 0);
//		}
//		gl.glNormal3fv(n[0], 0);
//		gl.glVertex3fv(v[0], 0);
//		
//		gl.glEnd();
//	
//		if (!picking) OutdatedShader.DEFAULT_SHADER.enable(gl);
	}
}
