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

package model.io;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.filechooser.FileFilter;

import common.CommonUtils;
import common.Vec3;
import gui.PrimitiveProperty;
import gui.PrimitiveProperty.FloatProperty;
import model.Atom;
import model.AtomData;
import model.ImportConfiguration;
import model.DataColumnInfo.Component;
import model.Filter;

public class KeyenceFileLoader extends MDFileLoader {

	private FloatProperty zScaling = new FloatProperty("zScaling", "Z-Scaling",
			"Multiplier for the z-axiz", 1f, 0f, 1000f);

	@Override
	public List<PrimitiveProperty<?>> getOptions(){
		ArrayList<PrimitiveProperty<?>> list = new ArrayList<PrimitiveProperty<?>>();
//		list.add(zScaling);
		return list;
	}
	
	@Override
	public String getName() {
		return "Keyence 3D-Image";
	}
	
	@Override
	public AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws Exception {
		ImportDataContainer idc = new ImportDataContainer();
		
		idc.name = f.getName();

			
		BufferedImage im3d = ImageIO.read(f);
		BufferedImage im2d = ImageIO.read(new File(f.getAbsolutePath().replace("_3D", "_2D")));
		
		boolean compressed = f.getName().endsWith(".jpg");
		
			
		idc.boxSizeX.setTo(new Vec3(im3d.getWidth()*(compressed?2:1), 0, 0));
		idc.boxSizeY.setTo(new Vec3(0, im3d.getHeight()*(compressed?2:1), 0));
		idc.boxSizeZ.setTo(new Vec3(0, 0, 256));
		
		idc.pbc[0] = false;
		idc.pbc[1] = false;
		idc.pbc[2] = false;
		
		idc.makeBox();
		
		
		int[] dataColumns = new int[ImportConfiguration.getInstance().getDataColumns().size()];
		
		int depthCol = -1;
		int imageCol = -1;
		
		for (int i = 0; i<dataColumns.length; i++) {
			if (ImportConfiguration.getInstance().getDataColumns().get(i).getId().equals("Depth"))
				depthCol = i;
			if (ImportConfiguration.getInstance().getDataColumns().get(i).getId().equals("Image"))
				imageCol = i;
		}
		
		
		int width = im3d.getWidth();
		int height = im3d.getHeight();
		
		if (compressed) {
			//reduced quality and resolution
			for (int x = 0; x<width;x++) {
				for (int y = 0; y<height;y++) {
					int z = im3d.getRaster().getSample(x, y, 0);
					
					if (z>0) {
						Vec3 pos = new Vec3();
						pos.x = x*2f;
						pos.y = y*2f;
						pos.z = z*zScaling.getValue();

						Atom a = new Atom(pos, x*2*height*2+y*2, (byte)0);
						
						idc.atoms.add(a);
					
						
						if (imageCol != -1)
							idc.dataArrays.get(imageCol).add(im2d.getRaster().getSample(x, y, 0)/255f);
						if (depthCol != -1)
							idc.dataArrays.get(depthCol).add(pos.z/255f);
						
					}
				}
			}
		} else {
			//Uncompressed full res bmp
			for (int x = 0; x<width;x++) {
				for (int y = 0; y<height;y++) {
					int r = im3d.getRaster().getSample(x, y, 0);
					int g = im3d.getRaster().getSample(x, y, 1);
					int b = im3d.getRaster().getSample(x, y, 2);
					
					int z = g<<4 | (r & 0x03)<<2 | (b & 0x03);
					
					if (z>0) {
						Vec3 pos = new Vec3();
						pos.x = x;
						pos.y = y;
						pos.z = z*zScaling.getValue()/16;
						//pos.z = g;
	
						Atom a = new Atom(pos, x*height+y, (byte)0);
						
						idc.atoms.add(a);
					
						
						if (imageCol != -1)
							idc.dataArrays.get(imageCol).add(im2d.getRaster().getSample(x, y, 0)/255f);
						if (depthCol != -1)
							idc.dataArrays.get(depthCol).add(pos.z/255f);
						
					}
				}
			}
		}
		
		//Add the names of the elements to the input
		idc.elementNames.put(1, "Pixel");
		
		idc.maxElementNumber = (byte)1;
		
		return new AtomData(previous, idc);
	}

	
	
	@Override
	public FileFilter getDefaultFileFilter() {
		FileFilter keyenceFileFilterBasic = new FileFilter() {
			@Override
			public String getDescription() {
				return "(ext)keyence file (*_3D.bmp | *_3D.jpg)";
			}
			
			@Override
			public boolean accept(File f) {
				if (f.isDirectory()) return true;
				String name = f.getName();
				if (name.endsWith("_3D.bmp") || name.endsWith("_3D.jpg")){
					return true;
				}
				return false;
			}
		};
		return keyenceFileFilterBasic;
	}
	
	
	@Override
	public String[][] getColumnNamesUnitsFromHeader(File f) throws IOException {		
		return new String[][] {{"Depth","-"}, {"Image","-"}};
	}
	
	@Override
	public Map<String, Component> getDefaultNamesForComponents() {
		HashMap<String, Component> map = new HashMap<String, Component>();
		
		return map;
	}

}
