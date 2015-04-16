package gui;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import model.RenderingConfiguration;

/**
 * Displays a progress bar suitable for a background task in a swing worker
 * Events to update the state of this dialog should be fired using an instance of @link{ProgressMonitor}
 * @author Christoph Begau
 *
 */
public class JProgressDisplayDialog extends JDialog implements PropertyChangeListener{
	private static final long serialVersionUID = 1L;
	private JProgressBar progressBar = new JProgressBar();
	private JProgressBar operationProgressBar = new JProgressBar(0, 100);
	
	public JProgressDisplayDialog(final SwingWorker<?,?> worker, Frame owner) {
		this(worker, owner, true);
	}
	
	public JProgressDisplayDialog(final SwingWorker<?,?> worker, Frame owner, final boolean cancellable) {
		super(owner);
		worker.addPropertyChangeListener(this);
		this.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent evt){
            	if (cancellable) worker.cancel(true);
            }
        });
		this.setLayout(new GridLayout(2+(cancellable?1:0),1));
		this.add(progressBar);
		progressBar.setStringPainted(true);
		operationProgressBar.setStringPainted(true);
		operationProgressBar.setIndeterminate(true);
		progressBar.setString("");
		
		this.add(operationProgressBar);
		if (cancellable){
			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					worker.cancel(true);
				}
			});
			this.add(cancelButton);
		}
		
		float factor = RenderingConfiguration.getGUIScalingFactor(); 
		
		this.setSize(new Dimension((int)(factor*400),(int)(factor*100)));
		this.setResizable(false);
		this.setModalityType(ModalityType.TOOLKIT_MODAL);
		
		GraphicsDevice gd = this.getGraphicsConfiguration().getDevice();
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if ("progress" == evt.getPropertyName()) {
			String progressing = evt.getNewValue().toString();
			progressBar.setString(progressing);
		}
		if ("title" == evt.getPropertyName()) {
			String title = evt.getNewValue().toString();
			this.setTitle(title);
		}
		if ("operation" == evt.getPropertyName()) {
			String operation = evt.getNewValue().toString();
			operationProgressBar.setString(operation);
		}
		
		if ("operation_progress" == evt.getPropertyName()) {
			int progress = ((Number)evt.getNewValue()).intValue();
			operationProgressBar.setIndeterminate(false);
			operationProgressBar.setValue(progress);
		}
		
		if ("operation_no_progress" == evt.getPropertyName()) {
			operationProgressBar.setValue(0);
			operationProgressBar.setIndeterminate(true);
		}
		
		if ("destroy" == evt.getPropertyName()) {
			this.dispose();
		}
	}
}