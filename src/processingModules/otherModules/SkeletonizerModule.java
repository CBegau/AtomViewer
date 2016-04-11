package processingModules.otherModules;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.PrimitiveProperty.*;
import model.AtomData;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.ProcessingResult;
import processingModules.skeletonizer.Skeletonizer;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport
public class SkeletonizerModule extends ClonableProcessingModule {

	@ExportableValue
	private float meshingThreshold = -1;
	
	@ExportableValue
	private boolean skeletonizeBetweenGrains = false;
	
	@Override
	public String getFunctionDescription() {
		return "Creates a dislocation network from defects";
	}

	@Override
	public String getShortName() {
		return "Dislocation skeletonizer";
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		Skeletonizer dc = new Skeletonizer(meshingThreshold, skeletonizeBetweenGrains);
		dc.processData(data);
		return new DataContainer.DefaultDataContainerProcessingResult(dc, dc.getFormattedResultInfo());
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(null, getShortName());
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());	
		
		FloatProperty meshThreshold = dialog.addFloat("meshThreshold", "Meshing distance factor for dislocation networks"
				, "<html>During creation fo dislocation networks, a mesh between defect atoms is created.<br>"
						+ "<br> A factor of one usually is equal to the nearest neighbor distance."
						+ "<br> Larger values create smoother dislcoations curves, but can suppress fine details like stair-rods or"
						+ "only slightly seperated partial dislocation cores"
						+ "<br> Min: 1.0, Max: 2.0</html>", 1.1f, 1f, 2f);
		
		BooleanProperty multipleGrains = dialog.addBoolean("skeletonizeGrains", "Create a single dislocation network across multiple grains",
				"If selected, grain or phase boundaries are ignored during creation of a dislocation network. "
				+ "All atoms are treated as if the belong to the same structure", false);
		
		multipleGrains.setEnabled(data.isPolyCrystalline());
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.meshingThreshold = meshThreshold.getValue();
			this.skeletonizeBetweenGrains = multipleGrains.getValue();
		}
		return ok;
	}
	
	@Override
	public String getRequirementDescription(){
		return "Resultant Burgers vectors must be computed for skeletonization";
	}	
	
	@Override
	public boolean isApplicable(AtomData data) {
		return data.isRbvAvailable();
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
}
