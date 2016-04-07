package processingModules.otherModules;

import model.AtomData;
import model.RenderingConfiguration;
import processingModules.ProcessingResult;

//TODO Add toolchain support once the handling of filters is properly implemented
public class RemoveInvisibleAtomsModule extends FilteringModule {	
	public RemoveInvisibleAtomsModule() {
		super(RenderingConfiguration.getAtomFilterset());
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
		return pr;
	}
}
