package model;

import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.jogamp.opengl.GL3;

import common.Tupel;
import common.Vec3;
import gui.ViewerGLJPanel;
import gui.ViewerGLJPanel.RenderOption;
import gui.glUtils.GLMatrix;
import gui.glUtils.Shader;
import gui.glUtils.SimpleGeometriesRenderer;
import gui.glUtils.TubeRenderer;
import gui.glUtils.Shader.BuiltInShader;

public class DefectMarking {
	
	private ArrayList<MarkedArea> marks = new ArrayList<>();
	
	
	public List<MarkedArea> getMarks() {
		return Collections.unmodifiableList(marks);
	}
	
	public MarkedArea startMarkedArea() {
		closeCurrentMarkedArea();
		
		MarkedArea newArea = new MarkedArea();
		marks.add(newArea);
		return newArea;
	}
	
	public void closeCurrentMarkedArea() {
		MarkedArea oldArea = getCurrentMarkedArea();
		if (oldArea != null) {
			oldArea.close();
			if (oldArea.numPoints()<=2)
				marks.remove(oldArea);
		}
	}
	
	public MarkedArea getCurrentMarkedArea() {
		if (marks.size()==0)
			return null;
		return marks.get(marks.size()-1);
	}
	
	public void removeMarkedArea(Object ma) {
		marks.remove(ma);
	}
	
	public void render(GL3 gl, boolean picking){
		if (!RenderOption.MARKER.isEnabled()) return;
		if (marks.size()==0) 
			return;
		
		
		ViewerGLJPanel viewer = RenderingConfiguration.getViewer();
		
		
		gl.glEnable(GL3.GL_POLYGON_OFFSET_FILL);
		gl.glPolygonOffset(0f, -20000f);

		
		Shader s = BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader();
		int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
		s.enable(gl);
		
		for (MarkedArea m : marks) {
			float[] color = m.isClosed ? new float[]{1f, 0f, 0.2f, 0.4f} : new float[]{0f, 1f, 0.2f, 0.4f};
			if(picking)
				color = viewer.getNextPickingColor(m);
			gl.glUniform4f(colorUniform, color[0], color[1], color[2], color[3]);
			
			if (m.path.size()==0)
				continue;
			//First marker is just a small sphere
			else if (m.path.size()==1) {
				Vec3 v = m.path.get(0);
				GLMatrix mvm = viewer.getModelViewMatrix().clone();
				mvm.translate(v.x, v.y, v.z);
				mvm.scale(3f, 3f, 3f);
				viewer.updateModelViewInShader(gl, s, mvm, viewer.getProjectionMatrix());
				SimpleGeometriesRenderer.drawSphere(gl);
			} else {
				TubeRenderer.drawTube(gl, m.path, 3f);
			}
		}
		
		gl.glPolygonOffset(0f, 0f);
		gl.glDisable(GL3.GL_POLYGON_OFFSET_FILL);

				
			
	}
	
	
	
	public class MarkedArea implements Pickable{
		private ArrayList<Vec3> path = new ArrayList<Vec3>();
		private boolean isClosed = false;
		
		public void addPoint(Vec3 v) {
			assert(!isClosed);
			if (!isClosed)
				//TODO test for intersection
				path.add(v);
		}

		public int numPoints() {
			return path.size();
		}
		
		protected void close() {
			if (isClosed)
				return;
			if (path.size()<=2)
				path.clear();
			else {
				//Close the loop
				path.add(path.get(0));
				isClosed = true;
			}
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
			return new Tupel<String, String>("Marked defect", String.format("Marked defect by %d vertices", this.numPoints()));
		}

		@Override
		public Vec3 getCenterOfObject() {
			//TODO meaningful cog needed?
			return new Vec3();
		}
	}
	
}
