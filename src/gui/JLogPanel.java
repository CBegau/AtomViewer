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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import model.RenderingConfiguration;


public class JLogPanel extends JPanel{
	private static final long serialVersionUID = 1L;
	
	private static final int MAX_EVENTS = 1000;
	private static final LogEntry dummyEntry = new LogEntry("No messages", "", LogEntry.Type.INFO);
	
	private JTextPane detailsPane = new JTextPane();
	
	private LogEntryTableModel model = new LogEntryTableModel();
	private JTable logTable = new JTable(model);
	private static JLogPanel logPanel = new JLogPanel();
	
	private JButton clearButton = new JButton("<html><body>Clear<br>Log</body></html>");
	
	public static JLogPanel getJLogPanel(){
		return logPanel;
	}
	
	private JLogPanel() {
		this.logTable.setDefaultRenderer(LogEntry.class, new ColorCellRenderer());
		this.logTable.setTableHeader(null);
		this.logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		
		splitPane.add(new JScrollPane(logTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED));
		splitPane.add(new JScrollPane(detailsPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED));
		splitPane.setResizeWeight(0.2);
		
		this.setLayout(new BorderLayout());
		this.add(splitPane, BorderLayout.CENTER);
		this.add(clearButton, BorderLayout.WEST);
		
		this.detailsPane.setEditable(false);
		this.clearButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				JLogPanel.this.clearLog();
			}
		});
		
		splitPane.setPreferredSize(new Dimension(400,300));
		
		this.logTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				int index = logTable.getSelectedRow();
				if (!e.getValueIsAdjusting() && index != -1){
					detailsPane.setText(model.getEntry(index).detail);
				}
				if (index == -1){
					detailsPane.setText("");
				}
			}
		});
	}
	
	/**
	 * Adds an error message into the LogPanel.
	 * The message should consist of a short title description that is shown in list of messages.
	 * More details can be provided if necessary.
	 * @param error A short error message
	 * @param details optional detail, the text may be formatted using html.
	 * Note: It is not required to wrap the message in html-tags, this is added automatically if needed
	 */
	public void addError(String error, String details){
		model.insertEntry(new LogEntry(error, details, LogEntry.Type.ERROR));
		if (!RenderingConfiguration.isHeadless())
			logTable.getSelectionModel().setSelectionInterval(0, 0);
	}
	
	/**
	 * Adds an info message into the LogPanel.
	 * The message should consist of a short title description that is shown in list of messages.
	 * More details can be provided if necessary.
	 * @param info A short info message
	 * @param details optional detail, the text may be formatted using html.
	 * Note: It is not required to wrap the message in html-tags, this is added automatically if needed
	 */
	public void addInfo(String info, String details){
		model.insertEntry(new LogEntry(info, details, LogEntry.Type.INFO));
		if (!RenderingConfiguration.isHeadless())
			logTable.getSelectionModel().setSelectionInterval(0, 0);
	}
	
	/**
	 * Adds a warning message into the LogPanel.
	 * The message should consist of a short title description that is shown in list of messages.
	 * More details can be provided if necessary.
	 * @param error A short warning message
	 * @param details optional detail, the text may be formatted using html.
	 * Note: It is not required to wrap the message in html-tags, this is added automatically if needed
	 */
	public void addWarning(String warning, String details){
		model.insertEntry(new LogEntry(warning, details, LogEntry.Type.WARNING));
		if (!RenderingConfiguration.isHeadless())
			logTable.getSelectionModel().setSelectionInterval(0, 0);
	}
	
	/**
	 * Removes all entries from the log
	 */
	public void clearLog(){
		model.clearLog();
	}
	
	
	@Deprecated
	public void addLog(String s){
		addInfo("Info",s);
	}
	
	private static class LogEntry {
		enum Type {INFO, WARNING, ERROR};
		String info;
		String detail;
		Type type;
		
		public LogEntry(String info, String detail, Type t) {
			this.type = t;
			this.info = info;
			//replace null by empty string
			this.detail = (detail == null ? "" : detail);
		}
		
		@Override
		public String toString() {
			return info;
		}
	}
	
	private static class ColorCellRenderer extends DefaultTableCellRenderer{
		private static final long serialVersionUID = 1L;
		private Color defaultColor = this.getForeground();
		
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			LogEntry e = (LogEntry)value;
			
			if (e.type == LogEntry.Type.INFO || isSelected)
				this.setForeground(defaultColor);
			else if (e.type == LogEntry.Type.WARNING)
				this.setForeground(Color.YELLOW);
			else if (e.type == LogEntry.Type.ERROR)
				this.setForeground(Color.RED);
			
			if (e.type == LogEntry.Type.INFO)
				this.setForeground(Color.RED);
			
			return this;
		}
		
	}
	
	private class LogEntryTableModel extends AbstractTableModel{
		private static final long serialVersionUID = 1L;

		private boolean noEntry = true;

		private ArrayList<LogEntry> entries = new ArrayList<LogEntry>(MAX_EVENTS);
		
		public LogEntryTableModel() {
			entries.add(dummyEntry);	//Empty list is filled with a dummy entry
		}
		
		@Override
		public int getColumnCount() {
			return 1;
		}

		@Override
		public int getRowCount() {
			return entries.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			return getEntry(rowIndex);
		}
		
		public void clearLog(){
			model.entries.clear();
			noEntry = true;
			entries.add(dummyEntry);  //Empty list is filled with a dummy entry
			if (!RenderingConfiguration.isHeadless()){
				logTable.setRowSelectionInterval(0, 0);
				fireTableDataChanged();
			}
		}
		
		private void insertEntry(LogEntry e){
			if (noEntry) {	//Remove the dummy entry
				entries.clear();
				noEntry = false;
			}
			if (entries.size() == MAX_EVENTS) entries.remove(0);
			entries.add(e);
			
			//If running without user interface, print to stdout/stderr
			if (RenderingConfiguration.isHeadless()){
				if (e.type == LogEntry.Type.ERROR){
					System.err.println("ERROR: "+e.info);
					System.err.println(e.detail);
				} else {
					System.out.println(e.info);
					System.out.println(e.detail);
				}
			} else {
				fireTableRowsInserted(0, 0);
			}				
		}
		
		LogEntry getEntry(int index){
			//Newest entries are at the end of the list, but should be shown in the first place
			return entries.get(entries.size()-1-index);
		}
	}
	
}
