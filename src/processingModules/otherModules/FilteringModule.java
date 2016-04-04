package processingModules.otherModules;

import javax.swing.JFrame;

import model.Atom;
import model.AtomData;
import model.Filter;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import model.DataColumnInfo;

public class FilteringModule extends ClonableProcessingModule {

	private Filter<Atom> filter;
	
	public FilteringModule(Filter<Atom> filter) {
		this.filter = filter;
	}
	
	@Override
	public String getShortName() {
		return "Filter atoms";
	}

	@Override
	public String getFunctionDescription() {
		return "Filter atoms";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public String getRequirementDescription() {
		return null;
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return true;
	}
	
	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		data.removeAtoms(filter);
		return null;
	}
}
