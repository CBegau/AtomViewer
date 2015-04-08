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

package gui.glUtils;

import java.nio.FloatBuffer;
import com.jogamp.common.nio.Buffers;
import common.MatrixOps;
import common.Vec3;

/**
 * Replaces the fix function operations in OpenGL, that are deprecated in OpenGL3
 */
public class GLMatrix {
	
	private FloatBuffer m = Buffers.newDirectFloatBuffer(16);
	
	public GLMatrix(){
		loadIdentity();
	}

	public GLMatrix(FloatBuffer fb){
		this.m.put(fb);
		this.m.rewind();
		fb.rewind();
	}
	
	private GLMatrix(GLMatrix m){
		float[] d = new float[16];
		m.getMatrix().get(d);
		m.getMatrix().rewind(); 
		this.m.put(d);
		this.m.rewind();
	}
	
	public FloatBuffer getMatrix(){
		return m;
	}
	
	public void translate(float x, float y, float z){
		float[] t = new float[16];
		t[0]= 1f; t[12] = x;
		t[5]= 1f; t[13] = y;
		t[10]= 1f; t[14] = z;
		t[15]= 1f;
		mult(t);
	}
	
	public void scale(float x, float y, float z){
		assert (x!=0f && y!=0 && z!=0);
		
		float[] t = new float[16];
		t[0]= x;
		t[5]= y;
		t[10]= z;
		t[15]= 1f;
		mult(t);
	}
	
	public void rotate(float angle, float x, float y, float z){
		//normalize
		float l = (x*x + y*y + z*z);
		
		assert (l!=0f);
		
		l = 1f / (float)Math.sqrt(l);
		x *= l; y *= l; z *= l; 
		
		float[] t = new float[16];
		
		float s = (float)Math.sin(angle/180.*Math.PI);
		float c = (float)Math.cos(angle/180.*Math.PI);
		
		t[0] = x*x*(1-c)+c; t[4] = x*y*(1-c)-z*s; t[8] = x*z*(1-c)+y*s;
		t[1] = y*x*(1-c)+z*s; t[5] = y*y*(1-c)+c; t[9] = y*z*(1-c)-x*s;
		t[2] = x*z*(1-c)-y*s; t[6] = y*z*(1-c)+x*s; t[10] = z*z*(1-c)+c;
		t[15] = 1f;
		mult(t);
	}
	
	public void createOrtho(float left, float right, float  bottom, float top, float znear, float zfar){
		float[] t = new float[16];
		t[0] = 2f/(right-left); t[12] = -(right+left)/(right-left);
		t[5] = 2f/(top-bottom); t[13] = -(top+bottom)/(top-bottom);
		t[10] = -2f/(zfar-znear); t[14] = -(zfar+znear)/(zfar-znear);
		t[15] = 1f;
		this.m.put(t);
		this.m.rewind();
	}
	
	public void createFrustum(float left, float right, float  bottom, float top, float znear, float zfar){
		float[] t = new float[16];
		t[0] = 2f*znear/(right-left); t[8] = (right+left)/(right-left);
		t[5] = 2f*znear/(top-bottom); t[9] = (top+bottom)/(top-bottom);
		t[10] = -(zfar+znear)/(zfar-znear); t[14] = -2f*zfar*znear/(zfar-znear);
		t[11] = -1f;
		this.m.put(t);
		this.m.rewind();
	}
	
	public void mult(GLMatrix m){
		float[] d = new float[16];
		m.getMatrix().get(d);
		m.getMatrix().rewind();
		mult(d);
	}
		
	public void mult(float[] m){
		float[] copy = new float[16];
		this.m.get(copy);
		this.m.rewind();
		
		this.m.put(m[0] *  copy[0] + m[1] *  copy[4] + m[2] *  copy[8] +  m[3] *  copy[12]); 
		this.m.put(m[0] *  copy[1] + m[1] *  copy[5] + m[2] *  copy[9] +  m[3] *  copy[13]);
		this.m.put(m[0] *  copy[2] + m[1] *  copy[6] + m[2] *  copy[10] + m[3] *  copy[14]);
		this.m.put(m[0] *  copy[3] + m[1] *  copy[7] + m[2] *  copy[11] + m[3] *  copy[15]);
		
		this.m.put(m[4] *  copy[0] + m[5] *  copy[4] + m[6] *  copy[8] +  m[7] *  copy[12]); 
		this.m.put(m[4] *  copy[1] + m[5] *  copy[5] + m[6] *  copy[9] +  m[7] *  copy[13]);
		this.m.put(m[4] *  copy[2] + m[5] *  copy[6] + m[6] *  copy[10] + m[7] *  copy[14]);
		this.m.put(m[4] *  copy[3] + m[5] *  copy[7] + m[6] *  copy[11] + m[7] *  copy[15]);
		
		this.m.put(m[8] *  copy[0] + m[9] *  copy[4] + m[10] * copy[8] +  m[11] * copy[12]); 
		this.m.put(m[8] *  copy[1] + m[9] *  copy[5] + m[10] * copy[9] +  m[11] * copy[13]);
		this.m.put(m[8] *  copy[2] + m[9] *  copy[6] + m[10] * copy[10] + m[11] * copy[14]);
		this.m.put(m[8] *  copy[3] + m[9] *  copy[7] + m[10] * copy[11] + m[11] * copy[15]);
		
		this.m.put(m[12] * copy[0] + m[13] * copy[4] + m[14] * copy[8] +  m[15] * copy[12]); 
		this.m.put(m[12] * copy[1] + m[13] * copy[5] + m[14] * copy[9] +  m[15] * copy[13]);
		this.m.put(m[12] * copy[2] + m[13] * copy[6] + m[14] * copy[10] + m[15] * copy[14]);
		this.m.put(m[12] * copy[3] + m[13] * copy[7] + m[14] * copy[11] + m[15] * copy[15]);
		
		this.m.rewind();
	}
	
	public void inverse(){
		float[] copy = new float[16];
		this.m.get(copy);
		this.m.rewind();
		MatrixOps.invert4x4Matrix(copy);
		this.m.put(copy);
		this.m.rewind();
	}
	
	public GLMatrix clone(){
		return new GLMatrix(this);
	}
	
	public FloatBuffer getNormalMatrix(){
		FloatBuffer fb = Buffers.newDirectFloatBuffer(9);
		//Normal matrix is the transpose of the inverted upper left 3x3 submatrix
		float[] sub = new float[9];
		
		float[] copy = new float[16];
		this.m.get(copy);
		this.m.rewind();
		
		sub[0] = copy[0]; sub[1] = copy[1]; sub[2] = copy[2];  
		sub[3] = copy[4]; sub[4] = copy[5]; sub[5] = copy[6];
		sub[6] = copy[8]; sub[7] = copy[9]; sub[8] = copy[10];
		
		MatrixOps.invert3x3matrix(sub);
		
		fb.put(sub[0]); fb.put(sub[3]); fb.put(sub[6]);
		fb.put(sub[1]); fb.put(sub[4]); fb.put(sub[7]);
		fb.put(sub[2]); fb.put(sub[5]); fb.put(sub[8]);
		
		fb.rewind();
		return fb;
	}
	
	public FloatBuffer getUpper3x3Matrix(){
		FloatBuffer fb = Buffers.newDirectFloatBuffer(9);
		
		float[] copy = new float[16];
		this.m.get(copy);
		this.m.rewind();
		
		fb.put(copy[0]); fb.put(copy[1]); fb.put(copy[2]);  
		fb.put(copy[4]); fb.put(copy[5]); fb.put(copy[6]);
		fb.put(copy[8]); fb.put(copy[9]); fb.put(copy[10]);
		
		fb.rewind();
		return fb;
	}
	
	public void loadIdentity(){
		m.put(1f); m.put(0f); m.put(0f); m.put(0f);
		m.put(0f); m.put(1f); m.put(0f); m.put(0f);
		m.put(0f); m.put(0f); m.put(1f); m.put(0f);
		m.put(0f); m.put(0f); m.put(0f); m.put(1f);
		m.rewind();
	}
	
	public void lookAt(Vec3 eye, Vec3 center, Vec3 up ){
		Vec3 f = center.subClone(eye).normalize();
		up = up.normalizeClone();
		Vec3 s = f.cross(up).normalize();
		Vec3 u = s.cross(f);
		
		float[] m = new float[16];
		
		m[0]=s.x;  m[4]=s.y;  m[8]=s.z;   m[12]=0f;
		m[1]=u.x;  m[5]=u.y;  m[9]=u.z;   m[13]=0f;
		m[2]=-f.x; m[6]=-f.y; m[10]=-f.z; m[14]=0f;
		m[3]= 0f ;  m[7]=0f;    m[11]=0f;    m[15]=1f;
		
		this.mult(m);
		this.translate(-eye.x, -eye.y, -eye.z);
	}
	
	public void setRotationFromQuaternion(float[] q1) {
        float n, s;
        float xs, ys, zs;
        float wx, wy, wz;
        float xx, xy, xz;
        float yy, yz, zz;

        n = (q1[0] * q1[0]) + (q1[1] * q1[1]) + (q1[2] * q1[2]) + (q1[3] * q1[3]);
        s = (n > 0.0f) ? (2.0f / n) : 0.0f;

        xs = q1[0] * s;
        ys = q1[1] * s;
        zs = q1[2] * s;
        wx = q1[3] * xs;
        wy = q1[3] * ys;
        wz = q1[3] * zs;
        xx = q1[0] * xs;
        xy = q1[0] * ys;
        xz = q1[0] * zs;
        yy = q1[1] * ys;
        yz = q1[1] * zs;
        zz = q1[2] * zs;

        float[] m = new float[16];
		
		m[0]=1.0f - (yy + zz);  m[4]= xy - wz;;  m[8]= xz + wy;   m[12]=0f;
		m[1]=xy + wz;  m[5]=1.0f - (xx + zz);  m[9]=yz - wx;   m[13]=0f;
		m[2]=xz - wy; m[6]=yz + wx; m[10]=1.0f - (xx + yy); m[14]=0f;
		m[3]= 0f ;  m[7]=0f;    m[11]=0f;    m[15]=1f;
        
		this.mult(m);
    }
	
	public float[][] getAsArray(){
		float[][] a = new float[4][4];
		
		a[0][0] = m.get(0); a[0][1] = m.get(1); a[0][2] = m.get(2); a[0][3] = m.get(3);
		a[1][0] = m.get(4); a[1][1] = m.get(5); a[1][2] = m.get(6); a[1][3] = m.get(7);
		a[2][0] = m.get(8); a[2][1] = m.get(9); a[2][2] = m.get(10); a[2][3] = m.get(11);
		a[3][0] = m.get(12); a[3][1] = m.get(13); a[3][2] = m.get(14); a[3][3] = m.get(15);
		
		return a;
	}
}
