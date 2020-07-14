package processingModules.otherModules;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

import common.Vec3;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.PrimitiveProperty.StringProperty;
import model.*;
import model.BurgersVector.BurgersVectorType;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.skeletonizer.Dislocation;
import processingModules.skeletonizer.SkeletonNode;
import processingModules.skeletonizer.Skeletonizer;

public class KeyenceMarkerExport extends ClonableProcessingModule {
	
	String filename;
	
	@Override
	public ProcessingResult process(AtomData atomData) throws IOException {	
		
		
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
		dialog.add(selectFileButton);
		
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
}
