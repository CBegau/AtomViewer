package processingModules;

import java.util.List;

import model.Atom;
import model.AtomData;
import model.AtomFilter;
import model.DataColumnInfo;

public class FilteringModule implements ProcessingModule {

	private AtomFilter filter;
	
	public FilteringModule(AtomFilter filter) {
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
	public String getRequirementDescription() {
		return null;
	}

	@Override
	public boolean isApplicable() {
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public void process(AtomData data) throws Exception {
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

	}
}
