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

import gui.glUtils.Shader;
import gui.glUtils.VertexDataStorageLocal;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import common.Vec3;

public class CuboidVolumeElement implements VolumeElement{

	public Vec3 upperBound, lowerBound;
	
	public CuboidVolumeElement(Vec3 upperBound, Vec3 lowerBound) {
		this.upperBound = upperBound;
		this.lowerBound = lowerBound;
	}

	@Override
	public boolean isInVolume(Vec3 c) {
		if (c.x<lowerBound.x || c.x>upperBound.x) return false;
		if (c.y<lowerBound.y || c.y>upperBound.y) return false;
		if (c.z<lowerBound.z || c.z>upperBound.z) return false;
		return true;
	}

	@Override
	public float getVolume() {
		Vec3 s = upperBound.subClone(lowerBound);
		return s.x * s.y * s.z;
	}
	
	public Vec3 getLowerBound() {
		return lowerBound;
	}
	
	public Vec3 getUpperBound() {
		return upperBound;
	}
	
	@Override
	public void render(GL3 gl, boolean picking, float[] color4){
		Vec3 u = upperBound;
		Vec3 l = lowerBound;
		
		Shader s = Shader.BuiltInShader.ADS_UNIFORM_COLOR.getShader();
		s.enable(gl);
		int col = gl.glGetUniformLocation(s.getProgram(),"Color");
		
		//TODO: Irgendwie an die ModelView Matrize rankommen und den Cube aus SimpleGeometries nutzen 
		
		gl.glUniform4f(col, color4[0], color4[1], color4[2], color4[3]);
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, 36, 3, 3, 0, 0, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		
		vds.setNormal(0, 0, -1);
		vds.setVertex(l.x, l.y, l.z); vds.setVertex(l.x, u.y, l.z); vds.setVertex(u.x, u.y, l.z);
		vds.setVertex(u.x, u.y, l.z); vds.setVertex(u.x, l.y, l.z); vds.setVertex(l.x, l.y, l.z); 
		
		vds.setNormal(0, 0, 1);
		vds.setVertex(l.x, l.y, u.z); vds.setVertex(u.x, l.y, u.z); vds.setVertex(u.x, u.y, u.z);
		vds.setVertex(u.x, u.y, u.z); vds.setVertex(l.x, u.y, u.z); vds.setVertex(l.x, l.y, u.z);
		
		vds.setNormal(0, -1, 0);
		vds.setVertex(l.x, l.y, l.z); vds.setVertex(u.x, l.y, l.z); vds.setVertex(u.x, l.y, u.z);
		vds.setVertex(u.x, l.y, u.z); vds.setVertex(l.x, l.y, u.z); vds.setVertex(l.x, l.y, l.z);
		
		vds.setNormal(0, 1, 0);
		vds.setVertex(l.x, u.y, l.z); vds.setVertex(l.x, u.y, u.z); vds.setVertex(u.x, u.y, u.z);
		vds.setVertex(u.x, u.y, u.z); vds.setVertex(u.x, u.y, l.z); vds.setVertex(l.x, u.y, l.z);
		
		vds.setNormal(-1, 0, 0);
		vds.setVertex(l.x, l.y, l.z); vds.setVertex(l.x, l.y, u.z); vds.setVertex(l.x, u.y, u.z);
		vds.setVertex(l.x, u.y, u.z); vds.setVertex(l.x, u.y, l.z); vds.setVertex(l.x, l.y, l.z);
		
		vds.setNormal(1, 0, 0);
		vds.setVertex(u.x, l.y, l.z); vds.setVertex(u.x, u.y, l.z); vds.setVertex(u.x, u.y, u.z);
		vds.setVertex(u.x, u.y, u.z); vds.setVertex(u.x, l.y, u.z); vds.setVertex(u.x, l.y, l.z);
		
		vds.endFillBuffer(gl);
		vds.draw(gl, GL.GL_TRIANGLES);
		vds.dispose(gl);
	}
}
