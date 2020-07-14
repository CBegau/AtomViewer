package processingModules.otherModules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import common.Vec3;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.PrimitiveProperty.StringProperty;
import model.*;
import model.DefectMarking.MarkedArea;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;

public class KeyenceMarkerExport extends ClonableProcessingModule {
	
	File file;
	
	@Override
	public ProcessingResult process(AtomData atomData) throws IOException {
		if (atomData.getPrevious() != null)
			return null;
		
		AtomData ad = atomData;
		try {
			XMLStreamWriter xmlout = XMLOutputFactory.newInstance().createXMLStreamWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
			
			xmlout.writeStartDocument();
			xmlout.writeStartElement("KeyenceDefects");
			
			while (ad!=null) {
				DefectMarking df = ad.getDefectMarking();
				if (df != null) {
					df.closeCurrentMarkedArea();
					
					if (df.getMarks().size()>0) {
						xmlout.writeStartElement("File");
						xmlout.writeStartElement("Name");
						xmlout.writeCharacters(ad.getName());
						xmlout.writeEndElement();
						
						for (MarkedArea ma : df.getMarks()) {
							xmlout.writeStartElement("Defect");
							
							for (Vec3 v : ma.getPath()) {
								xmlout.writeStartElement("Path");
								xmlout.writeStartElement("X");
								xmlout.writeCharacters(Float.toString(v.x/ad.getBox().getHeight().x));
								xmlout.writeEndElement();
								xmlout.writeStartElement("Y");
								xmlout.writeCharacters(Float.toString(v.y/ad.getBox().getHeight().y));
								xmlout.writeEndElement();
								xmlout.writeEndElement();
							}
							xmlout.writeEndElement();
						}
					}
						
				}	
						
				
				ad = ad.getNext();
			}
			
			xmlout.writeEndElement();
			xmlout.writeEndDocument();
			xmlout.close();
		} catch (XMLStreamException e) {
			throw new IOException(e.toString());
		}
		return null;
	}

	@Override
	public String getFunctionDescription() {
		return "Export marker positions";
	}
	
	@Override
	public String getShortName() {
		return "Export marker positions";
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}


	@Override
	public String getRequirementDescription() {
		return "";
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Export markers");
		dialog.addLabel(getFunctionDescription());
		
		JButton selectFileButton = new JButton("Select file");
		
		StringProperty sp = dialog.addString("markers.txt", "Filename", "file to save markers to", "markers.txt");
		
		selectFileButton.addActionListener(l -> {
			JFileChooser chooser = new JFileChooser();
			
			int result = chooser.showSaveDialog(frame);
			
			if (result == JFileChooser.APPROVE_OPTION) {
				sp.setValue(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		dialog.addComponent(selectFileButton);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.file = new File(sp.getValue());
		}
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
}
