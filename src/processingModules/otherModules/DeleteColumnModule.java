package processingModules.otherModules;

import javax.swing.JFrame;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.AtomData;
import model.DataColumnInfo;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchain;
import processingModules.toolchain.Toolchainable;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport
public class DeleteColumnModule extends ClonableProcessingModule implements Toolchainable{
	
	DataColumnInfo toRemove;
	//This is the indicator used for import from a toolchain
	//the column name the toolchain file is referring to might not exist at that moment 
	private String toRemoveID;
	
	public DeleteColumnModule() {}
	
	public DeleteColumnModule(DataColumnInfo toRemove) {
		this.toRemove = toRemove;
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {	
		return true;
	}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Deletes a data column";
	}
	
	@Override
	public boolean isApplicable(AtomData data) {
		//Identify the column by its ID if imported from a toolchain
		if (toRemove == null && toRemoveID != null){
			for (DataColumnInfo d : data.getDataColumnInfos()){
				if (d.getId().equals(toRemoveID)){
					this.toRemove = d;
					return true;
				}
			}
			return false;
		}
		
		return true;
	}
	
	@Override
	public String getRequirementDescription() {
		return "none";
	}
	
	@Override
	public String getShortName() {
		return "Delete data column";
	}
	
	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		data.removeDataColumnInfo(toRemove);
		return null;
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return false;
	}
	
	@Override
	public void exportParameters(XMLStreamWriter xmlOut)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException {
		xmlOut.writeStartElement("toRemoveColumn");
		xmlOut.writeAttribute("id", toRemove.getId());
		xmlOut.writeEndElement();
		
	}
	
	@Override
	public void importParameters(XMLStreamReader reader, Toolchain toolchain) throws XMLStreamException {
		reader.next();
		if (!reader.getLocalName().equals("toRemoveColumn")) throw new XMLStreamException("Illegal element detected");
		this.toRemoveID = reader.getAttributeValue(null, "id");
	}
}
