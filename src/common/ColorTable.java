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

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.regex.Pattern;

import model.Configuration;

/**
 * Table for some colors.
 * A given index return the color either for OpenGL or a Java Color
 * @author Begau
 *
 */
public class ColorTable {
	
	public enum ColorBarScheme{
		RAINBOW_print(new float[][]{		
		        { 12f/255f, 81f/255f,187f/255f},
		        {141f/255f,206f/255f,207f/255f},
		        {104f/255f,180f/255f, 54f/255f},
		        {252f/255f,235f/255f, 10f/255f},
		        {230f/255f, 60f/255f, 36f/255f}
			}, "Rainbow (printing friendly)", true),
		RAINBOW(new float[][]{		
		        {0.0f, 0.0f, 1.0f},
		        {0.0f, 1.0f, 1.0f},
		        {0.0f, 1.0f, 0.0f},
		        {1.0f, 1.0f, 0.0f},
		        {1.0f, 0.0f, 0.0f},
			}, "Rainbow", true),
		JET(new float[][]{		
				{0.0f, 0.0f, 0.5f},
		        {0.0f, 0.0f, 1.0f},
		        {0.0f, 0.5f, 1.0f},
		        {0.0f, 1.0f, 1.0f},
		        {0.5f, 1.0f, 0.5f},
		        {1.0f, 1.0f, 0.0f},
		        {1.0f, 0.5f, 0.0f},
		        {1.0f, 0.0f, 0.0f},
		        {0.5f, 0.0f, 0.0f}
			}, "Jet", true),
		SPECTRA(new float[][]{
				{0x5e / 255f, 0x4f / 255f, 0xa2 / 255f},
				{0x32 / 255f, 0x88 / 255f, 0xbd / 255f},
				{0x66 / 255f, 0xc2 / 255f, 0xa5 / 255f},
				{0xab / 255f, 0xdd / 255f, 0xa4 / 255f},
				{0xe6 / 255f, 0xf5 / 255f, 0x98 / 255f},
				{0xff / 255f, 0xff / 255f, 0xbf / 255f},
				{0xfe / 255f, 0xe0 / 255f, 0x8b / 255f},
				{0xfd / 255f, 0xae / 255f, 0x61 / 255f},
				{0xf4 / 255f, 0x6d / 255f, 0x43 / 255f},
				{0xd5 / 255f, 0x3e / 255f, 0x4f / 255f},
				{0x9e / 255f, 0x01 / 255f, 0x42 / 255f},
		}, "Spectra", true),
		REDYELLOWBLUE(new float[][]{
				{0x31 / 255f, 0x36 / 255f, 0x95 / 255f},
				{0x45 / 255f, 0x75 / 255f, 0xb4 / 255f},
				{0x74 / 255f, 0xad / 255f, 0xd1 / 255f},
				{0xab / 255f, 0xd9 / 255f, 0xe9 / 255f},
				{0xe0 / 255f, 0xf3 / 255f, 0xf8 / 255f},
				{0xff / 255f, 0xff / 255f, 0xbf / 255f},
				{0xfe / 255f, 0xe0 / 255f, 0x90 / 255f},
				{0xfd / 255f, 0xae / 255f, 0x61 / 255f},
				{0xf4 / 255f, 0x6d / 255f, 0x43 / 255f},
				{0xd7 / 255f, 0x30 / 255f, 0x27 / 255f},
				{0xa5 / 255f, 0x00 / 255f, 0x26 / 255f},
		}, "Red Yellow Blue", true),
		REDWHITEWBLUE(new float[][]{
				{0x05 / 255f, 0x30 / 255f, 0x61 / 255f},
				{0x21 / 255f, 0x66 / 255f, 0xac / 255f},
				{0x43 / 255f, 0x93 / 255f, 0xc3 / 255f},
				{0x92 / 255f, 0xc5 / 255f, 0xde / 255f},
				{0xd1 / 255f, 0xe5 / 255f, 0xf0 / 255f},
				{0xf7 / 255f, 0xf7 / 255f, 0xf7 / 255f},
				{0xfd / 255f, 0xdb / 255f, 0xc7 / 255f},
				{0xf4 / 255f, 0xa5 / 255f, 0x82 / 255f},
				{0xd6 / 255f, 0x60 / 255f, 0x4d / 255f},
				{0xb2 / 255f, 0x18 / 255f, 0x2b / 255f},
				{0x67 / 255f, 0x00 / 255f, 0x1f / 255f},
		}, "Red White Blue", true),
		HEAT(new float[][]{		
		        {0.0f, 0.0f, 0.0f},
		        {1.0f, 0.0f, 0.0f},
		        {1.0f, 1.0f, 0.0f},
		        {1.0f, 1.0f, 1.0f},
			}, "Heat", true),
		HEAT2(new float[][]{
				{128/255f,0  /255f,38 /255f},
				{189/255f,0  /255f,38 /255f},
				{227/255f,26 /255f,28 /255f},
				{252/255f,78 /255f,42 /255f},
				{253/255f,141/255f,60 /255f},
				{254/255f,178/255f,76 /255f},
				{254/255f,217/255f,118/255f},
				{255/255f,237/255f,160/255f},
		        {255/255f,255/255f,204/255f},
			}, "Heat2", true),
		THERMAL(new float[][]{
				{0.223607f, 0.000125f, 0.309017f},
				{0.316228f, 0.001000f, 0.587785f},
				{0.387298f, 0.003375f, 0.809017f},
				{0.447214f, 0.008000f, 0.951057f},
				{0.500000f, 0.015625f, 1.000000f},
				{0.547723f, 0.027000f, 0.951056f},
				{0.591608f, 0.042875f, 0.809017f},
				{0.632456f, 0.064000f, 0.587785f},
				{0.670820f, 0.091125f, 0.309017f},
				{0.707107f, 0.125000f, 0.000000f},
				{0.741620f, 0.166375f, 0.000000f},
				{0.774597f, 0.216000f, 0.000000f},
				{0.806226f, 0.274625f, 0.000000f},
				{0.836660f, 0.343000f, 0.000000f},
				{0.866025f, 0.421875f, 0.000000f},
				{0.894427f, 0.512000f, 0.000000f},
				{0.921954f, 0.614125f, 0.000000f},
				{0.948683f, 0.729000f, 0.000000f},
				{0.974679f, 0.857375f, 0.000000f},
				{1.000000f, 1.000000f, 0.000000f},
			}, "Thermal", true),
		GREY(new float[][]{		
		        {0.0f, 0.0f, 0.0f},
		        {1.0f, 1.0f, 1.0f},
			}, "Grey", true),
		MANYCOLORS(new float[][]{		
				{0xa6 / 255f, 0xce / 255f, 0xe3 / 255f},
				{0x1f / 255f, 0x78 / 255f, 0xb4 / 255f},
		        {0xb2 / 255f, 0xdf / 255f, 0x8a / 255f},
		        {0x33 / 255f, 0xa0 / 255f, 0x2c / 255f},
		        {0xfb / 255f, 0x9a / 255f, 0x99 / 255f},
		        {0xe3 / 255f, 0x1a / 255f, 0x1c / 255f},
		        {0xfd / 255f, 0xbf / 255f, 0x6f / 255f},
		        {0xff / 255f, 0x7f / 255f, 0x00 / 255f},
		        {0xca / 255f, 0xb2 / 255f, 0xd6 / 255f},
		        {0x6a / 255f, 0x3d / 255f, 0x9a / 255f},
		        {0xff / 255f, 0xff / 255f, 0x99 / 255f},
		        {0xb1 / 255f, 0x59 / 255f, 0x28 / 255f},
			}, "Many colors", true),
		MARTENSITE_COLORING(new float[][]{
				{1.0f, 0.0f, 0.0f},
				{1.0f, 0.46153843f, 0.0f},
				{1.0f, 0.9230769f, 0.0f},
				{0.6153846f, 1.0f, 0.0f},
				{0.15384614f, 1.0f, 0.0f},
				{0.0f, 1.0f, 0.30769253f},
				{0.0f, 1.0f, 0.76923084f},
				{0.0f, 0.7692306f, 1.0f},
				{0.0f, 0.3076923f, 1.0f},
				{0.15384626f, 0.0f, 1.0f},
				{0.61538506f, 0.0f, 1.0f},
				{1.0f, 0.0f, 0.9230771f},
				{0.5f, 0.5f, 0.5f},
				{0.5f, 0.5f, 0.5f}}, "MartensiteColors", false)
		;
		
		private final float[][] colorBar;
		private final String name;
		private final boolean generalScheme;
		
		@Override
		public String toString() {
			return this.name;
		};
		
		public boolean isGeneralScheme() {
			return generalScheme;
		}
		
		public float[][] getColorBar() {
			return colorBar;
		}
		
		ColorBarScheme(float[][] bar, String name, boolean generalScheme){
			this.colorBar = bar;
			this.name = name;
			this.generalScheme = generalScheme;
		}
	}
	
	public static final String ELEMENT_COLORS_ID = "elementColors"; 
	private static float[][] elementColors = null;
	private static ColorBarScheme colorBarScheme = ColorBarScheme.RAINBOW_print; 
	private static boolean colorBarSwapped = false;
	
	private static final int NUM_INTERPOLATIONS = 2048;
	private static float[][] interpolatedColors;
	
	static {
		interpolateColors();
	}
	
	public static ColorBarScheme getColorBarScheme() {
		return colorBarScheme;
	}
	
	public static void setColorBarScheme(ColorBarScheme colorBarScheme) {
		ColorTable.colorBarScheme = colorBarScheme;
		interpolateColors();
	}
	
	private static void interpolateColors(){
		interpolatedColors = new float[NUM_INTERPOLATIONS+1][];
		for (int i=0; i<=NUM_INTERPOLATIONS; i++){
			float value = (float)i/NUM_INTERPOLATIONS;
			interpolatedColors[i] = interpolateColor(value); 
		}
	}
	
	public static boolean isColorBarSwapped() {
		return colorBarSwapped;
	}
	
	public static void setColorBarSwapped(boolean colorBarSwapped) {
		ColorTable.colorBarSwapped = colorBarSwapped;
		ColorTable.setColorBarScheme(colorBarScheme);
	}
	
	/**
	 * Returns a color for intensity interpolated from the current color bar scheme
	 * Interpolates linearly between 0 and 1 for the given value.
	 * If the value is smaller than 0, the lowest color interpolation is returned,
	 * if the value is larger than 1, the highest color interpolation is returned.
	 * The returned value must not be modified
	 * Alpha value is fixed to 1
	 * @param value The value to get an interpolated color for in the range 0 to 1  
	 * @return The interpolated color
	 */
	public static float[] getIntensityGLColor(float value){
		if (value < 0f) value = 0f;
		if (value > 1f) value = 1f;
		int v = (int)(value*NUM_INTERPOLATIONS);
		return interpolatedColors[v];
	}
	
	private static float[] interpolateColor(float value){
		if (colorBarSwapped) value = 1f-value;
		float[][] pattern = colorBarScheme.colorBar;
		int size = pattern.length;
		
		if (value>=1) return new float[]{pattern[size-1][0],pattern[size-1][1],pattern[size-1][2], 1f};
		else if (value>0 && value <1){
			float frac = 1f/(size-1);
			float[] color = new float[4];
			color[3] = 1f;
			
			int index = (int)(value/frac);
			float w = (value-index*frac)/frac;
			
			color[0] = (pattern[index+1][0]*w + pattern[index][0]*(1f-w));
			color[1] = (pattern[index+1][1]*w + pattern[index][1]*(1f-w));
			color[2] = (pattern[index+1][2]*w + pattern[index][2]*(1f-w));
			
			return color;
		} else return new float[]{pattern[0][0],pattern[0][1],pattern[0][2], 1f};
	}
	
	/**
	 * Returns a color for intensity interpolated from the current color bar scheme.
	 * Interpolates linearly between min and max for the given value.
	 * If the value is smaller than min, the lowest color interpolation is returned,
	 * if the value is larger than max, the highest color interpolation is returned.
	 * The returned value must not be modified 
	 * @param min The value for the minimum interpolated color
	 * @param max The value for the maximum interpolated color
	 * @param value The value to get an interpolated color for
	 * @return The interpolated color
	 */
	public static float[] getIntensityGLColor(float min, float max, float value){
		float interval = max - min;
		value = (value-min)/interval;
		return getIntensityGLColor(value);
	}
	
	/**
	 * Returns a color for intensity interpolated from the current color bar scheme.
	 * Interpolates linearly between min and max for the given value.
	 * If the value is smaller than min, the lowest color interpolation is returned,
	 * if the value is larger than max, the highest color interpolation is returned.
	 * The returned value must not be modified 
	 * @param min The value for the minimum interpolated color
	 * @param max The value for the maximum interpolated color
	 * @param value The value to get an interpolated color for
	 * @return The interpolated color
	 */
	public static Color getIntensityColor(float min, float max, float value){
		float[] f = getIntensityGLColor(min, max, value);
		return new Color(f[0], f[1], f[2]);
	}
	
	/**
	 * Returns a color for intensity interpolated from the current color bar scheme
	 * Interpolates linearly between 0 and 1 for the given value.
	 * If the value is smaller than 0, the lowest color interpolation is returned,
	 * if the value is larger than 1, the highest color interpolation is returned.
	 * The returned value are copies of the internal table and can be modified
	 * @param value The value to get an interpolated color for in the range 0 to 1
	 * @param transparency Alpha value of the color, must be within [0-1]  
	 * @return The interpolated color
	 */
	public static float[] getIntensityGLColor(float value, float transparency){
		float[] c = getIntensityGLColor(value).clone();
		c[3] = transparency;
		return c;
	}
	
	/**
	 * Returns a color for intensity interpolated from the current color bar scheme.
	 * Interpolates linearly between min and max for the given value.
	 * If the value is smaller than min, the lowest color interpolation is returned,
	 * if the value is larger than max, the highest color interpolation is returned.
	 * The returned value are copies of the internal table and can be modified 
	 * @param min The value for the minimum interpolated color
	 * @param max The value for the maximum interpolated color
	 * @param value The value to get an interpolated color for
	 * @param transparency the alpha value of the color, must be within [0-1]
	 * @return The interpolated color
	 */
	public static float[] getIntensityGLColor(float min, float max, float value, float transparency){
		float interval = max - min;
		value = (value-min)/interval;
		return getIntensityGLColor(value, transparency);
	}
	
	/**
	 * Returns a color for intensity interpolated from the current color bar scheme
	 * Interpolates linearly between 0 and 1 for the given value.
	 * If the value is smaller than 0, the lowest color interpolation is returned,
	 * if the value is larger than 1, the highest color interpolation is returned.
	 * The returned value must not be modified
	 * Alpha value is fixed to 1
	 * @param value The value to get an interpolated color for in the range 0 to 1  
	 * @return The interpolated color
	 */
	public static Color getIntensityColor(float value){
		float[] f = getIntensityGLColor(value);
		return new Color(f[0], f[1], f[2]);
	}
	
	/**
	 * Creates a set of colors
	 * The colors are evenly distributed from the HSV cylinder, all colors have full saturation
	 * and no transparency 
	 * @param numberOfColors number of colors in the created table
	 * @return a table of colors
	 */
	public static float[][] createColorTable(int numberOfColors){
		return createColorTable(numberOfColors, 1f);
	}
	
	/**
	 * Creates a set of colors
	 * The colors are evenly distributed from the HSV cylinder, all colors have full saturation 
	 * @param numberOfColors number of colors in the created table
	 * @param transparency the alpha value of each 
	 * @return a table of colors
	 */
	public static float[][] createColorTable(int numberOfColors, float transparency){
		float[][] colors = new float[numberOfColors][];
		if (numberOfColors==0) return colors; 
		for (int i=0; i<numberOfColors; i++){
			colors[i] = ColorUtils.HSVToRGB(((float)i/(float)(numberOfColors+1)), 1f, 1f);
			colors[i][3] = transparency;
		}		
		return colors;
	}
	
	/**
	 * Provides a coloring scheme for elements
	 * It tries to read this scheme from a file named "elementColors.color".
	 * If the file does not exist, it will create a rainbow color table
	 * If more elements are requested than stored in the file, it will be
	 * filled with colors from the rainbow color table
	 * @param numElements number of elements that required coloring
	 * @return an array of rgb values as float. The array is at least the size of numElements
	 */
	public static float[][] getColorTableForElements(byte numElements){
		if (elementColors == null){
			elementColors = loadDefaultColorScheme(ELEMENT_COLORS_ID);
			//File does not exist, create defaults
			if (elementColors == null){
				elementColors = createColorTable(numElements); 
				return elementColors;
			}
		}
		
		if (elementColors.length >= numElements)
			return elementColors;
		else {
			float[][] colors = createColorTable(numElements);
			for (int i = 0; i<elementColors.length; i++)
				colors[i] = elementColors[i];
			elementColors = colors;
			return elementColors;	
		}
	}

	public static final float[][] loadColorsFromFile(File f) throws IOException {
		ArrayList<float[]> c = new ArrayList<float[]>();
		LineNumberReader lnr = new LineNumberReader(new FileReader(f));
		
		String s = lnr.readLine();
		Pattern p = Pattern.compile("\\s+");
		
		try {
			while (s!=null){
				String[] parts = p.split(s);
				if (parts.length<3) return null;
				float[] c1 = new float[3];
				c1[0] = Float.parseFloat(parts[0]);
				c1[1] = Float.parseFloat(parts[1]);
				c1[2] = Float.parseFloat(parts[2]);
				c.add(c1);
				
				s = lnr.readLine();
			}	
		} catch (NumberFormatException nf){
			return null;
		} finally {
			if (lnr!=null) lnr.close();
		}
		
		return ((float[][]) c.toArray(new float[c.size()][]));
	}

	public static final float[][] loadDefaultColorScheme(String id){
		File colorSchemeFile; 
		if (Configuration.RUN_AS_STICKWARE){
			colorSchemeFile = new File("ColorSchemes", id+".color");
		} else {
			String userHome = System.getProperty("user.home");
			File dir = new File(userHome+"/.AtomViewer/ColorSchemes");
			colorSchemeFile = new File(dir, id+".color");
		}
		if (colorSchemeFile.exists()){
			try {
				return loadColorsFromFile(colorSchemeFile);
			} catch (IOException e) {}
		}
		return null;
	}
	
	public static final void saveColorScheme(float[][] colors, String id){
		File colorSchemeFile; 
		if (Configuration.RUN_AS_STICKWARE){
			File subDir = new File("ColorSchemes");
			if (!subDir.exists()) subDir.mkdir();
			colorSchemeFile = new File(subDir, id+".color");
		} else {
			String userHome = System.getProperty("user.home");
			File dir = new File(userHome+"/.AtomViewer");
			File subDir = new File(dir+"ColorSchemes");
			if (!subDir.exists()) subDir.mkdir();
			colorSchemeFile = new File(subDir, id+".color");
		}
		try {
			saveColorsToFile(colorSchemeFile, colors);
		} catch (IOException e) {}
	}
	
	public static final void saveColorsToFile(File f, float[][] colors) throws IOException {
		PrintWriter pw = new PrintWriter(f);
		for (int i=0; i<colors.length; i++)
			pw.printf("%f %f %f\n", colors[i][0], colors[i][1], colors[i][2]);
		pw.close();
	}
	
}
