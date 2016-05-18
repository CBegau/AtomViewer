package processingModules.otherModules;

import gui.glUtils.BinningRenderer;
import gui.RenderRange;
import gui.ViewerGLJPanel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;

import com.jogamp.opengl.GL3;

import common.CommonUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import model.*;
import model.BinnedData.Bin;
import processingModules.*;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport
public class BinningDataContainer extends DataContainer {

	private static JBinningControlPanel dataPanel = null;	
	
	private static BinningRenderer renderer = new BinningRenderer();
	private static BinnedData binnedData = null;
	
	private static HashMap<DataColumnInfo, float[]> minMaxStorage = new HashMap<DataColumnInfo, float[]>();
	
	public class JBinningControlPanel extends JDataPanel {
		private static final long serialVersionUID = 1L;
		private JSlider transparencySlider = new JSlider(0, 100, 100);
	    private JSpinner lowerLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
	    private JSpinner upperLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
	    
	    private JSpinner xBlocksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5000, 1));
	    private JSpinner yBlocksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5000, 1));
	    private JSpinner zBlocksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5000, 1));
	    
	    private JButton resetButton = new JButton("Auto adjust min/max");
	    JCheckBox filterCheckboxMin = new JCheckBox("Filter <min");
	    JCheckBox filterCheckboxMax = new JCheckBox("Filter >max");
	    JCheckBox inverseFilterCheckbox = new JCheckBox("Inverse filtering");
	    private JComboBox valueComboBox = new JComboBox();
	    private boolean isVisible = false;
	    
	    DataColumnInfo selectedColumn;
	    float transparency = 1f;
	    
		public JBinningControlPanel() {
			super("Binning");
			
			//Create the label table
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			labelTable.put( new Integer( 0 ), new JLabel("Opaque") );
			labelTable.put( new Integer( 100 ), new JLabel("Transparent") );
			
			transparencySlider.setPaintTicks(true);
			transparencySlider.setMajorTickSpacing(100);
			transparencySlider.setLabelTable(labelTable);
			transparencySlider.setPaintLabels(true);
			
			((JSpinner.NumberEditor)lowerLimitSpinner.getEditor()).getFormat().setMaximumFractionDigits(4);
            ((JSpinner.NumberEditor)upperLimitSpinner.getEditor()).getFormat().setMaximumFractionDigits(4);
             
            ((SpinnerNumberModel)(lowerLimitSpinner.getModel())).setMinimum(null);
            ((SpinnerNumberModel)(upperLimitSpinner.getModel())).setMinimum(null);
            ((SpinnerNumberModel)(upperLimitSpinner.getModel())).setMaximum(null);
            ((SpinnerNumberModel)(lowerLimitSpinner.getModel())).setMaximum(null);
            
            filterCheckboxMin.setSelected(RenderingConfiguration.isFilterMin());
            filterCheckboxMax.setSelected(RenderingConfiguration.isFilterMax());
			
			this.setLayout(new GridBagLayout());
			GridBagConstraints gbc = CommonUtils.getBasicGridBagConstraint();
			
			gbc.gridwidth = 2;
			this.add(transparencySlider, gbc); gbc.gridy++;
	        this.add(valueComboBox, gbc); gbc.gridy++;
	        
	        JPanel cont = new JPanel();
            cont.setLayout(new GridLayout(2,3));
            cont.add(new JLabel("x")); cont.add(new JLabel("y")); cont.add(new JLabel("z"));
            cont.add(xBlocksSpinner); cont.add(yBlocksSpinner); cont.add(zBlocksSpinner);
            gbc.gridwidth = 2;
            this.add(cont, gbc); gbc.gridy++;
	        
	        gbc.gridwidth = 1;
	        this.add(new JLabel("Min."), gbc); gbc.gridx++;
	        this.add(new JLabel("Max."), gbc); gbc.gridx = 0; 
	        gbc.gridy++;
	        this.add(lowerLimitSpinner, gbc); gbc.gridx++;
	        this.add(upperLimitSpinner, gbc); gbc.gridx = 0;
	        gbc.gridy++;
	        
	        gbc.gridwidth = 1;
	        this.add(filterCheckboxMin, gbc); gbc.gridx++;
	        this.add(filterCheckboxMax, gbc); gbc.gridx = 0;
	        gbc.gridy++;
	        this.add(inverseFilterCheckbox, gbc); gbc.gridx = 0;
	        gbc.gridy++;
	        
	        gbc.gridwidth = 2;
	        this.add(resetButton, gbc); gbc.gridy++;
	        
	        ChangeListener subdivideChangeListener = new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    binnedData = computeBin(Configuration.getCurrentAtomData());
                    if (isVisible) RenderingConfiguration.getViewer().reDraw();
                }
            };
	        xBlocksSpinner.addChangeListener(subdivideChangeListener);
	        yBlocksSpinner.addChangeListener(subdivideChangeListener);
	        zBlocksSpinner.addChangeListener(subdivideChangeListener);
            
			transparencySlider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					transparency = 1-(transparencySlider.getValue()*0.01f);
					isVisible = transparency > 1e-6f;
					RenderingConfiguration.getViewer().reDraw();
				}
			});
	        valueComboBox.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.DESELECTED) return;
                    else  if (e.getStateChange() == ItemEvent.SELECTED){
                        selectedColumn = (DataColumnInfo)valueComboBox.getSelectedItem();
                        if (selectedColumn != null){
                            binnedData = computeBin(Configuration.getCurrentAtomData());
                            if (!minMaxStorage.containsKey(selectedColumn))
                                minMaxStorage.put(selectedColumn, binnedData.getMinMax());
                            setSpinner();
                        }
                        if (isVisible) RenderingConfiguration.getViewer().reDraw();
                    }
                }
            });
	        
	        lowerLimitSpinner.addChangeListener(new ChangeListener() {
	            @Override
	            public void stateChanged(ChangeEvent e) {
	                float value = ((Number)lowerLimitSpinner.getValue()).floatValue();
	                minMaxStorage.get(selectedColumn)[0] = value;
	                if (isVisible) RenderingConfiguration.getViewer().reDraw();
	            }
	        });
	        
	        upperLimitSpinner.addChangeListener(new ChangeListener() {
	            @Override
	            public void stateChanged(ChangeEvent e) {
	                float value = ((Number)upperLimitSpinner.getValue()).floatValue();
                    minMaxStorage.get(selectedColumn)[1] = value;
	                if (isVisible) RenderingConfiguration.getViewer().reDraw();
	            }
	        });
	        
	        filterCheckboxMin.addActionListener(new ActionListener() {
	            @Override
	            public void actionPerformed(ActionEvent arg0) {
	                RenderingConfiguration.setFilterMin(filterCheckboxMin.isSelected());
	                inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
	                if (isVisible) RenderingConfiguration.getViewer().reDraw();
	            }
	        });
	        
	        filterCheckboxMax.addActionListener(new ActionListener() {
	            @Override
	            public void actionPerformed(ActionEvent arg0) {
	                RenderingConfiguration.setFilterMax(filterCheckboxMax.isSelected());
	                inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
	                if (isVisible) RenderingConfiguration.getViewer().reDraw();
	            }
	        });
	        
	        inverseFilterCheckbox.addActionListener(new ActionListener() {
	            @Override
	            public void actionPerformed(ActionEvent arg0) {
	                RenderingConfiguration.setFilterInversed(inverseFilterCheckbox.isSelected());
	                if (isVisible) RenderingConfiguration.getViewer().reDraw();
	            }
	        });
	        
	        resetButton.addActionListener(new ActionListener() {
	            @Override
	            public void actionPerformed(ActionEvent e) {
	                minMaxStorage.put(selectedColumn, binnedData.getMinMax());
	                setSpinner();
	                if (isVisible) RenderingConfiguration.getViewer().reDraw();
	            }
	        });
	        
	        this.validate();
	        
		}

		private void setSpinner(){
	        if (selectedColumn == null) return;
	        float[] minMax = minMaxStorage.get(selectedColumn);
	        if (minMax == null) return;
	        lowerLimitSpinner.setValue(minMax[0]);
	        upperLimitSpinner.setValue(minMax[1]);
	    }
		
		 public void resetValues(){
		        if (selectedColumn!= null && Configuration.getCurrentAtomData().getDataColumnInfos().size() != 0)
		            selectedColumn.findRange(Configuration.getCurrentAtomData(), false);
		    }
		    
        public void resetDropDown() {
            DataColumnInfo s = selectedColumn; // Save from overwriting during
                                               // switching which triggers
                                               // actionListeners
            valueComboBox.removeAllItems();
            List<DataColumnInfo> dci = Configuration.getCurrentAtomData().getDataColumnInfos();
            for (int i = 0; i < dci.size(); i++)
                valueComboBox.addItem(dci.get(i));
            
            if (s == null || !dci.contains(s)) {
                valueComboBox.setSelectedIndex(0);
            } else if (dci.size() > 0) {
                valueComboBox.setSelectedItem(s);
            }
        }
		
		@Override
		public void setViewer(ViewerGLJPanel viewer) {}
		
		@Override
		public void update(DataContainer dc) {
		    resetDropDown();
		    computeBin(Configuration.getCurrentAtomData());
		    setSpinner();
		}

		@Override
		public boolean isDataVisible() {
			return isVisible;
		}
	}

	private BinnedData computeBin(AtomData data){
	    int x = ((Number)dataPanel.xBlocksSpinner.getValue()).intValue();
	    int y = ((Number)dataPanel.yBlocksSpinner.getValue()).intValue();
	    int z = ((Number)dataPanel.zBlocksSpinner.getValue()).intValue();
	    
	    return new BinnedData(x, y, z, data, dataPanel.selectedColumn, false);	        
	}
	
    @Override
    public boolean isTransparenceRenderingRequired() {
        getDataControlPanel(); //Ensure that data panel is not null
        return dataPanel.transparency<1f;
    }

    @Override
    public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking,
            AtomData data) {
        if (isTransparenceRenderingRequired()) return;
        drawObjects(viewer, gl, renderRange, picking);
    }

    @Override
    public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking,
            AtomData data) {
        if (!isTransparenceRenderingRequired()) return;
        drawObjects(viewer, gl, renderRange, picking);
    }
    
    private void drawObjects(ViewerGLJPanel viewer, GL3 gl, final RenderRange renderRange, boolean picking){
        if (binnedData == null) return;
        if (!dataPanel.isVisible) return;
        
        final float[] minMax = minMaxStorage.get(dataPanel.selectedColumn);
        FilterSet<Bin> binFilter = new FilterSet<BinnedData.Bin>();
        binFilter.addFilter(new Filter<BinnedData.Bin>() {
            @Override
            public boolean accept(Bin b) {
                return renderRange.isInInterval(b.getCenterOfObject());
            }
        });
        binFilter.addFilter(new Filter<BinnedData.Bin>() {
            final boolean filterMax = dataPanel.filterCheckboxMax.isSelected();
            final boolean filterMin = dataPanel.filterCheckboxMin.isSelected();
            final boolean inversed = dataPanel.inverseFilterCheckbox.isSelected();
            
            @Override
            public boolean accept(Bin b) {
                float avg = b.getAvg();
                if ((filterMin && avg < minMax[0]) || (filterMax && avg > minMax[1]))
                    return inversed;
                return !inversed;
            }
        });
        renderer.drawBins(viewer, gl, binFilter, picking, binnedData, dataPanel.transparency, minMax[0], minMax[1]);
        viewer.drawLegendThisFrame(Float.toString(minMax[0]), "", Float.toString(minMax[1]));
    }

    @Override
    public JDataPanel getDataControlPanel() {
        if (dataPanel == null) dataPanel = new JBinningControlPanel();
        return dataPanel;
    }
	
}
