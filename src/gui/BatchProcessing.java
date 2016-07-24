package gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.management.RuntimeErrorException;
import javax.swing.SwingWorker;

import model.Atom;
import model.AtomData;
import model.Configuration;
import model.Filter;
import model.ImportConfiguration;
import model.RenderingConfiguration;
import model.io.CfgFileLoader;
import model.io.ImdFileLoader;
import model.io.ImdFileWriter;
import model.io.LammpsAsciiDumpLoader;
import model.io.MDFileLoader;
import model.io.XYZFileLoader;
import processingModules.DataContainer;
import processingModules.ProcessingModule;
import processingModules.skeletonizer.Skeletonizer;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchain.ReferenceMode;

public class BatchProcessing {

	private enum Arguments {INPUT_FORMAT, INPUT_FILES, REFERENCE_FILE, CRYSTAL_CONF, PBC, OUTPUT_PATTERN, OUTPUT_FORMAT, TOOLCHAIN}
	
	public void processBatch(String[] args){
		
		
		/*
		 * -if: input format: valid 
		 * -of: out
		 */
		
		try {
			HashMap<Arguments, String[]> arguments = this.splitCommandLineArguemnts(args);
			
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
			
			//TODO load reference file if requested
			//TODO loop over all files
			
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
			

			ic.readConfigurationFile(new File(args[2]));
			File inputFile = new File(args[1]);

			Configuration.create();
			
			Configuration.setLastOpenedFolder(inputFile.getParentFile());
			Filter<Atom> filter = ImportConfiguration.getInstance().getCrystalStructure().getIgnoreAtomsDuringImportFilter();
			
			AtomData data = fileLoader.readInputData(inputFile, null, filter);
			
			if (toolchain != null){
				for (ProcessingModule pm : toolchain.getProcessingModules()){
					data.applyProcessingModule(pm);
				}
			}
			
			
			//TODO change the output system
			boolean binaryFormat = true;
			boolean exportDislocations = false;
			
			if (args.length > 5){
				for (int i=5; i<args.length; i++){
					if (args[i].equals("-A"))
						binaryFormat = false;
					if (args[i].equals("-DN"))
						exportDislocations = true;
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
			
			String outfile = args[4];
			ImdFileWriter writer = new ImdFileWriter(binaryFormat, binaryFormat);
			writer.writeFile(null, outfile, data, null);
			
			DataContainer dc = Configuration.getCurrentAtomData().getDataContainer(Skeletonizer.class);
			Skeletonizer skel = null;
			if (dc != null)
				skel = (Skeletonizer)dc;
			
			if (exportDislocations && skel != null){
				outfile = args[4]+"_dislocation.txt";
				skel.writeDislocationSkeleton(new File(outfile));
			}
		} catch (Exception e) {
			if (args.length < 5 || !args[0].equals("-h")){
				System.out.println("*************************************************");
				System.out.println("ERROR: Insufficient parameters for batch processing");
				System.out.println("USAGE: -b <inputFile> <crystalConfFile> <viewerConfFile> <outputFile> -options1 -option2...");
				System.out.println();
				System.out.println("<inputFile>: Input file");
				System.out.println("<crystalConfFile>: File containing the crystal information (usually named crystal.conf)");
				System.out.println("<outputFile>: Output file");
				System.out.println("Further options");
				System.out.println("-A: Write output in ASCII format and not in binary");
				System.out.println("-DN: Write dislocation network (if created)");
				System.out.println("*************************************************");
				System.exit(1);
			}
		}
		
		System.exit(0);
	}
	
	private HashMap<Arguments, String[]> splitCommandLineArguemnts(String[] args) throws RuntimeException{
		HashMap<Arguments, String[]> arguments = new HashMap<BatchProcessing.Arguments, String[]>();
		ArrayList<String> argsToParameter = new ArrayList<String>();
		
		for (int i=0; i<args.length; i++){
			if (args[i].startsWith("-")){
				
			}
		}
		return arguments;
	}
}
