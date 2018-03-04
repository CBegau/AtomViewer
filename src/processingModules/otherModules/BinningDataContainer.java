package processingModules.otherModules;

import gui.glUtils.BinningRenderer;
import gui.RenderRange;
import gui.ViewerGLJPanel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import com.jogamp.opengl.GL3;

import common.CommonUtils;
import common.Vec3;

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
		
		float transparency = 1f;
		DataColumnInfo selectedColumn;
		
		private JSlider transparencySlider = new JSlider(0, 100, 0);
	    private JSpinner lowerLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
	    private JSpinner upperLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
	    
	    private JSpinner xBlocksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5000, 1));
	    private JSpinner yBlocksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5000, 1));
	    private JSpinner zBlocksSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 5000, 1));
	    
	    private JButton resetButton = new JButton("Auto adjust min/max");
	    JCheckBox filterCheckboxMin = new JCheckBox("Filter <min");
	    JCheckBox filterCheckboxMax = new JCheckBox("Filter >max");
	    JCheckBox inverseFilterCheckbox = new JCheckBox("Inverse filter");
	    JButton exportButton = new JButton("Export");
	    
	    private JComboBox<DataColumnInfo> valueComboBox = new JComboBox<DataColumnInfo>();
	    
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
	        this.add(inverseFilterCheckbox, gbc); gbc.gridx++;
	        this.add(exportButton, gbc); gbc.gridx = 0;
	        gbc.gridy++;
	        
	        gbc.gridwidth = 2;
	        this.add(resetButton, gbc); gbc.gridy++;
	        
	        ChangeListener subdivideChangeListener = new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    binnedData = computeBin(Configuration.getCurrentAtomData());
                    RenderingConfiguration.getViewer().reDraw();
                }
            };
	        xBlocksSpinner.addChangeListener(subdivideChangeListener);
	        yBlocksSpinner.addChangeListener(subdivideChangeListener);
	        zBlocksSpinner.addChangeListener(subdivideChangeListener);
            
			transparencySlider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					transparency = 1-(transparencySlider.getValue()*0.01f);
					RenderingConfiguration.getViewer().reDraw();
				}
			});
			transparencySlider.addChangeListener(e -> {
				transparency = 1-(transparencySlider.getValue()*0.01f);
				RenderingConfiguration.getViewer().reDraw();
			});
	        valueComboBox.addItemListener(e-> {
                if (e.getStateChange() == ItemEvent.DESELECTED) return;
                else  if (e.getStateChange() == ItemEvent.SELECTED){
                    selectedColumn = (DataColumnInfo)valueComboBox.getSelectedItem();
                    if (selectedColumn != null){
                        binnedData = computeBin(Configuration.getCurrentAtomData());
                        if (!minMaxStorage.containsKey(selectedColumn))
                            minMaxStorage.put(selectedColumn, binnedData.getMinMax());
                        setSpinner();
                    }
                    RenderingConfiguration.getViewer().reDraw();
                }
            });
	       
            ActionListener minMaxListener = (e->{
                RenderingConfiguration.setFilterMin(filterCheckboxMin.isSelected());
                RenderingConfiguration.setFilterMax(filterCheckboxMax.isSelected());
                inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
                RenderingConfiguration.getViewer().reDraw();
            });
	        
	        lowerLimitSpinner.addChangeListener(e->{ 
	        	float low = ((Number)lowerLimitSpinner.getValue()).floatValue();
	        	minMaxStorage.get(selectedColumn)[0] = low;}
	        );
	        upperLimitSpinner.addChangeListener(e->{
	        	float up = ((Number)upperLimitSpinner.getValue()).floatValue();
                minMaxStorage.get(selectedColumn)[1] = up;
	        });
	        
	        filterCheckboxMin.addActionListener(minMaxListener);
	        filterCheckboxMax.addActionListener(minMaxListener);
	        
	        inverseFilterCheckbox.addActionListener(arg0-> {
                RenderingConfiguration.setFilterInversed(inverseFilterCheckbox.isSelected());
                RenderingConfiguration.getViewer().reDraw();
	        });
	        
	        resetButton.addActionListener(e->{
                minMaxStorage.put(selectedColumn, binnedData.getMinMax());
                setSpinner();
                RenderingConfiguration.getViewer().reDraw();
	        });
	        
	        exportButton.addActionListener(arg0->{
                BinningDataContainer bdc = (BinningDataContainer)(
                        Configuration.getCurrentAtomData().getDataContainer(BinningDataContainer.class));
                
                JFileChooser chooser = new JFileChooser();
                int ok = chooser.showSaveDialog(JBinningControlPanel.this);
                if (ok == JFileChooser.APPROVE_OPTION){
                    try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(chooser.getSelectedFile()))){
                        bdc.exportData(dos);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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
  
        public void resetDropDown() {
            DataColumnInfo s = selectedColumn; // Save from overwriting during
                                               // switching which triggers
                                               // actionListeners
            valueComboBox.removeAllItems();
            List<DataColumnInfo> dci = Configuration.getCurrentAtomData().getDataColumnInfos();
            if (dci.size() == 0) {
                selectedColumn = null;
                return;
            }
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
		    update();
		}
		
		public void update() {
            resetDropDown();
            computeBin(Configuration.getCurrentAtomData());
            setSpinner();
        }

		@Override
		public boolean isDataVisible() {
			return true;
		}
	}

	private BinnedData computeBin(AtomData data){
	    if (dataPanel.selectedColumn == null) {
	        return null;
	    }
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
        
        final float[] minMax = minMaxStorage.get(dataPanel.selectedColumn);
        FilterSet<Bin> binFilter = new FilterSet<BinnedData.Bin>();
        
        binFilter.addFilter(b-> {
        	return renderRange.isInInterval(b.getCenterOfObject());
        });
        
        binFilter.addFilter(new Filter<BinnedData.Bin>() {
            final boolean filterMax = dataPanel.filterCheckboxMax.isSelected();
            final boolean filterMin = dataPanel.filterCheckboxMin.isSelected();
            final boolean inversed = dataPanel.inverseFilterCheckbox.isSelected();
            
            @Override
            public boolean accept(Bin b) {
                float avg = b.getMean();
                if ((filterMin && avg < minMax[0]) || (filterMax && avg > minMax[1]))
                    return inversed;
                return !inversed;
            }
        });
        renderer.drawBins(viewer, gl, binFilter, picking, binnedData, dataPanel.transparency, minMax[0], minMax[1]);
        viewer.drawLegendThisFrame(Float.toString(minMax[0]), "", Float.toString(minMax[1]));
    }
    
    public void exportData(DataOutputStream os) throws IOException{
        if (binnedData == null) return;
        for (int x = 0; x<binnedData.getNumBinX(); x++){
            for (int y = 0; y<binnedData.getNumBinY(); y++){
                for (int z = 0; z<binnedData.getNumBinZ(); z++){
                    Bin b = binnedData.getBin(x, y, z);
                    Vec3 c = b.getCenterOfObject();
                    os.writeBytes(String.format("%d %d %d %f %f %f %f %f %d\n", x,y,z, c.x, c.y, c.z, 
                            b.getMean(), b.getSum(), b.getNumberOfParticles()));
                }   
            }    
        }
    }

    @Override
    public JBinningControlPanel getDataControlPanel() {
        if (dataPanel == null) dataPanel = new JBinningControlPanel();
        return dataPanel;
    }
	
}
