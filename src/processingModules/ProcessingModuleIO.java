package processingModules;

import java.io.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import java.lang.reflect.Field;
import processingModules.ProcessingParameterExport.ExportableValue;
import processingModules.ProcessingParameterExport.ToolchainSupport;

public class ProcessingModuleIO {
	
	
	
	public void exportProcessingModule(ProcessingModule pm){
		OutputStream outputStream = null;
		XMLStreamWriter out = null;
		
		try{
			outputStream = new FileOutputStream(new File("doc.xml"));
			out = XMLOutputFactory.newInstance().createXMLStreamWriter(new OutputStreamWriter(outputStream, "utf-8"));
			
			out.writeStartDocument();


			Class<?> clz = pm.getClass();

			if (!clz.isAnnotationPresent(ToolchainSupport.class)) return;

			out.writeStartElement("AtomViewerToolchain");
			out.writeStartElement("Module");
			out.writeAttribute("name", clz.getName());
			out.writeAttribute("version", Integer.toString(clz.getAnnotation(ToolchainSupport.class).version()));
			
			//Exporting the attributes that uses custom implementations
			if (ProcessingParameterExport.class.isAssignableFrom(clz)) {
				ProcessingParameterExport ex = (ProcessingParameterExport)pm;
				ex.exportParameters(out);
			}

			//Exporting primitive fields
			Field[] fields = clz.getDeclaredFields();
			for (Field f : fields) {
				if (f.isAnnotationPresent(ExportableValue.class) && f.getType().isPrimitive()) {
					exportPrimitiveField(f, out, pm);
				}
			}
		
			out.writeEndElement();
			out.writeEndElement();
			
			out.writeEndDocument();
			
		} catch (Exception e){
			e.printStackTrace();
		} finally {
			if (out!=null) try {
				out.close();
			} catch (XMLStreamException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void exportPrimitiveField(Field f, XMLStreamWriter out, ProcessingModule pm) throws XMLStreamException, IllegalArgumentException, IllegalAccessException{
		out.writeStartElement("Parameter");
		out.writeAttribute("name",f.getName());
		out.writeAttribute("type",f.getType().getName());
		f.setAccessible(true);
		if (f.getType().equals(Float.TYPE)){
			out.writeAttribute("value", Float.toString(f.getFloat(pm)));
		} else if (f.getType().equals(Double.TYPE)){
			out.writeAttribute("value", Double.toString(f.getDouble(pm)));
		} else if (f.getType().equals(Integer.TYPE)){
			out.writeAttribute("value", Integer.toString(f.getInt(pm)));
		} else if (f.getType().equals(Short.TYPE)){
			out.writeAttribute("value", Short.toString(f.getShort(pm)));
		} else if (f.getType().equals(Long.TYPE)){
			out.writeAttribute("value", Long.toString(f.getLong(pm)));
		} else if (f.getType().equals(Byte.TYPE)){
			out.writeAttribute("value", Byte.toString(f.getByte(pm)));
		} else if (f.getType().equals(Character.TYPE)){
			out.writeAttribute("value", Character.toString(f.getChar(pm)));
		} else if (f.getType().equals(Boolean.TYPE)){
			out.writeAttribute("value", Boolean.toString(f.getBoolean(pm)));
		}
		out.writeEndElement();
	}
	
	private void importPrimitiveField(XMLStreamReader xmlReader, ProcessingModule pm) 
			throws XMLStreamException, NumberFormatException, IllegalArgumentException, 
			IllegalAccessException, SecurityException, NoSuchFieldException{
		if (!xmlReader.getElementText().equals("Parameter")) throw new XMLStreamException("Illegal element detected");
		
		String name = xmlReader.getAttributeValue(null, "name");
		String type = xmlReader.getAttributeValue(null, "type");
		String value = xmlReader.getAttributeValue(null, "value");
		
		Field f = pm.getClass().getField(name);
		f.setAccessible(true);
		
		if (type.equals(Float.TYPE.toString())){
			f.setFloat(f, Float.parseFloat(value));
		} else if (f.getType().equals(Double.TYPE)){
			f.setDouble(f, Double.parseDouble(value));
		} else if (f.getType().equals(Integer.TYPE)){
			f.setInt(f, Integer.parseInt(value));
		} else if (f.getType().equals(Short.TYPE)){
			f.setShort(f, Short.parseShort(value));
		} else if (f.getType().equals(Long.TYPE)){
			f.setLong(f, Long.parseLong(value));
		} else if (f.getType().equals(Byte.TYPE)){
			f.setByte(f, Byte.parseByte(value));
		} else if (f.getType().equals(Character.TYPE)){
			f.setChar(f, (value.isEmpty())?' ':value.charAt(0));
		} else if (f.getType().equals(Boolean.TYPE)){
			f.setBoolean(f, Boolean.parseBoolean(value));
		}
	}
	
}
