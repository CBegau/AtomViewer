// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2016  ICAMS, Ruhr-Universität Bochum
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

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import model.AtomData;
import model.Configuration;

public class JOrderAtomDataSet extends JDialog {
	private static final long serialVersionUID = 1L;
	
	public JOrderAtomDataSet(Frame owner) {
		super(owner);
		this.setTitle("Atom data sets");
		this.setLayout(new BorderLayout());
		
		this.setPreferredSize(new Dimension(500, 400));
		final AtomDataTableModel model = new AtomDataTableModel();
		
		final JTable atomDataTable = new JTable(model){
			private static final long serialVersionUID = 1L;

			public String getToolTipText(MouseEvent e) {
				java.awt.Point p = e.getPoint();
		        int rowIndex = rowAtPoint(p);
		        
		        if (rowIndex == -1) return "";
		        else return model.atomDataList.get(rowIndex).getFullPathAndFilename(); 
			}
		};
		
		JScrollPane tableScrollPane = new JScrollPane(atomDataTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		atomDataTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		
		this.add(tableScrollPane, BorderLayout.CENTER);
		
		JPanel wrapperPanel = new JPanel();
		wrapperPanel.setLayout(new BoxLayout(wrapperPanel, BoxLayout.Y_AXIS));
		wrapperPanel.add(Box.createVerticalGlue());
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(3,1));
		JButton upButton = new JButton("\u25b2");
		JButton delButton = new JButton("\u2718");
		delButton.setToolTipText("Delete the selected atom data sets");
		JButton downButton = new JButton("\u25bc");
		buttonPanel.add(upButton);
		buttonPanel.add(downButton);
		buttonPanel.add(delButton);
		wrapperPanel.add(buttonPanel);
		wrapperPanel.add(Box.createVerticalGlue());
		
		this.add(wrapperPanel, BorderLayout.EAST);
		
		upButton.addActionListener(l -> {
			int start = atomDataTable.getSelectedRow();
			if (start != -1 && start!=0){
				int count = atomDataTable.getSelectedRowCount();
				model.moveSelected(start, count, start-1);
				atomDataTable.getSelectionModel().setSelectionInterval(start-1, start-2+count);
			}
		});
		
		downButton.addActionListener(l -> {
			int start = atomDataTable.getSelectedRow();
			int count = atomDataTable.getSelectedRowCount();
			if (start != -1 && start+count<atomDataTable.getRowCount()){
				model.moveSelected(start, count, start+1);
				atomDataTable.getSelectionModel().setSelectionInterval(start+1, start+count);
			}
		});
		
		delButton.addActionListener(l -> {
			int selectionStart = atomDataTable.getSelectedRow();
			if (selectionStart != -1){
				int selectionCount = atomDataTable.getSelectedRowCount();
				model.deleteSelected(selectionStart, selectionCount);
			}			
		});
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(1, 2));
		JButton okButton = new JButton("OK");
		JButton cancelButton = new JButton("Cancel");
		p.add(okButton);
		p.add(cancelButton);
		this.add(p, BorderLayout.SOUTH);
		
		cancelButton.addActionListener(l -> dispose());
		
		okButton.addActionListener(l -> {
			//rebuild the chain of data
			AtomData current = Configuration.getCurrentAtomData();
			boolean currentFound = false;
			
			AtomData previous = null;
			for (AtomData d : model.atomDataList){
				d.setPrevious(previous);
				previous = d;
				if (d == current)
					currentFound = true;
				d.setNextToNull();
			}
			
			if(!currentFound){
				if (!model.atomDataList.isEmpty()){
					current = model.atomDataList.get(0);
				} else current = null;
			}
			Configuration.setCurrentAtomData(current, true, false);
			dispose();
		});
		
		this.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
		this.pack();
		GraphicsDevice gd = owner.getGraphicsConfiguration().getDevice();
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
		this.setVisible(true);
	}
	
	private class AtomDataTableModel extends AbstractTableModel{
		private static final long serialVersionUID = 1L;
		ArrayList<AtomData> atomDataList;
		
		AtomDataTableModel(){
			atomDataList = new ArrayList<AtomData>();
			for (AtomData d : Configuration.getAtomDataIterable())
				atomDataList.add(d);
		}
		
		public void deleteSelected(int selectionStart, int selectionCount) {
			atomDataList.subList(selectionStart, selectionStart+selectionCount).clear();
			this.fireTableDataChanged();
		}
		
		public void moveSelected(int selectionStart, int selectionCount, int insertAt) {
			if (selectionStart == insertAt) return;
			List<AtomData> sublist = atomDataList.subList(selectionStart, selectionStart+selectionCount);
			List<AtomData> copy = new ArrayList<AtomData>(sublist);
			sublist.clear();

			atomDataList.addAll(insertAt, copy);
			
			this.fireTableDataChanged();
		}

		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public int getRowCount() {
			return atomDataList.size();
		}
		
		@Override
		public String getColumnName(int column) {
			return "Atom data sets";
		}

		@Override
		public Object getValueAt(int arg0, int arg1) {
			assert(arg1 == 0);
			return atomDataList.get(arg0);
		};
	}
}
