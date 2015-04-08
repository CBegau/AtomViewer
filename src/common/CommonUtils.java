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

import java.text.DecimalFormat;

public class CommonUtils {
	
	/**
	 * Computes the smallest common multiple (SCM) from two values
	 * @param a an integer value
	 * @param b another integer value
	 * @return the SCM of the two values
	 */
	public static int scm(int a, int b) {
		return Math.abs(a * b) / euclid(a, b);
	}

	private static int euclid(int a, int b) {
		a = Math.abs(a);
		b = Math.abs(b);
		if (b == 0) return a;
		else return euclid(b, a % b);
	}

	/**
	 * Computes the greatest common divisor (GCD) from an array of integers
	 * @param v Array of integers to compute GCD
	 * @return the GCD of the given values
	 */
	public static int gcd(int[] v) {
		if (v.length == 0) return 1;
		if (v.length == 1) return Math.abs(v[0]);
		int gcd = euclid(v[0], v[1]);
		for (int i = 2; i < v.length; i++)
			gcd = euclid(gcd, v[i]);
		return gcd;
	}
	
	public final static DecimalFormat outputDecimalFormatter = new DecimalFormat("#.####");
}
