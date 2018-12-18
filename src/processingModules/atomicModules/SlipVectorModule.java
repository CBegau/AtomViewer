package processingModules.atomicModules;

import gui.JLogPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.PrimitiveProperty.*;

import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import common.ThreadPool;
import common.Tupel;
import common.Vec3;
import gnu.trove.map.hash.TIntObjectHashMap;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchain.ReferenceMode;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
 
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
	private float cutoffRadius = 0f;
	@ExportableValue
	private float slipThreshold = 0.5f;
	
	@ExportableValue
	private ReferenceMode referenceMode = ReferenceMode.FIRST;
	
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
		return true;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, getShortName());
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		
		ReferenceModeProperty rp = dialog.addReferenceMode("referenceMode", 
				"Select reference configuration", referenceMode);
		float cutoff = this.cutoffRadius==0f?data.getCrystalStructure().getNearestNeighborSearchRadius():this.cutoffRadius;
		
		FloatProperty cRadius = dialog.addFloat("cutoffRadius", "Cutoff radius for finding neighbors"
				, "", cutoff, 0.01f, 1e20f);
		FloatProperty slipThres = dialog.addFloat("slipThreshold", "Threshold of displacement to be considered as slip"
				, "", this.slipThreshold, 0.0f, 1e20f);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.cutoffRadius = cRadius.getValue();
			this.referenceMode = rp.getValue();
			if (this.referenceMode == ReferenceMode.REF)
				rp.getReferenceAtomData().setAsReferenceForProcessingModule();
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
		
		if (!data.getBox().equals(referenceAtomData.getBox())){
			JLogPanel.getJLogPanel().addWarning("Inaccurate slip vectors",  
					String.format("Box sizes of reference %s is different from %s."
							+ " Slip vectors may be inaccurate.", referenceAtomData.getName(), data.getName()));
		}
		//Test if both sets have the same size
		if (data.getAtoms().size() != referenceAtomData.getAtoms().size())
			throw new RuntimeException(String.format("Cannot compute slip vectors: Number of atoms in %s and reference %s mismatch.", 
					data.getName(), referenceAtomData.getName()));
		
		//Create a look up map based on atom numbers (which must be unique for each atom )
		final TIntObjectHashMap<Atom> atomsMap = new TIntObjectHashMap<Atom>();
		for (Atom a : data.getAtoms())
			atomsMap.put(a.getNumber(), a);
		
		//Test if each atom in the set can be mapped to the reference data
		if (atomsMap.size() != data.getAtoms().size())
			throw new RuntimeException(
					String.format("Cannot compute slip vectors: IDs of atoms in %s are non-unique.", data.getName()));
		for (Atom a : referenceAtomData.getAtoms()){
			if (!atomsMap.containsKey(a.getNumber())){
				throw new RuntimeException(
						String.format("Cannot compute slip vectors: Atom %i in %s cannot be mapped to file %s.", 
								a.getNumber(), referenceAtomData.getName(), data.getName()));
			}
		}
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(referenceAtomData.getBox(),
				cutoffRadius, true);
		nnb.addAll(referenceAtomData.getAtoms());
		
		final float[] sx = data.getDataArray(data.getDataColumnIndex(cci[0])).getData();
		final float[] sy = data.getDataArray(data.getDataColumnIndex(cci[1])).getData();
		final float[] sz = data.getDataArray(data.getDataColumnIndex(cci[2])).getData();
		final float[] sa = data.getDataArray(data.getDataColumnIndex(cci[3])).getData();
		
		ThreadPool.executeAsParallelStream(referenceAtomData.getAtoms().size(), i->{
			Vec3 sum = new Vec3();
			int slipped = 0;

			Atom a = referenceAtomData.getAtoms().get(i);
			Atom a_current = atomsMap.get(a.getNumber());

			ArrayList<Tupel<Atom, Vec3>> neigh = nnb.getNeighAndNeighVec(a);
			if (neigh.size()!=0){
				for (Tupel<Atom,Vec3> t : neigh){
					Atom n_current = atomsMap.get(t.o1.getNumber());
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
			int id = a_current.getID();
			sx[id] = -sum.x;
			sy[id] = -sum.y;
			sz[id] = -sum.z;
			sa[id] = sum.getLength();
		});
		
		return null;
	}

	@Override
	public ReferenceMode getReferenceModeUsed() {
		return referenceMode;
	}
}
