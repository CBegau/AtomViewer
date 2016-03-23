// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
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

import java.awt.Component;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.io.File;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JLabel;

public class CommonUtils {
	
	/**
	 * Computes the smallest common multiple (SCM) from two values
	 * @param a an integer value
	 * @param b another integer value
	 * @return the SCM of the two values
	 */
	public static int smallestCommonMultiple(int a, int b) {
		return Math.abs(a * b) / euclid(a, b);
	}
	
	public static float getM4SmoothingKernelWeight(float distance, float h){
		float q = distance / h;

		float fac = 1f/(4*(float)Math.PI*h*h*h);
		
		float tmp2 = 2f - q;
		float val = tmp2*tmp2*tmp2;
		
		if (q<1f){
			float tmp1 = 1f - q;
			val -= 4*tmp1*tmp1*tmp1;
		}
		
		if (q > 2f) val = 0f;		
		return val*fac;
	}
	
	public static Vec3 getM4SmoothingKernelDerivative(Vec3 r, float h){
		float d = r.getLength();
		float q = d / h;
		
		float val;
        if (q < 1f) val = 0.75f*q*q - q;
        else val = -(1f - q + 0.25f*q*q);
		if (q > 2f) val = 0.f;
		
        return r.multiplyClone(3f * val / (h*h*h*h * d * (float)Math.PI) );
	}

	/**
	 * Computes the greatest common divisor (GCD) from an array of integers
	 * @param v Array of integers to compute GCD
	 * @return the GCD of the given values
	 */
	public static int greatestCommonDivider(int[] v) {
		if (v.length == 0) return 1;
		if (v.length == 1) return Math.abs(v[0]);
		int gcd = euclid(v[0], v[1]);
		for (int i = 2; i < v.length; i++)
			gcd = euclid(gcd, v[i]);
		return gcd;
	}
	
	private static int euclid(int a, int b) {
		a = Math.abs(a);
		b = Math.abs(b);
		if (b == 0) return a;
		else return euclid(b, a % b);
	}
	
	public final static DecimalFormat outputDecimalFormatter = new DecimalFormat("#.####");
	
	/**
	 * An implementation of the Kahan summation to compute the sum
	 * of a large number of floats/doubles with limited rounding errors
	 */
	public final static class KahanSum {
		double sum = 0.0;
		double c = 0.0;
		
		public void add(double v){
			 double y = v - c;
			 double t = sum + y;
			 // (t - sum) recovers the high-order part of y; subtracting y recovers -(low part of y)
			 c = (t - sum) - y;
			 sum = t; //Next time around, the lost low part c will be added to y
		}
		
		public double getSum() {
			return sum;
		}
		
		@Override
		public String toString() {
			return Double.toString(sum);
		}
	}
	
	public final static GridBagConstraints getBasicGridBagConstraint(){
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH; gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.weighty = 1;
		gbc.anchor = GridBagConstraints.SOUTHWEST;
		return gbc;
	}
	
	public final static boolean isFileGzipped(File f){
		final int GZIP_MAGIC = 0x8b1f;
		try {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(f, "r");
				int byte1 = raf.read();
				if (byte1==-1) return false;
				int byte2 = raf.read();
				if (byte2==-1) return false;
				if (((byte2 << 8) | byte1) == GZIP_MAGIC) return true;
			} finally {
				if (raf != null) raf.close();
			}
		} catch (Exception e) {}

		return false;
	}
	
	/**
	 * Checks if a string is a numeric value or not
	 * Handles negative numbers and decimal separators (. or ,) as well
	 * Does not handle non-latin numbers
	 * @param str String to test
	 * @return true if the string can be parsed 
	 */
	public final static boolean isStringNumeric(String str) {
		final Pattern p = Pattern.compile("^[+-]?\\d+([,\\.]\\d+)?([eE]-?\\d+)?$");
		return p.matcher(str).matches();
	}
	
	public static String getWordWrappedString(String s, JComponent c){
		GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		int maxSizeString = gd.getDisplayMode().getWidth()/3;
		if (c.getFontMetrics(c.getFont()).stringWidth(s) > maxSizeString)
			return "<html><table><tr><td width='"+maxSizeString+"'>"+ s +"</td></tr></table></html>";
		else return s;
	}
	
	public static String buildHTMLTableForKeyValue(String[] keys, Object[] values){
		assert (keys.length == values.length);
		StringBuilder sb = new StringBuilder();
		
		sb.append("<table>");
		for (int i=0; i<keys.length; i++){
			sb.append("<tr><td>");
			sb.append(keys[i]); sb.append(":");
			sb.append("</td></td>");
			sb.append(values[i].toString());
			sb.append("</td></tr>");
		}
		sb.append("</table>");
		
		return sb.toString();
	}
	
	public static JLabel getWordWrappedJLabel(String s){
		JLabel l = new JLabel();
		l.setText(getWordWrappedString(s, l));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
}
