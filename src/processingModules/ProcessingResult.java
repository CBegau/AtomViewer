package processingModules;

import javax.swing.JLabel;
import javax.swing.JPanel;

import model.dataContainer.DataContainer;

public abstract class ProcessingResult {

	public JPanel getResultInJPanel(){
		JPanel p = new JPanel();
		p.add(new JLabel(getResultInfoString()));
		return p;
	}
	
	public abstract String getResultInfoString();

	public abstract DataContainer getDataContainer();
}
