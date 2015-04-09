package processingModules;

import model.AtomData;
import model.RenderingConfiguration;

public class RemoveInvisibleAtomsModule extends FilteringModule {	
	public RemoveInvisibleAtomsModule() {
		super(RenderingConfiguration.getViewer().getCurrentAtomFilter());
	}
	
	@Override
	public String getShortName() {
		return "Delete non-visible atoms";
	}

	@Override
	public String getFunctionDescription() {
		return "Deletes all atoms that are currently made not visible. <br> This operation is not revertable.";
	}
	
	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		ProcessingResult pr = super.process(data); 
		data.countAtomTypes();
		return pr;
	}
}
