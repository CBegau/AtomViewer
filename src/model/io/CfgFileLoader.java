package model.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.swing.filechooser.FileFilter;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import common.CommonUtils;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.Filter;
import model.ImportConfiguration;
import model.DataColumnInfo.Component;

public class CfgFileLoader extends MDFileLoader {

	@Override
	public AtomData readInputData(File f, AtomData previous) throws Exception {
		LineNumberReader lnr = null;
		if (CommonUtils.isFileGzipped(f)) {
			// Directly read gzip-compressed files
			lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
		} else lnr = new LineNumberReader(new FileReader(f));
		
		CFGHeader header = new CFGHeader();
		header.readHeader(f);
		
		ImportDataContainer idc = new ImportDataContainer();
		
		try{
			
			idc.fullPathAndFilename = f.getCanonicalPath();
			idc.name = f.getName();
			Vec3[] boxVec = header.getBoxVectors();
			idc.boxSizeX = boxVec[0];
			idc.boxSizeY = boxVec[1];
			idc.boxSizeZ = boxVec[2];
			idc.makeBox();
			
			Pattern p = Pattern.compile("\\s+");
			String s = lnr.readLine();
			
			//The end of the header of cfg-files is not well defined
			//The first line that does start with a numeric entry is already an atom
			//Thus try to read until the first starting string is numeric
			boolean headerRead = false;
			int totalAtoms = 0;
			int atomsRead = 0;
			float currentMass = 0f;
			Map<String,Integer> typeMap = new TreeMap<String, Integer>();
			int currentType = 0;
			
			Filter<Atom> atomFilter = ImportConfiguration.getInstance().getCrystalStructure().getIgnoreAtomsDuringImportFilter();
			
			int[] dataColumns = new int[ImportConfiguration.getInstance().getDataColumns().size()];
			for (int i = 0; i<dataColumns.length; i++)
				dataColumns[i] = -1;
			
			int massColumn = -1;
			
			while(!headerRead && s != null){
				String[] parts = p.split(s);
				if (parts.length != 0 && !parts[0].startsWith("#")){
					if (parts[0].equals("Number"))
						totalAtoms = Integer.parseInt(parts[4]);
					
					//A numeric value indicates the end of the header and the first mass;
					if (CommonUtils.isStringNumeric(parts[0])) {
						headerRead = true;
						break;
					}
				}
				s = lnr.readLine();
			}
			if (!headerRead){
				throw new Exception("File seems to be broken");
			}
			
			for (int j = 0; j<ImportConfiguration.getInstance().getDataColumns().size(); j++){
				for (int i=0; i<header.valuesUnits.length;i++)
					if (header.valuesUnits[i][0].equals(ImportConfiguration.getInstance().getDataColumns().get(j).getId()))
						dataColumns[j] = i + 3 + (header.isExtended?0:2);
				if (ImportConfiguration.getInstance().getDataColumns().get(j).getComponent() == Component.MASS){
					massColumn = j;
					dataColumns[j] = -1;
				}
			}
			
			
			if (header.isExtended){
				//Extended CFG-format
				while (s != null){
					String[] parts = p.split(s);
					if (parts.length == 1){
						currentMass = Float.parseFloat(parts[0]);
						s = lnr.readLine();
						String element = s.trim();
						if (!typeMap.containsKey(element))
							typeMap.put(element, typeMap.size());
						currentType = typeMap.get(element);
					} else {
						Vec3 pos = new Vec3();
						pos.x = Float.parseFloat(parts[0]);
						pos.y = Float.parseFloat(parts[1]);
						pos.z = Float.parseFloat(parts[2]);
						
						Vec3 xyzPos = new Vec3();
						xyzPos.x = pos.dot(boxVec[0]);
						xyzPos.y = pos.dot(boxVec[1]);
						xyzPos.z = pos.dot(boxVec[2]);
						
						idc.box.backInBox(xyzPos);
						
						Atom a = new Atom(xyzPos, (byte)0, 0, (byte)currentType);
						atomsRead++;
						//Custom columns
						for (int j = 0; j<dataColumns.length; j++){
							if (dataColumns[j] != -1)
								a.setData(Float.parseFloat(parts[dataColumns[j]]), j);
						}
						if (massColumn != -1){
							a.setData(currentMass, massColumn);
						}
						
						if (atomFilter == null || atomFilter.accept(a)){
							idc.atoms.add(a);
						}
						
					}
				
					s = lnr.readLine();
				}
			} else {
				//Standard CFG-format
				while (s != null){
					String[] parts = p.split(s);
					
					String element = parts[1];
					if (!typeMap.containsKey(element))
						typeMap.put(element, typeMap.size());
					int type = typeMap.get(element);
					Vec3 pos = new Vec3();
					pos.x = Float.parseFloat(parts[2]);
					pos.y = Float.parseFloat(parts[3]);
					pos.z = Float.parseFloat(parts[4]);
					
					Vec3 xyzPos = new Vec3();
					xyzPos.x = pos.dot(boxVec[0]);
					xyzPos.y = pos.dot(boxVec[1]);
					xyzPos.z = pos.dot(boxVec[2]);
					
					//Put atoms back into the simulation box, they might be slightly outside
					idc.box.backInBox(xyzPos);
					
					Atom a = new Atom(xyzPos, (byte)0, 0, (byte)type);
					atomsRead++;
					//Custom columns
					for (int j = 0; j<dataColumns.length; j++){
						if (dataColumns[j] != -1)
							a.setData(Float.parseFloat(parts[dataColumns[j]]), j);
					}
					if (massColumn != -1){
						a.setData(Float.parseFloat(parts[0]), massColumn);
					}
					
					if (atomFilter == null || atomFilter.accept(a)){
						idc.atoms.add(a);
					}
					
					s = lnr.readLine();
				}
			}
			
			//Add the names of the elements to the input
			for (String st : typeMap.keySet()){
				idc.elementNames.put(typeMap.get(st), st);
			}
			idc.maxElementNumber = (byte)typeMap.size();
			
			if (totalAtoms != atomsRead){
				throw new Exception("File broken: Number of atoms read is not equal to the number of atoms defined in the header.");
			}
			
		} catch (IOException e){
			header.valuesUnits = new String[0][];
			throw e;
		} finally {
			lnr.close();
		}
		
		return new AtomData(previous, idc);
	}

	@Override
	public FileFilter getDefaultFileFilter() {
		FileFilter cfgFileFilterBasic = new FileFilter() {
			@Override
			public String getDescription() {
				return "(Extended) CFG-Files (*.cfg)";
			}
			
			@Override
			public boolean accept(File f) {
				if (f.isDirectory()) return true;
				String name = f.getName();
				if (name.endsWith(".cfg") || name.endsWith(".cfg.gz")){
					return true;
				}
				return false;
			}
		};
		return cfgFileFilterBasic;
	}

	@Override
	public String[][] getColumnNamesUnitsFromHeader(File f) throws IOException {
		CFGHeader header = new CFGHeader();
		header.readHeader(f);
		return header.valuesUnits;
	}

	@Override
	public String getName() {
		return "(ext.) CFG";
	}

	@Override
	public Map<String, Component> getDefaultNamesForComponents() {
		HashMap<String, Component> map = new HashMap<String, Component>();
		map.put("vx", Component.VELOCITY_X);
		map.put("vy", Component.VELOCITY_Y);
		map.put("vz", Component.VELOCITY_Z);
		map.put("mass", Component.MASS);
		return map;
	}

	private class CFGHeader {
		Matrix h0 = Matrix.identity(3, 3);
		Matrix transform = Matrix.identity(3, 3);
		Matrix eta = new Matrix(3, 3, 0);
		
		boolean isExtended = false;
		boolean hasVelocity = true;
		
		String[][] valuesUnits;
		
		Vec3[] getBoxVectors(){
			Matrix h = h0.times(transform);
			Matrix m = Matrix.identity(3, 3).plus(eta.times(2.));
			EigenvalueDecomposition evd = m.eig();
			Matrix d = evd.getD();
			for (int i=0; i<3;i++)
				d.set(i, i, Math.sqrt(d.get(i, i)));
			Matrix v = evd.getV();
			m = v.times(d).times(v.inverse());
			h = h.times(m);
			
			Vec3[] box = new Vec3[3];
			box[0] = new Vec3((float)h.get(0,0), (float)h.get(0,1), (float)h.get(0,2));
			box[1] = new Vec3((float)h.get(1,0), (float)h.get(1,1), (float)h.get(1,2));
			box[2] = new Vec3((float)h.get(2,0), (float)h.get(2,1), (float)h.get(2,2));
			
			return box;
		}
		
		void readHeader(File f) throws IOException{
			LineNumberReader lnr = null;
			if (CommonUtils.isFileGzipped(f)) {
				// Directly read gzip-compressed files
				lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
			} else lnr = new LineNumberReader(new FileReader(f));

			try{
				Pattern p = Pattern.compile("[=\\s]+");
				String s = lnr.readLine();
				
				//The end of the header of cfg-files is not well defined
				//The first line that does start with a numeric entry is already an atom
				//Thus try to read until the first starting string is numeric
				boolean headerRead = false;
				while(!headerRead && s != null){
					String[] parts = p.split(s);
					if (parts.length != 0){
						if (parts[0].startsWith("#")){
							//Comment, do nothing
						}
						//A numeric value indicates the end of the header
						else if (CommonUtils.isStringNumeric(parts[0])) {
							headerRead = true;
							break;
						}
						else if (parts[0].equals(".NO_VELOCITY.")){
							this.hasVelocity = false;
							this.isExtended = true;
						}
						else if (parts[0].equals("entry_count")){
							this.isExtended = true;
							int entries = Integer.parseInt(parts[1]);
							this.valuesUnits = new String[entries-3 + 1][];
							if (this.hasVelocity){
								this.valuesUnits[0] = new String[]{"vx", ""};
								this.valuesUnits[1] = new String[]{"vy", ""};
								this.valuesUnits[2] = new String[]{"vz", ""};
							}
							
							this.valuesUnits[this.valuesUnits.length-1] = new String[]{"mass", ""};
						}
						else if (parts[0].startsWith("auxiliary")){
							String ss = parts[0].substring(10,parts[0].length()-1);
							int index = Integer.parseInt(ss);
							if (this.hasVelocity) index+=3;
							String unit = "";
							if (parts.length>2){
								//Cut away the enclosing brackets [unit]
								unit = parts[2].substring(1, parts[2].length()-1);
							}
							this.valuesUnits[index] = new String[]{parts[1], unit};
						}
						else if (parts[0].startsWith("H0")){
							int index1 = Integer.parseInt(parts[0].substring(3,4)) - 1;
							int index2 = Integer.parseInt(parts[0].substring(5,6)) - 1;
							this.h0.set(index1, index2, Double.parseDouble(parts[1]));
						}
						else if (parts[0].startsWith("eta")){
							int index1 = Integer.parseInt(parts[0].substring(4,5)) - 1;
							int index2 = Integer.parseInt(parts[0].substring(6,7)) - 1;
							this.eta.set(index1, index2, Double.parseDouble(parts[1]));
							this.eta.set(index2, index1, Double.parseDouble(parts[1]));
						}
						else if (parts[0].startsWith("Transform")){
							int index1 = Integer.parseInt(parts[0].substring(10,11)) - 1;
							int index2 = Integer.parseInt(parts[0].substring(12,13)) - 1;
							this.transform.set(index1, index2, Double.parseDouble(parts[1]));
						}
						
					}
					
					s = lnr.readLine();
				}
				
				if (headerRead && !this.isExtended){
					this.valuesUnits = new String[4][];
					this.valuesUnits[0] = new String[]{"vx", ""};
					this.valuesUnits[1] = new String[]{"vy", ""};
					this.valuesUnits[2] = new String[]{"vz", ""};
					this.valuesUnits[3] = new String[]{"mass", ""};
				}
				
			} catch (IOException e){
				this.valuesUnits = new String[0][];
				throw e;
			} finally {
				lnr.close();
			}
		}
	}
	
}
