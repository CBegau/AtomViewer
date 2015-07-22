// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2013  ICAMS, Ruhr-Universität Bochum
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

package crystalStructures;

import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Properties;

import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import gui.PrimitiveProperty;


public class CrystalStructureProperties {
	
	public static JPanel createPropertyContainer(Collection<PrimitiveProperty<?>> properties, Window w){
		JPanel configPanel = new JPanel(new GridLayout(properties.size(), 1));
		
		configPanel.setBorder(new TitledBorder(new EtchedBorder(1), "Crystal structure options"));
		for (final PrimitiveProperty<?> c : properties)
			configPanel.add(PrimitiveProperty.getControlPanelForProperty(c, w));
		
		return configPanel;
	}
	
	public static void readProperties(Collection<PrimitiveProperty<?>> properties, Reader r) throws IOException{
		Properties prop = new Properties();
		prop.load(r);
		for (PrimitiveProperty<?> p : properties){
			p.load(prop);
		}
	}
	
	public static void storeProperties(Collection<PrimitiveProperty<?>> properties, Writer w) throws IOException{
		Properties prop = new Properties();
		for (PrimitiveProperty<?> p : properties){
			p.save(prop);
		}
		prop.store(w, "CrystalStructureOptions");
	}
}
