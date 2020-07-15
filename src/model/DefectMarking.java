package model;

import java.awt.event.InputEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.jogamp.opengl.GL3;

import common.Tupel;
import common.Vec3;
import gui.JLogPanel;
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

	
	public static void export(AtomData atomData, File f) throws IOException,XMLStreamException {
		AtomData ad = atomData;

		XMLStreamWriter xmlout = XMLOutputFactory.newInstance()
				.createXMLStreamWriter(new OutputStreamWriter(new FileOutputStream(f), "utf-8"));

		xmlout.writeStartDocument();
		xmlout.writeStartElement("File");

		xmlout.writeStartElement("Name");
		xmlout.writeCharacters(ad.getName());
		xmlout.writeEndElement();
		
		DefectMarking df = ad.getDefectMarking();
		if (df != null) {
			df.closeCurrentMarkedArea();
			if (df.getMarks().size() > 0) {
				for (MarkedArea ma : df.getMarks()) {
					xmlout.writeStartElement("DefectPath");

					for (Vec3 v : ma.getPath()) {
						xmlout.writeStartElement("Vertex");
						{
							xmlout.writeStartElement("X");
							xmlout.writeCharacters(Float.toString(v.x / ad.getBox().getHeight().x));
							xmlout.writeEndElement();
							xmlout.writeStartElement("Y");
							xmlout.writeCharacters(Float.toString(v.y / ad.getBox().getHeight().y));
							xmlout.writeEndElement();
							xmlout.writeStartElement("Z");
							xmlout.writeCharacters(Float.toString(v.z / ad.getBox().getHeight().z));
							xmlout.writeEndElement();
						}
						xmlout.writeEndElement();
					}
					xmlout.writeEndElement();
				}
			}
		}

		xmlout.writeEndElement();
		xmlout.writeEndDocument();
		xmlout.close();
	}
	
	public static DefectMarking importFile (File f) throws IOException,XMLStreamException {
		
		XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(f));
		
		DefectMarking dm = null;
//		String name = null;
		Vec3 point = null;
		
		try{
			while (reader.hasNext()){
				reader.next();
				switch (reader.getEventType()){
	
					case XMLStreamReader.START_ELEMENT:{
						if (reader.getLocalName().equals("File")) 
							dm = new DefectMarking();
//						if (reader.getLocalName().equals("Name")) 
//							name = reader.getElementText();
						if (reader.getLocalName().equals("DefectPath")) 
							dm.startMarkedArea();
						if (reader.getLocalName().equals("Vertex")) 
							point = new Vec3();
						if (reader.getLocalName().equals("X"))
							point.x = Float.parseFloat(reader.getElementText());
						if (reader.getLocalName().equals("Y"))
							point.y = Float.parseFloat(reader.getElementText());
						if (reader.getLocalName().equals("Z"))
							point.z = Float.parseFloat(reader.getElementText());
						break;
					}
					
					case XMLStreamReader.END_ELEMENT:{
						if (reader.getLocalName().equals("DefectPath")) 
							dm.getCurrentMarkedArea().isClosed = true;
						if (reader.getLocalName().equals("Vertex"))
							dm.getCurrentMarkedArea().path.add(point);
						break;
					}
						
					default: break;
				}
			}
		} catch (Exception e){
			throw e;
		} finally {
			reader.close();
		}
		return dm;
	}
	
	
	public class MarkedArea implements Pickable{
		private ArrayList<Vec3> path = new ArrayList<Vec3>();
		private boolean isClosed = false;
		
		public void addPoint(Vec3 v) {
			assert(!isClosed);
			if (!isClosed) {
				//Test if the new segment would intersect with an existing one
				
				if (!testNewSegementIntersects(v, 0))
					path.add(v);
			}
		}
		
		private boolean testNewSegementIntersects(Vec3 v, int startIndex) {
			if (path.size()>=3) {
				for (int i=startIndex; i<path.size()-2; i++) {
					if (testIntersectionOnProjection(path.get(i), path.get(i+1), path.get(path.size()-1), v)) {
						return true;
					}
				}
			}
			return false;
		}
		
		private boolean testIntersectionOnProjection(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1) {
			Vec3 s1 = a1.subClone(a0);
			Vec3 s2 = b1.subClone(b0);
			
		    float s = (-s1.y * (a0.x - b0.x) + s1.x * (a0.y - b0.y)) / (-s2.x * s1.y + s1.x * s2.y);
		    float t = ( s2.x * (a0.y - b0.y) - s2.y * (a0.x - b0.x)) / (-s2.x * s1.y + s1.x * s2.y);

		    if (s >= 0 && s <= 1 && t >= 0 && t <= 1){
		        // Collision detected
		        return true;
		    }
		    return false; // No collision
		}
		

		public int numPoints() {
			return path.size();
		}
		
		public ArrayList<Vec3> getPath() {
			return path;
		}
		
		protected void close() {
			if (isClosed)
				return;
			if (path.size()<=2)
				path.clear();
			else {
				//Closing the loop would cause a self-intersection
				//Cause the mark to collapse by removing the path
				if (testNewSegementIntersects(path.get(0), 1)) {
					path.clear();
					JLogPanel.getJLogPanel().addWarning("Illegal geometry", "Closing the loop caused an self-intersecting polygon");
				}
				else 
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
