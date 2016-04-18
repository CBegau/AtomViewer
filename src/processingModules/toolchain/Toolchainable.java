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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public interface Toolchainable {

	@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@Target( {java.lang.annotation.ElementType.TYPE}) 
	@interface ToolchainSupport {
		int version() default 1;
	}
	
	/**
	 * Indicates that this field is part of the state that needs to be
	 * stored/restored in the Toolchain mechanism
	 * Only allowed on primitive fields and the type {@link Toolchain.ReferenceMode}
	 */
	@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@Target( {java.lang.annotation.ElementType.FIELD})
	@interface ExportableValue{}
	
	void exportParameters(XMLStreamWriter xmlOut) throws XMLStreamException, IllegalArgumentException, IllegalAccessException;
	void importParameters(XMLStreamReader reader, Toolchain toolchain) throws Exception;
	
}
