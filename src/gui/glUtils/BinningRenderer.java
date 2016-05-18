package gui.glUtils;

import com.jogamp.opengl.GL3;

import common.ColorTable;
import common.Vec3;
import gui.ViewerGLJPanel;
import gui.glUtils.Shader.BuiltInShader;
import model.BinnedData;
import model.BinnedData.Bin;
import model.Filter;

public class BinningRenderer {
    
    public void drawBins(ViewerGLJPanel viewer, GL3 gl, Filter<Bin> filter,
            boolean picking, BinnedData data, float transparency, float min, float max){
        Shader s = BuiltInShader.VERTEX_COLOR_DEFERRED.getShader();
        if (transparency<1f && !picking)
            s = BuiltInShader.OID_ADS_VERTEX_COLOR.getShader();
        
        final int[] triangleIndices = new int[]{0,5,4, 0,1,5, 1,7,5, 1,3,7, 3,2,7, 
                2,6,7, 2,0,6, 0,4,6, 4,7,6, 4,5,7, 0,2,3, 3,1,0};
        s.enableAndPushOld(gl);
        gl.glUniform1i(gl.glGetUniformLocation(s.getProgram(), "noShading"),0);
         
        VertexDataStorageLocal vds = 
                new VertexDataStorageLocal(gl, data.getNumBinX()*data.getNumBinY()*data.getNumBinZ()*36, 3, 3, 0, 4, 0, 0, 0, 0);
        vds.beginFillBuffer(gl);
        int numElements = 0;
        for (int x = 0; x < data.getNumBinX(); x++) {
            for (int y = 0; y < data.getNumBinY(); y++) {
                for (int z = 0; z < data.getNumBinZ(); z++) {
                    Bin b = data.getBin(x, y, z);
                    if (filter.accept(b)){
                    
                        if (picking) vds.setColor(viewer.getNextPickingColor(b));
                        else vds.setColor(ColorTable.getIntensityGLColor(min, max, b.getAvg(), transparency));
                        
                        Vec3[] corners = b.getCorners();
                        
                        for (int j=0; j<12; j++){
                            Vec3 p1 = corners[triangleIndices[3*j+0]];
                            Vec3 p2 = corners[triangleIndices[3*j+1]];
                            Vec3 p3 = corners[triangleIndices[3*j+2]];
                            Vec3 normal = Vec3.makeNormal(p1, p2, p3);
                            vds.setNormal(normal.x, normal.y, normal.z);
                            vds.setVertex(p1.x, p1.y, p1.z);
                            vds.setVertex(p2.x, p2.y, p2.z);
                            vds.setVertex(p3.x, p3.y, p3.z);
                            numElements += 3;
                        }
                    }
                }
            }
        }
        
        vds.endFillBuffer(gl);
        vds.setNumElements(numElements);
        vds.draw(gl, GL3.GL_TRIANGLES);
        
        Shader.popAndEnableShader(gl);
    }
}
