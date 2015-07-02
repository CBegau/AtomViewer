package processingModules.toolchain;

import java.io.InputStream;
import java.lang.reflect.Field;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import model.AtomData;
import model.Configuration;
import processingModules.ProcessingModule;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

public class ToolchainReader {

	public boolean applyToolChain(InputStream is, AtomData data) throws XMLStreamException, FactoryConfigurationError{
		try {
		
			XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
			try{
				while (reader.hasNext()){
					reader.next();
					switch (reader.getEventType()){
		
						case XMLStreamReader.START_ELEMENT:{
							if (reader.getLocalName().equals("Module")) 
								processModule(reader, data);
							break;
						}
							
						default: break;
					}
				}
			} catch (Exception e){
				e.printStackTrace();
				return false;
			} finally {
				reader.close();
			}
		
		} catch (Exception e){
			e.printStackTrace();
		}
		
		Configuration.setCurrentAtomData(data, true, false);
		return true;
	}
	
	
	private void processModule(XMLStreamReader reader, AtomData data) throws Exception{
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
						data.applyProcessingModule(pm);
						return;
					}
					break;
				}
					
				default: break;
			}
		}
	}
	
	public static void importPrimitiveField(XMLStreamReader xmlReader, Object module) 
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
		} else throw new IllegalArgumentException("Field is not a primitive type");
	}
}
