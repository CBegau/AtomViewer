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

import java.awt.Color;

import model.Configuration;
import crystalStructures.CrystalStructure;

public class ColorUtils {

	public static float[] HSVToRGB(float ... hsv) {
		assert (hsv.length >= 3);
		float h = hsv[0];
		if (h<0f) h+=1f;
		float s = Math.min(Math.max(hsv[1], 0f), 1f);
		float v = Math.min(Math.max(hsv[2], 0f), 1f);
		
        float[] color = new float[4];
        int i;
        float f, p, q, t;
        if (s == 0f) {
            color[0] = v; color[1] = v; color[2] = v;
            return color;
        }
        
        h *= 6f;
        i = (int)Math.floor(h);
        f = h - i;
        p = v * (1 - s);
        q = v * (1 - s * f);
        t = v * (1 - s * (1 - f));
        switch (i) {
            case 0:
                color[0] = v; color[1] = t; color[2] = p;
                break;
            case 1:
            	color[0] = q; color[1] = v; color[2] = p;
                break;
            case 2:
            	color[0] = p; color[1] = v; color[2] = t;
                break;
            case 3:
            	color[0] = p; color[1] = q; color[2] = v;
                break;
            case 4:
            	color[0] = t; color[1] = p; color[2] = v;
                break;
            default:
            	color[0] = v; color[1] = p; color[2] = q;
                break;
        }
        return color;
    }
	
	
	public static float[] RGBToHSV(float... rgb) {
		float r = Math.min(Math.max(rgb[0], 0f), 1f);
		float g = Math.min(Math.max(rgb[1], 0f), 1f);
		float b = Math.min(Math.max(rgb[2], 0f), 1f);
		
		float min = Math.min(r, Math.min(g, b));
		float max = Math.max(r, Math.max(g, b));

		float delta = max - min;

		if (max == 0f) 
			return new float[] { 0f, 0f, 0f };

		float s = delta / max;
		float h = 0f;
		if (delta == 0f)
			h = 0f;
		else if (rgb[0] == max) h = (rgb[1] - rgb[2]) / delta;
		else if (rgb[1] == max) h = 2 + (rgb[2] - rgb[0]) / delta;
		else h = 4 + (rgb[0] - rgb[1]) / delta;
		
		h *= 60;
		if (h < 0) h += 360;
		return new float[] { h / 360f, s, max };
	}
	
	
	public static float[] RGBToHSB(float ... rgb) {
        float[] hsb = new float[3];
        Color.RGBtoHSB((int)(rgb[0]*255f), (int)(rgb[1]*255f), (int)(rgb[2]*255f), hsb);
        return hsb;
    }
	
	public static float[] HSBToRGB(float ... hsb) {
        float[] rgb = new float[4];
        Color c = new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
        c.getColorComponents(rgb);
        return rgb;
    }
	
	public static Tupel<float[][],Integer> getColorShift(boolean shiftColorsForVTypes, CrystalStructure cs, Vec3 colorShift){
		final int realTypes = Configuration.getCrystalStructure().getNumberOfElements();
		int numEleColors;
		if (shiftColorsForVTypes){
			numEleColors = Configuration.getNumElements()/realTypes;
			if (Configuration.getNumElements()%realTypes!=0) numEleColors++;
		} else numEleColors = realTypes;
		
		//Fill array of color per type and per element
		float[][] colors = new float[numEleColors*cs.getNumberOfTypes()][];
		for (int i=0; i<cs.getNumberOfTypes(); i++){
			for (int j=0; j<numEleColors; j++){
				colors[i*numEleColors+j] = cs.getGLColor(i);
				//Modify colors as given in the colorShift vector
				if (numEleColors>1){
					float[] hsv = ColorUtils.RGBToHSV(cs.getGLColor(i));
					Vec3 hsvVec = new Vec3(hsv[0], hsv[1], hsv[2]);
					float mul = j/(float)(numEleColors-1f);
					hsvVec.add(colorShift.multiplyClone(mul));
					colors[i*numEleColors+j] = ColorUtils.HSVToRGB(hsvVec.asArray());
				}
			}
		}
		return new Tupel<float[][], Integer>(colors, numEleColors);
	}
}
