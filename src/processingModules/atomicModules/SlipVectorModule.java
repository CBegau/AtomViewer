package processingModules.atomicModules;

import gui.JLogPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JSeparator;

import common.ThreadPool;
import common.Tupel;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchain.ReferenceData;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

//TODO handle reference in Toolchain
//Currently a workaround is implemented that always picks the first file in the sequence 
@ToolchainSupport()
public class SlipVectorModule extends ClonableProcessingModule{
	
	private static DataColumnInfo[] cci = { new DataColumnInfo("Slip-Vector", "slip_x", ""),
			new DataColumnInfo("Slip-Vector", "slip_y", ""),
			new DataColumnInfo("Slip-Vector", "slip_z", ""),
			new DataColumnInfo("Slip-Vector (length)" , "slip_abs" ,"")};
	
	static {
		//Define the vector components
		cci[0].setAsFirstVectorComponent(cci[1], cci[2], cci[3], "Slip-Vector");
	}
	
	@ExportableValue
	private float cutoffRadius = 3f;
	@ExportableValue
	private float slipThreshold = 0.5f;
	
	@ExportableValue
	private int referenceMode = 0;
	
	@Override
	public String getShortName() {
		return "Slip Vector";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes the slip vector per atom in comparison to a reference configuration."
				+ "The algorithm uses the formula as given in (Zimmerman et al., Phys.Rev.Lett vol 87, nr. 16)";
	}

	@Override
	public String getRequirementDescription() {
		return "Atoms in the reference configuration must have the same ID"
				+ " as in the file the slip vector is to be computed";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return (data.getNext() != null || data.getPrevious() != null);
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, getShortName());
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		
		JComboBox referenceComboBox = new JComboBox();
		AtomData d = data;
		while (d.getPrevious()!=null) d = d.getPrevious();
		
		do {
			referenceComboBox.addItem(d);
			d = d.getNext();
		} while (d!=null);
		
		dialog.addLabel("Select reference configuration");
		dialog.addComponent(referenceComboBox);
		FloatProperty cRadius = dialog.addFloat("cutoffRadius", "Cutoff radius for finding neighbors"
				, "", data.getCrystalStructure().getNearestNeighborSearchRadius(), 0.01f, 1e20f);
		FloatProperty slipThres = dialog.addFloat("slipThreshold", "Threshold of displacement to be considered as slip"
				, "", 0.5f, 0.0f, 1e20f);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.cutoffRadius = cRadius.getValue();
			this.referenceMode = ReferenceData.REF.getID();
			((AtomData)referenceComboBox.getSelectedItem()).setAsReferenceForProcessingModule();
			this.slipThreshold = slipThres.getValue();
		}
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return cci;
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final AtomData referenceAtomData = Toolchain.getReferenceData(data, referenceMode);
		
		if (data == referenceAtomData) return null;
		
		if (!data.getBox().getBoxSize()[0].equals(referenceAtomData.getBox().getBoxSize()[0]) || 
				!data.getBox().getBoxSize()[1].equals(referenceAtomData.getBox().getBoxSize()[1]) || 
				!data.getBox().getBoxSize()[2].equals(referenceAtomData.getBox().getBoxSize()[2])){
			JLogPanel.getJLogPanel().addLog(String.format("Warning: Slip vectors may be inaccurate. "
					+ "Box sizes of reference %s is different from %s", referenceAtomData.getName(), data.getName()));
		}
		
		if (data.getAtoms().size() != referenceAtomData.getAtoms().size()) 
			throw new RuntimeException(String.format("Cannot compute slip vectors: Number of atoms in %s and reference %s mismatch.", 
					data.getName(), referenceAtomData.getName()));
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(referenceAtomData.getBox(),
				cutoffRadius, true);
		nnb.addAll(referenceAtomData.getAtoms());
		
		final HashMap<Integer, Atom> atomsMap = new HashMap<Integer, Atom>();
		for (Atom a : data.getAtoms())
			atomsMap.put(a.getNumber(), a);
		
		if (atomsMap.size() != data.getAtoms().size())
			throw new RuntimeException(
					String.format("Cannot compute slip vectors: IDs of atoms in %s are non-unique.", data.getName()));
		
		final int sx = data.getIndexForCustomColumn(cci[0]);
		final int sy = data.getIndexForCustomColumn(cci[1]);
		final int sz = data.getIndexForCustomColumn(cci[2]);
		final int sa = data.getIndexForCustomColumn(cci[3]);
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					
					final int start = (int)(((long)referenceAtomData.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)referenceAtomData.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						Vec3 sum = new Vec3();
						int slipped = 0;
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = referenceAtomData.getAtoms().get(i);
						Atom a_current = atomsMap.get(a.getNumber());
						if (a_current == null) throw new RuntimeException(
								String.format("Cannot compute slip vectors: Atom %i in %s cannot be mapped to file %s.", 
										a.getNumber(), referenceAtomData.getName(), data.getName()));
							
						ArrayList<Tupel<Atom, Vec3>> neigh = nnb.getNeighAndNeighVec(a);
						if (neigh.size()!=0){
							for (Tupel<Atom,Vec3> t : neigh){
								Atom n_current = atomsMap.get(t.o1.getNumber());
								if (n_current == null) throw new RuntimeException(
										String.format("Cannot compute slip vectors: Atom %i in %s cannot be mapped to file %s.", 
												a.getNumber(), referenceAtomData.getName(), data.getName()));
								
								//Subtract current distance of the atoms (t.o2) from reference distance and add to sum
								Vec3 slip = t.o2.sub(data.getBox().getPbcCorrectedDirection(n_current, a_current));
								if (slip.getLengthSqr() > slipThreshold*slipThreshold){
									sum.add(slip);
									slipped++;
								}
							}
							if (slipped>0)
								sum.divide(slipped);
						}
						a_current.setData(-sum.x, sx);
						a_current.setData(-sum.y, sy);
						a_current.setData(-sum.z, sz);
						a_current.setData(sum.getLength(), sa);
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		
		return null;
	}

}
