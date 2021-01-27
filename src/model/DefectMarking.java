package model;

import java.awt.event.InputEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
	
	static float[][] severityColors = new float[][]{		
        {255f/255f,255f/255f,178f/255f},
        {254f/255f,204f/255f, 92f/255f},
        {253f/255f,141f/255f, 60f/255f},
        {240f/255f, 59f/255f, 32f/255f},
        //{189f/255f,  0f/255f, 38f/255f},
        {122f/255f,  1f/255f,119f/255f},
	};
	
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
			float[] color = m.isClosed ? severityColors[m.severity] : new float[]{44f/255f, 162f/255f, 95f/255f};
			if(picking)
				color = viewer.getNextPickingColor(m);
			gl.glUniform4f(colorUniform, color[0], color[1], color[2], 1f);
			
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

		try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), "utf-8")) {
			XMLStreamWriter xmlout = XMLOutputFactory.newInstance().createXMLStreamWriter(osw);

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
						
						xmlout.writeStartElement("Severity");
						xmlout.writeCharacters(Integer.toString(ma.severity));
						xmlout.writeEndElement();
	
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
	}
	
	
	public static void exportCsv(AtomData atomData, File f) throws IOException {
		try (PrintWriter pw = new PrintWriter(f)){
			pw.println("filename,file_size,file_attributes,region_count,region_id,region_shape_attributes,region_attributes");
			DefectMarking df = atomData.getDefectMarking();
			
			String filename = atomData.getFileMetaData("File2D_abs_path").toString();
			int filesize = -1;
			int maxSeverity = df.marks.stream().mapToInt(m->m.severity).max().orElse(0);
			
			String fileattributes = String.format("{\"\"total_severity\"\":\"\"%d\"\",\"\"corruption\"\":\"\"no\"\"}", maxSeverity);
					
			if (df.marks.size()>0) {
				for (int i=0; i<df.marks.size(); i++) {
					MarkedArea ma = df.marks.get(i);
					
					String x_coords = ma.path.stream()
							.map(v -> Integer.toString((int)(v.x+0.5)))
		                       .collect(Collectors.joining(","));
					String y_coords = ma.path.stream()
		                       .map(v -> Integer.toString((int)(v.y+0.5)))
		                       .collect(Collectors.joining(","));
					
					String shapeattributes = String.format("{\"\"name\"\":\"\"polygon\"\",\"\"all_points_x\"\":[%s],\"\"all_points_y\"\":[%s]}", x_coords, y_coords);
					String regionattributes = String.format("{\"\"severity\"\":\"\"%d\"\"}", ma.severity);  
							pw.println(String.format("%s,%d,\"%s\",%d,%d,\"%s\",\"%s\"", filename, filesize, fileattributes, 
									df.marks.size(), i, shapeattributes, regionattributes ));
				}
			} else {
				//No defect region
				pw.println(String.format("%s,%d,\"%s\",0,0,\"{}\",\"{}\"", filename, filesize, fileattributes));
			}
		}
	}
	
	public static void exportSvg(AtomData atomData, File f, boolean link2DImage)
			throws IOException, XMLStreamException {
		AtomData ad = atomData;

		try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), "utf-8")) {
			XMLStreamWriter xmlout = XMLOutputFactory.newInstance().createXMLStreamWriter(osw);

			float boxx = ad.getBox().getHeight().x;
			float boxy = ad.getBox().getHeight().y;

			int x = (Integer) ad.getFileMetaData("width2D");
			int y = (Integer) ad.getFileMetaData("height2D");

			xmlout.writeStartDocument();
			xmlout.writeStartElement("svg");

			xmlout.writeAttribute("xmlns", "http://www.w3.org/2000/svg");
			xmlout.writeAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
			xmlout.writeAttribute("version", "1.1");
			xmlout.writeAttribute("baseProfile", "full");

			xmlout.writeAttribute("width", Float.toString(x));
			xmlout.writeAttribute("height", Float.toString(y));
			xmlout.writeAttribute("viewBox", String.format("0. 0. %d %d", x, y));

			xmlout.writeAttribute("style", "background: white");

			xmlout.writeStartElement("title");
			xmlout.writeCharacters(ad.getName());
			xmlout.writeEndElement();
			xmlout.writeStartElement("desc");
			xmlout.writeEndElement();

			if (link2DImage) {
				xmlout.writeStartElement("image");
				xmlout.writeAttribute("x", "0");
				xmlout.writeAttribute("y", "0");
				xmlout.writeAttribute("width", Float.toString(x));
				xmlout.writeAttribute("height", Float.toString(y));

				String filename2D = (String) ad.getFileMetaData("File2D");
				xmlout.writeAttribute("xlink:href", filename2D);
				xmlout.writeEndElement();
			}

			DefectMarking df = ad.getDefectMarking();
			if (df != null) {
				df.closeCurrentMarkedArea();
				if (df.getMarks().size() > 0) {
					for (MarkedArea ma : df.getMarks()) {
						xmlout.writeStartElement("polygon");

						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < ma.getPath().size() - 1; i++) {
							Vec3 v = ma.getPath().get(i);
							sb.append(String.format("%f,%f ", (v.x / boxx) * x, (v.y / boxy) * y));
						}

						xmlout.writeAttribute("points", sb.toString());
						if (link2DImage) {
							float[] c = DefectMarking.severityColors[ma.severity];
							String rgb = String.format("rgb(%d, %d, %d)", (int) (c[0] * 255), (int)(c[1] * 255),
									(int)(c[2] * 255));
							xmlout.writeAttribute("fill", rgb);
							xmlout.writeAttribute("fill-opacity", "0.4");
						} else {
							xmlout.writeAttribute("fill", "black");
						}

						xmlout.writeEndElement();
					}
				}
			}

			xmlout.writeEndElement();
			xmlout.writeEndDocument();
			xmlout.close();
		}
	}
	
	public static DefectMarking importFile (File f) throws IOException,XMLStreamException {
		
		try (FileInputStream fis = new FileInputStream(f)) {
			XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(fis);

			DefectMarking dm = null;
			Vec3 point = null;

			try {
				while (reader.hasNext()) {
					reader.next();
					switch (reader.getEventType()) {

					case XMLStreamReader.START_ELEMENT: {
						if (reader.getLocalName().equals("File"))
							dm = new DefectMarking();
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
						if (reader.getLocalName().equals("Severity"))
							dm.getCurrentMarkedArea().severity = Integer.parseInt(reader.getElementText());
						break;
					}

					case XMLStreamReader.END_ELEMENT: {
						if (reader.getLocalName().equals("DefectPath"))
							dm.getCurrentMarkedArea().isClosed = true;
						if (reader.getLocalName().equals("Vertex"))
							dm.getCurrentMarkedArea().path.add(point);
						break;
					}

					default:
						break;
					}
				}
			} catch (Exception e) {
				throw e;
			} finally {
				reader.close();
			}
			return dm;
		}
	}
	
	
	public class MarkedArea implements Pickable{
		private ArrayList<Vec3> path = new ArrayList<Vec3>();
		private boolean isClosed = false;
		private int severity = 3;
		
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
		
		private boolean isCounterClockwise() {
			float sum = 0f;
			
			for (int i=0; i<path.size()-1; i++) {
				Vec3 v1 = path.get(i);
				Vec3 v2 = path.get(i+1);
				sum += (v2.x - v1.x) * (v2.y + v1.y);
			}
			Vec3 v1 = path.get(path.size()-1);
			Vec3 v2 = path.get(0);
			sum += (v2.x - v1.x) * (v2.y + v1.y);
			
			return sum<0;
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
		
		public int getSeverity() {
			return severity;
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
				else {
					//Close the loop
					path.add(path.get(0));
					//Ensure loop is in counterclockwise order
					if (!isCounterClockwise()) {
						Collections.reverse(path);
					}
				}
				isClosed = true;
			}
		}

		@Override
		public Collection<?> getHighlightedObjects() {
			return null;
		}

		@Override
		public boolean isHighlightable() {
			return true;
		}

		@Override
		public Tupel<String, String> printMessage(InputEvent ev, AtomData data) {
			if (ev!=null && (ev.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK){
				severity--;
			}
			else 
				severity++;
		
			if (severity < 0)
				severity = 4;
			else if (severity >= 5)
				severity = 0;
			
			return new Tupel<String, String>("Marked defect", String.format("Marked defect by %d vertices. Severity %d", this.numPoints(), this.severity));
		}

		@Override
		public Vec3 getCenterOfObject() {
			Vec3 cog = new Vec3();
			for (Vec3 m : this.path) {
				cog.add(m);
			}
			return cog.divide(this.path.size());
		}
	}
}
