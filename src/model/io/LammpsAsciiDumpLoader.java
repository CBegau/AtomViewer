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

package model.io;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.swing.filechooser.FileFilter;

import common.CommonUtils;
import common.Vec3;
import model.*;
import model.DataColumnInfo.Component;

public class LammpsAsciiDumpLoader extends MDFileLoader {
	
	@Override
	public String getName() {
		return "Lammps dump (ascii)";
	}
	
	@Override
	public AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws IOException{
		return readFile(f, previous, atomFilter);
	}
	
	@Override
	public FileFilter getDefaultFileFilter() {
		FileFilter lammpsFileFilterBasic = new FileFilter() {
			@Override
			public String getDescription() {
				return "Lammps ASCII dump file (*.dump, *.*)";
			}
			
			@Override
			public boolean accept(File f) {
				return true;
			}
		};
		return lammpsFileFilterBasic;
	}
	
	@Override
	public String[][] getColumnNamesUnitsFromHeader(File f) throws IOException {
		LineNumberReader lnr = null;
		if (CommonUtils.isFileGzipped(f)) {
			// Directly read gzip-compressed files
			lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
		} else lnr = new LineNumberReader(new FileReader(f));
		
		ArrayList<String[]> values = new ArrayList<String[]>();
		try{
			Pattern p = Pattern.compile("\\s+");
			String s = lnr.readLine();
			
			boolean headerRead = false;
			
			while (!headerRead && s != null) {
				if (s.contains("ITEM: ATOMS")) {
					headerRead = true;
					String[] parts = p.split(s);
					for (int i = 2; i < parts.length; i++) {
						values.add(new String[]{parts[i],""});
					}
				}
				s = lnr.readLine();
			}
		} catch (IOException e){
			values.clear();
			throw e;
		} finally {
			lnr.close();
		}
		
		ArrayList<String[]> filteredValues = new ArrayList<String[]>();
		for (String[] v : values){
			String s = v[0];
			if (!s.equals("id") && !s.equals("type")
					&& !s.equals("x") && !s.equals("y") && !s.equals("z")
					&& !s.equals("xu") && !s.equals("yu") && !s.equals("zu")
					&& !s.equals("xs") && !s.equals("ys") && !s.equals("zs")
					&& !s.equals("xsu") && !s.equals("ysu") && !s.equals("zsu"))
				filteredValues.add(v);
		}
		
		return filteredValues.toArray(new String[filteredValues.size()][]);
	}
	
	
	/**
	 * Read lammps-files, only ASCII format
	 * @param f File to read
	 * @throws IOException
	 * @throws IllegalAccessException
	 */
	private AtomData readFile(File f, AtomData previous, Filter<Atom> atomFilter) throws IOException {
		LineNumberReader lnr = null;
		if (CommonUtils.isFileGzipped(f)) {
			// Directly read gzip-compressed files
			lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
		} else lnr = new LineNumberReader(new FileReader(f));

		int elementColumn = -1;
		int xColumn = -1;
		int numberColumn = -1;
		boolean scaledCoords = false;

		int[] dataColumns = new int[ImportConfiguration.getInstance().getDataColumns().size()];
		for (int i = 0; i<dataColumns.length; i++)
			dataColumns[i] = -1;

		Pattern p = Pattern.compile("\\s+");

		ImportDataContainer idc = new ImportDataContainer();
		try {
			int num=0;
			String s = lnr.readLine();
			while (s != null) {
				idc.fullPathAndFilename = f.getCanonicalPath();
				idc.name = String.format("%s.%05d", f.getName(), num++);
				
				boolean headerRead = false;
				int atomsCount = 0;

				idc.fileMetaData = new HashMap<String, Object>();
				while (!headerRead) {
					if (s.contains("ITEM: NUMBER OF ATOMS")) {
						s = lnr.readLine();
						atomsCount = Integer.parseInt(s);
						s = lnr.readLine();
					} else if (s.contains("ITEM: BOX BOUNDS")) {
						String[] parts = p.split(s);
						boolean triclinic = false;
						float xy = 0f, xz = 0f, yz = 0f; 
						if (parts.length >= 6){
							if (parts.length >= 9){
								//Triclinic box
								if (parts[3].equals("xy"))
									triclinic = true;
								idc.pbc[0] = parts[6].startsWith("p");
								idc.pbc[1] = parts[7].startsWith("p");
								idc.pbc[2] = parts[8].startsWith("p");
							} else {
								idc.pbc[0] = parts[3].startsWith("p");
								idc.pbc[1] = parts[4].startsWith("p");
								idc.pbc[2] = parts[5].startsWith("p");
							}
						}
						
						s = lnr.readLine();
						parts = p.split(s);
						float min = Float.parseFloat(parts[0]);
						float max = Float.parseFloat(parts[1]);
						if(triclinic) xy = Float.parseFloat(parts[2]);
						idc.offset.x = min;
						idc.boxSizeX.x = max - min;

						s = lnr.readLine();
						parts = p.split(s);
						min = Float.parseFloat(parts[0]);
						max = Float.parseFloat(parts[1]);
						if(triclinic) xz = Float.parseFloat(parts[2]);
						idc.offset.y = min;
						idc.boxSizeY.x = xy;
						idc.boxSizeY.y = max - min;
						
						s = lnr.readLine();
						parts = p.split(s);
						min = Float.parseFloat(parts[0]);
						max = Float.parseFloat(parts[1]);
						if(triclinic) yz = Float.parseFloat(parts[2]);
						idc.offset.z = min;
						idc.boxSizeZ.x = xz;
						idc.boxSizeZ.y = yz;
						idc.boxSizeZ.z = max - min;
						s = lnr.readLine();
						
						idc.makeBox();
					} else if (s.contains("ITEM: ATOMS")) {
						headerRead = true;
						String[] parts = p.split(s);
						for (int i = 0; i < parts.length; i++) {
							if (parts[i].equals("id")) numberColumn = i - 2;
							if (parts[i].equals("x") || parts[i].equals("xu")) xColumn = i - 2;
							if (parts[i].equals("xs") || parts[i].equals("xsu")){
								xColumn = i - 2;
								scaledCoords = true;
							}
							if (parts[i].equals("type")) elementColumn = i - 2;
							
							for (int j = 0; j<ImportConfiguration.getInstance().getDataColumns().size(); j++){
								if (parts[i].equals(ImportConfiguration.getInstance().getDataColumns().get(j).getId()))
									dataColumns[j] = i - 2;
							}
						}
						s = lnr.readLine();
					} else if (s.startsWith("ITEM:")){
						try{ // Try reading additional lines, read each line until the next item
							//and put everything into an array of floats
							String label = s.substring(6);
							ArrayList<Float> values = new ArrayList<Float>();
							s = lnr.readLine();
							while (!s.startsWith("ITEM:")){
								String[] parts = p.split(s);
								
								for (int i=0; i<parts.length; i++){
									values.add(Float.parseFloat(parts[i]));
								}
								s = lnr.readLine();
							}
							float[] tmp = new float[values.size()];
							for (int i=0; i<values.size(); i++)
								tmp[i] = values.get(i); 
							
							idc.fileMetaData.put(label.toLowerCase(), tmp);
						} catch (Exception e) {
						} finally{
							while(!s.startsWith("ITEM:")){
								s = lnr.readLine();
							}
						}
					}
					//Nothing done, process next line
					else s = lnr.readLine();
				}
				
				if (xColumn == -1) throw new IllegalArgumentException("Broken header, no coordinates x y z");

				if (idc.boxSizeX.x <= 0f || idc.boxSizeY.y <= 0f || idc.boxSizeZ.z <= 0f) {
					throw new IllegalArgumentException("Broken header, box sizes must be larger than 0");
				}

				Vec3 pos = new Vec3();
				byte element = 0;
				int number = 0;

				for (int i = 0; i < atomsCount; i++) {
					s = s.trim();
					String[] parts = p.split(s);

					if (elementColumn != -1) {
						element = (byte)Integer.parseInt(parts[elementColumn]);
						if (element + 1 > idc.maxElementNumber) idc.maxElementNumber = (byte)(element + 1);
					}

					if (numberColumn != -1)
						number = Integer.parseInt(parts[numberColumn]);

					if (scaledCoords){
						pos.x = Float.parseFloat(parts[xColumn + 0])*idc.boxSizeX.x - idc.offset.x;
						pos.y = Float.parseFloat(parts[xColumn + 1])*idc.boxSizeY.y - idc.offset.y;
						pos.z = Float.parseFloat(parts[xColumn + 2])*idc.boxSizeZ.z - idc.offset.z;
					} else {
						pos.x = Float.parseFloat(parts[xColumn + 0]) - idc.offset.x;
						pos.y = Float.parseFloat(parts[xColumn + 1]) - idc.offset.y;
						pos.z = Float.parseFloat(parts[xColumn + 2]) - idc.offset.z;
					}
					
					//Put atoms back into the simulation box, they might be slightly outside
					idc.box.backInBox(pos);

					Atom a = new Atom(pos, number, element);

					//Custom columns
					for (int j = 0; j<dataColumns.length; j++){
						if (dataColumns[j] != -1)
							a.setData(Float.parseFloat(parts[dataColumns[j]]), j);
					}
					
					if (atomFilter == null || atomFilter.accept(a)){
						idc.atoms.add(a);
					}
					
					s = lnr.readLine();
				}
				previous = new AtomData(previous, idc);
				idc = new ImportDataContainer();
			}
		} catch (IOException ex) {
			throw ex;
		} finally {
			lnr.close();
		}
		return previous;
	}
	
	@Override
	public Map<String, Component> getDefaultNamesForComponents() {
		HashMap<String, Component> map = new HashMap<String, Component>();
		map.put("vx", Component.VELOCITY_X);
		map.put("vy", Component.VELOCITY_Y);
		map.put("vz", Component.VELOCITY_Z);
		map.put("mass", Component.MASS);
		map.put("c_EPe", Component.E_POT);
		
		map.put("fx", Component.FORCE_X);
		map.put("fy", Component.FORCE_Y);
		map.put("fz", Component.FORCE_Z);
		
		map.put("v_s11", Component.STRESS_XX);
		map.put("v_s22", Component.STRESS_YY);
		map.put("v_s33", Component.STRESS_ZZ);
		
		return map;
	}
}
	
	
	
