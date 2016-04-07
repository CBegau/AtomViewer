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

import common.CommonUtils;
import common.DataInputStreamWrapper;
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
		BufferedReader inputReader = null;
		FileInputStream fis;
		
		final boolean gzipped = CommonUtils.isFileGzipped(f);
		fis = new FileInputStream(f);
		if (gzipped){
			//Directly read gzip-compressed files
			GZIPInputStream gzipis = new GZIPInputStream(fis, 16384*64);
			inputReader = new BufferedReader(new InputStreamReader(gzipis), 16384*32);
		} else 
			inputReader = new BufferedReader(new InputStreamReader(fis), 16384*32);
		
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
		} catch (IOException e){
			filteredValues.clear();
			throw(e);
		} finally{
			inputReader.close();
		}
		return filteredValues.toArray(new String[filteredValues.size()][]);
	}
	
	@Override
	public AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws Exception{
		
		//Dispose perfect lattice atoms during import
		if (ImportStates.DISPOSE_DEFAULT.isActive()){ 
			final int defaultType = ImportConfiguration.getInstance().getCrystalStructure().getDefaultType();
			Filter<Atom> defaultAtomFilter = new Filter<Atom>() {
				@Override
				public boolean accept(Atom a) {
					return a.getType() != defaultType;
				}
			};
			if (atomFilter == null) atomFilter = defaultAtomFilter;
			else {
				AtomFilterSet af = new AtomFilterSet();
				af.addFilter(atomFilter);
				af.addFilter(defaultAtomFilter);
				atomFilter = af;
			}
		}
		
		return new AtomData(previous, this.readFile(f, atomFilter));
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
		BufferedReader inputReader = null;
		GZIPInputStream gzipis = null;
		FileInputStream fis;
		
		final boolean gzipped = CommonUtils.isFileGzipped(f);
		fis = new FileInputStream(f);
		if (gzipped){
			//Directly read gzip-compressed files
			gzipis = new GZIPInputStream(fis, 16384*64);
			inputReader = new BufferedReader(new InputStreamReader(gzipis), 16384*32);
		} else 
			inputReader = new BufferedReader(new InputStreamReader(fis), 16384*32);
		
		//Reading the header
		final IMD_Header header = new IMD_Header();
		header.readHeader(inputReader, idc);
		
		if (f.getPath().endsWith(".head") || f.getPath().endsWith(".head.gz"))
			header.multiFileInput = true;
		
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
								fis = new FileInputStream(nextFile);
								if (gzipped){
									//Directly read gzip-compressed files
									gzipis = new GZIPInputStream(fis, 16384*64);
									inputReader = new BufferedReader(new InputStreamReader(gzipis), 16384*32);
								} else 
									inputReader = new BufferedReader(new FileReader(nextFile));
								readASCIIFile(idc, inputReader, header, fis, atomFilter);
								fileNumber++;
							} finally {
								if (inputReader!=null) inputReader.close();	
							}
						}
					} while (nextFile.exists());
				} else
					readASCIIFile(idc, inputReader, header, fis, atomFilter);
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
							readBinaryFile(nextFile, idc, gzipped, header, atomFilter);							
							fileNumber++;
						}
					} while (nextFile.exists());
				} else
					readBinaryFile(f, idc, gzipped, header, atomFilter);
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
			throws FileNotFoundException, IOException {
		 
		if (header.numberColumn == -1) 
			throw new IllegalArgumentException("binary files must contain number");
		
		FileInputStream fis = new FileInputStream(f);
		BufferedInputStream bis;
		long filesize = 0l;
		byte defaultType = (byte)ImportConfiguration.getInstance().getCrystalStructure().getDefaultType();
		
		if (gzipped){
			//Setup streams for compressed files
			//read streamSize from the last 4 bytes in the file
			//This value is equal to the true file size modulo 2^32
			RandomAccessFile raf = new RandomAccessFile(f, "r");
			long l = raf.length();
			raf.seek(l-4);
			
			byte[] w = new byte[4];
			raf.readFully(w, 0, 4);
			filesize =  (long)(w[3]&0xff) << 24 | (long)(w[2]&0xff) << 16 | (long)(w[1]&0xff) << 8 | (long)(w[0]&0xff);
			raf.close();
			GZIPInputStream gzipis = new GZIPInputStream(fis, 16384*64);
			bis = new BufferedInputStream(gzipis, 16384*32);
		} else {
			bis = new BufferedInputStream(fis, 16384*64);
			filesize = fis.getChannel().size();
		}
		
		if (!header.multiFileInput){
			//Set an approximate size of the array to store atoms and avoid frequent reallocations
			int approxBytesPerAtom = header.numColumns;
			if (header.format.equals("l") || header.format.equals("b")) approxBytesPerAtom*=4;
			else approxBytesPerAtom*=8;
			int approxAtoms = (int)((filesize/approxBytesPerAtom)*1.1);
			idc.atoms.ensureCapacity(approxAtoms);
		}
		
		ProgressMonitor.getProgressMonitor().start(fis.getChannel().size());
		
		boolean littleEndian = header.format.equals("l") || header.format.equals("L");
		boolean doublePrecision = header.format.equals("L") || header.format.equals("B");
		
		DataInputStreamWrapper dis = 
			DataInputStreamWrapper.getDataInputStreamWrapper(bis, doublePrecision, littleEndian);
		
		try {
			if (!header.multiFileInput){ //Files created with parallel output do not have a header
				//Seek the end of the header
				boolean terminationSymbolFound = false;
				byte b1, b2 = 0;
				do {
					b1 = b2;
					b2 = dis.readByte();
					if (b1 == 0x23 && b2 == 0x45) terminationSymbolFound = true; // search "#E" 
				} while (!terminationSymbolFound);
				//read end of line - lf for unix or cr/lf for windows
				do {
					b1 = dis.readByte(); //read bytes until end of line (there might be whitespace after #E 
					if (b1 == 0x0d) {    //cr found, read lf 
						dis.readByte();
					}
				} while (b1!=0x0a && b1!=0x0d);
			}
			
			Vec3 pos = new Vec3();
			Vec3 rbv = new Vec3();
			Vec3 lineDirection = new Vec3();
			byte type = (byte)(defaultType+1);
			byte element = 0;
			int num = 0;
			boolean rbv_read = false;
			if (header.lsX_Column != -1 && header.readRBV) rbv_read = true;
			//Add six values for RBV and line direction
			float[] dataColumnValues = new float[header.dataColumns.length+6];
			int grain = 0;
			
			int atomRead = 0;
			
			while (filesize > dis.getBytesRead()){
				atomRead++;
				if (atomRead%10000 == 0){	//Update the progressBar
					ProgressMonitor.getProgressMonitor().setCounter(fis.getChannel().position());
				}
				int read=0;
				if (header.numberColumn!=-1){	    //Read number if present
					num = dis.readIntSingle();	    //always 32bit
					read++;
				}
				if (header.elementColumn != -1){	//Read type if present
					element = (byte)dis.readIntSingle();	//always 32bit
					read++;
				}
				if (header.massColumn == read){       //Read mass if present
					if (header.columnToCustomIndex[read] != -1) {
						dataColumnValues[header.columnToCustomIndex[read]] = dis.readFloat();
					} else dis.skip();
					read++;
				}
				
				//Read position
				pos.x = dis.readFloat();
				pos.y = dis.readFloat();
				pos.z = dis.readFloat();
				read += 3;
				//Read optional values
				while (read < header.numColumns) {
					if (!header.columnToBeRead[read]) {
						dis.skip();
					} else {
						if (header.columnToCustomIndex[read] != -1) {
							dataColumnValues[header.columnToCustomIndex[read]] = dis.readFloat();
						} else if (read == header.atomTypeColumn) {
							if (header.atomTypeAsInt)
								type = (byte) dis.readInt();
							else type = (byte) dis.readFloat();
						} else if (read == header.rbv_data) {
							rbv_read = false;
							if (dis.readInt() == 1) {
								dataColumnValues[header.dataColumns.length+0] = dis.readFloat();
								dataColumnValues[header.dataColumns.length+1] = dis.readFloat();
								dataColumnValues[header.dataColumns.length+2] = dis.readFloat();
								dataColumnValues[header.dataColumns.length+3] = dis.readFloat();
								dataColumnValues[header.dataColumns.length+4] = dis.readFloat();
								dataColumnValues[header.dataColumns.length+5] = dis.readFloat();
								rbv_read = true;
							}
						} else if (read == header.grainColumn) {
							if (header.grainAsInt)
								grain = dis.readInt();
							else grain = (int)dis.readFloat();
						}
					}
					read++;
				}

				//Put atoms back into the simulation box, they might be slightly outside
				idc.box.backInBox(pos);
				
				Atom a = new Atom(pos, num, element);
				if (idc.atomTypesAvailable) a.setType(type);
				if (element+1 > idc.maxElementNumber) idc.maxElementNumber = (byte)(element + 1);
				
				//Add rbv info is found in file
				if (header.readRBV && rbv_read){
					lineDirection.x = dataColumnValues[header.dataColumns.length+0];
					lineDirection.y = dataColumnValues[header.dataColumns.length+1];
					lineDirection.z = dataColumnValues[header.dataColumns.length+2];
					rbv.x = dataColumnValues[header.dataColumns.length+3];
					rbv.y = dataColumnValues[header.dataColumns.length+4];
					rbv.z = dataColumnValues[header.dataColumns.length+5];
					idc.rbvStorage.addRBV(a, rbv, lineDirection);
				}
				if (header.grainColumn!=-1) 
					a.setGrain(grain);	//Assign grain number if found
				
				if (atomFilter == null || atomFilter.accept(a)){
					//Put atom into the list of all atoms
					idc.atoms.add(a);
					
					//Custom columns
					for (int i = 0; i<header.dataColumns.length; i++)
						idc.dataArrays.get(i).add(dataColumnValues[i]);
				}
				
				if (gzipped && filesize <= dis.getBytesRead()){
					if (filesize < dis.getBytesRead())
						filesize += 1l<<32;	//File seems to be larger than 4GB
					else if (bis.available()>1 &&  fis.getChannel().position() != fis.getChannel().size()-1){
						//File seems to be larger than 4GB, there is data left in the InputStreams
						filesize += 1l<<32;	 
					}
				}
			}
		} catch (IOException ex) {
			throw ex;
		} finally {
			dis.close();
			ProgressMonitor.getProgressMonitor().stop();
		}
	}

	private void readASCIIFile(ImportDataContainer idc, BufferedReader inputReader, IMD_Header header,
			FileInputStream fis, Filter<Atom> atomFilter) throws IOException {
		String s = inputReader.readLine();
		Vec3 pos = new Vec3();
		byte type = 0;
		int num = 0;
		byte element = 0;
		Pattern p = Pattern.compile("\\s+");
		int atomRead = 0;
		
		ProgressMonitor.getProgressMonitor().start(fis.getChannel().size());
		
		while (s!=null){
			atomRead++;
			if (atomRead%10000 == 0){	//Update the progressBar
				ProgressMonitor.getProgressMonitor().setCounter(fis.getChannel().position());
			}
			
			s = s.trim();
			if (s.isEmpty()){ /*Skipping empty lines if someone inserted them in the file */
				s = inputReader.readLine();
				continue;
			}
			String[] parts = p.split(s);
			
			int grain = 0;
			
			if (header.atomTypeColumn!=-1) type = (byte)Integer.parseInt(parts[header.atomTypeColumn]);
			if (header.grainColumn!=-1) {
				//Parse as float and cast to int used for backwards compatibility.
				//Old formats stored value as ints, new implementation do support float
				//Although in ASCII it does not matter, this implementation is more safe
				grain = (int)Float.parseFloat(parts[header.grainColumn]);	
			}
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
			if (header.grainColumn!=-1) 
				a.setGrain(grain);	//Assign grain number if found
			
			//Put atom into the list of all atoms
			if (atomFilter == null || atomFilter.accept(a)){
				idc.atoms.add(a);
				
				//Custom columns
				for (int i = 0; i<header.dataColumns.length; i++){
					if (header.dataColumns[i]!=-1)
						idc.dataArrays.get(i).add(Float.parseFloat(parts[header.dataColumns[i]])); 
				}
				
				if (header.readRBV){
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
		ProgressMonitor.getProgressMonitor().stop();
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
		int massColumn = -1; 
		String format = "A";
		int[] dataColumns;
		
		boolean multiFileInput = false;
		boolean readRBV = false;
		
		boolean[] columnToBeRead;
		int[] columnToCustomIndex;
		
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
		
		
		private void readHeader(BufferedReader inputReader,
				ImportDataContainer idc) throws IOException{
			
			
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
					this.columnToBeRead = new boolean[this.numColumns+1];
					this.columnToCustomIndex = new int[this.numColumns+1];
					
					for (int i = 0; i < parts.length; i++) {
						this.columnToCustomIndex[i] = -1;
						if (parts[i].equals("number")) {
							this.numberColumn = i - 1;
							this.columnToBeRead[i-1] = true;
						} else if (importTypes.getValue() && parts[i].equals("ada_type")){
							this.atomTypeColumn = i - 1;
							this.columnToBeRead[i-1] = true;
							this.atomTypeAsInt = true;
						} else if (importTypes.getValue() && parts[i].equals("struct_type")){
							this.atomTypeColumn = i - 1;
							this.columnToBeRead[i-1] = true;
							this.atomTypeAsInt = false;
						} else if (parts[i].equals("type")){
							this.elementColumn = i - 1;
							this.columnToBeRead[i-1] = true;
						} else if (parts[i].equals("mass")){
							this.massColumn = i - 1;
							for (int j = 0; j<dataColumns.size(); j++){
								if (parts[i].equals(dataColumns.get(j).getId())){
									this.dataColumns[j] = i - 1;
									this.columnToBeRead[i-1] = true;
									this.columnToCustomIndex[i-1] = j;
								}
							}
						} else if (parts[i].equals("x")){
							this.xColumn = i - 1;
							this.columnToBeRead[i-1] = true;
							this.columnToBeRead[i] = true;
							this.columnToBeRead[i+1] = true;
						} else if (parts[i].equals("rbv_data")) {
							this.rbv_data = i - 1;
							this.columnToBeRead[i-1] = true;
						} else if (importRBV.getValue() && parts[i].equals("rbv_x")) {
							this.rbvX_Column = i - 1;
							this.columnToBeRead[i-1] = true;
							this.columnToCustomIndex[i-1] = dataColumns.size()+3;
						} else if (importRBV.getValue() && parts[i].equals("rbv_y")) {
							this.columnToBeRead[i-1] = true;
							this.columnToCustomIndex[i-1] = dataColumns.size()+4;
						} else if (importRBV.getValue() && parts[i].equals("rbv_z")) {
							this.columnToBeRead[i-1] = true;
							this.columnToCustomIndex[i-1] = dataColumns.size()+5;
						} else if (importRBV.getValue() && parts[i].equals("ls_x")){
							this.lsX_Column = i - 1;
							this.columnToBeRead[i-1] = true;
							this.columnToCustomIndex[i-1] = dataColumns.size();
						} else if (importRBV.getValue() && parts[i].equals("ls_y")){
							this.columnToBeRead[i-1] = true;
							this.columnToCustomIndex[i-1] = dataColumns.size()+1;
						} else if (importRBV.getValue() && parts[i].equals("ls_z")){
							this.columnToBeRead[i-1] = true;
							this.columnToCustomIndex[i-1] = dataColumns.size()+2;
						} else if (importRBV.getValue() && parts[i].equals("grain")) {
							this.grainAsInt = true;
							this.grainColumn = i - 1;
							this.columnToBeRead[i-1] = true;
						} else if (importRBV.getValue() && parts[i].equals("grainID")) {
							this.grainAsInt = false;
							this.grainColumn = i - 1;
							this.columnToBeRead[i-1] = true;
						} else {
							for (int j = 0; j< dataColumns.size(); j++){
								if (parts[i].equals(dataColumns.get(j).getId())){
									this.dataColumns[j] = i - 1;
									this.columnToBeRead[i-1] = true;
									this.columnToCustomIndex[i-1] = j;
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
			
			if (this.xColumn ==-1) 
				throw new IllegalArgumentException("Broken header, no coordinates x y z");
			idc.makeBox();
			if (idc.boxSizeX.x <= 0f || idc.boxSizeY.y <= 0f || idc.boxSizeZ.z <= 0f){
				throw new IllegalArgumentException("Broken header, box sizes must be larger than 0");
			}
			
			if (this.grainColumn != -1 && importGrains.getValue()) idc.grainsImported = true;
			if (this.atomTypeColumn != -1) idc.atomTypesAvailable = true;
			
			if ( ((this.rbvX_Column!=-1 && this.lsX_Column!=-1 ) || this.rbv_data!=-1) && importRBV.getValue()){
				this.readRBV = true;
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
				if (name.endsWith(".ada") || name.endsWith(".chkpt") || name.endsWith(".ss") 
						|| name.endsWith(".ada.gz") || name.endsWith(".chkpt.gz") || name.endsWith(".ss.gz")
						|| name.endsWith(".chkpt.head") || name.endsWith(".chkpt.head.gz") 
						|| name.endsWith(".ada.head") || name.endsWith(".ada.head.gz")
						|| name.endsWith(".ss.head") || name.endsWith(".ss.head.gz")){
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