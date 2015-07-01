package processingModules;

import java.io.*;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import gui.JLogPanel;
import model.Configuration;
import model.Configuration.AtomDataChangedEvent;
import model.Configuration.AtomDataChangedListener;
import model.dataContainer.DataContainerAsProcessingModuleWrapper;

import java.lang.reflect.Field;

import processingModules.Toolchainable.ExportableValue;
import processingModules.Toolchainable.ToolchainSupport;

public class ToolchainWriter implements AtomDataChangedListener{
	
	private XMLStreamWriter xmlout = null;
	private boolean toolchainOpen = true;
	
	public ToolchainWriter(OutputStream os) throws UnsupportedEncodingException, XMLStreamException, FactoryConfigurationError{
		xmlout = XMLOutputFactory.newInstance().createXMLStreamWriter(new OutputStreamWriter(os, "utf-8"));
		
		xmlout.writeStartDocument();
		xmlout.writeStartElement("AtomViewerToolchain");
		
		Configuration.addAtomDataListener(this);
	}
	
	public void closeToolChain() throws XMLStreamException{
		if(!toolchainOpen) throw new RuntimeException("Toolchain is already closed");
		
		xmlout.writeEndElement();
		xmlout.writeEndDocument();
		
		xmlout.close();
		toolchainOpen = false;
	}
	
	public boolean isClosed(){
		return !toolchainOpen;
	}
	
	public void exportProcessingModule(ProcessingModule pm)
			throws IllegalArgumentException, XMLStreamException, IllegalAccessException{
		if(!toolchainOpen) throw new RuntimeException("Toolchain is already closed");
		
		Class<?> clz = pm.getClass();
		if (!clz.isAnnotationPresent(ToolchainSupport.class)) return;

		// Wrapped DataContainer have their own IO-Routines
		if (DataContainerAsProcessingModuleWrapper.class.isAssignableFrom(clz)) {
			((Toolchainable) pm).exportParameters(xmlout);
		} else {
			xmlout.writeStartElement("Module");
			xmlout.writeAttribute("name", clz.getName());
			xmlout.writeAttribute("version", Integer.toString(clz.getAnnotation(ToolchainSupport.class).version()));

			// Exporting the attributes that uses custom implementations
			if (Toolchainable.class.isAssignableFrom(clz)) {
				xmlout.writeStartElement("CustomParameter");
				((Toolchainable) pm).exportParameters(xmlout);
				xmlout.writeEndElement();
			}

			// Exporting primitive fields
			Field[] fields = clz.getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(ExportableValue.class) && f.getType().isPrimitive()) {
					exportPrimitiveField(f, xmlout, pm);
				}
			}
			xmlout.writeEndElement();
		}	
	}
	
	@Override
	public void atomDataChanged(AtomDataChangedEvent e) {
		if (!isClosed() && (e.getNewAtomData() == null || e.getOldAtomData() == null || 
				!e.getNewAtomData().equals(e.getOldAtomData()))){
			JLogPanel.getJLogPanel().addLog("Changing the data stopped recording of toolchain");
			try {
				closeToolChain();
			} catch (XMLStreamException ex) {
				ex.printStackTrace();
			}
		};
	}
	
	public static void exportPrimitiveField(Field f, XMLStreamWriter out, Object module)
			throws XMLStreamException, IllegalArgumentException, IllegalAccessException{
		out.writeStartElement("Parameter");
		out.writeAttribute("name",f.getName());
		out.writeAttribute("type",f.getType().getName());
		f.setAccessible(true);
		if (f.getType().equals(Float.TYPE)){
			out.writeAttribute("value", Float.toString(f.getFloat(module)));
		} else if (f.getType().equals(Double.TYPE)){
			out.writeAttribute("value", Double.toString(f.getDouble(module)));
		} else if (f.getType().equals(Integer.TYPE)){
			out.writeAttribute("value", Integer.toString(f.getInt(module)));
		} else if (f.getType().equals(Short.TYPE)){
			out.writeAttribute("value", Short.toString(f.getShort(module)));
		} else if (f.getType().equals(Long.TYPE)){
			out.writeAttribute("value", Long.toString(f.getLong(module)));
		} else if (f.getType().equals(Byte.TYPE)){
			out.writeAttribute("value", Byte.toString(f.getByte(module)));
		} else if (f.getType().equals(Character.TYPE)){
			out.writeAttribute("value", Character.toString(f.getChar(module)));
		} else if (f.getType().equals(Boolean.TYPE)){
			out.writeAttribute("value", Boolean.toString(f.getBoolean(module)));
		} else throw new IllegalArgumentException("Field is not a primitive type");
		out.writeEndElement();
	}
}
