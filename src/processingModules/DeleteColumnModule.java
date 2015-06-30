package processingModules;

import java.util.List;

import javax.swing.JFrame;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.AtomData;
import model.Configuration;
import model.DataColumnInfo;
import processingModules.Toolchainable.ToolchainSupport;

@ToolchainSupport
public class DeleteColumnModule implements ProcessingModule, Toolchainable{
	
	DataColumnInfo toRemove;
	
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
	public void importParameters(XMLStreamReader reader) throws XMLStreamException {
		if (!reader.getElementText().equals("toRemoveColumn")) throw new XMLStreamException("Illegal element detected");
		String id = reader.getAttributeValue(null, "id");
		
		
		List<DataColumnInfo> dci = Configuration.getCurrentAtomData().getDataColumnInfos();
		for (DataColumnInfo d : dci){
			if (d.getId().equals(id)){
				this.toRemove = d;
				break;
			}
		}
	}
}
