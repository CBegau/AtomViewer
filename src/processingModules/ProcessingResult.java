package processingModules;

public interface ProcessingResult {
	
	public String getResultInfoString();

	/**
	 * Returns an instance of a DataContainer if this is 
	 * available. Otherwise the returned value is null
	 * @return
	 */
	public DataContainer getDataContainer();
}
