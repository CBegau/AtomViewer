package gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.SwingWorker;

import model.*;
import model.io.*;
import processingModules.ProcessingModule;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchain.ReferenceMode;

public class BatchProcessing {

	private enum Arguments {INPUT_FORMAT, INPUT_FILES, REFERENCE_FILE, CRYSTAL_CONF, PBC, OUTPUT_PATTERN, OUTPUT_FORMAT, TOOLCHAIN}
	
	public void processBatch(String[] args){
	
		try {
			HashMap<Arguments, String[]> arguments = this.splitCommandLineArguemnts(args);
			if (arguments.get(Arguments.INPUT_FILES) == null)
				throw new RuntimeException("No input files specified");
			
			MDFileLoader fileLoader;
			if (arguments.get(Arguments.INPUT_FORMAT)!=null){
				String type = arguments.get(Arguments.INPUT_FORMAT)[0];
				if (type.toLowerCase().equals("imd"))
					fileLoader = new ImdFileLoader();
				else if (type.toLowerCase().equals("lammps"))
					fileLoader = new LammpsAsciiDumpLoader();
				else if (type.toLowerCase().equals("xyz"))
					fileLoader = new XYZFileLoader();
				else if (type.toLowerCase().equals("cfg"))
					fileLoader = new CfgFileLoader();
				else throw new RuntimeException("Input format "+type+" is not valid.");
			}
			else
				fileLoader = new ImdFileLoader();
			
			Toolchain toolchain = null;
			boolean keepPreviousFile = false;
			boolean keepFirstFile = false;
			
			if (arguments.get(Arguments.TOOLCHAIN) != null){
				File toolchainFile = new File(arguments.get(Arguments.TOOLCHAIN)[0]);
				if (!toolchainFile.exists())
					throw new RuntimeException("Toolchain does not exist");
				FileInputStream fis = new FileInputStream(toolchainFile);
				toolchain = Toolchain.readToolchain(fis);
				//check if toolchain referencences are valid
				for (ProcessingModule pm : toolchain.getProcessingModules()){
					if (pm.getReferenceModeUsed() == ReferenceMode.LAST || pm.getReferenceModeUsed() == ReferenceMode.NEXT)
						throw new RuntimeException("Toolchains with references to the following or next file in a sequence are not supported for batch processing");
					if (pm.getReferenceModeUsed() == ReferenceMode.REF && arguments.get(Arguments.REFERENCE_FILE)==null)
						throw new RuntimeException("No reference file specified, but requested in the selected toolchain");
					if (pm.getReferenceModeUsed() == ReferenceMode.FIRST)
						keepFirstFile = true;
					if (pm.getReferenceModeUsed() == ReferenceMode.PREVIOUS)
						keepPreviousFile = true;
				}
				fis.close();
			}
			
			
			Configuration.setCurrentFileLoader(fileLoader);
			final SwingWorker<AtomData, String> worker = fileLoader.getNewSwingWorker();
			
			worker.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if ("progress" == evt.getPropertyName()) {
						String progressing = evt.getNewValue().toString();
						System.out.println("Processing "+progressing);
					}
					if ("operation" == evt.getPropertyName()) {
						String operation = evt.getNewValue().toString();
						System.out.println(operation);
					}
				}
			});
			
			RenderingConfiguration.setHeadless(true);
			

			ImportConfiguration ic = ImportConfiguration.getNewInstance();
			
			//Set periodicity
			if(arguments.get(Arguments.PBC) != null){
				String[] pbc = arguments.get(Arguments.PBC);
				ic.getPeriodicBoundaryConditions()[0] = pbc[0]=="1";
				ic.getPeriodicBoundaryConditions()[1] = pbc[1]=="1";
				ic.getPeriodicBoundaryConditions()[2] = pbc[2]=="1";
			} else {	//Default is no periodicity
				ic.getPeriodicBoundaryConditions()[0] = false;
				ic.getPeriodicBoundaryConditions()[1] = false;
				ic.getPeriodicBoundaryConditions()[2] = false;
			}
			
			AtomData reference = null;
			//Load reference file if requested
			if (arguments.get(Arguments.REFERENCE_FILE) != null){
				File inputFile = new File(arguments.get(Arguments.REFERENCE_FILE)[0]);
				if (!inputFile.exists())
					throw new RuntimeException("Reference file "+
							arguments.get(Arguments.REFERENCE_FILE)[0]+" not found");
				
				readCrystalConf(arguments, ic, inputFile);

				Configuration.create();
				
				Configuration.setLastOpenedFolder(inputFile.getParentFile());
				Filter<Atom> filter = ImportConfiguration.getInstance().getCrystalStructure().getIgnoreAtomsDuringImportFilter();
				
				reference = fileLoader.readInputData(inputFile, null, filter);
				reference.setAsReferenceForProcessingModule();
				if (toolchain != null){
					for (ProcessingModule pm : toolchain.getProcessingModules()){
						reference.applyProcessingModule(pm);
					}
				}
			}
			
			AtomData previousFile = null;
			AtomData firstFile = null;
			int countFiles = 0;
			
			//loop over all files
			if (arguments.get(Arguments.INPUT_FILES) != null){
				for (String f : arguments.get(Arguments.INPUT_FILES)){
					File inputFile = new File(f);
					if (!inputFile.exists())
						throw new RuntimeException("Input file "+f+" not found");
					
					readCrystalConf(arguments, ic, inputFile);
	
					Configuration.create();
					
					Configuration.setLastOpenedFolder(inputFile.getParentFile());
					Filter<Atom> filter = ImportConfiguration.getInstance().getCrystalStructure().getIgnoreAtomsDuringImportFilter();
					
					if (keepFirstFile){
						previousFile = firstFile;
						previousFile.setNextToNull();
					}
					AtomData data = fileLoader.readInputData(inputFile, previousFile, filter);
					if (toolchain != null){
						for (ProcessingModule pm : toolchain.getProcessingModules()){
							data.applyProcessingModule(pm);
						}
					}
					if (keepFirstFile && countFiles == 0){
						firstFile = data;
					}
					
					if (keepPreviousFile){
						if (data.getPrevious()!=null)
							data.getPrevious().setNextToNull();
						previousFile = data;
					}
					
					//File output
					String outfile;
					if (arguments.get(Arguments.INPUT_FILES).length >1)
						outfile = String.format("%s.%05d.chkpt", arguments.get(Arguments.OUTPUT_PATTERN)[0], countFiles);
					else outfile = arguments.get(Arguments.OUTPUT_PATTERN)[0];
					
					boolean binaryOutput = false;
					if (arguments.get(Arguments.OUTPUT_FORMAT) != null && 
							arguments.get(Arguments.OUTPUT_FORMAT)[0].equals("imd_b")){
						binaryOutput = true;
					}
					
					ImdFileWriter writer = new ImdFileWriter(binaryOutput, false);
					writer.writeFile(null, outfile, data, null);
					countFiles++;
					
//					DataContainer dc = Configuration.getCurrentAtomData().getDataContainer(Skeletonizer.class);
//					Skeletonizer skel = null;
//					if (dc != null)
//						skel = (Skeletonizer)dc;
//					
//					if (exportDislocations && skel != null){
//						outfile = args[4]+"_dislocation.txt";
//						skel.writeDislocationSkeleton(new File(outfile));
//					}
				}
			}
			
		

//			Code snippet to render to an offscreen buffer and create a "screenshot" from this 
//			try {			
//				GLProfile.initSingleton();
//				GLProfile maxProfile = GLProfile.getMaxProgrammableCore(true);
//				GLCapabilities glCapabilities = new GLCapabilities(maxProfile);
//				glCapabilities.setOnscreen(false);
//				
//				ViewerGLJPanel viewer = new ViewerGLJPanel(128, 128, glCapabilities);
//				viewer.setAtomData(data, true);
//				GLDrawableFactory factory = GLDrawableFactory.getFactory(maxProfile);
//				GLOffscreenAutoDrawable drawable = factory.createOffscreenAutoDrawable(null,glCapabilities,null,128,128);
//				drawable.display();
//				drawable.getContext().makeCurrent();
//				
//				viewer.init(drawable);
//				viewer.reshape(drawable, 0, 0, 128, 128);
//				viewer.makeScreenshot("test.png", "png", false, 1000, 1000);
//				drawable.destroy();
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
			
		
		} catch (Exception e) {
			if (args.length < 5 || !args[0].equals("-h")){
				System.out.println("*************************************************");
				System.out.println("ERROR: Insufficient parameters for batch processing");
				System.out.println("USAGE: -b -i <Input Files> -o <output pattern> [-options ...] ");
				System.out.println();
				System.out.println("-i <input Files>: List of all input files to be processed");
				System.out.println("-o <output prefix>: If only a single input file is specified, the given argument will be the output filename.");
				System.out.println("                    For multiple input files, the output files will start by this prefix.");
				System.out.println("Optional arguments:");
				System.out.println("-if <format>: Select input format. Valid formats:");
				System.out.println("              imd: IMD format (default)");
				System.out.println("              lammps: Lammps ascii dump");
				System.out.println("              xyz: (extended) XYZ format");
				System.out.println("              cfg: Cfg format");
				System.out.println("-of <format>: Select output format. Valid formats:");
				System.out.println("              imd_a: Output in IMD ASCII format (default)");
				System.out.println("              imd_b: Output in IMD binary format");
				System.out.println("-cc <crystal.Conf file>: File containing the crystal information (usually named crystal.conf).");
				System.out.println("                         If not give, AtomViewer tries to read the file from the same folder as the input files");
				System.out.println("-tc <Toolchain file>: Toolchain file to be applied to each input file");
				System.out.println("-ref <Reference file>: A reference file is needed for a toolchain");
				System.out.println("-pbc <0|1 0|1 0|1>: Enable/disable periodicity. By default periodicity is diabled. If PBCs are provide by the input file, this setting is ignored.");
				System.out.println("                    If PBCs are provide by the input file, this setting is ignored.");
				System.out.println("*************************************************");
				System.exit(1);
			}
		}
		
		System.exit(0);
	}

	private void readCrystalConf(HashMap<Arguments, String[]> arguments, ImportConfiguration ic, File inputFile) {
		if (arguments.get(Arguments.CRYSTAL_CONF) != null){
			File confFile = new File(arguments.get(Arguments.CRYSTAL_CONF)[0]);
			if (!confFile.exists())
				throw new RuntimeException("crystal.conf file "+
					arguments.get(Arguments.CRYSTAL_CONF)[0]+" not found");
			ic.readConfigurationFile(confFile);
		} else {
			File confFile = new File(inputFile.getAbsolutePath(),"crystal.conf");
			if (!confFile.exists())
				throw new RuntimeException("crystal.conf file "+
					confFile.getAbsolutePath()+" not found");
		}
	}
	
	private HashMap<Arguments, String[]> splitCommandLineArguemnts(String[] args) throws RuntimeException{
		HashMap<Arguments, String[]> arguments = new HashMap<BatchProcessing.Arguments, String[]>();
		
		for (int i=0; i<args.length; i++){
			if (args[i].startsWith("-")){
				//Read input format
				if (args[i].startsWith("-if")){
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Input format missing after -if");
					arguments.put(Arguments.INPUT_FORMAT, new String[]{args[i+1]});
				}
				
				//Read output format
				if (args[i].startsWith("-of")){
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Output format missing after -of");
					arguments.put(Arguments.OUTPUT_FORMAT, new String[]{args[i+1]});
				}
				
				//Read Toolchain file
				if (args[i].startsWith("-tc")){
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Toolchain file missing after -tc");
					arguments.put(Arguments.TOOLCHAIN, new String[]{args[i+1]});
				}
				
				//Read Toolchain file
				if (args[i].startsWith("-ref")){
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Reference file missing after -ref");
					arguments.put(Arguments.REFERENCE_FILE, new String[]{args[i+1]});
				}
				
				//Read crystal.conf file
				if (args[i].startsWith("-cc")){
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Crystal.conf file missing after -cc");
					arguments.put(Arguments.CRYSTAL_CONF, new String[]{args[i+1]});
				}
				
				//Read Output filename patter
				if (args[i].startsWith("-o")){
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Output pattern missing after -o");
					arguments.put(Arguments.OUTPUT_PATTERN, new String[]{args[i+1]});
				}
				
				//Read Input files
				if (args[i].startsWith("-i")){
					ArrayList<String> inputfiles = new ArrayList<String>();
					if (args.length<i+1 || args[i+1].startsWith("-")) 
						throw new RuntimeException("Input files missing after -i");
					
					int j = i+1;
					while (j<args.length && !args[j].startsWith("-")){
						inputfiles.add(args[j]);
						j++;
					}
					
					arguments.put(Arguments.INPUT_FILES, inputfiles.toArray(new String[inputfiles.size()]));
				}
				
				if (args[i].startsWith("-pbc")){
					String[] pbcs = new String[3];
					for (int j = 1; j<=3;j++){
						if (args.length<i+j || args[i+j].startsWith("-"))
							throw new RuntimeException("PBCs missing after -pbc");
						if (!args[i+j].equals("1") && !args[i+j].equals("0"))
							throw new RuntimeException("PBCs must be either 0 or 1");
						pbcs[j-1] = args[i+j];
					}
						
					arguments.put(Arguments.PBC, pbcs);
				}
				
			}
		}
		return arguments;
	}
}
