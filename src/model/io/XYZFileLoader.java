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

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.filechooser.FileFilter;

import common.CommonUtils;
import common.Vec3;
import gui.PrimitiveProperty;
import gui.PrimitiveProperty.BooleanProperty;
import model.Atom;
import model.AtomData;
import model.ImportConfiguration;
import model.DataColumnInfo.Component;
import model.Filter;

public class XYZFileLoader extends MDFileLoader {

	private BooleanProperty autoAtomNumbers = new BooleanProperty("autoNumbers", "Assign atom number",
			"Generate a number for each atom in the order they appear in the file. Otherwise each atom is assigned 0", false);

	@Override
	public List<PrimitiveProperty<?>> getOptions(){
		ArrayList<PrimitiveProperty<?>> list = new ArrayList<PrimitiveProperty<?>>();
		list.add(autoAtomNumbers);
		return list;
	}
	
	@Override
	public String getName() {
		return "(ext.) xyz";
	}
	
	@Override
	public AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws Exception {
		String line = null;
		
		ImportDataContainer idc = new ImportDataContainer();
		Pattern p = Pattern.compile("\\s+");
		
		Map<String,Integer> typeMap = new TreeMap<String, Integer>(); 
		
		idc.name = f.getName();
		
		int atomNumber = 0;
		
		boolean gzipped = CommonUtils.isFileGzipped(f);
		FileInputStream fis = new FileInputStream(f);
		BufferedReader inputReader = CommonUtils.createBufferedReader(fis, gzipped);
		
		try {
			boolean extendedFormat = false;
			line = inputReader.readLine(); // Number of atoms -> don't care
			line = inputReader.readLine(); // Actual header
			
			Map<String, String> map = readKeyValuesFromHeader(line);
			if (map != null) extendedFormat = true;
			
			if (extendedFormat){
				
				int[] dataColumns = new int[ImportConfiguration.getInstance().getDataColumns().size()];
				for (int i = 0; i<dataColumns.length; i++)
					dataColumns[i] = -1;
				
				Vec3[] lattice = splitLattice(map.get("Lattice"));
				idc.boxSizeX.setTo(lattice[0]);
				idc.boxSizeY.setTo(lattice[1]);
				idc.boxSizeZ.setTo(lattice[2]);
				
				if (map.containsKey("pbc")){
					boolean[] pbc = splitPBC(map.get("pbc"));
					idc.pbc[0] = pbc[0];
					idc.pbc[1] = pbc[1];
					idc.pbc[2] = pbc[2];
				}
				
				idc.makeBox();
				
				String[][] properties = splitProperties(map.get("Properties"));
				
				for (int j = 0; j<properties.length; j++){
					for (int i = 0; i<ImportConfiguration.getInstance().getDataColumns().size(); i++){
					if (properties[j][0].equals(ImportConfiguration.getInstance().getDataColumns().get(i).getId()))
						dataColumns[i] = j + 4;
					}
				}
				
				while ( (line = inputReader.readLine())!=null && !line.isEmpty()){
					String[] parts = p.split(line);
					String element = parts[0];
					int ele = 0;
					
					if (!typeMap.containsKey(element))
						typeMap.put(element, typeMap.size());
					
					ele = typeMap.get(element);
					Vec3 pos = new Vec3();
					pos.x = Float.parseFloat(parts[1]);
					pos.y = Float.parseFloat(parts[2]);
					pos.z = Float.parseFloat(parts[3]);
					
					idc.box.backInBox(pos);
					
					Atom a = new Atom(pos, atomNumber, (byte)ele);
					if (autoAtomNumbers.getValue()) atomNumber++;
					
					if (atomFilter == null || atomFilter.accept(a)){
						idc.atoms.add(a);
						//Custom columns
						for (int j = 0; j<dataColumns.length; j++){
							if (dataColumns[j] != -1)
								idc.dataArrays.get(j).add(Float.parseFloat(parts[dataColumns[j]]));
						}
					}
				}
				
				
			} else {
				Vec3 box = new Vec3();
				
				while ( (line = inputReader.readLine())!=null){
					String[] parts = p.split(line);
					String element = parts[0];
					int ele = 0;
					
					if (!typeMap.containsKey(element))
						typeMap.put(element, typeMap.size());
					
					ele = typeMap.get(element);
					Vec3 pos = new Vec3();
					pos.x = Float.parseFloat(parts[1]);
					pos.y = Float.parseFloat(parts[2]);
					pos.z = Float.parseFloat(parts[3]);
					
					Atom a = new Atom(pos, atomNumber, (byte)ele);
					if (autoAtomNumbers.getValue()) atomNumber++;
					idc.atoms.add(a);
				}
				
				if (!extendedFormat){
					Vec3 offset = new Vec3();
					for (Atom a : idc.atoms){
						if (a.x > box.x) box.x = a.x;
						if (a.y > box.y) box.y = a.y;
						if (a.z > box.z) box.z = a.z;
						
						if (a.x<offset.x) offset.x = a.x;
						if (a.y<offset.y) offset.y = a.y;
						if (a.z<offset.z) offset.z = a.z;
					}
					
					for (Atom a : idc.atoms){
						a.sub(offset);
					}
					box.sub(offset);
					
					idc.offset.setTo(offset);
					idc.boxSizeX.x = box.x;
					idc.boxSizeY.y = box.y;
					idc.boxSizeZ.z = box.z;
				}
				
				idc.makeBox();
			}
			
		} finally {
			inputReader.close();
		}
		
		//Add the names of the elements to the input
		for (String s : typeMap.keySet()){
			idc.elementNames.put(typeMap.get(s), s);
		}
		idc.maxElementNumber = (byte)(typeMap.size());
		
		return new AtomData(previous, idc);
	}

	@Override
	public String[][] getColumnNamesUnitsFromHeader(File f) throws IOException {
		boolean gzipped = CommonUtils.isFileGzipped(f);
		FileInputStream fis = new FileInputStream(f);
		BufferedReader inputReader = CommonUtils.createBufferedReader(fis, gzipped);
		
		String header = null;
		
		try {
			header = inputReader.readLine(); // Number of atoms -> don't care
			header = inputReader.readLine(); // Actual header
		} finally {
			inputReader.close();
		}
		
		Map<String, String> map = readKeyValuesFromHeader(header);
		if (map == null) return new String[0][];
		
		String[][] components = splitProperties(map.get("Properties"));
		
		return components;
	}
	
	@Override
	public FileFilter getDefaultFileFilter() {
		FileFilter lammpsFileFilterBasic = new FileFilter() {
			@Override
			public String getDescription() {
				return "(ext)xyz file (*.xyz, *.extxyz)";
			}
			
			@Override
			public boolean accept(File f) {
				if (f.isDirectory()) return true;
				String name = f.getName();
				if (name.endsWith(".xyz") || name.endsWith(".extxyz")){
					return true;
				}
				return false;
			}
		};
		return lammpsFileFilterBasic;
	}
	
	private HashMap<String, String> readKeyValuesFromHeader(String s) throws IOException{
		//Split string at whitespaces that are not enclosed by quotes ("") and at "="
		Pattern p = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

		Matcher m = p.matcher(s);
		HashMap<String, String> keyValuePairs = new HashMap<String, String>();
		
		while(m.find()){
			keyValuePairs.put(m.group(1), m.group(2));
		}
		
		if (keyValuePairs.containsKey("Lattice") && keyValuePairs.containsKey("Properties"))
			return keyValuePairs;
		else return null;	
	}
	
	private String[][] splitProperties(String properties){
		ArrayList<String[]> components = new ArrayList<String[]>();
		Pattern p = Pattern.compile(":");
		
		String parts[] = p.split(properties);
		
		for (int i=0; i<parts.length; i+=3){
			if (parts[i+1].equals("S")) continue;
			if (parts[i+0].equals("pos")) continue;
			
			if (parts[i+1].equals("I") || parts[i+1].equals("R")){
				int num = Integer.parseInt(parts[i+2]); 
				if (num>1){
					for (int j=0; j<num; j++)
						components.add(new String[]{parts[i]+"_"+j, ""});
				} else {
					components.add(new String[]{parts[i], ""});
				}
			}
		}
		
		return components.toArray(new String[components.size()][]);
	}
	
	private Vec3[] splitLattice(String lattice){
		Pattern p = Pattern.compile("\\s+");
		
		String parts[] = p.split(lattice);
		
		Vec3[] box = new Vec3[3];
		box[0] = new Vec3();
		box[1] = new Vec3();
		box[2] = new Vec3();
		
		box[0].x = Float.parseFloat(parts[0]);
		box[0].y = Float.parseFloat(parts[1]);
		box[0].z = Float.parseFloat(parts[2]);
		
		box[1].x = Float.parseFloat(parts[3]);
		box[1].y = Float.parseFloat(parts[4]);
		box[1].z = Float.parseFloat(parts[5]);
		
		box[2].x = Float.parseFloat(parts[6]);
		box[2].y = Float.parseFloat(parts[7]);
		box[2].z = Float.parseFloat(parts[8]);
		
		return box;
	}
	
	private boolean[] splitPBC(String pbc){
		Pattern p = Pattern.compile("\\s+");
		
		String parts[] = p.split(pbc);
		
		boolean[] pbcs = new boolean[3];
		pbcs[0] = parts[0].equals("T");
		pbcs[1] = parts[1].equals("T");
		pbcs[2] = parts[2].equals("T");
		
		return pbcs;
	}

	@Override
	public Map<String, Component> getDefaultNamesForComponents() {
		HashMap<String, Component> map = new HashMap<String, Component>();
		
		return map;
	}

}
