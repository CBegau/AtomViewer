package processingModules;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.xml.sax.XMLReader;

public interface ProcessingParameterExport {

	@Retention(RetentionPolicy.RUNTIME)
	@Target( {java.lang.annotation.ElementType.TYPE}) 
	@interface ToolchainSupport {
		int version() default 1;
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target( {java.lang.annotation.ElementType.FIELD})
	@interface ExportableValue{}
	
	void exportParameters(XMLStreamWriter xmlOut) throws XMLStreamException, IllegalArgumentException, IllegalAccessException;
	void importParameters(XMLReader reader);
	
}
