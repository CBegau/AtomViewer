package model.dataContainer;

import gui.JColorSelectPanel;
import gui.RenderRange;
import gui.ViewerGLJPanel;
import gui.glUtils.ObjectRenderData;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL3;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.Vec3;
import model.BoxParameter;
import model.DataColumnInfo;
import model.Pickable;
import model.RenderingConfiguration;

public abstract class ParticleDataContainer<T extends Vec3 & Pickable> extends DataContainer{
	protected ArrayList<T> particles = new ArrayList<T>();
	protected List<DataColumnInfo> particleDataColumns = new ArrayList<DataColumnInfo>();
	protected ObjectRenderData<T> ord;
	
	@Override
	public boolean isTransparenceRenderingRequired() {
		return false;
	}
	
	protected void updateRenderData(BoxParameter box){
		if (RenderingConfiguration.isHeadless());
		ord = new ObjectRenderData<T>(particles, true, box);
		float size = getParticleDataControlPanel().getParticleSize();
		float[] col = getParticleDataControlPanel().getColor();
		
		for (ObjectRenderData<T>.Cell c: ord.getRenderableCells()){
			for (int i = 0; i < c.getNumObjects(); i++) {
				c.getColorArray()[3*i+0] = col[0];
				c.getColorArray()[3*i+1] = col[1];
				c.getColorArray()[3*i+2] = col[2];
				c.getSizeArray()[i] = size;
				c.getVisibiltyArray()[i] = true;
			}
		}
	}
	
	@Override
	public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		if (!getDataControlPanel().isDataVisible()) return;
		
		float[] col = getParticleDataControlPanel().getColor();
		if (viewer.isUpdateRenderContent()){
			float size = getParticleDataControlPanel().getParticleSize();
			for (ObjectRenderData<T>.Cell c: ord.getRenderableCells()){
				for (int i = 0; i < c.getNumObjects(); i++) {
					T v = c.getObjects().get(i);
					if (renderRange.isInInterval(v)){
						c.getVisibiltyArray()[i] = true;
						c.getSizeArray()[i] = size;
						c.getColorArray()[3*i+0] = col[0];
						c.getColorArray()[3*i+1] = col[1];
						c.getColorArray()[3*i+2] = col[2];
					}
					else c.getVisibiltyArray()[i] = false;
				}	
			}
			
			ord.reinitUpdatedCells();
		}
		viewer.drawSpheres(gl, ord, picking);
	}
	
	@Override
	public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		return;
	}

	protected abstract JParticleDataControlPanel<?> getParticleDataControlPanel();
	
	public List<T> getParticles(){
		return particles;
	}
	
	protected abstract String getLabelForControlPanel();
	
	public static class JParticleDataControlPanel<T> extends JDataPanel {
		private static final long serialVersionUID = 1L;
		private JCheckBox showParticlesCheckbox = new JCheckBox("Show", false);
		
		private ViewerGLJPanel viewer;
		private JColorSelectPanel colorSelectPanel;
		
		private JLabel numParticlesLabel;
		private float particleSize = 1.5f;
		private float[] color;
		
		public JParticleDataControlPanel(ParticleDataContainer<?> container, float[] color, float partSize) {
			this.color = color;
			this.particleSize = partSize;
			this.setBorder(new TitledBorder(new EtchedBorder(1), container.getLabelForControlPanel()));
			
			this.setLayout(new GridBagLayout());
			
			GridBagConstraints gbc = new GridBagConstraints();
			
			gbc.anchor = GridBagConstraints.WEST;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.gridy = 0; gbc.gridx = 0;
			
			numParticlesLabel = new JLabel(container.particles.size()+" elements");
			this.add(numParticlesLabel, gbc); gbc.gridx++;
			this.add(showParticlesCheckbox, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			colorSelectPanel = new JColorSelectPanel(color, new Color(color[0], color[1], color[2]));
			
			this.add(new JLabel("Color"), gbc); gbc.gridx++;
			this.add(colorSelectPanel, gbc);
			
			gbc.gridx = 0; gbc.gridy++;
			this.add(new JLabel("Size"), gbc); gbc.gridx++;
			final JSpinner sphereSizeSpinner = 
					new JSpinner(new SpinnerNumberModel(particleSize, 0.1, 10., 0.01));
			this.add(sphereSizeSpinner, gbc);
			
			sphereSizeSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					particleSize = ((Number)sphereSizeSpinner.getModel().getValue()).floatValue();
					viewer.updateAtoms();
				}
			});
			
			showParticlesCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					viewer.updateAtoms();
				}
			});
		}
		
		public float getParticleSize() {
			return particleSize;
		}
		
		public void setParticleSize(float particleSize) {
			this.particleSize = particleSize;
			this.viewer.updateAtoms();
		}
		
		public float[] getColor() {
			return color;
		}

		@Override
		public void setViewer(ViewerGLJPanel viewer) {
			this.viewer = viewer;
		}
		
		public boolean isDataVisible(){
			return showParticlesCheckbox.isSelected();
		}
		
		@Override
		public void update(DataContainer dc) {
			assert (dc instanceof ParticleDataContainer<?>);
			ParticleDataContainer<?> container = (ParticleDataContainer<?>)dc;
			numParticlesLabel.setText(container.particles.size()+" elements");
			numParticlesLabel.repaint();
		}
	}
}
