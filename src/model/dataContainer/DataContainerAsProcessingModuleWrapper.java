// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
//
// AtomViewer is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// AtomViewer is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with AtomViewer. If not, see <http://www.gnu.org/licenses/> 
package model.dataContainer;

import java.lang.reflect.Field;

import javax.swing.JFrame;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.AtomData;
import model.DataColumnInfo;
import processingModules.ProcessingModule;
import processingModules.Toolchain;
import processingModules.Toolchainable;
import processingModules.Toolchainable.ToolchainSupport;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import processingModules.ProcessingResult;

@ToolchainSupport
public class DataContainerAsProcessingModuleWrapper implements ProcessingModule, Toolchainable{

	private DataContainer dc;
	private boolean applicableToMultipleFiles;
	
	public DataContainerAsProcessingModuleWrapper(DataContainer dc, boolean applicableToMultipleFiles){
		this.dc = dc;
		this.applicableToMultipleFiles = applicableToMultipleFiles;
	}
	
	@Override
	public String getShortName() {
		return dc.getName();
	}

	@Override
	public String getFunctionDescription() {
		return dc.getDescription();
	}

	@Override
	public String getRequirementDescription() {
		return dc.getRequirementDescription();
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return dc.isApplicable(data);
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return applicableToMultipleFiles;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return dc.showOptionsDialog();
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		final DataContainer clonedDC = dc.deriveNewInstance();
		boolean success = clonedDC.processData(data);
		if (!success) throw new RuntimeException(String.format("Failed to create %s", dc.getName()));
		
		ProcessingResult pr = new ProcessingResult() {
			
			@Override
			public String getResultInfoString() {
				return clonedDC.getName();
			}
			
			@Override
			public DataContainer getDataContainer() {
				return clonedDC;
			}
		};
		
		return pr;
	}
	
	@Override
	public void exportParameters(XMLStreamWriter xmlOut)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException {
		Class<?> clz = dc.getClass();

		if (!clz.isAnnotationPresent(ToolchainSupport.class)) return;
		
		xmlOut.writeStartElement("DataContainer");
		xmlOut.writeAttribute("name", clz.getName());
		xmlOut.writeAttribute("version", Integer.toString(clz.getAnnotation(ToolchainSupport.class).version()));
		
		//Exporting the attributes that uses custom implementations
		if (Toolchainable.class.isAssignableFrom(clz)) {
			xmlOut.writeStartElement("CustomParameter");
			Toolchainable ex = (Toolchainable)dc;
			ex.exportParameters(xmlOut);
			xmlOut.writeEndElement();
		}

		//Exporting primitive fields
		Field[] fields = clz.getDeclaredFields();
		for (Field f : fields) {
			if (f.isAnnotationPresent(ExportableValue.class) && f.getType().isPrimitive()) {
				Toolchain.exportPrimitiveField(f, xmlOut, dc);
			}
		}
		xmlOut.writeEndElement();
	}
	
	@Override
	public void importParameters(XMLStreamReader reader) throws XMLStreamException {
		//TODO implement
		throw new NotImplementedException();
	}

}
