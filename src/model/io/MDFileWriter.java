package model.io;

import java.io.File;
import java.util.List;

import gui.PrimitiveProperty;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.Filter;

public abstract class MDFileWriter {

	public abstract void setDataToExport(boolean number, boolean element, boolean type, 
			boolean rbv, boolean grain, DataColumnInfo ... dci ); 
	
	/**
	 * 
	 * @param path the path in which the file is to be stored
	 * @param filenamePrefix The start of the filename, file endings are to be added by the FileWriter instances
	 * @param data
	 * @param filter
	 * @throws Exception
	 */
	public abstract void writeFile(File path, String filenamePrefix, AtomData data, Filter<Atom> filter) throws Exception;
	
	/**
	 * Returns a list of advanced properties supported by this FileWriter
	 * The key in the maps defines the name of the options, and is interpreted in the
	 * method {@link MDFileWriter#setOptions(String, String)}.
	 * The type should be either "float", "int", "boolean" or "string".
	 * @return
	 */
	public abstract List<PrimitiveProperty<?>> getAdditionalProperties(AtomData data, boolean allFiles);
}
