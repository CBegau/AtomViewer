package model.io;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import common.Vec3;
import gui.PrimitiveProperty;
import gui.PrimitiveProperty.BooleanProperty;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.Filter;
import model.RenderingConfiguration;
import model.DataColumnInfo.Component;
import model.polygrain.Grain;

public class ImdFileWriter extends MDFileWriter {
	
	private BooleanProperty binaryExport = new PrimitiveProperty.BooleanProperty("binary", "Binary export",
			"Using big endian, single precision binary format for export, otherwise use ASCII format", false);
	private BooleanProperty gzippedExport = new PrimitiveProperty.BooleanProperty("gzip", "GZip compression",
			"Compress the output using gzip", false);
	private BooleanProperty compressedRBV = new PrimitiveProperty.BooleanProperty("compRBV", "Compress RBV",
			"Use a compressed format to save RBVs, the resulting files are not compatible with the standard"
			+ "IMD format", false);
	private BooleanProperty useFilter = new PrimitiveProperty.BooleanProperty("use filter", "Export only visible particles",
			"Only the particles not affected by the currently selected visibilty filter are exported", false);
	
	private boolean exportNumber = false;
	private boolean exportElement = false;
	private boolean exportType = false;
	private boolean exportRBV = false;
	private boolean exportGrain = false;
	
	private DataColumnInfo[] toExportColumns;
	
	public ImdFileWriter(){}
	
	public ImdFileWriter(boolean binary, boolean gzipped) {
		this.exportNumber  = true;    
		this.exportElement = true;  
		this.exportType    = true;
		this.exportRBV     = true;
		this.exportGrain   = true;
		
		compressedRBV.setValue(true);
		binaryExport.setValue(binary);
		gzippedExport.setValue(true);
	}
	
	@Override
	public void setDataToExport(boolean number, boolean element, boolean type, boolean rbv, boolean grain,
			DataColumnInfo... dci) {
		this.exportNumber = number;
		this.exportElement = element;
		this.exportType = type;
		this.exportRBV = rbv;
		this.exportGrain = grain;
		toExportColumns = dci;
	}

	@Override
	public void writeFile(File path, String filenamePrefix, AtomData data, Filter<Atom> filter) throws IOException {
		if (useFilter.getValue() && filter == null)
			filter = RenderingConfiguration.getAtomFilterset();			
		
		//Construct the file to export, based on options
		String fullFilename = filenamePrefix;
		if (gzippedExport.getValue()){
			if(fullFilename.endsWith(".chkpt"))
				fullFilename+=".gz";
			else if(!fullFilename.endsWith(".chkpt.gz"))
				fullFilename+=".chkpt.gz";
		} else {
			if(!fullFilename.endsWith(".chkpt"))
				fullFilename+=".chkpt";
		}
		
		File out = new File(path, fullFilename);
		
		DataOutputStream dos;	
		
		if (gzippedExport.getValue()){
			dos = new DataOutputStream(new BufferedOutputStream(
					new GZIPOutputStream(new FileOutputStream(out), 1024*1024), 4096*1024));
		}
		else dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(out), 4096*1024));
		

		int massColumn = -1;
		int vxColumn = -1, vyColumn = -1, vzColumn = -1;
		for (int i=0; i<toExportColumns.length; i++){
			if (toExportColumns[i].getComponent() == Component.MASS)
				massColumn = data.getIndexForCustomColumn(toExportColumns[i]);
			if (toExportColumns[i].getComponent() == Component.VELOCITY_X)
				vxColumn = data.getIndexForCustomColumn(toExportColumns[i]);
			if (toExportColumns[i].getComponent() == Component.VELOCITY_Y)
				vyColumn = data.getIndexForCustomColumn(toExportColumns[i]);
			if (toExportColumns[i].getComponent() == Component.VELOCITY_Z)
				vzColumn = data.getIndexForCustomColumn(toExportColumns[i]);
		}
		
		//Tag which data need not to be exported in the end of each line
		int[] mapData = new int[toExportColumns.length];
		for (int i=0; i<mapData.length;i++){
			mapData[i] = data.getIndexForCustomColumn(toExportColumns[i]);
		}
		
		boolean hasMass = massColumn!=-1;
		if (hasMass)
			mapData[massColumn] = -1;
		
		boolean hasVelocity = false;
		if (vxColumn != -1 && vyColumn != -1 && vzColumn != -1){
			hasVelocity = true;
			mapData[vxColumn] = -1; mapData[vyColumn] = -1; mapData[vzColumn] = -1;
		}
		
		int countExportFields = 0;
		for (int i=0; i<mapData.length;i++)
			if (mapData[i] != -1) countExportFields++;
		
		countExportFields += (data.isPolyCrystalline() && exportGrain ? 1 : 0);
		countExportFields += (exportType ? 1 : 0);
		if (exportRBV && data.isRbvAvailable())
			countExportFields += compressedRBV.getValue() ? 1 : 6;
		
		
		
		try {
			//Write header 
			if (binaryExport.getValue()) dos.writeBytes("#F b ");
			else dos.writeBytes("#F A ");
			dos.writeBytes(String.format("%d %d %d 3 %d %d\n", (exportNumber?1:0), (exportElement?1:0), 
					(hasMass?1:0), (hasVelocity?3:0), countExportFields ));
			
			dos.writeBytes(String.format("#C %s%s%sx y z %s", exportNumber?"number ":"",
					exportElement?"type ":"", hasMass?"mass ":"", hasVelocity?"vx vy vz ":""));

			for (int i=0; i<mapData.length; i++){
				if (mapData[i] != -1){
					String id = " "+data.getDataColumnInfos().get(mapData[i]).getId();
					dos.writeBytes(id);
				}
			}
			
			if (exportType) dos.writeBytes(" struct_type");
			if (data.isPolyCrystalline() && exportGrain) dos.writeBytes(" grainID");
			//rbv always at the end
			if (exportRBV && data.isRbvAvailable()){
				if (compressedRBV.getValue())
					dos.writeBytes(" rbv_data");
				else dos.writeBytes(" ls_x ls_y ls_z rbv_x rbv_y rbv_z");
			}
			dos.writeBytes("\n");

			
			Vec3[] b = data.getBox().getBoxSize();
			dos.writeBytes(String.format("#X %.8f %.8f %.8f\n", b[0].x, b[0].y, b[0].z));
			dos.writeBytes(String.format("#Y %.8f %.8f %.8f\n", b[1].x, b[1].y, b[1].z));
			dos.writeBytes(String.format("#Z %.8f %.8f %.8f\n", b[2].x, b[2].y, b[2].z));
			
			dos.writeBytes("##META\n");
			if (data.isPolyCrystalline() && exportGrain){
				for (Grain g: data.getGrains()){
					float[][] rot = g.getCystalRotationTools().getDefaultRotationMatrix();
					dos.writeBytes(String.format("##grain %d %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f\n",
							g.getGrainNumber(), rot[0][0], rot[1][0], rot[2][0]
							, rot[0][1], rot[1][1], rot[2][1]
							, rot[0][2], rot[1][2], rot[2][2]));
					dos.writeBytes(String.format("##atomsInGrain %d %d\n",
							g.getGrainNumber(), g.getNumberOfAtoms()));
					g.getMesh().getFinalMesh().printMetaData(dos, g.getGrainNumber());
				}
			}
			if (data.getFileMetaData("extpot") != null){
				float[] indent = (float[]) data.getFileMetaData("extpot");
				dos.writeBytes(String.format("##extpot %.4f %.4f %.4f %.4f %.4f\n", indent[0], indent[1], indent[2], indent[3], indent[4]));
			}
			if (data.getFileMetaData("wall") != null){
				float[] wall = (float[]) data.getFileMetaData("wall");
				dos.writeBytes(String.format("##wall %.4f %.4f %.4f\n", wall[0], wall[1], wall[2]));
			}
			if (data.getFileMetaData("timestep") != null){
				float[] timestep = (float[])data.getFileMetaData("timestep");
				dos.writeBytes(String.format("##timestep %f\n", timestep[0]));
			}
			
			dos.writeBytes("##METAEND\n");
			dos.writeBytes("#E\n");
			
			
			
			if (binaryExport.getValue()){
				for (Atom a : data.getAtoms()){
					//Test and apply filtering
					if (filter!=null && !filter.accept(a)) continue;
					
					if (exportNumber) dos.writeInt(a.getNumber());
					if (exportElement) dos.writeInt(a.getElement());
					if (hasMass) dos.writeFloat(a.getData(massColumn));
					
					dos.writeFloat(a.x); dos.writeFloat(a.y); dos.writeFloat(a.z);
					
					if (hasVelocity){
						dos.writeFloat(a.getData(vxColumn));
						dos.writeFloat(a.getData(vyColumn));
						dos.writeFloat(a.getData(vzColumn));
					}
					
					for (int i = 0; i < mapData.length; i++)
						if (mapData[i] != -1)
							dos.writeFloat(a.getData(mapData[i]));
					
					if (exportType) dos.writeFloat(a.getType());
					
					if (data.isPolyCrystalline() && exportGrain)
						dos.writeFloat(a.getGrain());
					
					if (exportRBV && data.isRbvAvailable()){
						boolean writeRBV = false;
						if (compressedRBV.getValue()){
							if (a.getRBV()!=null){
								dos.writeInt(1);
								writeRBV = true;
							} else  dos.writeInt(0);
						} else writeRBV = true;
						
						if (writeRBV){
							if (a.getRBV()!=null){
								dos.writeFloat(a.getRBV().lineDirection.x);
								dos.writeFloat(a.getRBV().lineDirection.y);
								dos.writeFloat(a.getRBV().lineDirection.z);
								dos.writeFloat(a.getRBV().bv.x); 
								dos.writeFloat(a.getRBV().bv.y); 
								dos.writeFloat(a.getRBV().bv.z);
							} else {
								dos.writeFloat(0); dos.writeFloat(0); dos.writeFloat(0);
								dos.writeFloat(0); dos.writeFloat(0); dos.writeFloat(0);
							}
						}
					}
				}
			} else {
				for (Atom a : data.getAtoms()){
					//Test and apply filtering
					if (filter!=null && !filter.accept(a)) continue;
					if (exportNumber) dos.writeBytes(String.format(" %d", a.getNumber()));
					if (exportElement) dos.writeBytes(String.format(" %d", a.getElement()));
					
					if (hasMass)
						dos.writeBytes(String.format(" %.8f", a.getData(massColumn)));
					dos.writeBytes(String.format(" %.8f %.8f %.8f", a.x, a.y, a.z));
					if (hasVelocity)
						dos.writeBytes(String.format(" %.8f %.8f %.8f", a.getData(vxColumn), a.getData(vyColumn), a.getData(vzColumn)));
					
					
					for (int i = 0; i < mapData.length; i++)
						if (mapData[i] != -1)
								dos.writeBytes(String.format(" %.8f",a.getData(mapData[i])));
					
					if (exportType) dos.writeBytes(String.format(" %d", a.getType()));
					
					if (data.isPolyCrystalline() && exportGrain)
						dos.writeBytes(String.format(" %d", a.getGrain()));
					
					
					if (exportRBV && data.isRbvAvailable()){
						boolean writeRBV = false;
						if (compressedRBV.getValue()){
							if (a.getRBV()!=null){
								dos.writeBytes(" 1");
								writeRBV = true;
							} else  dos.writeBytes(" 0");
						} else writeRBV = true;
						
						if (writeRBV){
							if (a.getRBV()!=null){
								dos.writeBytes(String.format(" %.4f %.4f %.4f %.4f %.4f %.4f",
										a.getRBV().lineDirection.x, a.getRBV().lineDirection.y, a.getRBV().lineDirection.z,
										a.getRBV().bv.x, a.getRBV().bv.y, a.getRBV().bv.z));
							} else {
								dos.writeBytes(" 0. 0. 0. 0. 0. 0.");
							}
						}
					}
					dos.writeBytes("\n");
				}
			}
		} catch (IOException e){
			throw e;
		} finally {
			dos.close();
		}

	}
	
	
	@Override
	public List<PrimitiveProperty<?>> getAdditionalProperties(AtomData data, boolean allFiles) {
		ArrayList<PrimitiveProperty<?>> options = new ArrayList<PrimitiveProperty<?>>();
		
		options.add(binaryExport);
		options.add(gzippedExport);
		
		if (allFiles){
			//Check if all files have RBVs, if so show the option to compress RBVs or not
			boolean allFilesWithRBV = true;
			AtomData d = data;
			while (d.getPrevious()!=null) d = d.getPrevious(); 
			
			do {
				if (!d.isRbvAvailable()) allFilesWithRBV = false;
				d = d.getNext();
			} while (d != null);
			
			if (allFilesWithRBV)
				options.add(compressedRBV);
		} else {
			if (data.isRbvAvailable())	//Otherwise just test this file
				options.add(compressedRBV);
			//TODO unclean solution. Only supporting a single file.
			//Replace by a generic solution, where filter can be used for different files
			//without risk of exceptions
			options.add(useFilter);
		}
		
		return options;
	}

}
