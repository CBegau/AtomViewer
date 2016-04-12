package processingModules;

/**
 * An abstract class that implements the clone routine of ProcessingModule
 * ProcessingModules that do not extends other classes can extend this class
 * instead of implementing the full interface ProcessingModule

 */
public abstract class ClonableProcessingModule implements ProcessingModule{
	@Override
	public ProcessingModule clone() {
		try {
			return (ProcessingModule)(super.clone());
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return null;
	}
}
