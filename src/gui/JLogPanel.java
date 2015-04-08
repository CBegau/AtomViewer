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

package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;
import javax.swing.text.BadLocationException;

public class JLogPanel extends JPanel{
	
	private static final long serialVersionUID = 1L;
	private JTextArea log = new JTextArea();
	private static JLogPanel logPanel = new JLogPanel();
	private JScrollPane scrollPane = new JScrollPane(log);
	private JButton clearButton = new JButton("<html><body>Clear<br>Log</body></html>");
	private static final int MAX_TEXT_LENGHT = 1000000;
	
	public static JLogPanel getJLogPanel(){
		return logPanel;
	}
	
	private JLogPanel() {
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		this.setLayout(new BorderLayout());
		this.add(scrollPane, BorderLayout.CENTER);
		this.add(clearButton, BorderLayout.EAST);
		this.log.setEditable(false);
		this.clearButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				JLogPanel.this.clearLog();
			}
		});
		
		scrollPane.setPreferredSize(new Dimension(400,300));
	}
	
	public void clearLog(){
		log.setText("");
	}
	
	public void addLog(String s){
		log.insert(s+"\n", 0);
		log.setCaretPosition(0);
		
		if (log.getDocument().getLength() > MAX_TEXT_LENGHT) try {
			log.getDocument().remove(MAX_TEXT_LENGHT, log.getDocument().getLength()-MAX_TEXT_LENGHT);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}
}
