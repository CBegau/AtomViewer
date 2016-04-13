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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Properties;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import common.CommonUtils;
import gui.PrimitiveProperty;


public class CrystalStructureProperties {
	
	public static JPanel createPropertyContainer(Collection<PrimitiveProperty<?>> properties){
		JPanel configPanel = new JPanel(new GridBagLayout());
		configPanel.setBorder(new TitledBorder(new EtchedBorder(1), "Crystal structure options"));
		
		GridBagConstraints gbc = CommonUtils.getBasicGridBagConstraint();
		
		JPanel innerPanel = new JPanel();
		innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
		for (final PrimitiveProperty<?> p : properties)
			innerPanel.add(p);
		
		gbc.weighty = 0;
		configPanel.add(innerPanel, gbc); gbc.gridy++;
		gbc.weighty = 1;
		configPanel.add(Box.createVerticalGlue(), gbc);
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
