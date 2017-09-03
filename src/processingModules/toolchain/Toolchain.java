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

package processingModules.toolchain;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import model.AtomData;
import processingModules.ProcessingModule;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

public class Toolchain {
	
	public enum ReferenceMode {FIRST("First file in sequence"), LAST("Last file in sequence"), 
		NEXT("Next file in sequence"), PREVIOUS("Previous file in sequence"), REF("Select a file");
		private String displayedName;
				
		private ReferenceMode(String displayedName){
			this.displayedName = displayedName;
		}
		
		@Override
		public String toString() {
			return displayedName;
		}
	};
	
	public static AtomData getReferenceData(AtomData currentData, ReferenceMode mode){
		AtomData referenceData = null;
		switch(mode){
			case FIRST: {
				referenceData = currentData;
				while (referenceData.getPrevious() != null) referenceData = referenceData.getPrevious();
				break;
			}
			case LAST: {
				referenceData = currentData;
				while (referenceData.getNext() != null) referenceData = referenceData.getNext();
				break;
			}
			case NEXT:{
				referenceData = currentData.getNext()==null ? currentData : currentData.getNext();
				break;
			}
			case PREVIOUS:{
				referenceData = currentData.getPrevious()==null ? currentData : currentData.getPrevious();
				break;
			}
			case REF:{
				referenceData = currentData;
				while(referenceData.getPrevious()!=null) referenceData = referenceData.getPrevious();
				while(referenceData!=null){
					if (referenceData.isReferenceForProcessingModule())
						return referenceData;
					referenceData = referenceData.getNext();
				}
				break;
			}
		}
		return referenceData;
	}
	
	private List<ProcessingModule> processingModules;
	
	public Toolchain(){
		processingModules = new ArrayList<ProcessingModule>();
	}
	
	public void addModule(ProcessingModule pm){
		this.processingModules.add(pm);
	}
	
	public List<ProcessingModule> getProcessingModules() {
		return Collections.unmodifiableList(processingModules);
	}
	
	/**
	 * Saves the current toolchain in xml-format to the given output stream
	 * @param os stream to write output
	 * @return true if toolchain is saved completly
	 * @throws Exception
	 */
	public boolean saveToolchain(OutputStream os) throws Exception {
		XMLStreamWriter xmlout = XMLOutputFactory.newInstance().createXMLStreamWriter(new OutputStreamWriter(os, "utf-8"));
		boolean writeComplete = true;
		try {
			xmlout.writeStartDocument();
			xmlout.writeStartElement("AtomViewerToolchain");
			
			for (ProcessingModule pm : processingModules){
				boolean s = exportProcessingModule(xmlout, pm);
				if (!s) {
					writeComplete = false;
					break;
				}
			}
			
			xmlout.writeEndElement();
			xmlout.writeEndDocument();
		
		} catch (Exception e){
			throw e;
		} finally {
			xmlout.close();
		}
		
		return writeComplete;
	}
	
	/**
	 * Reads a toolchain from an input stream
	 * @param is stream to read input. The stream itself is not closed
	 * @return the toolchain read from file
	 * @throws Exception
	 */
	public static Toolchain readToolchain(InputStream is) throws Exception{
		Toolchain tc = new Toolchain();
		
		try {
			XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
			try{
				while (reader.hasNext()){
					reader.next();
					switch (reader.getEventType()){
		
						case XMLStreamReader.START_ELEMENT:{
							if (reader.getLocalName().equals("Module")) 
								tc.readProcessModule(reader);
							break;
						}
							
						default: break;
					}
				}
			} catch (Exception e){
				throw e;
			} finally {
				reader.close();
			}
		
		} catch (Exception e){
			throw e;
		}
		
		return tc;
	}
	
	private boolean exportProcessingModule(XMLStreamWriter xmlout, ProcessingModule pm)
			throws IllegalArgumentException, XMLStreamException, IllegalAccessException{
		Class<?> clz = pm.getClass();
		//TODO handle not toolchainable modules properly
		if (!clz.isAnnotationPresent(ToolchainSupport.class)) return true;

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
			if (f.isAnnotationPresent(ExportableValue.class)) {
				exportPrimitiveField(f, xmlout, pm);
			}
		}
		xmlout.writeEndElement();
		
		return true;
	}
	
	private void readProcessModule(XMLStreamReader reader) throws Exception{
		String module = reader.getAttributeValue(null, "name");
		
		Class<?> clz = Class.forName(module);
		ProcessingModule pm = (ProcessingModule)clz.newInstance();
		
		int version = Integer.parseInt(reader.getAttributeValue(null, "version"));
		int requiredVersion = clz.getAnnotation(ToolchainSupport.class).version();
		if (version != requiredVersion) 
			throw new IllegalArgumentException(
					String.format("Version differ for module %s: required %i existing %i"
							, module, requiredVersion, version)); 
		
		while (reader.hasNext()){
			reader.next();
			switch (reader.getEventType()){
				case XMLStreamReader.START_ELEMENT:{
					if (reader.getLocalName().equals("Parameter"))
						importPrimitiveField(reader, pm);
					else if (reader.getLocalName().equals("CustomParameter"))
						((Toolchainable)pm).importParameters(reader, null);
					break;
				}
				case XMLStreamReader.END_ELEMENT:{
					if (reader.getLocalName().equals("Module")){
						this.addModule(pm);
						return;
					}
					break;
				}
					
				default: break;
			}
		}
		
		throw new Exception("End element of module not found");
	}
	
	/**
	 * Exports a primitive field or an a field of type {@link Toolchain.ReferenceMode}
	 * @param f
	 * @param out
	 * @param module
	 * @throws XMLStreamException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	static void exportPrimitiveField(Field f, XMLStreamWriter out, Object module)
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
		} else if (f.getType().equals(Toolchain.ReferenceMode.class)){
			out.writeAttribute("value", ((Toolchain.ReferenceMode)(f.get(module))).name());
		} else throw new IllegalArgumentException("Field is not a primitive type");
		out.writeEndElement();
	}
	

	static void importPrimitiveField(XMLStreamReader xmlReader, Object module) 
			throws XMLStreamException, NumberFormatException, IllegalArgumentException, 
			IllegalAccessException, SecurityException, NoSuchFieldException{
		if (!xmlReader.getLocalName().equals("Parameter")) throw new XMLStreamException("Illegal element detected");
		
		String name = xmlReader.getAttributeValue(null, "name");
		String type = xmlReader.getAttributeValue(null, "type");
		String value = xmlReader.getAttributeValue(null, "value");
		
		Field f = module.getClass().getDeclaredField(name);
		f.setAccessible(true);
		
		if (type.equals(Float.TYPE.toString())){
			f.setFloat(module, Float.parseFloat(value));
		} else if (f.getType().equals(Double.TYPE)){
			f.setDouble(module, Double.parseDouble(value));
		} else if (f.getType().equals(Integer.TYPE)){
			f.setInt(module, Integer.parseInt(value));
		} else if (f.getType().equals(Short.TYPE)){
			f.setShort(module, Short.parseShort(value));
		} else if (f.getType().equals(Long.TYPE)){
			f.setLong(module, Long.parseLong(value));
		} else if (f.getType().equals(Byte.TYPE)){
			f.setByte(module, Byte.parseByte(value));
		} else if (f.getType().equals(Character.TYPE)){
			f.setChar(module, (value.isEmpty())?' ':value.charAt(0));
		} else if (f.getType().equals(Boolean.TYPE)){
			f.setBoolean(module, Boolean.parseBoolean(value));
		} else if (f.getType().equals(Toolchain.ReferenceMode.class)){
			f.set(module, ReferenceMode.valueOf(value));
		} else throw new IllegalArgumentException("Field is not a primitive type");
	}
}
