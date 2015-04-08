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

/**
 * Some optimized routines to invert 3x3 and 4x4 matrices
 */
public class MatrixOps {

	/**
	 * Invert a packed 3x3 matrix
	 * @param m, the matrix to be inverted, will be overwritten with the inverted matrix
	 * @return true if matrix has been inverted
	 */
	public static boolean invert3x3matrix(double[] m){
		double a = m[0]; double b = m[1]; double c = m[2];
		double d = m[3]; double e = m[4]; double f = m[5];
		double g = m[6]; double h = m[7]; double i = m[8];
		
		double det = (a*e*i + b*f*g + c*d*h - c*e*g - a*f*h - b*d*i);
		double deti = 1./det;
		if (Double.isNaN(deti) || Double.isInfinite(deti)) return false;
		
		m[0] = deti * (e*i-h*f); m[1] = deti * (c*h-b*i); m[2] = deti * (b*f-c*e); 
		m[3] = deti * (f*g-d*i); m[4] = deti * (a*i-c*g); m[5] = deti * (c*d-a*f);
		m[6] = deti * (d*h-e*g); m[7] = deti * (b*g-a*h); m[8] = deti * (a*e-b*d);
		return true;
	}
	
	/** Invert a packed 3x3 matrix
	 * @param m, the matrix to be inverted, will be overwritten with the inverted matrix
	 * @param threshold do not inverse matrix if the determinate is smaller than this threshold
	 * @return true if matrix has been inverted
	 */
	public static boolean invert3x3matrix(double[] m, double threshold){
		double a = m[0]; double b = m[1]; double c = m[2];
		double d = m[3]; double e = m[4]; double f = m[5];
		double g = m[6]; double h = m[7]; double i = m[8];
		
		double det = (a*e*i + b*f*g + c*d*h - c*e*g - a*f*h - b*d*i);
		if (det<threshold) return false;
		double deti = 1./det;
		if (Double.isNaN(deti) || Double.isInfinite(deti)) return false;
		
		m[0] = deti * (e*i-h*f); m[1] = deti * (c*h-b*i); m[2] = deti * (b*f-c*e); 
		m[3] = deti * (f*g-d*i); m[4] = deti * (a*i-c*g); m[5] = deti * (c*d-a*f);
		m[6] = deti * (d*h-e*g); m[7] = deti * (b*g-a*h); m[8] = deti * (a*e-b*d);
		return true;
	}
	
	/**
	 * Invert a packed 3x3 matrix
	 * @param m, the matrix to be inverted, will be overwritten with the inverted matrix
	 * @return true if matrix has been inverted
	 */
	public static boolean invert3x3matrix(float[] m){
		float a = m[0]; float b = m[1]; float c = m[2];
		float d = m[3]; float e = m[4]; float f = m[5];
		float g = m[6]; float h = m[7]; float i = m[8];
		
		float det = (a*e*i + b*f*g + c*d*h - c*e*g - a*f*h - b*d*i);
		float deti = 1f/det;
		if (Float.isNaN(deti) || Float.isInfinite(deti)) return false;
		
		m[0] = deti * (e*i-h*f); m[1] = deti * (c*h-b*i); m[2] = deti * (b*f-c*e); 
		m[3] = deti * (f*g-d*i); m[4] = deti * (a*i-c*g); m[5] = deti * (c*d-a*f);
		m[6] = deti * (d*h-e*g); m[7] = deti * (b*g-a*h); m[8] = deti * (a*e-b*d);
		return true;
	}
	
	/**
	 * Invert a packed 4x4 matrix
	 * @param m, the matrix to be inverted, will be overwritten with the inverted matrix
	 * @return true if matrix has been inverted
	 */
	public static boolean invert4x4Matrix(double[] mat) {
		double[] dst = new double[16];
		
		/* calculate pairs for first 8 elements (cofactors) */		
		double tmp1 = (mat[10] * mat[15] - mat[14] * mat[11]);
		double tmp2 = (mat[6] * mat[15] - mat[14] * mat[7]);
		double tmp3 = (mat[6] * mat[11] - mat[10] * mat[7]);
		double tmp4 = (mat[2] * mat[15] - mat[14] * mat[3]);
		double tmp5 = (mat[2] * mat[11] - mat[10] * mat[3]);
		double tmp6 = (mat[2] * mat[7] - mat[6] * mat[3]);
		/* calculate first 8 elements (cofactors) */
		dst[0] =  tmp1 * mat[5] - tmp2 * mat[9] + tmp3 * mat[13];
		dst[1] = -tmp1 * mat[1] + tmp4 * mat[9] - tmp5 * mat[13];
		dst[2] =  tmp2 * mat[1] - tmp4 * mat[5] + tmp6 * mat[13];
		dst[3] = -tmp3 * mat[1] + tmp5 * mat[5] - tmp6 * mat[9];
		dst[4] = -tmp1 * mat[4] + tmp2 * mat[8] - tmp3 * mat[12];
		dst[5] =  tmp1 * mat[0] - tmp4 * mat[8] + tmp5 * mat[12];
		dst[6] = -tmp2 * mat[0] + tmp4 * mat[4] - tmp6 * mat[12];
		dst[7] =  tmp3 * mat[0] - tmp5 * mat[4] + tmp6 * mat[8];
		
		/* calculate pairs for second 8 elements (cofactors) */
		tmp1 = (mat[8] * mat[13] - mat[12] * mat[9]);
		tmp2 = (mat[4] * mat[13] - mat[12] * mat[5]);
		tmp3 = (mat[4] * mat[9] - mat[8] * mat[5]);
		tmp4 = (mat[0] * mat[13] - mat[12] * mat[1]);
		tmp5 = (mat[0] * mat[9] - mat[8] * mat[1]);
		tmp6 = (mat[0] * mat[5] - mat[4] * mat[1]);
		
		/* calculate second 8 elements (cofactors) */
		dst[8]  =  tmp1 * mat[7] - tmp2 * mat[11] + tmp3 * mat[15];
		dst[9]  = -tmp1 * mat[3] + tmp4 * mat[11] - tmp5 * mat[15];
		dst[10] =  tmp2 * mat[3] - tmp4 * mat[7] + tmp6 * mat[15];
		dst[11] = -tmp3 * mat[3] + tmp5 * mat[7] - tmp6 * mat[11];
		dst[12] =  tmp2 * mat[10] - tmp3 * mat[14] - tmp1 * mat[6];
		dst[13] =  tmp5 * mat[14] + tmp1 * mat[2]  - tmp4 * mat[10];
		dst[14] =  tmp4 * mat[6]  - tmp6 * mat[14] - tmp2 * mat[2];
		dst[15] =  tmp6 * mat[10] + tmp3 * mat[2]  - tmp5 * mat[6];
		/* calculate determinant */
		double det = mat[0] * dst[0] + mat[4] * dst[1] + mat[8] * dst[2] + mat[12] * dst[3];
		
		/* calculate matrix inverse */
		det = 1f / det;
		if (Double.isNaN(det) || Double.isInfinite(det)) return false;
		mat[0]  = det*dst[0];  mat[1]  = det*dst[1];  mat[2]  = det*dst[2];  mat[3]  = det*dst[3];
		mat[4]  = det*dst[4];  mat[5]  = det*dst[5];  mat[6]  = det*dst[6];  mat[7]  = det*dst[7];
		mat[8]  = det*dst[8];  mat[9]  = det*dst[9];  mat[10] = det*dst[10]; mat[11] = det*dst[11];
		mat[12] = det*dst[12]; mat[13] = det*dst[13]; mat[14] = det*dst[14]; mat[15] = det*dst[15];
		
		return true;
	}
	
	
	/**
	 * Invert a packed 4x4 matrix
	 * @param m, the matrix to be inverted, will be overwritten with the inverted matrix
	 * @return true if matrix has been inverted
	 */
	public static boolean invert4x4Matrix(float[] mat) {
		float[] dst = new float[16];
		
		/* calculate pairs for first 8 elements (cofactors) */		
		float tmp1 = (mat[10] * mat[15] - mat[14] * mat[11]);
		float tmp2 = (mat[6] * mat[15] - mat[14] * mat[7]);
		float tmp3 = (mat[6] * mat[11] - mat[10] * mat[7]);
		float tmp4 = (mat[2] * mat[15] - mat[14] * mat[3]);
		float tmp5 = (mat[2] * mat[11] - mat[10] * mat[3]);
		float tmp6 = (mat[2] * mat[7] - mat[6] * mat[3]);
		/* calculate first 8 elements (cofactors) */
		dst[0] =  tmp1 * mat[5] - tmp2 * mat[9] + tmp3 * mat[13];
		dst[1] = -tmp1 * mat[1] + tmp4 * mat[9] - tmp5 * mat[13];
		dst[2] =  tmp2 * mat[1] - tmp4 * mat[5] + tmp6 * mat[13];
		dst[3] = -tmp3 * mat[1] + tmp5 * mat[5] - tmp6 * mat[9];
		dst[4] = -tmp1 * mat[4] + tmp2 * mat[8] - tmp3 * mat[12];
		dst[5] =  tmp1 * mat[0] - tmp4 * mat[8] + tmp5 * mat[12];
		dst[6] = -tmp2 * mat[0] + tmp4 * mat[4] - tmp6 * mat[12];
		dst[7] =  tmp3 * mat[0] - tmp5 * mat[4] + tmp6 * mat[8];
		
		/* calculate pairs for second 8 elements (cofactors) */
		tmp1 = (mat[8] * mat[13] - mat[12] * mat[9]);
		tmp2 = (mat[4] * mat[13] - mat[12] * mat[5]);
		tmp3 = (mat[4] * mat[9] - mat[8] * mat[5]);
		tmp4 = (mat[0] * mat[13] - mat[12] * mat[1]);
		tmp5 = (mat[0] * mat[9] - mat[8] * mat[1]);
		tmp6 = (mat[0] * mat[5] - mat[4] * mat[1]);
		
		/* calculate second 8 elements (cofactors) */
		dst[8]  =  tmp1 * mat[7] - tmp2 * mat[11] + tmp3 * mat[15];
		dst[9]  = -tmp1 * mat[3] + tmp4 * mat[11] - tmp5 * mat[15];
		dst[10] =  tmp2 * mat[3] - tmp4 * mat[7] + tmp6 * mat[15];
		dst[11] = -tmp3 * mat[3] + tmp5 * mat[7] - tmp6 * mat[11];
		dst[12] =  tmp2 * mat[10] - tmp3 * mat[14] - tmp1 * mat[6];
		dst[13] =  tmp5 * mat[14] + tmp1 * mat[2]  - tmp4 * mat[10];
		dst[14] =  tmp4 * mat[6]  - tmp6 * mat[14] - tmp2 * mat[2];
		dst[15] =  tmp6 * mat[10] + tmp3 * mat[2]  - tmp5 * mat[6];
		/* calculate determinant */
		float det = mat[0] * dst[0] + mat[4] * dst[1] + mat[8] * dst[2] + mat[12] * dst[3];
		
		/* calculate matrix inverse */
		det = 1f / det;
		if (Float.isNaN(det) || Float.isInfinite(det)) return false;
		mat[0]  = det*dst[0];  mat[1]  = det*dst[1];  mat[2]  = det*dst[2];  mat[3]  = det*dst[3];
		mat[4]  = det*dst[4];  mat[5]  = det*dst[5];  mat[6]  = det*dst[6];  mat[7]  = det*dst[7];
		mat[8]  = det*dst[8];  mat[9]  = det*dst[9];  mat[10] = det*dst[10]; mat[11] = det*dst[11];
		mat[12] = det*dst[12]; mat[13] = det*dst[13]; mat[14] = det*dst[14]; mat[15] = det*dst[15];
		
		return true;
	}
}
