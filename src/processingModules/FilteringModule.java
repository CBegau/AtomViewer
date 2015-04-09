package processingModules;

import java.util.List;

import javax.swing.JFrame;

import model.Atom;
import model.AtomData;
import model.Filter;
import model.DataColumnInfo;

public class FilteringModule implements ProcessingModule {

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
		if (filter == null) return null;
		List<Atom> atoms = data.getAtoms();
		int origSize = atoms.size();
		int size = origSize;
		int i=0;
		while (i<size){
			if (filter.accept(atoms.get(i))){
				i++;
			} else {
				//Replace the not accepted entry by the last
				//element in the list
				atoms.set(i, atoms.get(--size));
			}
		}
		
		for (i = origSize-1; i>=size; i--){
			atoms.remove(i);
		}
		return null;
	}
}
