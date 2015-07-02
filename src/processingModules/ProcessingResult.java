package processingModules;

import javax.swing.JLabel;
import javax.swing.JPanel;

public abstract class ProcessingResult {

	public JPanel getResultInJPanel(){
		JPanel p = new JPanel();
		p.add(new JLabel(getResultInfoString()));
		return p;
	}
	
	public abstract String getResultInfoString();

	/**
	 * Returns an instance of a DataContainer if this is 
	 * availabale. Otherwise the returned value is null
	 * @return
	 */
	public abstract DataContainer getDataContainer();
}
