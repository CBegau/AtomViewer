package model;

import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import common.*;
import gui.JLogPanel;

public class BinnedData {
    private int numBinX, numBinY, numBinZ;
    private Bin[] bins;
    private BoxParameter box;
    //Size of each bin
    private Vec3 sizeBinX;
    private Vec3 sizeBinY;
    private Vec3 sizeBinZ;
    private DataColumnInfo dci;
    private boolean filter = false;
    
    public BinnedData(int numBinX, int numBinY, int numBinZ, AtomData data, DataColumnInfo dci, boolean filter){
        this.numBinX = numBinX;
        this.numBinY = numBinY;
        this.numBinZ = numBinZ;
        this.bins = new Bin[numBinX*numBinY*numBinZ];
        this.box = data.getBox();
        this.sizeBinX = this.box.getBoxSize()[0].divideClone(numBinX);
        this.sizeBinY = this.box.getBoxSize()[1].divideClone(numBinY);
        this.sizeBinZ = this.box.getBoxSize()[2].divideClone(numBinZ);
        this.dci = dci;
        this.filter = filter;
        this.computeBins(data);
    }
    
    public Bin getBin(int x, int y, int z){
        return bins[x*numBinY*numBinZ + y*numBinZ + z];
    }
    
    public int getNumBinX() {
        return numBinX;
    }
    
    public int getNumBinY() {
        return numBinY;
    }
    
    public int getNumBinZ() {
        return numBinZ;
    }
    
    private void computeBins(AtomData data){
        FilterSet<Atom> afs;
        if (filter) afs = RenderingConfiguration.getAtomFilterset();
        else afs = new FilterSet<Atom>(); //Accept all "filter"
        
        final int valueIndex = data.getDataColumnIndex(dci);
        if (valueIndex == -1){
            JLogPanel.getJLogPanel().addError("Column not found",
                    String.format("Cannot create binning of value %s, "
                            + "column not available in %s",dci.getName(), data.getName()));
            throw new RuntimeException();
        }
        
        final float[] valueArray = data.getDataArray(valueIndex).getData();
        
        for (int x = 0; x < this.numBinX; x++) {
            for (int y = 0; y < this.numBinY; y++) {
                for (int z = 0; z < this.numBinZ; z++) {
                    int index = x * numBinZ * numBinY + y * numBinZ + z;
                    bins[index] = new Bin(x, y, z);
                }
            }
        }
        
        List<Atom> atoms = data.getAtoms();
        if (!dci.isVectorComponent()){  //scalar
            for (int i=0; i<atoms.size(); i++){
                Atom a = atoms.get(i);
                if (afs.accept(a)){
                    int x = (int) (numBinX * a.dot(box.getTBoxSize()[0]));
                    int y = (int) (numBinY * a.dot(box.getTBoxSize()[1]));
                    int z = (int) (numBinZ * a.dot(box.getTBoxSize()[2]));
                    if (x>=0 && y>=0 && z>=0 && x<numBinX && y<numBinY && z<numBinZ){
                        int index = x * numBinZ * numBinY + y * numBinZ + z;
                        bins[index].sum += valueArray[i];
                        bins[index].num++;
                    }
                }
            }
        } else {    //Vector computation
            final int xIndex = data.getDataColumnIndex(dci.getVectorComponents()[0]);
            final int yIndex = data.getDataColumnIndex(dci.getVectorComponents()[1]);
            final int zIndex = data.getDataColumnIndex(dci.getVectorComponents()[2]);
            
            final float[] valueArrayX = data.getDataArray(xIndex).getData();
            final float[] valueArrayY = data.getDataArray(yIndex).getData();
            final float[] valueArrayZ = data.getDataArray(zIndex).getData();
            
            for (int i=0; i<atoms.size(); i++){
                Atom a = atoms.get(i);
                if (afs.accept(a)){
                    int x = (int) (numBinX * a.dot(box.getTBoxSize()[0]));
                    int y = (int) (numBinY * a.dot(box.getTBoxSize()[1]));
                    int z = (int) (numBinZ * a.dot(box.getTBoxSize()[2]));
                    if (x>=0 && y>=0 && z>=0 && x<numBinX && y<numBinY && z<numBinZ){
                        int index = x * numBinZ * numBinY + y * numBinZ + z;
                        bins[index].sum += valueArray[i];
                        bins[index].sumVec.x += valueArrayX[i];
                        bins[index].sumVec.y += valueArrayY[i];
                        bins[index].sumVec.z += valueArrayZ[i];
                        bins[index].num++;
                    }
                }
            }
        }
    }
  
    public float[] getMinMax(){
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        
        for (Bin b: bins){
            min = Math.min(min, b.getAvg());
            max = Math.max(max, b.getAvg());
        }
        return new float[]{min, max};
    }
    
    public class Bin implements Pickable{      
        private int num = 0;
        private Vec3Double sumVec = new Vec3Double();
        private double sum = 0.0;
        
        private int indexX, indexY, indexZ;
        
        Bin(int indexX, int indexY, int indexZ){
            this.indexX = indexX;
            this.indexY = indexY;
            this.indexZ = indexZ;
        }
        
        public float getAvg() {
            return (float)(sum/(num>0?num:1));
        }
        
        @Override
        public Collection<?> getHighlightedObjects() {
            return null;
        }
        
        @Override
        public boolean isHighlightable() {
            return false;
        }
        
        @Override
        public Tupel<String, String> printMessage(InputEvent ev, AtomData data) {
            Vec3 center = getCenterOfObject();
            String text = "Bin of "+dci.getName();
            
            ArrayList<String> keys = new ArrayList<String>();
            ArrayList<String> values = new ArrayList<String>();
            
            keys.add("Value"); values.add(dci.getName());
            keys.add("Center"); values.add(center.toString());
            keys.add("Volume"); values.add(
                    Float.toString(box.getVolume()/(numBinX*numBinY*numBinZ)));
            keys.add("Number of particles"); values.add(Integer.toString(num));
            if (dci.isVectorComponent()){
                keys.add("Sum"); values.add(sumVec.toString());
                keys.add("Mean"); values.add(sumVec.divideClone((num>0?num:1)).toString());
            } else {
                keys.add("Sum"); values.add(Double.toString(sum));
                keys.add("Mean"); values.add(Double.toString(sum/(num>0?num:1)));
            }
            
            String table = CommonUtils.buildHTMLTableForKeyValue(
                    keys.toArray(new String[keys.size()]), values.toArray(new String[values.size()]));
            return new Tupel<String, String>(text, table);
        }
        
        @Override
        public Vec3 getCenterOfObject() {
            Vec3 center = new Vec3();
            center.add(sizeBinX.multiplyClone(indexX+0.5f));
            center.add(sizeBinY.multiplyClone(indexY+0.5f));
            center.add(sizeBinZ.multiplyClone(indexZ+0.5f));
            return center;
        }
        
        public Vec3[] getCorners(){
            Vec3[] corners = new Vec3[8];
            corners[0] = sizeBinX.multiplyClone(indexX).add(sizeBinY.multiplyClone(indexY)).add(sizeBinZ.multiplyClone(indexZ));
            corners[1] = corners[0].addClone(sizeBinX);
            corners[2] = corners[0].addClone(sizeBinY);
            corners[3] = corners[1].addClone(sizeBinY);
            corners[4] = corners[0].addClone(sizeBinZ);
            corners[5] = corners[1].addClone(sizeBinZ);
            corners[6] = corners[2].addClone(sizeBinZ);
            corners[7] = corners[3].addClone(sizeBinZ);
            return corners;
        }
        
    }
}
