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

import gui.PrimitiveProperty.*;
import gui.PrimitiveProperty;
import gui.ProgressMonitor;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

import javax.swing.filechooser.FileFilter;

import common.ByteArrayReader;
import common.CommonUtils;
import common.FastTFloatArrayList;
import common.Vec3;
import crystalStructures.PolygrainMetadata;
import model.*;
import model.DataColumnInfo.Component;
import model.ImportConfiguration.ImportStates;

public class ImdFileLoader extends MDFileLoader{
	
	private BooleanProperty importGrains = new BooleanProperty("importGrains", "Import Grains", "", false);
	private BooleanProperty importRBV = new BooleanProperty("importRBV", "Import Burgers Vectors", 
			"Import Burgers vectors from input file", false);
	private BooleanProperty importTypes = new BooleanProperty("importTypes", "Import atom types from file", 
			"If enable, atomic classification are read from file, if available.", false);
	
	@Override
	public String getName() {
		return "IMD";
	}
	
	@Override
	public List<PrimitiveProperty<?>> getOptions(){
		ArrayList<PrimitiveProperty<?>> list = new ArrayList<PrimitiveProperty<?>>();
		list.add(importTypes);
		list.add(importRBV);
		list.add(importGrains);
		return list;
	}
	
	@Override
	public String[][] getColumnNamesUnitsFromHeader(File f) throws IOException {
		final boolean gzipped = CommonUtils.isFileGzipped(f);
		FileInputStream fis = new FileInputStream(f);
		BufferedReader inputReader = CommonUtils.createBufferedReader(fis, gzipped);
		
		ArrayList<String[]> filteredValues = new ArrayList<String[]>();
		try{
			ArrayList<String[]> values = new IMD_Header().readValuesFromHeader(inputReader);
			
			for (String[] v : values){
				String s = v[0];
				if (!s.equals("number") && !s.equals("type") && !s.equals("rbv_data")
						&& !s.equals("rbv_x") && !s.equals("rbv_y") && !s.equals("rbv_z")
						&& !s.equals("ls_x") && !s.equals("ls_y") && !s.equals("ls_z")
						&& !s.equals("x") && !s.equals("y") && !s.equals("z")
						&& !s.equals("grain") && !s.equals("ada_type")
						&& !s.equals("grainID") && !s.equals("struct_type"))
					filteredValues.add(v);
			}
		} finally{
			inputReader.close();
		}
		return filteredValues.toArray(new String[filteredValues.size()][]);
	}
	
	@Override
	public AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws Exception{
		Filter<Atom> af = atomFilter;
		//Dispose perfect lattice atoms during import
		if (ImportStates.DISPOSE_DEFAULT.isActive()){ 
			Filter<Atom> defaultAtomFilter = new Filter<Atom>() {
				final int defaultType = ImportConfiguration.getInstance().getCrystalStructure().getDefaultType();
				@Override
				public boolean accept(Atom a) {
					return a.getType() != defaultType;
				}
			};
			af = new FilterSet<Atom>().addFilter(atomFilter).addFilter(defaultAtomFilter);
		}
		
		return new AtomData(previous, this.readFile(f, af));
	}
	
	/**
	 * Read IMD-files, both ASCII and binary versions are supported
	 * @param f File to read
	 * @throws IOException
	 */
	protected ImportDataContainer readFile(File f, final Filter<Atom> atomFilter) throws IOException{
		ProgressMonitor.getProgressMonitor().setActivityName("Reading file");		
		final ImportDataContainer idc = new ImportDataContainer();
		idc.name = f.getName();
		idc.fullPathAndFilename = f.getCanonicalPath();
		
		final boolean gzipped = CommonUtils.isFileGzipped(f);
		Filter<Atom> filter = atomFilter;
		if (atomFilter==null)
            filter = new Filter.AcceptAllFilter<Atom>();
		
		//Reading the header
		final IMD_Header header = new IMD_Header();
		header.readHeader(f, idc, gzipped);
		
		if (f.getPath().endsWith(".head") || f.getPath().endsWith(".head.gz"))
			header.multiFileInput = true;

		FileInputStream fis = new FileInputStream(f);
		BufferedReader inputReader = CommonUtils.createBufferedReader(fis, gzipped);
		try{
			if (header.format.equals("A")){	//Ascii-Format
				if (header.multiFileInput){
					inputReader.close();
					
					int fileNumber = 0;
					File nextFile = null;
					do {
						String nextFilename = f.getAbsolutePath();
						if (gzipped)	//remove ".head.gz"
							nextFilename = nextFilename.substring(0, nextFilename.length()-8);
						else //remove ".head"
							nextFilename = nextFilename.substring(0, nextFilename.length()-5);
						
						nextFilename += "."+fileNumber;
						if (gzipped) nextFilename += ".gz";
						nextFile = new File(nextFilename);
						if (nextFile.exists()){
							try{
								readASCIIFile(nextFile, idc, gzipped, header, filter);
								fileNumber++;
							} finally {
								if (inputReader!=null) inputReader.close();	
							}
						}
					} while (nextFile.exists());
				} else
					readASCIIFile(f, idc, gzipped, header, filter);
			}
			//binary formats
			else if (header.format.equals("l") || header.format.equals("b") || 
					header.format.equals("L") || header.format.equals("B")){
				if (header.multiFileInput){
					inputReader.close();
					
					int fileNumber = 0;
					File nextFile = null;
					
					do {
						String nextFilename = f.getAbsolutePath();
						if (gzipped)	//remove ".head.gz"
							nextFilename = nextFilename.substring(0, nextFilename.length()-8);
						else //remove ".head"
							nextFilename = nextFilename.substring(0, nextFilename.length()-5);
						
						nextFilename += "."+fileNumber;
						if (gzipped) nextFilename += ".gz";
						nextFile = new File(nextFilename);
						if (nextFile.exists()){
							readBinaryFile(nextFile, idc, gzipped, header, filter);							
							fileNumber++;
						}
					} while (nextFile.exists());
				} else
					readBinaryFile(f, idc, gzipped, header, filter);
			}
			else throw new IllegalArgumentException("File format not supported");
		}catch (IOException ex){
			throw ex;
		} finally {
			inputReader.close();
		}
		return idc;
	}

	private void readBinaryFile(File f, ImportDataContainer idc, boolean gzipped, IMD_Header header, Filter<Atom> atomFilter)
            throws IOException {
        
	    //Setup source InputStream and identify filesize
        FileInputStream fis = new FileInputStream(f);
        InputStream is = fis;
        long filesize = 0l;
        
        //Get the file size. For non-compressed file read directly from the file channel
        if (!gzipped){
            filesize = fis.getChannel().size();
        } else {
            filesize = CommonUtils.getGzipFilesize(f);
            try {
                is = new GZIPInputStream(fis, 16384*64);  //Add gzip decompressor
            } catch (IOException ex) {
                fis.close();
                throw ex;
            }           
        }
        BufferedInputStream bis = new BufferedInputStream(is, 16384*32);
        DataInputStream dis = new DataInputStream(bis);
         
        boolean littleEndian = header.format.equals("l") || header.format.equals("L");
        boolean doublePrecision = header.format.equals("L") || header.format.equals("B");
        //Compute size per atom and offsets
        int inc = doublePrecision?8:4;
        int bytesPerAtom = header.numColumns*inc;
        int offsetCorrector = 0;
        //Correct for "number" in double precision which is always single precision
        if (header.numberColumn !=-1 && doublePrecision){
            bytesPerAtom -= 4;
            offsetCorrector += 4;
        }
        //Correct for "elements" in double precision which is always single precision
        if (header.elementColumn !=-1 && doublePrecision){
            bytesPerAtom -= 4;
            offsetCorrector += 4;
        }
        
        //Set an approximate size of the array to store atoms and avoid frequent reallocations
        int approxAtoms = (int)(filesize/bytesPerAtom);
        idc.atoms.ensureCapacity(idc.atoms.size()+approxAtoms);
        for (FastTFloatArrayList fa : idc.dataArrays)
            fa.ensureCapacity(idc.atoms.size()+approxAtoms);
        
        //Counters for atoms and bytes read
        int atomsRead = 0;
        long bytesRead = 0l;
        
        //Read the file content
        try {
            ProgressMonitor.getProgressMonitor().start(fis.getChannel().size());
            
            if (!header.multiFileInput){ //Files created with parallel output do not have a header
                //Seek the end of the header
                boolean terminationSymbolFound = false;
                byte b1, b2 = 0;
                do {
                    b1 = b2;
                    b2 = dis.readByte(); bytesRead++;
                    if (b1 == 0x23 && b2 == 0x45) terminationSymbolFound = true; // search "#E" 
                } while (!terminationSymbolFound);
                //read end of line - lf for unix or cr/lf for windows
                do {
                    b1 = dis.readByte(); //read bytes until end of line (there might be whitespace after #E)
                    bytesRead++;
                    if (b1 == 0x0d) {    //cr found, read lf 
                        dis.readByte();
                        bytesRead++;
                    }
                } while (b1!=0x0a && b1!=0x0d);
            }
            
            //Create temporary variables only once
            Vec3 pos = new Vec3();
            byte element = 0;
            int num = 0;
            byte[] byteBuffer = new byte[bytesPerAtom];
            //Reader to access values independent of endianess and precision
            ByteArrayReader bar = ByteArrayReader.getReader(doublePrecision, littleEndian);
            
            //RBV temporary variables
            byte[] rbvBuffer = new byte[6*inc];
            Vec3 rbv = new Vec3(), lineDirection = new Vec3();
            
            int elementOffset = (header.numberColumn!= -1) ? 4 : 0;
            
            while (filesize > bytesRead){
                if (atomsRead%10000 == 0){   //Update the progressBar
                    ProgressMonitor.getProgressMonitor().setCounter(fis.getChannel().position());
                }
                atomsRead++;
                //Read byte array containing information for an atom
                dis.read(byteBuffer);
                bytesRead+=byteBuffer.length;
                
                //Read position
                pos.x = bar.readFloat(byteBuffer, (header.xColumn+0)*inc-offsetCorrector);
                pos.y = bar.readFloat(byteBuffer, (header.xColumn+1)*inc-offsetCorrector);
                pos.z = bar.readFloat(byteBuffer, (header.xColumn+2)*inc-offsetCorrector);
                //Put atoms back into the simulation box, they might be slightly outside
                idc.box.backInBox(pos);
                
                //Read non-custom, but optional data
                if (header.numberColumn != -1){       //Read number if present, always 32bit
                    num = bar.readIntSingle(byteBuffer, 0);
                }
                if (header.elementColumn != -1){    //Read type if present, always 32bit
                    element = (byte)bar.readIntSingle(byteBuffer, elementOffset);
                    if (element+1 > idc.maxElementNumber) idc.maxElementNumber = (byte)(element + 1);
                }
                
                //Create the atom
                Atom a = new Atom(pos, num, element);
                
                //Read structural type and grain and assign to atom
                if (idc.atomTypesAvailable) {
                    int o = header.atomTypeColumn*inc-offsetCorrector;
                    int type = (byte)(header.atomTypeAsInt?
                            bar.readInt(byteBuffer, o):bar.readFloat(byteBuffer, o));
                    a.setType(type);
                }
                if (idc.grainsImported) {
                    int o = header.grainColumn*inc-offsetCorrector;
                    int grain = (int)(header.grainAsInt?
                            bar.readInt(byteBuffer, o):bar.readFloat(byteBuffer, o));
                    a.setGrain(grain);  //Assign grain number if found
                } 
                
                //Add rbv info if in file and to be imported
                if (header.rbv_data != -1){ //Read compressed format
                    int rbvAvail = bar.readInt(byteBuffer, header.rbv_data*inc-offsetCorrector); 
                    if (rbvAvail == 1){ 
                        dis.read(rbvBuffer);    //Read the six values
                        bytesRead+=rbvBuffer.length;
                        if (idc.rbvAvailable && atomFilter.accept(a)){  //But process only if needed
                            lineDirection.x = bar.readFloat(rbvBuffer, 0*inc);
                            lineDirection.y = bar.readFloat(rbvBuffer, 1*inc);
                            lineDirection.z = bar.readFloat(rbvBuffer, 2*inc);
                            rbv.x = bar.readFloat(rbvBuffer, 3*inc);
                            rbv.y = bar.readFloat(rbvBuffer, 4*inc);
                            rbv.z = bar.readFloat(rbvBuffer, 5*inc);   
                            idc.rbvStorage.addRBV(a, rbv, lineDirection);
                        }
                    }
                } else if (idc.rbvAvailable && atomFilter.accept(a) ) {
                    // read uncompressed format
                    lineDirection.x = bar.readFloat(byteBuffer, (header.lsX_Column + 0) * inc - offsetCorrector);
                    lineDirection.y = bar.readFloat(byteBuffer, (header.lsX_Column + 1) * inc - offsetCorrector);
                    lineDirection.z = bar.readFloat(byteBuffer, (header.lsX_Column + 2) * inc - offsetCorrector);
                    rbv.x = bar.readFloat(byteBuffer, (header.rbvX_Column + 0) * inc - offsetCorrector);
                    rbv.y = bar.readFloat(byteBuffer, (header.rbvX_Column + 1) * inc - offsetCorrector);
                    rbv.z = bar.readFloat(byteBuffer, (header.rbvX_Column + 2) * inc - offsetCorrector);
                    idc.rbvStorage.addRBV(a, rbv, lineDirection);   
                }
                
                if (atomFilter.accept(a)){
                    //Put atom into the list of all atoms
                    idc.atoms.add(a);
                    
                    //Read and store Custom columns
                    for (int i = 0; i<header.dataColumns.length; i++){
                        float value = 0f;
                        if (header.dataColumns[i]!=-1)
                            value = bar.readFloat(byteBuffer, inc*header.dataColumns[i]-offsetCorrector);
                        
                        idc.dataArrays.get(i).add(value);
                    }
                }
                
                //Gzip stores the file size module 2^32
                //It is necessary to check if the underlying file is read to the end and increase the assumed filesize if not
                if (gzipped && filesize <= bytesRead){
                    if (filesize < bytesRead || 
                            (bis.available()>1 && fis.getChannel().position() != fis.getChannel().size()-1))
                        filesize += 1l<<32;
                }
            }
        } finally {
            dis.close();
            ProgressMonitor.getProgressMonitor().stop();
        }
	}

	private void readASCIIFile(File f, ImportDataContainer idc, boolean gzipped, IMD_Header header, Filter<Atom> atomFilter)
			throws IOException {
		
		FileInputStream fis = new FileInputStream(f);
		BufferedReader inputReader = CommonUtils.createBufferedReader(fis, gzipped);
		
		try {			
			String s = inputReader.readLine();
			Vec3 pos = new Vec3();
			byte type = 0;
			int num = 0;
			byte element = 0;
			Pattern p = Pattern.compile("\\s+");
			int atomRead = 0;
			
			ProgressMonitor.getProgressMonitor().start(fis.getChannel().size());

			//Skip header
			while (!s.trim().equals("#E"))
				s = inputReader.readLine();
			s = inputReader.readLine();
			
			while (s!=null){
				atomRead++;
				if (atomRead%10000 == 0){	//Update the progressBar
					ProgressMonitor.getProgressMonitor().setCounter(fis.getChannel().position());
				}
				
				s = s.trim();
				if (s.isEmpty()){ //Skipping empty lines if someone inserted them in the file
					s = inputReader.readLine();
					continue;
				}
				String[] parts = p.split(s);
				
				if (idc.atomTypesAvailable) type = (byte)Integer.parseInt(parts[header.atomTypeColumn]);
				
				if (header.elementColumn!=-1) {
					element = (byte)Integer.parseInt(parts[header.elementColumn]);
					if (element + 1 > idc.maxElementNumber) idc.maxElementNumber = (byte)(element + 1);
				}
				
				
				if (header.numberColumn != -1)
					num = Integer.parseInt(parts[header.numberColumn]);
				
				pos.x = Float.parseFloat(parts[header.xColumn]);
				pos.y = Float.parseFloat(parts[header.xColumn+1]);
				pos.z = Float.parseFloat(parts[header.xColumn+2]);
				
				//Put atoms back into the simulation box, they might be slightly outside
				idc.box.backInBox(pos);
	
				Atom a = new Atom(pos, num, element);
				if (idc.atomTypesAvailable) a.setType(type);
				
				if (idc.grainsImported) {
                    //Parse as float and cast to int used for backwards compatibility.
                    //Old formats stored value as ints, new implementations do use float
                    int grain = (int)Float.parseFloat(parts[header.grainColumn]);
                    a.setGrain(grain);  //Assign grain number if found
                }
				
				//Put atom into the list of all atoms
				if (atomFilter.accept(a)){
					idc.atoms.add(a);
					
					//Custom columns
					for (int i = 0; i<header.dataColumns.length; i++){
						if (header.dataColumns[i]!=-1)
							idc.dataArrays.get(i).add(Float.parseFloat(parts[header.dataColumns[i]])); 
					}
					
					if (idc.rbvAvailable){
						Vec3 rbv = new Vec3();
						Vec3 lineDirection = new Vec3();
	
						if (header.rbv_data == -1){
							rbv.x = Float.parseFloat(parts[header.rbvX_Column]);
							rbv.y = Float.parseFloat(parts[header.rbvX_Column+1]);
							rbv.z = Float.parseFloat(parts[header.rbvX_Column+2]);
								
							lineDirection.x = Float.parseFloat(parts[header.lsX_Column]);
							lineDirection.y = Float.parseFloat(parts[header.lsX_Column+1]);
							lineDirection.z = Float.parseFloat(parts[header.lsX_Column+2]);
						} else {
							int data = Integer.parseInt(parts[header.rbv_data]);
							if (data == 1){
								lineDirection.x = Float.parseFloat(parts[header.rbv_data+1]);
								lineDirection.y = Float.parseFloat(parts[header.rbv_data+2]);
								lineDirection.z = Float.parseFloat(parts[header.rbv_data+3]);
								
								rbv.x = Float.parseFloat(parts[header.rbv_data+4]);
								rbv.y = Float.parseFloat(parts[header.rbv_data+5]);
								rbv.z = Float.parseFloat(parts[header.rbv_data+6]);
							}
						}
						idc.rbvStorage.addRBV(a, rbv, lineDirection);
					}
				}
				
				s = inputReader.readLine();
			}
		} finally {
			inputReader.close();
			ProgressMonitor.getProgressMonitor().stop();	
		}	
	}
	
	private class IMD_Header{
		int atomTypeColumn = -1; boolean atomTypeAsInt = false;
		int elementColumn = -1;
		int xColumn = -1;
		int rbvX_Column = -1;
		int lsX_Column = -1;
		int rbv_data = -1;
		int grainColumn = -1;	boolean grainAsInt = false;
		int numberColumn = -1;
		int numColumns = 0;
		String format = "A";
		int[] dataColumns;
		
		boolean multiFileInput = false;
		
		private ArrayList<String[]> readValuesFromHeader(BufferedReader inputReader) throws IOException{
			ArrayList<String[]> values = new ArrayList<String[]>();
			Pattern p = Pattern.compile("\\s+");

			String s = inputReader.readLine();
			while (s != null && !s.startsWith("#E")) {
				if (s.startsWith("#C")) {
					String[] parts = p.split(s);
					for (int i = 1; i < parts.length; i++) {
						values.add(new String[]{parts [i],""});
					}
					return values;
				}
				s = inputReader.readLine();
			}
			return values;
		}
		
		
		private void readHeader(File f, ImportDataContainer idc, boolean gzipped) throws IOException{
			FileInputStream is = new FileInputStream(f);
			BufferedReader inputReader = CommonUtils.createBufferedReader(is, gzipped);
			try {
				Pattern p = Pattern.compile("\\s+");
				ArrayList<DataColumnInfo> dataColumns = ImportConfiguration.getInstance().getDataColumns();
				
				this.dataColumns = new int[dataColumns.size()];
				for (int i = 0; i<this.dataColumns.length; i++){
					this.dataColumns[i] = -1;
				}
			
				String s = inputReader.readLine();
				while (s != null && !s.startsWith("#E")) {
					if (s.startsWith("#F")){
						String[] parts = p.split(s);
						this.format = parts[1];
					}
					if (s.startsWith("#C")) {
						String[] parts = p.split(s);
						this.numColumns = parts.length - 1;
						
						for (int i = 0; i < parts.length; i++) {
							if (parts[i].equals("number")) {
								this.numberColumn = i - 1;
							} else if (importTypes.getValue() && parts[i].equals("ada_type")){
								this.atomTypeColumn = i - 1;
								this.atomTypeAsInt = true;
							} else if (importTypes.getValue() && parts[i].equals("struct_type")){
								this.atomTypeColumn = i - 1;
								this.atomTypeAsInt = false;
							} else if (parts[i].equals("type")){
								this.elementColumn = i - 1;
							} else if (parts[i].equals("x")){
								this.xColumn = i - 1;
							} else if (parts[i].equals("rbv_data")) {
								this.rbv_data = i - 1;
							} else if (importRBV.getValue() && parts[i].equals("rbv_x")) {
								this.rbvX_Column = i - 1;
							} else if (importRBV.getValue() && parts[i].equals("ls_x")){
								this.lsX_Column = i - 1;
							} else if (importGrains.getValue() && parts[i].equals("grain")) {
								this.grainAsInt = true;
								this.grainColumn = i - 1;
							} else if (importGrains.getValue() && parts[i].equals("grainID")) {
								this.grainAsInt = false;
								this.grainColumn = i - 1;
							} else {
							    //Generic column
								for (int j = 0; j< dataColumns.size(); j++){
									if (parts[i].equals(dataColumns.get(j).getId())){
										this.dataColumns[j] = i - 1;
									}
								}
							}
						}
					} else if (s.startsWith("#X")){
						String[] parts = p.split(s);
						idc.boxSizeX.x = Float.parseFloat(parts[1]);
						idc.boxSizeX.y = Float.parseFloat(parts[2]);
						idc.boxSizeX.z = Float.parseFloat(parts[3]);
					} else if (s.startsWith("#Y")){
						String[] parts = p.split(s);
						idc.boxSizeY.x = Float.parseFloat(parts[1]);
						idc.boxSizeY.y = Float.parseFloat(parts[2]);
						idc.boxSizeY.z = Float.parseFloat(parts[3]);
					} else if (s.startsWith("#Z")){
						String[] parts = p.split(s);
						idc.boxSizeZ.x = Float.parseFloat(parts[1]);
						idc.boxSizeZ.y = Float.parseFloat(parts[2]);
						idc.boxSizeZ.z = Float.parseFloat(parts[3]);
					} else if (s.startsWith("##PBC")){
						String[] parts = p.split(s);
						idc.pbc[0] = Integer.parseInt(parts[1])==1;
						idc.pbc[1] = Integer.parseInt(parts[2])==1;
						idc.pbc[2] = Integer.parseInt(parts[3])==1;
					} else if (s.startsWith("##META")){
						s = inputReader.readLine();
						if (idc.fileMetaData == null)
							idc.fileMetaData = new HashMap<String, Object>();
						while (!s.startsWith("##METAEND")) {
							//First check if there are custom options
							if (s.startsWith("##") && !processGrainMetaData(s, idc.fileMetaData, inputReader, idc)){
								//Try to store the line as a array of floats, e.g. timesteps, indenter...
								try{
									s = s.substring(2);
									String[] parts = p.split(s);
									double[] info = new double[parts.length-1];
									for (int i=1; i<parts.length; i++){
										info[i-1] = Double.parseDouble(parts[i]);
									}
									idc.fileMetaData.put(parts[0].toLowerCase(), info);
								} catch (Exception e) {}
							}
							s = inputReader.readLine();
						}
					}
					s = inputReader.readLine();
				}
			} finally {
				inputReader.close();
			}
			
			if (this.xColumn ==-1) 
				throw new IllegalArgumentException("Broken header, no coordinates x y z");
			idc.makeBox();
			if (idc.boxSizeX.x <= 0f || idc.boxSizeY.y <= 0f || idc.boxSizeZ.z <= 0f){
				throw new IllegalArgumentException("Broken header, box sizes must be larger than 0");
			}
			
			if (this.grainColumn != -1 && importGrains.getValue()) idc.grainsImported = true;
			if (this.atomTypeColumn != -1) idc.atomTypesAvailable = true;
			
			if ( ((this.rbvX_Column!=-1 && this.lsX_Column!=-1 ) || this.rbv_data!=-1) && importRBV.getValue()){
				idc.rbvAvailable = true;
			}
		}
	}
	
	@Override
	public Map<String, Component> getDefaultNamesForComponents() {
		HashMap<String, Component> map = new HashMap<String, Component>();
		map.put("vx", Component.VELOCITY_X);
		map.put("vy", Component.VELOCITY_Y);
		map.put("vz", Component.VELOCITY_Z);
		map.put("mass", Component.MASS);
		map.put("Epot", Component.E_POT);
		
		map.put("fx", Component.FORCE_X);
		map.put("fy", Component.FORCE_Y);
		map.put("fz", Component.FORCE_Z);
		map.put("f_x", Component.FORCE_X);
		map.put("f_y", Component.FORCE_Y);
		map.put("f_z", Component.FORCE_Z);
		
		map.put("s_xx", Component.STRESS_XX);
		map.put("s_yy", Component.STRESS_YY);
		map.put("s_zz", Component.STRESS_ZZ);
		map.put("s_xy", Component.STRESS_XY);
		map.put("s_yz", Component.STRESS_YZ);
		map.put("s_zx", Component.STRESS_ZX);
		
		return map;
	}
	
	@Override
	public FileFilter getDefaultFileFilter() {
		FileFilter imdFileFilterBasic = new FileFilter() {
			@Override
			public String getDescription() {
				return "IMD files (*.chkpt, *.ada, *.ss)";
			}
			
			@Override
			public boolean accept(File f) {
				if (f.isDirectory()) return true;
				String name = f.getName();
				if (name.endsWith(".gz")) name = name.substring(0, name.length()-3); //Accept .gz files
				if (name.endsWith(".head")) name = name.substring(0, name.length()-5); //Accept .head files
				if (name.endsWith(".ada") || name.endsWith(".chkpt") || name.endsWith(".ss")){
					return true;
				}
				return false;
			}
		};
		return imdFileFilterBasic;
	}
	
	private final static boolean processGrainMetaData(String s, Map<String, Object> metaContainer,
			BufferedReader lnr, ImportDataContainer idc) throws IOException{
		boolean imported = PolygrainMetadata.processMetadataLine(s, metaContainer, lnr, idc); 
		return imported;
	}
}