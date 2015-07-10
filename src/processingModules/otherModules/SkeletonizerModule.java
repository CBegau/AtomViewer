package processingModules.otherModules;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.JPrimitiveVariablesPropertiesDialog.FloatProperty;
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
		Skeletonizer dc = new Skeletonizer(meshingThreshold);
		dc.processData(data);
		return new DataContainer.DefaultDataContainerProcessingResult(dc, "");
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
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.meshingThreshold = meshThreshold.getValue(); 
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
