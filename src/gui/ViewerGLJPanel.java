// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
//
// AtomViewer is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// AtomViewer is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with AtomViewer. If not, see <http://www.gnu.org/licenses/> 

package gui;

import model.*;
import model.BurgersVector.BurgersVectorType;
import model.dataContainer.DataContainer;
import model.polygrain.Grain;
import model.polygrain.mesh.*;
import model.skeletonizer.*;
import gui.glUtils.*;
import gui.glUtils.Shader.BuiltInShader;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
import java.util.*;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import javax.media.opengl.*;
import javax.media.opengl.awt.GLJPanel;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import common.*;
import common.ColorTable.ColorBarScheme;
import crystalStructures.CrystalStructure;

public class ViewerGLJPanel extends GLJPanel implements MouseMotionListener, MouseListener, 
	MouseWheelListener, GLEventListener, KeyListener {
	
	public enum RenderOption {
		INDENTER(false), BURGERS_VECTORS_ON_CORES(false), GRAINS(false), DISLOCATIONS(false),
		STACKING_FAULT(false), LEGEND(true), COORDINATE_SYSTEM(false), THOMPSON_TETRAEDER(false),
		PRINTING_MODE(false), STEREO(false), BOUNDING_BOX(true), PERSPECTIVE(false), LENGTH_SCALE(false);
		
		private boolean enabled;
		private static ViewerGLJPanel viewer;
		
		private RenderOption(boolean enabled){
			this.enabled = enabled;
		}
		
		public void setEnabled(boolean enabled){
			this.enabled = enabled;
			if (viewer!=null) viewer.reDraw();
			if (this == RenderOption.PRINTING_MODE){
				viewer.makeBackground();
			}
		}
		
		public boolean isEnabled(){
			return enabled;
		}
		
		private static void setViewer(ViewerGLJPanel viewer){
			RenderOption.viewer = viewer;
		}
	}
	
	public enum AtomRenderType {
		TYPE, ELEMENTS, GRAINS, DATA
	};
	
	private static final long serialVersionUID = 1L;
	private static final float CORE_THICKNESS = 5f;
	
	private AtomData atomData;
	private RenderRange renderInterval;
	
	private FrameBufferObject fboLeft;
	private FrameBufferObject fboRight;
	private FrameBufferObject fboBackground;
	private FrameBufferObject fboDeferredBuffer;

	private VertexDataStorage fullScreenQuad;
	private Texture noiseTexture;
	
	// Rotation-Zoom-Move-Variables
	private GLMatrix rotMatrix = new GLMatrix();
	private GLMatrix dragMatrix = new GLMatrix();
	private ArcBall arcBall;
	private float moveX = 0f, moveY = 0;
	private Vec3 coordinateCenterOffset = new Vec3();
	private float zoom = 1f;
	private boolean mousePressed = false;
	private Point startDragPosition;

	//Caching one half of the bounding box
	private Vec3 halfbounds = new Vec3();
	private Vec3 globalMaxBounds = new Vec3();
	
	private String[] legendLabels = new String[3];
	private boolean drawLegendThisFrame = false;
	
	private boolean reRenderTexture = true;
	//The index is the position in the list, therefore it is some kind of map
	private ArrayList<Pickable> pickList = new ArrayList<Pickable>();

	private boolean[] ignoreTypes;
	private boolean[] ignoreElement;
	private HashMap<Integer,Boolean> ignoreGrain = new HashMap<Integer, Boolean>();
	private HashMap<Integer,float[]> grainColorTable = new HashMap<Integer, float[]>();
	private boolean renderingAtomsAsRBV;
	
	private AtomRenderType atomRenderType = AtomRenderType.TYPE;
	//Sphere render data
	private int[] sphereVboIndices;
	private float defaultSphereSize = 1.5f;
	private int sphereVBOIndexCount = 0;
	private int sphereVBOPrimitive = GL.GL_TRIANGLE_FAN;
	
	private ArrayList<Object> highLightObjects = new ArrayList<Object>();
	
	private int width, height;
	
	private GLMatrix projectionMatrix = new GLMatrix();
	private GLMatrix modelViewMatrix = new GLMatrix();
	private TextRenderer textRenderer = null;
	
	private boolean updateRenderContent = true;
	
	private boolean showRotationSphere = false;
	
	private final static int SIZE_CELL_RENDER_RING = 8;
	private RingBuffer<CellRenderBuffer> cellRenderRingBuffer = null;
	private SphereRenderData<Atom> renderData;
	
	public static double openGLVersion = 0.;
	private GLAutoDrawable glDrawable = null;
	
	private Vec3 colorShiftForElements = new Vec3();
	/**
	 * If true, each virtual element can get a slightly shifted color for showing types
	 * Else, only physically different elements are shifted in color 
	 */
	private boolean colorShiftForVElements = true;
	
	private DataColoringAndFilter dataAtomFilter = new DataColoringAndFilter();
	private final AtomFilterSet atomFilterSet = new AtomFilterSet();
	
	public ViewerGLJPanel(int width, int height, GLCapabilities caps) {
		super(caps);
		
		addKeyListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addGLEventListener(this);
		
		this.setPreferredSize(new Dimension(width, height));
		this.setMinimumSize(new Dimension(100, 100));
		
		this.setFocusable(true);
		
		RenderOption.setViewer(this);
		Configuration.Options.setViewerPanel(this);
	}
	
	@Override
	public void display(GLAutoDrawable arg0) {
		this.glDrawable = arg0;
		GL3 gl = arg0.getGL().getGL3();
		
		if (reRenderTexture){
			updateIntInAllShader(gl, "ads", Configuration.Options.ADS_SHADING.isEnabled() ? 1 : 0);
			renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
			reRenderTexture = false;
		}
		
		renderFinalScene(gl, null);
	}

	@Override
	public void init(GLAutoDrawable arg0) {
		this.glDrawable = arg0;
		//Check which OpenGL version is installed
		String glVersion = arg0.getGL().glGetString(GL.GL_VERSION);
		glVersion = glVersion.substring(0, 3);
		openGLVersion = Double.parseDouble(glVersion);
		
		if (openGLVersion < 3.2) {
			String s = String.format("OpenGL 3.2 or higher is required, your version is: "+openGLVersion);
			System.out.println(s);
			System.exit(0);
		}
		
		GL3 gl = arg0.getGL().getGL3();
		createSphereRenderData(gl);
		
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glCullFace(GL.GL_BACK);
        gl.glEnable(GL.GL_CULL_FACE);
        
		gl.glDisable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glEnable(GL3.GL_DEPTH_CLAMP);
        
        Shader.init(gl);
        this.initShaderUniforms(gl);

        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 72);
        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (Font f : fonts){
        	if (f.getFamily().startsWith("Arial")){
        		font = new Font(f.getFamily(), Font.PLAIN, 72);
        		break;
        	}
        }
        textRenderer = new TextRenderer(font, arg0.getGLProfile());
		
		this.arcBall = new ArcBall(width, height);
		
		makeFullScreenQuad(gl);
		
		if (openGLVersion >=3.3 && cellRenderRingBuffer == null){
			ArrayList<CellRenderBuffer> ringElements = new ArrayList<ViewerGLJPanel.CellRenderBuffer>();
			for (int i = 0; i < SIZE_CELL_RENDER_RING; i++) {
				CellRenderBuffer crb = new CellRenderBuffer();
				crb.init(gl, SphereRenderData.MAX_ELEMENTS_PER_CELL * 8 * Float.SIZE / 8, GL.GL_ARRAY_BUFFER);
				ringElements.add(crb);
			}			
			cellRenderRingBuffer = new RingBuffer<ViewerGLJPanel.CellRenderBuffer>(ringElements);
		}
		
		
		if (noiseTexture == null){
			final String resourcesPath = "resources/noise.png";
			ClassLoader l = this.getClass().getClassLoader();
			InputStream stream = l.getResourceAsStream(resourcesPath);
			try {
				BufferedImage noise = ImageIO.read(stream);
				noiseTexture = AWTTextureIO.newTexture(gl.getGLProfile(), noise, false);
				noiseTexture.setTexParameterf(gl, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_LINEAR);
				noiseTexture.setTexParameterf(gl, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_LINEAR);
				noiseTexture.setTexParameterf(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
				noiseTexture.setTexParameterf(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private GL3 getGLFromContext(){
		GLContext context = this.glDrawable.getContext();
		context.makeCurrent();
		return context.getGL().getGL3();
	}

	@Override
	public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {
		GL3 gl = arg0.getGL().getGL3();
		arg0.getGL().glViewport(arg1, arg2, arg3, arg4);
		reRenderTexture = true;
		this.width = arg3;
		this.height = arg4;
		
		if (fboDeferredBuffer != null) fboDeferredBuffer.destroy(gl);
		if (fboLeft != null) fboLeft.destroy(gl);
		if (fboRight != null) fboRight.destroy(gl);
		if (fboBackground != null) fboBackground.destroy(gl);
		
		fboDeferredBuffer = new FrameBufferObject(width, height, gl, true);
		fboLeft = new FrameBufferObject(width, height, gl);
		fboRight = new FrameBufferObject(width, height, gl);
		fboBackground = new FrameBufferObject(width, height, gl);
		this.makeBackground();
		
		this.arcBall.setSize(this.width, this.height);
		this.makeFullScreenQuad(gl);
	}
	
	private void setupProjectionOrthogonal() {
		projectionMatrix.loadIdentity();

		// Correct the screen-aspect
		float aspect = width / (float) height;
		if (aspect > 1) {
			projectionMatrix.createOrtho(-aspect, aspect, -1f, 1f, -2, +2);
		} else {
			projectionMatrix.createOrtho(-1f, 1f, (-1f / aspect), (1f / aspect), -2, +2);
		}
		
		projectionMatrix.scale(this.zoom, this.zoom, 1f);
	}
	
	/**
	 * 
	 * @param eye -1: left eye,1 right eye, 0 centered (no shift) 
	 */
	private void setupProjectionPerspective(int eye) {
		projectionMatrix.loadIdentity();
		
		float zNear = 1.2f;
        float zFar  = 10f;
        
        float viewPaneCorrection = 0.5f;
        float focus = 0.8f;
        float eyeDist = 0.04f;	//Offset for stereo basis
        float right = viewPaneCorrection, top = viewPaneCorrection;
        float left = -viewPaneCorrection, bottom = -viewPaneCorrection;
        
        //Create asymmetric viewing-frustrum
        if (width<height){
            bottom /= width/(float)height;
            top    /= width/(float)height;
        } else {
        	left   *= width/(float)height;
            right  *= width/(float)height;
        }
        //Shift view for stereo displaying 
        left  += eyeDist * focus * eye;
        right += eyeDist * focus * eye;

        projectionMatrix.createFrustum(left, right, bottom, top, zNear, zFar);

        //Eye Position
        projectionMatrix.translate(eyeDist*eye, 0f, -3.2f);
        projectionMatrix.scale(this.zoom, this.zoom, 1f);
	}

	private GLMatrix getProjectionFlatMatrix() {
		GLMatrix pm = new GLMatrix();
		pm.createOrtho(0f, width, 0f, height, -5f, +5f);
		return pm;
	}
	
	private void setupModelView() {
		modelViewMatrix.loadIdentity();

		modelViewMatrix.translate(-moveX, -moveY, 0);
		modelViewMatrix.mult(rotMatrix);
		modelViewMatrix.translate(coordinateCenterOffset.x , coordinateCenterOffset.y, coordinateCenterOffset.z);
		float scale = 1f / globalMaxBounds.maxComponent();
		modelViewMatrix.scale(scale, scale, scale);
		modelViewMatrix.translate(-halfbounds.x, -halfbounds.y, -halfbounds.z);
	}

	private void renderSceneIntoFBOs(GL3 gl, boolean stereo){
//		long time = System.nanoTime();
		
		//Render content
		if (stereo) {
			fboLeft.bind(gl, true);
			renderScene(gl, false, -1,fboLeft); //left eye
			fboLeft.unbind(gl);
			
			fboRight.bind(gl, true);
			renderScene(gl, false, 1, fboRight); //right eye
			fboRight.unbind(gl);
		} else {
			fboLeft.bind(gl, true);
			renderScene(gl, false, 0, fboLeft); 	//central view
			fboLeft.unbind(gl);
		}
//		gl.glFinish();
//		System.out.println(1000000000./(System.nanoTime()-time));
	}

	/**
	 * Renders the scene into the given framebuffer or directly to the screen if fbo is null.
	 * The scene must be rendered to textures by calling
	 * "renderSceneToTexture(gl)" before. The textures are then placed on a
	 * large quad into the current framebuffer. 
	 * @param gl
	 * @param fbo
	 */
	private void renderFinalScene(GL3 gl, FrameBufferObject fbo) {
		if (GLContext.getCurrent() == null) {
			System.out.println("Current context is null");
			System.exit(0);
		}
		
		if (Configuration.Options.FXAA.isEnabled()) //Draw into an FBO if anti-aliasing is required
			fboDeferredBuffer.bind(gl, false);
		else if (fbo!=null){
			fbo.bind(gl, false);
			gl.glViewport(0, 0, width, height);
		}
		
		GLMatrix proj = getProjectionFlatMatrix();
		updateModelViewInShader(gl, BuiltInShader.ANAGLYPH_TEXTURED.getShader(), new GLMatrix(), proj);
		
		gl.glDisable(GL.GL_DEPTH_TEST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
	    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
	    BuiltInShader.ANAGLYPH_TEXTURED.getShader().enable(gl);
	    
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboLeft.getColorTextureName());
		
		gl.glUniform1i(gl.glGetUniformLocation(BuiltInShader.ANAGLYPH_TEXTURED.getShader().getProgram(), "stereo"), 0);
		if (RenderOption.STEREO.isEnabled()){	//Bind second texture
			gl.glActiveTexture(GL.GL_TEXTURE1);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboRight.getColorTextureName());		    
		    gl.glUniform1i(gl.glGetUniformLocation(BuiltInShader.ANAGLYPH_TEXTURED.getShader().getProgram(), "stereo"), 1);
		}
		//Bind background
		gl.glActiveTexture(GL.GL_TEXTURE2);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboBackground.getColorTextureName());
	   
		fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		
		if (Configuration.Options.FXAA.isEnabled()){
			//Draw the image in the FBO to the target using FXAA
			fboDeferredBuffer.unbind(gl);
			
			if (fbo!=null){
				fbo.bind(gl, false);
				gl.glViewport(0, 0, width, height);
			}
			
			updateModelViewInShader(gl, BuiltInShader.FXAA.getShader(), new GLMatrix(), proj);
			
			gl.glActiveTexture(GL.GL_TEXTURE0);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getColorTextureName());
			
			BuiltInShader.FXAA.getShader().enable(gl);
			
			gl.glUniform1i(gl.glGetUniformLocation(BuiltInShader.FXAA.getShader().getProgram(), "Texture0"), 0);
			gl.glUniform1f(gl.glGetUniformLocation(BuiltInShader.FXAA.getShader().getProgram(), "rt_w"), width);
			gl.glUniform1f(gl.glGetUniformLocation(BuiltInShader.FXAA.getShader().getProgram(), "rt_h"), height);
			
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		}
		
		if (fbo!=null)
			fbo.unbind(gl);

		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glEnable(GL.GL_DEPTH_TEST);
		
	    Shader.disableLastUsedShader(gl);
	}
	
	private void renderScene(GL3 gl, boolean picking, int eye,  FrameBufferObject drawIntoFBO) {
		if (GLContext.getCurrent() == null) {
			System.out.println("Current context is null");
			System.exit(0);
		}

		//Update sphere render data if necessary
		createSphereRenderData(gl);
		
		// clear colour and depth buffers
		gl.glClearColor(0f, 0f, 0f, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		if (atomData == null) return;
		
		//setup modelview and projection matrices
		setupModelView();
		
		if (RenderOption.STEREO.isEnabled())
			setupProjectionPerspective(eye);
		else if (RenderOption.PERSPECTIVE.isEnabled())
			setupProjectionPerspective(0);
		else setupProjectionOrthogonal();
		 
		updateModelViewInAllShader(gl);
		
		//Perform all draw calls in the scene
		draw(gl, picking, drawIntoFBO);
	}

	/**
	 * All draw calls to render the scene are placed here 
	 * @param gl
	 * @param picking
	 */
	private void draw(GL3 gl, boolean picking, FrameBufferObject drawIntoFBO) {
		if (drawIntoFBO != null)
			drawIntoFBO.unbind(gl);

		gl.glDisable(GL.GL_BLEND);
		if (!picking)
			fboDeferredBuffer.bind(gl, false);
		
		gl.glClearColor(1f, 1f, 1f, 1f);
		gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
		gl.glClearColor(0f, 0f, 0f, 0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		
		//Render solid objects
		drawSimulationBox(gl, picking);
		drawCores(gl, picking);
		for (DataContainer dc : atomData.getAdditionalData())
			dc.drawSolidObjects(this, gl, renderInterval, picking);
		
		drawAtoms(gl, picking);
		
		if (!picking) fboDeferredBuffer.unbind(gl);
		
		if (drawIntoFBO != null)
			drawIntoFBO.bind(gl, !picking);
		
		if (!picking) drawFromDeferredBuffer(gl, picking);
		
		//Using forward rendering
		//Renderpass 2 for transparent objects
		if (!picking) gl.glEnable(GL.GL_BLEND);
		if (!picking) gl.glDepthMask(false);	
		
		drawSurfaces(gl, picking);
		drawGrain(gl, picking);
		for (DataContainer dc : atomData.getAdditionalData())
			dc.drawTransparentObjects(this, gl, renderInterval, picking);
		
		gl.glDepthMask(true);
		drawGrain(gl, picking);
		
		//Additional objects
		//Some are placed on top of the scene as 2D objects
		if (!picking){
			if (showRotationSphere)
				drawRotationSphere(gl);
			drawIndent(gl);
			drawCoordinateSystem(gl);
			drawThompsonTetraeder(gl);			
			drawLegend(gl);
			drawLengthScale(gl);
		}
		
		this.updateRenderContent = false;
		Shader.disableLastUsedShader(gl);
	}

	private void drawFromDeferredBuffer(GL3 gl, boolean picking) {
		GLMatrix pm = getProjectionFlatMatrix();
	    
		gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_COLOR);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getColorTextureName());
		gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_NORMAL);
		gl.glBindTexture(GL.GL_TEXTURE_2D,  fboDeferredBuffer.getNormalTextureName());
		gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_POSITION);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getPositionTextureName());
		
		Shader s = BuiltInShader.DEFERRED_ADS_RENDERING.getShader();
		int prog = s.getProgram();
		s.enable(gl);
		
		updateModelViewInShader(gl, s, modelViewMatrix, projectionMatrix);
		gl.glUniformMatrix4fv(gl.glGetUniformLocation(s.getProgram(), "mvpm"), 1, false, pm.getMatrix());
		
		if (!picking){
			if (Configuration.Options.NO_SHADING.isEnabled() || picking)
				gl.glUniform1i(gl.glGetUniformLocation(BuiltInShader.DEFERRED_ADS_RENDERING.getShader().getProgram(), "picking"), 1);
			else gl.glUniform1i(gl.glGetUniformLocation(prog, "picking"), 0);
		}
		
		if (Configuration.Options.SSAO.isEnabled()){
			gl.glActiveTexture(GL.GL_TEXTURE0+4);
			gl.glBindTexture(GL.GL_TEXTURE_2D, noiseTexture.getTextureObject());
			gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoOffset"), 5.25f*zoom);
		}
		gl.glUniform1i(gl.glGetUniformLocation(prog, "ambientOcclusion"), Configuration.Options.SSAO.isEnabled()?1:0);
		
		fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
	}

	private void drawRotationSphere(GL3 gl){
		float thickness = atomData.getBox().getHeight().maxComponent()/300f;
		
		Shader s = BuiltInShader.ADS_UNIFORM_COLOR.getShader();
		int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
		s.enable(gl);
		gl.glUniform4f(colorUniform, 0.0f, 0.0f, 0.0f, 1f);
		gl.glUniform1i(gl.glGetUniformLocation(s.getProgram(), "ads"), 0);
		
		float maxSize = atomData.getBox().getHeight().maxComponent()*0.866f;
		Vec3 h = coordinateCenterOffset.multiplyClone(-globalMaxBounds.maxComponent()).add(halfbounds);
				
		//three rings, aligned at the x,y,z axes, centered around the coordinate system
		ArrayList<Vec3> path = new ArrayList<Vec3>();
		ArrayList<Vec3> path2 = new ArrayList<Vec3>();
		ArrayList<Vec3> path3 = new ArrayList<Vec3>();
		for (int i=0; i<=64; i++){
			double a = 2*Math.PI*(i/64.);
			float sin = (float)Math.sin(a)*maxSize;
			float cos = (float)Math.cos(a)*maxSize;
			path.add(new Vec3(sin+h.x, cos+h.y, h.z));
			path2.add(new Vec3(sin+h.x, h.y, cos+h.z));
			path3.add(new Vec3(h.x, sin+h.y, cos+h.z));
		}
		
		TubeRenderer.drawTube(gl, path, thickness);
		TubeRenderer.drawTube(gl, path2, thickness);
		TubeRenderer.drawTube(gl, path3, thickness);
		
		//Straight lines along x,y,z direction in the center of the coordinate system
		path.clear();
		path.add(new Vec3(maxSize+h.x, h.y, h.z)); path.add(new Vec3(-maxSize+h.x, h.y, h.z));
		TubeRenderer.drawTube(gl, path, thickness);
		
		path.clear();
		path.add(new Vec3(h.x, maxSize+h.y, h.z)); path.add(new Vec3(h.x, -maxSize+h.y, h.z));
		TubeRenderer.drawTube(gl, path, thickness);
		
		path.clear();
		path.add(new Vec3(h.x, h.y, maxSize+h.z)); path.add(new Vec3(h.x, h.y, -maxSize+h.z));
		TubeRenderer.drawTube(gl, path, thickness);
		
		//Draw a sphere centered on the coordinate system
		gl.glUniform4f(colorUniform, 0.5f, 0.5f, 1.0f, 0.8f);
		GLMatrix mvm = modelViewMatrix.clone();
		mvm.translate(h.x, h.y, h.z);
		mvm.scale(maxSize, maxSize, maxSize);
		updateModelViewInShader(gl, s, mvm, projectionMatrix);
		SimpleGeometriesRenderer.drawSphere(gl);
		updateModelViewInShader(gl, s, modelViewMatrix, projectionMatrix);
		
		gl.glUniform1i(gl.glGetUniformLocation(s.getProgram(), "ads"), Configuration.Options.ADS_SHADING.isEnabled() ? 1 : 0);
	}
	
	private void drawSimulationBox(GL3 gl, boolean picking) {
		if (!RenderOption.BOUNDING_BOX.isEnabled() || picking) return;
		
		Vec3[] box = atomData.getBox().getBoxSize();
		float[] boxVertices = new float[]{
			0f, 0f, 0f,	 	 0f, 1f, 0f,
			0f, 1f, 0f,	 	 1f, 1f, 0f,
			1f, 1f, 0f,	 	 1f, 0f, 0f,
			1f, 0f, 0f,	 	 0f, 0f, 0f,
			0f, 0f, 1f,	 	 1f, 0f, 1f,
			1f, 0f, 1f,	 	 1f, 1f, 1f,
			1f, 1f, 1f,	 	 0f, 1f, 1f,
			0f, 1f, 1f,	 	 0f, 0f, 1f,
			0f, 0f, 0f,	 	 0f, 0f, 1f,
			0f, 1f, 0f,	 	 0f, 1f, 1f,
			1f, 0f, 0f,	 	 1f, 0f, 1f,
			1f, 1f, 0f,	 	 1f, 1f, 1f,
		};
		
		for (int i=0; i<24; i++){
			float x = box[0].x * boxVertices[i*3] + box[1].x * boxVertices[i*3+1] + box[2].x * boxVertices[i*3+2];
			float y = box[0].y * boxVertices[i*3] + box[1].y * boxVertices[i*3+1] + box[2].y * boxVertices[i*3+2];
			float z = box[0].z * boxVertices[i*3] + box[1].z * boxVertices[i*3+1] + box[2].z * boxVertices[i*3+2];
			boxVertices[i*3] = x;
			boxVertices[i*3+1] = y;
			boxVertices[i*3+2] = z;
		}
		
		boolean[] pbc = Configuration.getPbc();
		Configuration.setPBC(new boolean[]{false, false, false}); //Turn off PBC before drawing a box with tubes
		
		BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader().enable(gl);
		int colorUniform = gl.glGetUniformLocation(BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader().getProgram(), "Color");
		gl.glUniform4f(colorUniform, 0.7f, 0.7f, 0.7f, 1f);
		for (int i=0; i<12; i++){
			ArrayList<Vec3> points = new ArrayList<Vec3>();
			points.add(new Vec3(boxVertices[i*6], boxVertices[i*6+1], boxVertices[i*6+2]));
			points.add(new Vec3(boxVertices[i*6+3], boxVertices[i*6+4], boxVertices[i*6+5]));
			TubeRenderer.drawTube(gl, points, atomData.getBox().getHeight().maxComponent()/200f);
		}
		
		Configuration.setPBC(pbc); //Turn back on PBCs	
	}
	
	private void drawIndent(GL3 gl){
		if (!RenderOption.INDENTER.isEnabled()) return;
		
		//Test indenter geometry, sphere or cylinder
		Object o = atomData.getFileMetaData("extpot_cylinder");
		if (o != null) {
			float[] indent = null;
			if (o instanceof float[]) indent = (float[])o;
			else return;
			BuiltInShader.ADS_UNIFORM_COLOR.getShader().enable(gl);
			gl.glUniform4f(gl.glGetUniformLocation(
					BuiltInShader.ADS_UNIFORM_COLOR.getShader().getProgram(),"Color"), 0f, 1f, 0.5f, 0.4f);
			GLMatrix mvm = modelViewMatrix.clone();
			mvm.translate(indent[1], indent[2], indent[3]);
			
			float length = 0f;
			if (indent[1] == 0f){
				length = halfbounds.x * 2f;
				mvm.rotate(90f, 0f, -1f, 0f);
			} else if (indent[2] == 0f){
				length = halfbounds.y * 2f;
				mvm.rotate(90f, -1f, 0f, 0f);
			} else if (indent[3] == 0f){
				length = halfbounds.z * 2f;
			}
			mvm.scale(indent[4], indent[4], length);
			
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawCylinder(gl);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), modelViewMatrix, projectionMatrix);
		}
		o = atomData.getFileMetaData("extpot");
		if (o != null) {
			float[] indent = null;
			if (o instanceof float[]) indent = (float[])o;
			else return;
			BuiltInShader.ADS_UNIFORM_COLOR.getShader().enable(gl);
			gl.glUniform4f(gl.glGetUniformLocation(
					BuiltInShader.ADS_UNIFORM_COLOR.getShader().getProgram(),"Color"), 0f, 1f, 0.5f, 0.4f);
			GLMatrix mvm = modelViewMatrix.clone();
			mvm.translate(indent[1], indent[2], indent[3]);
			mvm.scale(indent[4], indent[4], indent[4]);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawSphere(gl);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), modelViewMatrix, projectionMatrix);
		}
		o = atomData.getFileMetaData("wall");
		if (o != null) {
			float[] indent = null;
			if (o instanceof float[]) indent = (float[]) o;
			else return;
			BuiltInShader.ADS_UNIFORM_COLOR.getShader().enable(gl);
			gl.glUniform4f(gl.glGetUniformLocation(
					BuiltInShader.ADS_UNIFORM_COLOR.getShader().getProgram(),"Color"), 0f, 1f, 0.5f, 1f);
			GLMatrix mvm = modelViewMatrix.clone();
			Vec3[] box = atomData.getBox().getBoxSize();
			float hx = box[0].x + box[1].x + box[2].x;
			float hy = box[0].y + box[1].y + box[2].y;
			mvm.translate(0, 0, indent[1] - indent[2]);
			mvm.scale(hx, hy, 3*indent[2]);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawCube(gl);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), modelViewMatrix, projectionMatrix);
		}
	}
	
	private void drawSurfaces(GL3 gl, boolean picking){
		if (!RenderOption.STACKING_FAULT.isEnabled()  || !ImportStates.SKELETONIZE.isActive())
			return;
		
		Skeletonizer skel = atomData.getSkeletonizer();
		if (skel.getDislocations()==null) return;
		
		Shader shader = BuiltInShader.VERTEX_ARRAY_COLOR_UNIFORM.getShader();
		shader.enable(gl);
		int colorUniform = gl.glGetUniformLocation(shader.getProgram(), "Color");
		
		gl.glDisable(GL.GL_CULL_FACE);
		for (int i=0; i<skel.getPlanarDefects().size(); i++) {
			PlanarDefect s = skel.getPlanarDefects().get(i);
			
			VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, s.getFaces().length, 3, 0, 0, 0, 0, 0, 0, 0);
			int numElements = 0;
			vds.beginFillBuffer(gl);
			if (picking){
				float[] color = getNextPickingColor(s);
				gl.glUniform4f(colorUniform, color[0], color[1], color[2], color[3]);
			}
			else {
				if (Configuration.getCrystalStructure().hasMultipleStackingFaultTypes()){
					float[] c = Configuration.getCrystalStructure().getGLColor(s.getPlaneComposedOfType());
					if (highLightObjects.contains(s))
						gl.glUniform4f(colorUniform, c[0], c[1], c[2], 0.85f);
					else gl.glUniform4f(colorUniform, c[0], c[1], c[2], 0.35f);
				} else {
					if (highLightObjects.contains(s))
						gl.glUniform4f(colorUniform, 0.8f,0.0f,0.0f,0.35f);
					else {
						if (RenderOption.PRINTING_MODE.isEnabled()) gl.glUniform4f(colorUniform, 0.0f,0.0f,0.0f,0.35f);
						else gl.glUniform4f(colorUniform, 0.5f,0.5f,0.5f,0.35f);
					}
				}
			}
			
			for (int j = 0; j < s.getFaces().length; j+=3) {
				if (renderInterval.isInInterval(s.getFaces()[j]) && 
						isVectorInPBC(s.getFaces()[j].subClone(s.getFaces()[j+1])) && 
						isVectorInPBC(s.getFaces()[j].subClone(s.getFaces()[j+2])) && 
						isVectorInPBC(s.getFaces()[j+1].subClone(s.getFaces()[j+2]))) {
					vds.setVertex(s.getFaces()[j].x, s.getFaces()[j].y, s.getFaces()[j].z);
					vds.setVertex(s.getFaces()[j+1].x, s.getFaces()[j+1].y, s.getFaces()[j+1].z);
					vds.setVertex(s.getFaces()[j+2].x, s.getFaces()[j+2].y, s.getFaces()[j+2].z);
					numElements += 3;
				}
			}
			
			vds.endFillBuffer(gl);
			vds.setNumElements(numElements);
			vds.draw(gl, GL.GL_TRIANGLES);
			vds.dispose(gl);
		}
		gl.glEnable(GL.GL_CULL_FACE);
	}
		
	private void drawCores(GL3 gl, boolean picking) {
		if (!RenderOption.DISLOCATIONS.isEnabled() || !ImportStates.SKELETONIZE.isActive()) return;
		
		Skeletonizer skel = atomData.getSkeletonizer();
		if (skel.getDislocations() == null) return;
		
		//Check for object to highlight
		if (!picking){
			int numEle = Configuration.getCrystalStructure().getNumberOfElements();
			ArrayList<RenderableObject<Atom>> objectsToRender = new ArrayList<RenderableObject<Atom>>();
			ArrayList<Atom> atomsToRender = new ArrayList<Atom>();
			
			float[] sphereSize = Configuration.getCrystalStructure().getSphereSizeScalings();
			float maxSphereSize = 0f;
			for (int i=0; i<sphereSize.length; i++){
				sphereSize[i] *= defaultSphereSize;
				if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
			}
			
			for (int i=0; i<skel.getDislocations().size(); i++) {
				Dislocation dis = skel.getDislocations().get(i);
				if (highLightObjects.contains(dis)) {
					for (int  j=0; j<dis.getLine().length; j++) {
						SkeletonNode sn = dis.getLine()[j];
						for (Atom a : sn.getMappedAtoms()){
							float[] color;
							if (j==0) color = new float[]{0f, 1f, 0f, 1f};
							else if (j==dis.getLine().length-1) color = new float[]{0f, 0f, 1f, 1f};
							else color = new float[]{1f, 0f, 0f, 1f};
							objectsToRender.add(new RenderableObject<Atom>(a, color, sphereSize[a.getElement()%numEle]));
							atomsToRender.add(a);
						}
					}
				}
			}
					
			SphereRenderData<?> ard = new SphereRenderData<Atom>(atomsToRender, false);
			SphereRenderData<?>.Cell c = ard.getRenderableCells().get(0);
			for(int i=0; i<objectsToRender.size(); i++){
				c.getColorArray()[3*i+0] = objectsToRender.get(i).color[0];
				c.getColorArray()[3*i+1] = objectsToRender.get(i).color[1];
				c.getColorArray()[3*i+2] = objectsToRender.get(i).color[2];
				c.getSizeArray()[i] = objectsToRender.get(i).size;
				c.getVisibiltyArray()[i] = true;
			}
			ard.reinitUpdatedCells();
			drawSpheres(gl, ard, false);
			gl.glDisable(GL.GL_BLEND);
		}
		
		//Render the dislocation core elements
		Shader s = BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader();
		int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
		s.enable(gl);
		
		for (int i=0; i<skel.getDislocations().size(); i++) {
			Dislocation dis = skel.getDislocations().get(i);
			//Disable some dislocations if needed
			if (dis.getBurgersVectorInfo().getBurgersVector().getType() == BurgersVectorType.DONT_SHOW) continue;
			if (picking){
				float[] col = getNextPickingColor(dis);
				gl.glUniform4f(colorUniform, col[0], col[1], col[2], col[3]);
			}
			else if (dis.getBurgersVectorInfo()!=null) {
				float[] col = dis.getBurgersVectorInfo().getBurgersVector().getType().getColor();
				gl.glUniform4f(colorUniform, col[0], col[1], col[2], 1f);
			} else gl.glUniform4f(colorUniform, 0.5f, 0.5f, 0.5f, 1f);
			
			ArrayList<Vec3> path = new ArrayList<Vec3>();
			
			for (int j = 0; j < dis.getLine().length; j++) {
				SkeletonNode c = dis.getLine()[j];
				if (renderInterval.isInInterval(c)) {
					path.add(c);
				} else {
					if (path.size()>1) 
						TubeRenderer.drawTube(gl, path, CORE_THICKNESS);
					path.clear();
				}
				//Check PBC and restart tube if necessary
				if (j<dis.getLine().length-1){
					SkeletonNode c1 = dis.getLine()[j+1];
					if (!isVectorInPBC(c.subClone(c1))){
						if (path.size()>1)
							TubeRenderer.drawTube(gl, path, CORE_THICKNESS);
						path.clear();
					}
				}
			}
			if (path.size()>1)
				TubeRenderer.drawTube(gl, path, CORE_THICKNESS);
		}
		
		//Draw Burgers vectors on cores
		if (!picking && RenderOption.BURGERS_VECTORS_ON_CORES.isEnabled() && atomData.isRbvAvailable()){
			for (int i = 0; i < skel.getDislocations().size(); i++) {
				Dislocation dis = skel.getDislocations().get(i);
				
				Vec3 f;
				float[] col;
				if (dis.getBurgersVectorInfo().getBurgersVector().isFullyDefined()) {
					col = new float[]{ 0f, 1f, 0f, 1f};
					if (!ImportStates.POLY_MATERIAL.isActive() || dis.getGrain() == null)
						f = dis.getBurgersVectorInfo().getBurgersVector().getInXYZ(Configuration.getCrystalRotationTools());
					else
						f = dis.getBurgersVectorInfo().getBurgersVector().getInXYZ(dis.getGrain().getCystalRotationTools());
				} else {
					col = new float[]{1f, 0f, 0f, 1f};
					f = dis.getBurgersVectorInfo().getAverageResultantBurgersVector();
				}

				f.multiply(CORE_THICKNESS);
				SkeletonNode c = dis.getLine()[dis.getLine().length / 2]; // node "in the middle"
				if (renderInterval.isInInterval(c))
					ArrowRenderer.renderArrow(gl, c, f, 0.4f, col, true);
			}
		}
	}
	
	private void drawAtoms(GL3 gl, boolean picking){
		final int numEle = Configuration.getCrystalStructure().getNumberOfElements();
		
		final CrystalStructure cs = Configuration.getCrystalStructure();

		final float[] sphereSize = cs.getSphereSizeScalings();
		float maxSphereSize = 0f;
		for (int i=0; i<sphereSize.length; i++){
			sphereSize[i] *= defaultSphereSize;
			if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
		}
			
		if (!renderingAtomsAsRBV || !atomData.isRbvAvailable()){
			if (atomRenderType == AtomRenderType.DATA){
				DataColumnInfo dataInfo = Configuration.getSelectedColumn();
				
				final float min = dataInfo.getLowerLimit();
				final float max = dataInfo.getUpperLimit();
				
				//Set custom color scheme of the data value if present
				ColorBarScheme scheme = ColorTable.getColorBarScheme();
				boolean swapped = ColorTable.isColorBarSwapped();
				if (dataInfo.getScheme()!=null){
					ColorTable.setColorBarScheme(dataInfo.getScheme());
					ColorTable.setColorBarSwapped(false);
				}
				
				if (dataInfo.getScheme()!=null){
					ColorTable.setColorBarScheme(scheme);
					ColorTable.setColorBarSwapped(swapped);
				} else if (RenderOption.LEGEND.isEnabled()){
					drawLegendThisFrame(
							Float.toString(min)+" "+dataInfo.getUnit(), 
							Float.toString((max+min)*0.5f)+" "+dataInfo.getUnit(),
							Float.toString(max)+" "+dataInfo.getUnit()
							);
				}
			}
			
			if (updateRenderContent){
				atomFilterSet.clear();
				//Test if types need to be filtered
				TypeColoringAndFilter tf = new TypeColoringAndFilter();
				if (tf.isNeeded()) atomFilterSet.addFilter(tf);
				//Test if elements need to be filtered
				ElementColoringAndFilter ef = new ElementColoringAndFilter();
				if (ef.isNeeded()) atomFilterSet.addFilter(ef);
				//Test if grains need to be filtered
				GrainColoringAndFilter gf = new GrainColoringAndFilter();
				if (gf.isNeeded()) atomFilterSet.addFilter(gf);
				//Test if cutting planes are defined for filtering
				if (!renderInterval.isNoLimiting()) atomFilterSet.addFilter(renderInterval);
				//Test if data needs to be filtered
				if (Configuration.isFilterRange() && Configuration.getSizeDataColumns() != 0)
					atomFilterSet.addFilter(dataAtomFilter);
				
				final ColoringFunction colFunc;
				switch (atomRenderType){
					case TYPE: colFunc = new TypeColoringAndFilter(); break;
					case ELEMENTS: colFunc = new ElementColoringAndFilter(); break;
					case GRAINS: colFunc = new GrainColoringAndFilter(); break;
					case DATA: colFunc = dataAtomFilter; break;
					default: colFunc = null;
				}
				colFunc.update();
				
				Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
				for (int i=0; i<ThreadPool.availProcessors(); i++){
					final int j = i;
					parallelTasks.add(new Callable<Void>() {
						@Override
						public Void call() throws Exception {
							final int start = (int)(((long)renderData.getRenderableCells().size() * j)/ThreadPool.availProcessors());
							final int end = (int)(((long)renderData.getRenderableCells().size() * (j+1))/ThreadPool.availProcessors());
							
							for (int i = start; i < end; i++) {
								SphereRenderData<Atom>.Cell cell = renderData.getRenderableCells().get(i);
								for (int j=0; j<cell.getNumObjects();j++){
									Atom c = cell.getObjects().get(j);
									if (atomFilterSet.accept(c)) {
										cell.getVisibiltyArray()[j] = true;
										cell.getSizeArray()[j] = sphereSize[c.getElement() % numEle];
										float[] color = colFunc.getColor(c);
										cell.getColorArray()[j*3+0] = color[0];
										cell.getColorArray()[j*3+1] = color[1];
										cell.getColorArray()[j*3+2] = color[2];
									} else {
										cell.getVisibiltyArray()[j] = false;
									}
								}
							}
							return null;
						}
					});
				}
				
				ThreadPool.executeParallel(parallelTasks);
				
				renderData.reinitUpdatedCells();
			}
			
			//Draw the spheres using the pre-computed render cells 
			drawSpheres(gl, renderData, picking);
		} else {
			gl.glDisable(GL.GL_BLEND);
			float lsVectorLength = cs.getLatticeConstant()/3f;
			float minLength = cs.getPerfectBurgersVectorLength() * 0.2f;
			minLength *= minLength; 
			float maxLength = cs.getPerfectBurgersVectorLength() * 2f;
			maxLength *= maxLength;
			for (int i=0; i<atomData.getAtoms().size(); i++){
				Atom c = atomData.getAtoms().get(i);
				if (c.getRBV()!=null && atomFilterSet.accept(c)){
					float l = c.getRBV().bv.getLengthSqr();
					if (l<minLength || l>maxLength) continue; 
					
					float[] col;
					if (picking) col = this.getNextPickingColor(c);
					else col = cs.getGLColor(c.getType());

					ArrowRenderer.renderArrow(gl, c, c.getRBV().bv, 0.1f, col, true);
					if (!picking) col = new float[]{0.5f, 0.5f, 0.5f};
					ArrowRenderer.renderArrow(gl, c,
							c.getRBV().lineDirection.multiplyClone(lsVectorLength), 0.05f, col, true);
				}
			}
		}
	}
	
	
	private void drawGrain(GL3 gl, boolean picking){
		if (!RenderOption.GRAINS.isEnabled() || !ImportStates.POLY_MATERIAL.isActive()) return;
		Shader s = BuiltInShader.ADS_UNIFORM_COLOR.getShader();
		s.enable(gl);
		int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");

		for (Grain grain : atomData.getGrains()){
//			if (grain.getMesh().getVolume()<1000.) continue;
			if (isGrainIgnored(grain.getGrainNumber())) continue;
			
			float[] col;
			if (picking) col = this.getNextPickingColor(grain);
			else col = getGrainColor(grain.getGrainNumber());
			
			gl.glUniform4f(colorUniform, col[0], col[1], col[2], col[3]);
			Mesh mesh = grain.getMesh();
			mesh.getFinalMesh().renderMesh(gl);
			
//			if (!picking){
//				gl.glUniform4f(colorUniform, 0.0f, 0.0f, 0.0f, 0.5f);
//				
//				gl.glEnable(GL3.GL_POLYGON_OFFSET_LINE);
//				gl.glPolygonOffset(0f, -500f);
//				gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_LINE);
//			
//				mesh.getFinalMesh().renderMesh(gl);
//			
//				gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_FILL);
//				gl.glPolygonOffset(0f, 0f);
//				gl.glDisable(GL3.GL_POLYGON_OFFSET_LINE);
//			}
		}
	}
	
	private void drawThompsonTetraeder(GL3 gl){
		if (!RenderOption.THOMPSON_TETRAEDER.isEnabled()) return;
		gl.glDisable(GL.GL_DEPTH_TEST);
		
		GLMatrix pm = projectionMatrix.clone();
		pm.scale(1f/zoom, 1f/zoom, 1f);
		
		GLMatrix mvm = new GLMatrix();
		float aspect = width / (float) height;
		if (aspect > 1) mvm.translate(-aspect*0.8f, -0.8f, 0.8f);
		else  mvm.translate(-0.8f, -(1/aspect)*0.8f, 0.8f);
		mvm.scale(0.2f, 0.2f, 0.2f);
		mvm.mult(rotMatrix);
		mvm.translate(0f, 0f, -0.333f);
			
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), mvm, pm);
		
		Vec3[] a = Configuration.getCrystalRotationTools().getThompsonTetraeder();
		float[] alpha = a[1].addClone(a[2]).add(a[3]).multiply(0.333f).asArray();
		float[] beta  = a[0].addClone(a[2]).add(a[3]).multiply(0.333f).asArray();
		float[] gamma = a[0].addClone(a[1]).add(a[3]).multiply(0.333f).asArray();
		float[] delta = a[0].addClone(a[1]).add(a[2]).multiply(0.333f).asArray();		
		
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, 12, 3, 0, 0, 4, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		vds.setColor(1f,0f,0f,1f);
		vds.setVertex(a[3].asArray()); vds.setVertex(a[2].asArray()); vds.setVertex(a[1].asArray());
		vds.setColor(0f,0f,1f,1f);
		vds.setVertex(a[0].asArray()); vds.setVertex(a[2].asArray()); vds.setVertex(a[3].asArray());
		vds.setColor(1f,1f,0f,1f);
		vds.setVertex(a[1].asArray()); vds.setVertex(a[0].asArray()); vds.setVertex(a[3].asArray());
		vds.setColor(0f,1f,0f,1f);
		vds.setVertex(a[0].asArray()); vds.setVertex(a[1].asArray()); vds.setVertex(a[2].asArray());
		vds.endFillBuffer(gl);
		vds.draw(gl, GL.GL_TRIANGLES);
		vds.dispose(gl);
		
		vds = new VertexDataStorageLocal(gl, 36, 3, 0, 0, 4, 0, 0, 0, 0);
		
		gl.glEnable(GL3.GL_POLYGON_OFFSET_LINE);
		gl.glPolygonOffset(0f, -500f);
		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_LINE);
		
		vds.beginFillBuffer(gl);
		vds.setColor(0.5f,0.5f,0.5f,1f);
		vds.setVertex(alpha); vds.setVertex(a[2].asArray()); vds.setVertex(a[1].asArray());
		vds.setVertex(alpha); vds.setVertex(a[1].asArray()); vds.setVertex(a[3].asArray());
		vds.setVertex(alpha); vds.setVertex(a[3].asArray()); vds.setVertex(a[2].asArray());
		vds.setVertex(beta ); vds.setVertex(a[3].asArray()); vds.setVertex(a[0].asArray());
		vds.setVertex(beta ); vds.setVertex(a[0].asArray()); vds.setVertex(a[2].asArray());
		vds.setVertex(beta ); vds.setVertex(a[2].asArray()); vds.setVertex(a[3].asArray());
		vds.setVertex(gamma); vds.setVertex(a[0].asArray()); vds.setVertex(a[3].asArray());
		vds.setVertex(gamma); vds.setVertex(a[3].asArray()); vds.setVertex(a[1].asArray());
		vds.setVertex(gamma); vds.setVertex(a[1].asArray()); vds.setVertex(a[0].asArray());
		vds.setVertex(delta); vds.setVertex(a[1].asArray()); vds.setVertex(a[2].asArray());
		vds.setVertex(delta); vds.setVertex(a[2].asArray()); vds.setVertex(a[0].asArray());
		vds.setVertex(delta); vds.setVertex(a[0].asArray()); vds.setVertex(a[1].asArray());
		vds.endFillBuffer(gl);
		vds.draw(gl, GL.GL_TRIANGLES);
		vds.dispose(gl);
		
		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_FILL);
		gl.glPolygonOffset(0f, 0f);
		gl.glDisable(GL3.GL_POLYGON_OFFSET_LINE);
		
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), modelViewMatrix, projectionMatrix);
		gl.glEnable(GL.GL_DEPTH_TEST);
	}
	
	private void drawCoordinateSystem(GL3 gl) {
		if (!RenderOption.COORDINATE_SYSTEM.isEnabled()) return;
		gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
		
		GLMatrix pm = projectionMatrix.clone();
		pm.scale(1f/zoom, 1f/zoom, 1f);
		
		GLMatrix mvm = new GLMatrix();
		float aspect = width / (float) height;
		if (aspect > 1) {
			mvm.translate(aspect*0.6f, -0.6f, 1f);
		} else {
			mvm.translate(0.6f, -(1/aspect)*0.6f, 1f);
		}
		
		mvm.scale(0.40f, 0.40f, 0.40f);
		mvm.mult(rotMatrix);
		mvm.translate(-0.2f, -0.2f, -0.2f);
		
		//Coordinate lines
		Shader shader = BuiltInShader.ARROW.getShader();
		shader.enable(gl);
		updateModelViewInShader(gl, shader, mvm, pm);
		
		Vec3 o = new Vec3(0f, 0f, 0f);
		float[] col = new float[]{0.5f,0.5f,0.5f, 1f};
		ArrowRenderer.renderArrow(gl, o, atomData.getBox().getBoxSize()[0].normalizeClone(), 0.02f, col, false);
		ArrowRenderer.renderArrow(gl, o, atomData.getBox().getBoxSize()[1].normalizeClone(), 0.02f, col, false);
		ArrowRenderer.renderArrow(gl, o, atomData.getBox().getBoxSize()[2].normalizeClone(), 0.02f, col, false);
		
		textRenderer.setColor(gl, 0f, 0f, 0f, 1f);
		
		//Labels
		for (int i=0; i<3; i++){
			String s = "["+(int)Configuration.getCrystalOrientation()[i].x+" "
				+(int)Configuration.getCrystalOrientation()[i].y+" "
				+(int)Configuration.getCrystalOrientation()[i].z +"]";
			if (i==0) s = "x="+s;
			if (i==1) s = "y="+s;
			if (i==2) s = "z="+s;
			
			GLMatrix mvm_new = mvm.clone();
			Vec3 v = atomData.getBox().getBoxSize()[i].normalizeClone();
			mvm_new.translate(v.x, v.y, v.z);
			GLMatrix rotInverse = new GLMatrix(rotMatrix.getMatrix());
			rotInverse.inverse();
			mvm_new.mult(rotInverse);
			mvm_new.translate(-0.5f, 0.1f, 0f);
			
			gl.glDisable(GL.GL_DEPTH_TEST);
			textRenderer.beginRendering(gl);
			updateModelViewInShader(gl, Shader.BuiltInShader.PLAIN_TEXTURED.getShader(), mvm_new, pm);
	        textRenderer.draw(gl, s, 0f, 0f, 0f, 0.0025f);
	        updateModelViewInShader(gl, Shader.BuiltInShader.PLAIN_TEXTURED.getShader(), mvm, pm);
	        textRenderer.endRendering(gl);
	        gl.glEnable(GL.GL_DEPTH_TEST);
		}
		
		//Reset modelView in shader used
		updateModelViewInShader(gl, shader, modelViewMatrix, projectionMatrix);
	}
	
	private void drawLegend(GL3 gl) {
		if (!drawLegendThisFrame || !RenderOption.LEGEND.enabled) return;
		
		//Draw always on top of everything else
		gl.glDisable(GL.GL_DEPTH_TEST);
		
		GLMatrix mvm = new GLMatrix();
		GLMatrix pm = getProjectionFlatMatrix();
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), mvm, pm);
		
		float[][] scale = ColorTable.getColorBarScheme().getColorBar();
		
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, scale.length*2, 3, 0, 0, 4, 0, 0, 0, 0);
		
		vds.beginFillBuffer(gl);
    	for (int i=0; i<scale.length; i++){
    		if (!ColorTable.isColorBarSwapped())
    			vds.setColor(scale[i][0],scale[i][1],scale[i][2], 1f);
    		else vds.setColor(scale[scale.length-1-i][0],scale[scale.length-1-i][1],scale[scale.length-1-i][2],1f);
    		float h = height*(0.015f + 0.24f*(i/(float)(scale.length-1)));
    		vds.setVertex(width*0.005f,                h, 0f); 
    		vds.setVertex(width*0.005f + height*0.06f, h, 0f);
    		
    	}
        vds.endFillBuffer(gl);
        vds.draw(gl, GL.GL_TRIANGLE_STRIP);
        vds.dispose(gl);
        
		textRenderer.setColor(gl, 0f, 0f, 0f, 1f);
		//Labels
		textRenderer.beginRendering(gl);
		updateModelViewInShader(gl, Shader.BuiltInShader.PLAIN_TEXTURED.getShader(), mvm, pm);
    	textRenderer.draw(gl, this.legendLabels[0], width*0.01f+height*0.06f, height*0.01f, zoom*1.01f, 0.00035f*height);
   		textRenderer.draw(gl, this.legendLabels[1], width*0.01f+height*0.06f, height*0.13f, zoom*1.01f, 0.00035f*height);
    	textRenderer.draw(gl, this.legendLabels[2], width*0.01f+height*0.06f, height*0.25f, zoom*1.01f, 0.00035f*height);
	    textRenderer.endRendering(gl);
	    updateModelViewInShader(gl, Shader.BuiltInShader.PLAIN_TEXTURED.getShader(), modelViewMatrix, projectionMatrix);
        gl.glEnable(GL.GL_DEPTH_TEST);
        drawLegendThisFrame = false;
	}
	
	private void drawLengthScale(GL3 gl){
		if (!RenderOption.LENGTH_SCALE.isEnabled()) return;
		if (RenderOption.STEREO.isEnabled() || RenderOption.PERSPECTIVE.isEnabled()) return;
		if (Configuration.getCurrentAtomData() == null) return;
		
		float size = 50;
		float unitLengthInPixel = estimateUnitLengthInPixels();
		float unitsOnScreen = width/unitLengthInPixel;
		float a = unitsOnScreen/10;
		
		float power = (float)Math.ceil(Math.log10(a));
		float b = a/(float)Math.pow(10, power);
		if (b>0.5) size = 1;
		else if (b>0.33f) size = 0.5f;
		else size = 0.2f;
		
		size *= (int)Math.pow(10, power);
		
		String sizeString = Float.toString(size);
		int blocks = 4;
		
		int xshift = width/10;
		int yshift = height-2*(height/20);
		
		gl.glDisable(GL.GL_DEPTH_TEST);
		
		GLMatrix mvm = new GLMatrix();
		GLMatrix pm = getProjectionFlatMatrix();
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), mvm, pm);
		
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		
		float w = (size*unitLengthInPixel)/blocks;
		float h_scale = (height/20f)/10f;
		
		float textscale = 0.1f*h_scale;
		int border = 2;
		float textHeigh = textscale*textRenderer.getStringHeigh();
		float textWidth = textRenderer.getStringWidth(sizeString)*textscale;
		float minSize = Math.max(textWidth+5, w*blocks);
		
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, 4, 2, 0, 0, 4, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift-10-border, yshift-textHeigh-border);
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift+minSize+10+border, yshift-textHeigh-border);
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift-10-border, yshift+10*h_scale+border);
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift+minSize+10+border, yshift+10*h_scale+border);
			
		vds.endFillBuffer(gl);
		vds.draw(gl, GL.GL_TRIANGLE_STRIP);
		vds.dispose(gl);
		
		//Draw white background
		vds = new VertexDataStorageLocal(gl, 4, 2, 0, 0, 4, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift-10, yshift-textHeigh);
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift+minSize+10, yshift-textHeigh);
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift-10, yshift+10*h_scale);
		vds.setColor(1f, 1f, 1f, 1f); vds.setVertex(xshift+minSize+10, yshift+10*h_scale);
		
		vds.endFillBuffer(gl);
		vds.draw(gl, GL.GL_TRIANGLE_STRIP);
		vds.dispose(gl);
		
		//Lenght bar
		vds = new VertexDataStorageLocal(gl, blocks*6, 2, 0, 0, 4, 0, 0, 0, 0);
		float offset = Math.min(w*blocks-(textWidth+5), 0f)*-0.5f;
    	for (int i=0; i<blocks; i++){
    		float c = i%2==0? 0.7f: 0f;
    		for (int j=0; j<6; j++)
    			vds.setColor(c, c, c, 1f);
    		
    		vds.setVertex(offset + w*i+xshift, yshift+8*h_scale);
    		vds.setVertex(offset + w*i+xshift, yshift+2*h_scale);
    		vds.setVertex(offset + w*(i+1)+xshift, yshift+8*h_scale);
    		
    		vds.setVertex(offset + w*i+xshift, yshift+2*h_scale);
    		vds.setVertex(offset + w*(i+1)+xshift, yshift+2*h_scale);
    		vds.setVertex(offset + w*(i+1)+xshift, yshift+8*h_scale);
    	}
    	vds.endFillBuffer(gl);
		vds.draw(gl, GL.GL_TRIANGLES);
		vds.dispose(gl);
		
		
		textRenderer.setColor(gl, 0f, 0f, 0f, 1f);
		//Labels
		textRenderer.beginRendering(gl);
		updateModelViewInShader(gl, Shader.BuiltInShader.PLAIN_TEXTURED.getShader(), mvm, pm);
    	textRenderer.draw(gl, sizeString, minSize*0.5f-textWidth*0.5f+xshift,
    			yshift+2*h_scale-textRenderer.getStringHeigh()*textscale, 1f, textscale);
	    textRenderer.endRendering(gl);
	    updateModelViewInShader(gl, Shader.BuiltInShader.PLAIN_TEXTURED.getShader(), modelViewMatrix, projectionMatrix);
		
		gl.glEnable(GL.GL_DEPTH_TEST);
	}
	
	public void drawLegendThisFrame(String lowerLabel, String middleLabel, String upperLabel){
		this.drawLegendThisFrame = true;
		this.legendLabels[0] = lowerLabel;
		this.legendLabels[1] = middleLabel;
		this.legendLabels[2] = upperLabel;
	}
	
	public void reDraw(){
		reRenderTexture = true;
		this.repaint();
	}
	
	public void updateAtoms(){
		this.updateRenderContent = true;
		this.reDraw();
	}
	
	// region MouseListener
	public void mouseClicked(MouseEvent arg0) {
		this.performPicking(arg0);
	}

	public void mouseEntered(MouseEvent arg0) {}

	public void mouseExited(MouseEvent arg0) {}

	public void mousePressed(MouseEvent arg0) {
		this.requestFocusInWindow();
		if (!mousePressed) {
			this.dragMatrix = new GLMatrix();
			this.dragMatrix.mult(rotMatrix);
			arcBall.setRotationStartingPoint(arg0.getPoint());
		}
		mousePressed = true;
		startDragPosition = arg0.getPoint();
	}

	public void mouseReleased(MouseEvent arg0) {
		mousePressed = false;
	}

	// endregion MouseListener

	// region MouseMotionListener
	@Override
	public void mouseDragged(MouseEvent e) {
		if (mousePressed) {
			Point newDragPosition = e.getPoint();
			if ((e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) == InputEvent.ALT_DOWN_MASK){
				zoom *= 1 + (newDragPosition.y - startDragPosition.y) / 50f;
				if (zoom>50f) zoom = 50f;
				if (zoom<0.05f) zoom = 0.05f;
			}
			else if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) == 
					(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)){
				
				float[][] a = rotMatrix.getAsArray();
				
				Vec3 x_rot = new Vec3(-a[0][0], -a[1][0], -a[2][0]);
				Vec3 y_rot = new Vec3(a[0][1], a[1][1], a[2][1]);
				
				x_rot.multiply((startDragPosition.x - newDragPosition.x) / (globalMaxBounds.maxComponent()*zoom));
				y_rot.multiply((startDragPosition.y - newDragPosition.y) / (globalMaxBounds.maxComponent()*zoom));
				
				Vec3 newOffset = coordinateCenterOffset.addClone(x_rot).add(y_rot);
				Vec3 max = globalMaxBounds.divideClone(globalMaxBounds.maxComponent());
				
				if (Math.abs(newOffset.x)>0.5f*max.x) newOffset.x = 0.5f*max.x*Math.signum(newOffset.x);
				if (Math.abs(newOffset.y)>0.5f*max.y) newOffset.y = 0.5f*max.y*Math.signum(newOffset.y);
				if (Math.abs(newOffset.z)>0.5f*max.z) newOffset.z = 0.5f*max.z*Math.signum(newOffset.z);
				coordinateCenterOffset.setTo(newOffset);
				
			}
			else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK){
				moveX -= (newDragPosition.x - startDragPosition.x) / (globalMaxBounds.maxComponent()*zoom);
				moveY += (newDragPosition.y - startDragPosition.y) / (globalMaxBounds.maxComponent()*zoom);
			} else {
				if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK){
					GLMatrix mat = new GLMatrix();
					mat.rotate(newDragPosition.x - startDragPosition.x, 0, 1, 0);
					mat.mult(rotMatrix);
					rotMatrix.loadIdentity();
					rotMatrix.mult(mat);
				} else {
					//ArcBall update
					float[] quat = arcBall.drag(newDragPosition);
					if (quat != null) {
						rotMatrix.loadIdentity();
					    rotMatrix.setRotationFromQuaternion(quat);   
					    rotMatrix.mult(dragMatrix);
					}
				}
			}
			startDragPosition = newDragPosition;
			this.reDraw();
		}
	}
	
	public void mouseMoved(MouseEvent arg0) {}

	// endregion MouseMotionListener

	// region MouseWheelListener
	public void mouseWheelMoved(MouseWheelEvent arg0) {
		zoom *= 1 + arg0.getWheelRotation() / 20f;
		if (zoom>200f) zoom = 200f;
		if (zoom<0.05f) zoom = 0.05f;
		this.reDraw();
	}

	// endregion MouseWheelListener
	
	public void setAtomData(AtomData ada, boolean newSet){
		this.atomData = ada;
		if (ada == null) {
			renderData = null;
			return;
		}
		
		this.halfbounds = ada.getBox().getHeight().multiplyClone(0.5f);
		if (newSet){
			ignoreTypes = new boolean[Configuration.getCrystalStructure().getNumberOfTypes()];
			ignoreElement = new boolean[Configuration.getNumElements()];
			setSphereSize(Configuration.getCrystalStructure().getDistanceToNearestNeighbor()*0.55f);
			if (!ImportStates.POLY_MATERIAL.isActive()) RenderOption.GRAINS.enabled = false;
			if (!ImportStates.SKELETONIZE.isActive()) RenderOption.DISLOCATIONS.enabled = false;
			if (!ImportStates.SKELETONIZE.isActive()) RenderOption.STACKING_FAULT.enabled = false;
			
			//Find global maximum boundaries
			globalMaxBounds = new Vec3();
			AtomData tmp = ada;
			//Iterate over the whole set
			while (tmp.getPrevious()!=null) tmp = tmp.getPrevious();
			do {
				Vec3 bounds = tmp.getBox().getHeight();
				if (bounds.x>globalMaxBounds.x) globalMaxBounds.x = bounds.x;
				if (bounds.y>globalMaxBounds.y) globalMaxBounds.y = bounds.y;
				if (bounds.z>globalMaxBounds.z) globalMaxBounds.z = bounds.z;
				tmp = tmp.getNext();
			} while (tmp != null);
			
			ignoreGrain.clear();
			if (Configuration.getGrainIndices().size()!=0){
				for (int g : Configuration.getGrainIndices()){
					ignoreGrain.put(g, false);
				}
				ignoreGrain.put(Atom.IGNORED_GRAIN, false);
				ignoreGrain.put(Atom.DEFAULT_GRAIN, false);
			}
			
			createGrainColorTable();
		}
		
		//TODO switching between ortho & non-ortho
		if (this.renderInterval==null || newSet)
			this.renderInterval = new RenderRange(ada.getBox().getHeight());
		else {
			renderInterval.setGlobalLimit(3, this.atomData.getBox().getHeight().x);
			renderInterval.setGlobalLimit(4, this.atomData.getBox().getHeight().y);
			renderInterval.setGlobalLimit(5, this.atomData.getBox().getHeight().z);
			if (newSet || renderInterval.isNoLimiting()) renderInterval.reset();		
		}
		renderData = new SphereRenderData<Atom>(atomData.getAtoms(), true);
		this.updateAtoms();
	}

	private void createGrainColorTable() {
		grainColorTable.clear();
		ArrayList<Integer> sortGrainIndices = new ArrayList<Integer>(Configuration.getGrainIndices());
		Collections.sort(sortGrainIndices);
		float[][] colors = ColorTable.createColorTable(sortGrainIndices.size()+2, 0.5f);
		int j=0;
		for (int i: sortGrainIndices){
			grainColorTable.put(i, colors[j]);
			j++;
		}
	}
	
	public RenderRange getRenderRange(){
		return renderInterval;
	}
	
	public boolean isUpdateRenderContent() {
		return updateRenderContent;
	}
	
	public void setAtomRenderMethod(AtomRenderType value){
		atomRenderType = value;
		this.updateAtoms();
	}
	
	public AtomRenderType getAtomRenderType() {
		return atomRenderType;
	}

	public float getSphereSize() {
		return defaultSphereSize;
	}

	public void setSphereSize(float sphereSize) {
		this.defaultSphereSize = sphereSize;
		this.updateAtoms();
	}
	
	public boolean isTypeIgnored(int i){
		if (i>=ignoreTypes.length) return true;
		return ignoreTypes[i];
	}
	
	public boolean isElementIgnored(int i){
		return ignoreElement[i];
	}
	
	public boolean isRenderingAtomsAsRBV() {
		return renderingAtomsAsRBV;
	}
		
	public void setRenderingAtomsAsRBV(boolean rbvVisible) {
		this.renderingAtomsAsRBV = rbvVisible;
		this.reDraw();
	}
	
	public boolean isGrainIgnored(int i){
		if (ignoreGrain.containsKey(i))
			return ignoreGrain.get(i);
		else return true;
	}
	
	public float[] getGrainColor(int i){
		float[] color = grainColorTable.get(i); 
		if (color != null) return color;
		else return new float[]{0f, 0f, 0f};
	}
	
	public void setTypeIgnored(int i, boolean ignore){
		if (i>Configuration.getCrystalStructure().getNumberOfTypes()) return;
		ignoreTypes[i] = ignore;
	}
	
	public void setElementIgnored(int i, boolean ignore){
		if (i>ignoreElement.length) return;
		ignoreElement[i] = ignore;
	}
	
	public void setGrainIgnored(int i, boolean ignore){
		ignoreGrain.put(i, ignore);
	}
	
	public void resetZoom(){
		zoom = 1f;
		coordinateCenterOffset.setTo(0f, 0f, 0f);
		this.reDraw();
	}
	
	/**
	 * Sets a shift [-1..1] in HSV colorspace 
	 * @param h
	 * @param s
	 * @param v
	 * @param perVType if true, the color shift is applied for virtual elements,
	 * otherwise only for real elements
	 */
	public void setColorShiftForElements(float h, float s, float v, boolean perVType){
		this.colorShiftForVElements = perVType;
		this.colorShiftForElements.x = h;
		this.colorShiftForElements.y = s;
		this.colorShiftForElements.z = v;
		this.updateAtoms();
	}
	
	public Tupel<Vec3,Boolean> getColorShiftForElements(){
		return new Tupel<Vec3, Boolean>(colorShiftForElements, colorShiftForVElements);
	}
	
	/**
	 * Sets the selected point of view, resets model shift, but leaves the zoom unchanged
	 * @param rotX
	 * @param rotY
	 * @param rotZ
	 */
	public void setPOV(float rotX, float rotY, float rotZ){
		this.rotMatrix.loadIdentity();
		this.rotMatrix.rotate(rotX, 1.0f, 0.0f, 0.0f);
		this.rotMatrix.rotate(rotY, 0.0f, 1.0f, 0.0f);
		this.rotMatrix.rotate(rotZ, 0.0f, 0.0f, 1.0f);
		this.moveX = 0f; this.moveY = 0;
		
		this.reDraw();
	}
	
	public void setPOV(float[] m){
		//0-15 rot, 16 zoom, 17-18 moveXY, 19-21 coordinateCenterOffset
		if (m == null || m.length<=19) return; 
		this.rotMatrix.loadIdentity();
		this.rotMatrix.mult(m);
		this.moveX = m[17]; this.moveY = m[18];
		this.zoom = m[16];
		if (m.length>=22)
			this.coordinateCenterOffset.setTo(m[19], m[20], m[21]);
		else this.coordinateCenterOffset.setTo(0f, 0f, 0f);
		this.reDraw();
	}
	
	public float[] getPov(){
		float[] m = new float[22];
		rotMatrix.getMatrix().get(m, 0, 16);
		rotMatrix.getMatrix().rewind();
		m[16] = zoom;
		m[17] = moveX;
		m[18] = moveY;
		m[19] = coordinateCenterOffset.x;
		m[20] = coordinateCenterOffset.y;
		m[21] = coordinateCenterOffset.z;
		return m;
	}
	
	/**
	 * 
	 * @param e 
	 */
	private void performPicking(MouseEvent e){
		final int picksize = 7;
		
		if (atomData==null) return;

		GL3 gl = this.getGLFromContext();
		updateIntInAllShader(gl, "picking", 1);
		
		//Extract viewport
		int[] viewport = new int[4];
		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
		Point p = e.getPoint();
		
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		//Render in Picking mode
		gl.glDisable(GL.GL_BLEND);
		gl.glEnable(GL.GL_SCISSOR_TEST);
		gl.glScissor(p.x-picksize/2, viewport[3] - p.y-picksize/2, picksize, picksize);
		renderScene(gl, true ,0, null);
		gl.glDisable(GL.GL_SCISSOR_TEST);
		gl.glEnable(GL.GL_BLEND);
		
		if (pickList.size() > 16777214){
			JLogPanel.getJLogPanel().addLog("Too many objects in the scene, show less objects to enable picking");
			pickList.clear();
			return;
		}
		
		float[] selectBuf = new float[3*picksize*picksize];
		FloatBuffer wrappedBuffer = FloatBuffer.wrap(selectBuf);
		gl.glReadPixels(p.x-picksize, viewport[3] - p.y-picksize/2, picksize, picksize, GL.GL_RGB, GL.GL_FLOAT, wrappedBuffer);
		
		int hits = 0;
		
		//Identify unique objects
		TreeSet<Integer> hitMap = new TreeSet<Integer>();
		for (int i=0; i<selectBuf.length/3; i++){
			int r = (int)(selectBuf[i*3]*255f);
			int g = (int)(selectBuf[i*3+1]*255f);
			int b = (int)(selectBuf[i*3+2]*255f);
			int num = (r<<16) | (g<<8) | b ;
			hitMap.add(num);
		}
		
		boolean repaintRequired = false;
		boolean cleared = false;
		
		//Process hits
		for (Integer num : hitMap){			
			if (num == 0x000000) continue; //Pure black > Background
			
			Pickable picked = pickList.get(num-1);
			JLogPanel.getJLogPanel().addLog(picked.printMessage(e));
			
			if (picked.isHighlightable()){
				if (!cleared) {
					highLightObjects.clear();
					cleared = true;
				}
				repaintRequired = true;
				highLightObjects.add(picked);
			}
			if (picked.getHighlightedObjects() != null){
				if (!cleared) {
					highLightObjects.clear();
					cleared = true;
				}
				repaintRequired = true;
				highLightObjects.addAll(picked.getHighlightedObjects());
			}
			hits++;
		}
		if (hits == 0) {
			if (highLightObjects.size() > 0){
				highLightObjects.clear();
				repaintRequired = true;
			}
		}
		pickList.clear();
		
		updateIntInAllShader(gl,"picking", 0);
		if (repaintRequired){
			reRenderTexture = true;
			this.reDraw();
		}
	}
	
    private boolean isVectorInPBC(Vec3 v){
		return (!(Configuration.getPbc()[0] && Math.abs(v.x) > halfbounds.x)
				&& !(Configuration.getPbc()[1] && Math.abs(v.y) > halfbounds.y)
				&& !(Configuration.getPbc()[2] && Math.abs(v.z) > halfbounds.z));
	}
    
    //region export methods
	public void makeScreenshot(String filename, String type, boolean sequence, int w, int h){
		GL3 gl = this.getGLFromContext();
		
		int oldwidth = this.width;
		int oldheight = this.height;
		this.width = w;
		this.height = h;
		
		try {
			FrameBufferObject screenshotFBO = new FrameBufferObject(w, h, gl);
			//store size and FBOs
			FrameBufferObject fboLeftOld = fboLeft;
			FrameBufferObject fboRightOld = fboRight;
			FrameBufferObject fboBackgroundOld = fboBackground;
			FrameBufferObject deferredFBOOld = fboDeferredBuffer;
			
			fboDeferredBuffer= new FrameBufferObject(w, h, gl, true);
			fboLeft = new FrameBufferObject(w, h, gl);
			fboRight = new FrameBufferObject(w, h, gl);
			fboBackground = new FrameBufferObject(w, h, gl);
			this.makeBackground();
			this.makeFullScreenQuad(gl);
			
			if (!sequence) {
				renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
				renderFinalScene(gl, screenshotFBO);
				
				BufferedImage bi = screenshotFBO.textureToBufferedImage(w, h, gl);
				ImageIO.write(bi, type, new java.io.File(filename));
			} else {
				if (this.atomData!=null){
					AtomData currentAtomData = this.atomData;
					
					//rewind to beginning
					while (atomData.getPrevious()!=null)
						this.atomData = atomData.getPrevious();
					
					this.setAtomData(this.atomData, false);
					Configuration.setCurrentAtomData(this.atomData);
					
					do {
						renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
						renderFinalScene(gl, screenshotFBO);

						BufferedImage bi = screenshotFBO.textureToBufferedImage(w, h, gl);
						ImageIO.write(bi, type, new java.io.File(filename+atomData.getName()+"."+type) );
						
						this.setAtomData(this.atomData.getNext(), false);
						Configuration.setCurrentAtomData(this.atomData);
					} while (this.atomData != null);
					
					this.setAtomData(currentAtomData, false);
					Configuration.setCurrentAtomData(currentAtomData);
				}
			}
			//restore old size and FBOs
			fboLeft.destroy(gl); fboLeft = fboLeftOld;             
			fboRight.destroy(gl); fboRight = fboRightOld;           
			fboBackground.destroy(gl); fboBackground = fboBackgroundOld;
			fboDeferredBuffer.destroy(gl); fboDeferredBuffer = deferredFBOOld; 
			renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
			screenshotFBO.destroy(gl);
			
		} catch (GLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			this.width = oldwidth;
			this.height = oldheight;
			this.makeFullScreenQuad(gl);
		}
		this.reDraw();
	}
	
	//endregion export methods
	
	/**
	 * Creates oriented billboards.
	 * Depending on the visible size of atoms on screen, the detail level of billboards is adjusted,
	 * ranging from a simple flat quad, to close approximations of a hemisphere.
	 * This way, atoms do visibly correctly overlap each other on screen
	 * @param gl
	 */
	private void createSphereRenderData(GL3 gl){
		int[] t;
		float[] v = null;
		
		float maxPixelSize = estimatePixelSizeOfAtom();
		
		float[] normal = new float[]{0.0f,0.0f,  -1f, 1f,   1f, -1f,  1f, 1f,  -1f, -1f};
		float[] v_orig = new float[]{0f,0f,1f, -1f,1f,0f,  1f,-1f,0f, 1f,1f,0f, -1f,-1f,0f};
		if (Configuration.Options.PERFECT_SPHERES.isEnabled())
			v_orig = new float[]{0f,0f,0f, -1f,1f,0f,  1f,-1f,0f, 1f,1f,0f, -1f,-1f,0f};
		
		//A simple flat quad
		t = new int[]{4,2,1,3};
		sphereVBOPrimitive = GL.GL_TRIANGLE_STRIP;
		
		if (maxPixelSize>10 && !Configuration.Options.PERFECT_SPHERES.isEnabled()){
			//upgrade to a pyramid of 4 triangles
			t = new int[]{0,1,4,2,3,1};
			sphereVBOPrimitive = GL.GL_TRIANGLE_FAN;
			
			if (maxPixelSize>40){ 
				//Tesselate two or three times for improved rendering
				float s = (float)(1./Math.sqrt(2.))*1f;
				v_orig = new float[]{0f,0f,1f, -s,s,0f,  s,-s,0f, s,s,0f, -s,-s,0f};
				t = new int[]{0,1,4, 0,4,2, 0,2,3, 0,3,1};
				
				Tupel<int[],float[]> data;
				int iter = maxPixelSize > 200 ? 3 : 2;
				normal = new float[v_orig.length/3*2];
				
				for (int j=0; j<iter;j++){
					data = SphereGenerator.tesselate(t, v_orig);
					t = data.o1;
					v_orig=data.o2;
					normal = new float[v_orig.length/3*2];
					
					for (int i = 0; i<v_orig.length/3; i++){
						normal[i*2]   = v_orig[i*3];
						normal[i*2+1] = v_orig[i*3+1];
						float a = 1f-(v_orig[i*3]*v_orig[i*3] + v_orig[i*3+1]*v_orig[i*3+1]);
						if (a<0f) a = 0.0f; 
						v_orig[i*3+2] = (float)Math.sqrt(a);
					}
				}
				
				sphereVBOPrimitive = GL.GL_TRIANGLES;
			}
		}
		
		GLMatrix rotInverse = rotMatrix.clone();
		rotInverse.inverse();
		float[] tmp = new float[16];
		rotInverse.getMatrix().get(tmp, 0, 16);
		
		v = new float[v_orig.length];
		for (int i = 0; i<v.length; i+=3){
			v[i+0] = v_orig[i] * tmp[0] + v_orig[i+1] * tmp[4] + v_orig[i+2] * tmp[8]; 
			v[i+1] = v_orig[i] * tmp[1] + v_orig[i+1] * tmp[5] + v_orig[i+2] * tmp[9];
			v[i+2] = v_orig[i] * tmp[2] + v_orig[i+1] * tmp[6] + v_orig[i+2] * tmp[10];
		}
		
		sphereVBOIndexCount = t.length;
		if (sphereVboIndices != null)
			gl.glDeleteBuffers(3, sphereVboIndices, 0);
		else  sphereVboIndices = new int[3];
		
		gl.glGenBuffers(3, sphereVboIndices, 0);
		
		IntBuffer tb = IntBuffer.wrap(t);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[0]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, tb.capacity()*Integer.SIZE/8, tb, GL.GL_STATIC_DRAW);
		
		FloatBuffer vb = FloatBuffer.wrap(v);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[1]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.capacity()*Float.SIZE/8, vb, GL.GL_STATIC_DRAW);
		
		if (normal != null){
			FloatBuffer texb = FloatBuffer.wrap(normal);
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[2]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, texb.capacity()*Float.SIZE/8, texb, GL.GL_STATIC_DRAW);
		}
		
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	}
	
	private void makeFullScreenQuad(GL3 gl){
		if (fullScreenQuad != null)
			fullScreenQuad.dispose(gl);
		fullScreenQuad = new VertexDataStorageLocal(gl, 4, 3, 0, 2, 0, 0, 0, 0, 0);
		fullScreenQuad.beginFillBuffer(gl);
		fullScreenQuad.setTexCoord(1f, 0f); fullScreenQuad.setVertex(width,0f,0f);
		fullScreenQuad.setTexCoord(1f, 1f); fullScreenQuad.setVertex(width,height,0f);
		fullScreenQuad.setTexCoord(0f, 0f); fullScreenQuad.setVertex(0f,0f,0f);
		fullScreenQuad.setTexCoord(0f, 1f); fullScreenQuad.setVertex(0f,height,0f);
		
		fullScreenQuad.endFillBuffer(gl);
	}
	
	private void makeBackground(){
		//Render Background
		GL3 gl = getGLFromContext();
		fboBackground.bind(gl, false);
		gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
	
		//Use a gray to gray color gradient as background, otherwise use pure white
		GLMatrix mvm = new GLMatrix();
		GLMatrix pm = getProjectionFlatMatrix();
		
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), mvm, pm);
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, 4, 3, 0, 0, 4, 0, 0, 0, 0);		
		vds.beginFillBuffer(gl);
		if (!RenderOption.PRINTING_MODE.isEnabled()){
			vds.setColor(0.8f, 0.8f, 0.8f, 1f); vds.setVertex(0f, 0f, 0f);
			vds.setColor(0.8f, 0.8f, 0.8f, 1f); vds.setVertex(width, 0f, 0f);
			vds.setColor(0.2f, 0.2f, 0.2f, 1f); vds.setVertex(0f, height, 0f);
			vds.setColor(0.2f, 0.2f, 0.2f, 1f); vds.setVertex(width, height, 0f);
		} else {
			vds.setColor(1f, 1f, 1f, 1f);
			vds.setVertex(0f, 0f, 0f);     vds.setVertex(width, 0f, 0f);
			vds.setVertex(0f, height, 0f); vds.setVertex(width, height, 0f);
		}
		vds.endFillBuffer(gl);
        vds.draw(gl, GL.GL_TRIANGLE_STRIP);
        vds.dispose(gl);
        updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), modelViewMatrix, projectionMatrix);
	
		fboBackground.unbind(gl);
	}
	
	/**
	 * Computes the size of the largest atom on the screen in pixel.
	 * Using orthogonal projection, the value is exact, in perspective projection
	 * an approximation
	 * @return
	 */
	private float estimatePixelSizeOfAtom(){
		if (atomData == null) return 1f;
		float[] sphereSize = Configuration.getCrystalStructure().getSphereSizeScalings();
		float maxSphereSize = 0f;
		for (int i=0; i<sphereSize.length; i++){
			sphereSize[i] *= defaultSphereSize;
			if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
		}
		return estimateUnitLengthInPixels()*maxSphereSize*2;
	}
	
	private float estimateUnitLengthInPixels(){
		if (atomData == null) return 1f;
		float boxSize = atomData.getBox().getHeight().maxComponent();
		float unitFraction = 1f/boxSize;	//Apply scaling factor as in modelview
		
		float size = 0f;
		float[][] pm = projectionMatrix.getAsArray();
		
		if (!RenderOption.PERSPECTIVE.isEnabled() && !RenderOption.STEREO.isEnabled()){
			float p1 = pm[0][0]+pm[0][1]+pm[0][2];	//A point at (1,0,0)
			float p2 = pm[1][0]+pm[1][1]+pm[1][2];  //A point at (0,1,0)
			//Depending on the aspect ratio, select the correct size
			size = Math.min(Math.abs(p1),Math.abs(p2))*unitFraction*Math.max(width, height)/2;
		} else {
			//Perspective projection is currently an approximation
			//TODO Implement formula correctly
			float p1 = (pm[0][0]+pm[0][1]+pm[0][2]+pm[2][0]-pm[2][1]-pm[2][2]);
			float p2 = (pm[1][0]+pm[1][2]+pm[1][2]+pm[2][0]-pm[2][1]-pm[2][2]);
			size = Math.max(Math.abs(p1),Math.abs(p2))*unitFraction*Math.min(width, height)/2;
		}
		
		return size;
	}
	
	public float[] getNextPickingColor(Pickable pickableObject){
		int pickingIndex = pickList.size()+1; //Avoid 0x000000 which is the black background
		int r = (pickingIndex & 0xff0000) >> 16;
		int g = (pickingIndex & 0xff00) >> 8;
		int b = pickingIndex & 0xff;
		
		pickList.add(pickableObject);
		
		float[] f = {r*0.003921569f, g*0.003921569f, b*0.003921569f, 1f};
		return f;
	}
	
	public void drawSpheres(GL3 gl, SphereRenderData<?> ard, boolean picking){
		VertexDataStorage.unbindAll(gl);
		
		if (openGLVersion>=3.3 && ard.isSubdivided()){
			drawSpheresInstanced(gl.getGL3(), ard, picking);
			return;
		}
		
		gl.glDisable(GL.GL_BLEND); //Transparency can cause troubles and should be avoided, disabling blending might be faster then
		gl.glDisable(GL.GL_CULL_FACE); // The billboard is always correctly oriented, do not bother testing
		
		//Select the rendering shader
		Shader shader;
		if (Configuration.Options.PERFECT_SPHERES.isEnabled())
			shader = BuiltInShader.BILLBOARD_DEFERRED_PERFECT.getShader();
		else shader = BuiltInShader.BILLBOARD_DEFERRED.getShader();
		
		//Set all uniforms, depending on the active shader, some may not be used
		shader.enable(gl);
		//Pass transpose (=inverse) of rotation matrix to rotate billboard normals
		gl.glUniformMatrix3fv(gl.glGetUniformLocation(shader.getProgram(), "inv_rot"), 1, true, rotMatrix.getUpper3x3Matrix());
		

		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, sphereVboIndices[0]);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[1]);
		gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX, 3, GL.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[2]);
		gl.glVertexAttribPointer(Shader.ATTRIB_TEX0, 2, GL.GL_FLOAT, false, 0, 0);
		
		if (!picking){
			if (Configuration.Options.NO_SHADING.isEnabled())
				gl.glUniform1i(gl.glGetUniformLocation(shader.getProgram(), "picking"), 1);
			else gl.glUniform1i(gl.glGetUniformLocation(shader.getProgram(), "picking"), 0);
		}

		int sphereColorUniform = gl.glGetUniformLocation(shader.getProgram(), "Color");
		int sphereTranslateUniform = gl.glGetUniformLocation(shader.getProgram(), "Move");

		for (int i=0; i<ard.getRenderableCells().size(); ++i){
			SphereRenderData<? extends Pickable>.Cell c = ard.getRenderableCells().get(i);
			//TODO remove this line and the upper extend in case of Java7 or higher
			SphereRenderData<? extends Vec3>.Cell c2 = ard.getRenderableCells().get(i);
			if (c.getNumVisibleObjects() == 0) continue;
			float[] colors = c.getColorArray();
			float[] sizes = c.getSizeArray();
			List<? extends Vec3> objects = c2.getObjects();
			boolean[] visible = c.getVisibiltyArray();
			
			for (int j=0; j<c.getNumObjects(); j++){
				if (visible[j]){
					Vec3 a = objects.get(j);
					 
					if (picking){
						float[] col = getNextPickingColor(c.getObjects().get(j));
						gl.glUniform4f(sphereColorUniform, col[0], col[1], col[2], 1f);
					} else {
						gl.glUniform4f(sphereColorUniform, colors[3*j], colors[3*j+1], colors[3*j+2], 1f);
					}
					
					gl.glUniform4f(sphereTranslateUniform, a.x, a.y, a.z, sizes[j]);
	
					gl.glDrawElements(sphereVBOPrimitive, sphereVBOIndexCount, GL.GL_UNSIGNED_INT, 0);
				}
			}
		}

		//Disable vbos and switch active shader
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		Shader.disableLastUsedShader(gl);
		
		if (!picking) gl.glEnable(GL.GL_BLEND);
		gl.glEnable(GL.GL_CULL_FACE);
	}

	private void drawSpheresInstanced(GL3 gl, SphereRenderData<?> ard, boolean picking){
		gl.glDisable(GL.GL_BLEND); //Transparency can cause troubles and should be avoided, disabling blending might be faster then
		gl.glDisable(GL.GL_CULL_FACE); // The billboard is always correctly oriented, do not bother testing
		
		//Select the rendering shader
		Shader shader;
		if (Configuration.Options.PERFECT_SPHERES.isEnabled())
			if (BuiltInShader.BILLBOARD_INSTANCED_DEFERRED_PERFECT_GL4.getShader().isAvailable()) 
				shader = BuiltInShader.BILLBOARD_INSTANCED_DEFERRED_PERFECT_GL4.getShader();
			else shader = BuiltInShader.BILLBOARD_INSTANCED_DEFERRED_PERFECT.getShader();
		else shader = BuiltInShader.BILLBOARD_INSTANCED_DEFERRED.getShader();

		// Init the shader for drawing test bounding boxes
		Shader visTestShader = BuiltInShader.VERTEX_ARRAY_COLOR_UNIFORM.getShader();
		visTestShader.enable(gl);
		int visMvmpUniform = gl.glGetUniformLocation(visTestShader.getProgram(), "mvpm");
		GLMatrix mvmp = projectionMatrix.clone();
		mvmp.mult(modelViewMatrix);
		gl.glUniform4f(
				gl.glGetUniformLocation(visTestShader.getProgram(), "Color"),1f, 0f, 0f, 1f);

		shader.enable(gl);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[2]);
		gl.glVertexAttribPointer(Shader.ATTRIB_TEX0, 2, GL.GL_FLOAT, false, 0, 0);
		gl.glVertexAttribDivisor(Shader.ATTRIB_TEX0, 0);
		//Pass transpose (=inverse) of rotation matrix to rotate billboard normals
		gl.glUniformMatrix3fv(gl.glGetUniformLocation(shader.getProgram(), "inv_rot"), 1, true, rotMatrix.getUpper3x3Matrix());
		
		if (!picking){
			if (Configuration.Options.NO_SHADING.isEnabled())
				gl.glUniform1i(gl.glGetUniformLocation(shader.getProgram(), "picking"), 1);
			else gl.glUniform1i(gl.glGetUniformLocation(shader.getProgram(), "picking"), 0);
		}

		gl.glVertexAttribDivisor(Shader.ATTRIB_VERTEX, 0);
		gl.glVertexAttribDivisor(Shader.ATTRIB_VERTEX_OFFSET, 1);
		gl.glVertexAttribDivisor(Shader.ATTRIB_COLOR, 1);

		CellRenderBuffer cbr = cellRenderRingBuffer.getCurrent();
		ard.sortCells(modelViewMatrix);

		HashMap<SphereRenderData<?>.Cell, int[]> currentActiveRenderingTest = 
				new HashMap<SphereRenderData<?>.Cell, int[]>();
		boolean hasRenderedCell = true;	//Set to true if spheres have been rendered and to false in occlusion test
				
//		int cellsDrawn = 0;

		for (int j=0; j<ard.getRenderableCells().size(); j++){
			SphereRenderData<? extends Pickable>.Cell c = ard.getRenderableCells().get(j);
			//TODO remove this line and the upper extend in case of Java7 or higher
			SphereRenderData<? extends Vec3>.Cell c2 = ard.getRenderableCells().get(j);

			boolean renderCell = true;
			//Test if the cell needs to be rendered 
			if (currentActiveRenderingTest.containsKey(c)){
				int[] result = new int[1];
				int[] query = currentActiveRenderingTest.remove(c);
				gl.glGetQueryObjectuiv(query[0], GL3.GL_QUERY_RESULT, result, 0);
				if (result[0] == 0) renderCell=false;
				gl.glDeleteQueries(1, query, 0);
			}
			
			//Full renderer
			if (renderCell){
				if (cbr.fence != -1){
					// Wait until last call using the buffer is finished
					gl.glClientWaitSync(cbr.fence, GL3.GL_SYNC_FLUSH_COMMANDS_BIT, GL3.GL_TIMEOUT_IGNORED);
					gl.glDeleteSync(cbr.fence);
					cbr.fence = -1;
				}
				
				if (c.getNumVisibleObjects()>0){
//					cellsDrawn++;
					//Reenable masks that have been disable in the occlusion test
					gl.glColorMask(true, true, true, true);
					gl.glDepthMask(true);
					VertexDataStorage.unbindAll(gl);
					
					shader.enable(gl);
					
					gl.glBindBuffer(cbr.bufferType, cbr.buffer);
					FloatBuffer buf = gl.glMapBufferRange(cbr.bufferType,0, cbr.bufferSize, 
							 GL3.GL_MAP_WRITE_BIT | GL3.GL_MAP_INVALIDATE_BUFFER_BIT| GL3.GL_MAP_UNSYNCHRONIZED_BIT).asFloatBuffer();
					
					float[] colors = c.getColorArray();
					float[] sizes = c.getSizeArray();
					List<? extends Vec3> objects = c2.getObjects();
					boolean[] visible = c.getVisibiltyArray();
					//Fill render buffer, color values is either the given value or a picking color
					if (picking){
						for (int i=0; i<c.getNumObjects(); i++){
							if (visible[i]){
								Vec3 ra = objects.get(i);
								float[] col = getNextPickingColor(c.getObjects().get(i));
								buf.put(col[0]); buf.put(col[1]); buf.put(col[2]); buf.put(1f);
								buf.put(ra.x); buf.put(ra.y); buf.put(ra.z); buf.put(sizes[i]);
							}
						}	
					} else {
						for (int i=0; i<c.getNumObjects(); i++){
							if (visible[i]){
								Vec3 ra = objects.get(i);
								buf.put(colors[3*i]); buf.put(colors[3*i+1]); buf.put(colors[3*i+2]); buf.put(1f);
								buf.put(ra.x); buf.put(ra.y); buf.put(ra.z); buf.put(sizes[i]);
							}
						}
					}
					gl.glUnmapBuffer(cbr.bufferType);
					
					gl.glVertexAttribPointer(Shader.ATTRIB_COLOR, 4, GL.GL_FLOAT, false, 8*Float.SIZE/8, 0);
					gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX_OFFSET, 4, GL.GL_FLOAT, false, 8*Float.SIZE/8, 4*Float.SIZE/8);
					gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[1]);
					gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX, 3, GL.GL_FLOAT, false, 0, 0);
					gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, sphereVboIndices[0]);
					//Draw spheres
					gl.glDrawElementsInstanced(sphereVBOPrimitive, sphereVBOIndexCount, GL.GL_UNSIGNED_INT, 0, c.getNumVisibleObjects());
					
					cbr.fence = gl.glFenceSync(GL3.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
					hasRenderedCell = true;
					cbr = cellRenderRingBuffer.getNext();
				}
			}
			//Occlusion test
			if (j+SIZE_CELL_RENDER_RING<ard.getRenderableCells().size()){
				SphereRenderData<?>.Cell futureCell = ard.getRenderableCells().get(j+SIZE_CELL_RENDER_RING);
				if (futureCell.getNumVisibleObjects() > 0){
					int[] query = new int[1];
					gl.glGenQueries(1, query, 0);
					if (hasRenderedCell){
						gl.glColorMask(false, false, false, false);
						gl.glDepthMask(false);
						visTestShader.enable(gl);
						hasRenderedCell = false;
					}
					
					Vec3 trans = futureCell.getOffset();
					Vec3 scale = futureCell.getSize();
					GLMatrix m = mvmp.clone();
					m.translate(trans.x, trans.y, trans.z);
					m.scale(scale.x, scale.y, scale.z);
	
//					gl.glUniform4f(gl.glGetUniformLocation(visTestShader.getProgram(), "Color"), 1f,1f, 0f, 1f);
//					gl.glColorMask(true, true, true, true);
//					gl.glDepthMask(true);
//					
//					gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_LINE);
					gl.glUniformMatrix4fv(visMvmpUniform, 1, false, m.getMatrix());
					gl.glBeginQuery(GL3.GL_SAMPLES_PASSED, query[0]);
					SimpleGeometriesRenderer.drawCubeWithoutNormals(gl);	//Bounding box
					gl.glEndQuery(GL3.GL_SAMPLES_PASSED);
//					gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_FILL);
					
					currentActiveRenderingTest.put(futureCell, query);
				}
			}
		}

		//System.out.println("Drawn "+cellsDrawn+" of "+ard.getRenderableCells().size());

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);

		gl.glVertexAttribDivisor(Shader.ATTRIB_VERTEX_OFFSET, 0);
		gl.glVertexAttribDivisor(Shader.ATTRIB_COLOR, 0);
		
		VertexDataStorage.unbindAll(gl);
		gl.glColorMask(true, true, true, true);
		gl.glDepthMask(true);
		
		Shader.disableLastUsedShader(gl);

		updateModelViewInShader(gl, visTestShader, modelViewMatrix, projectionMatrix);
		if (!picking) gl.glEnable(GL.GL_BLEND);
		gl.glEnable(GL.GL_CULL_FACE);
	}
	
	private void updateModelViewInShader(GL3 gl, Shader shader, GLMatrix modelView, GLMatrix projection){
		GLMatrix pmvp = projection.clone();
		pmvp.mult(modelView);
		Shader.pushShader();
		gl.glUseProgram(shader.getProgram());
		gl.glUniformMatrix4fv(gl.glGetUniformLocation(shader.getProgram(), "mvm"), 1, false, modelView.getMatrix());
		gl.glUniformMatrix3fv(gl.glGetUniformLocation(shader.getProgram(), "nm"), 1, false, modelView.getNormalMatrix());
		gl.glUniformMatrix4fv(gl.glGetUniformLocation(shader.getProgram(), "mvpm"), 1, false, pmvp.getMatrix());
		Shader s = Shader.popShader();
		if (s != null)
			gl.glUseProgram(s.getProgram());
	}
	
	private void updateModelViewInAllShader(GL3 gl){
		for (Shader s : Shader.getAllShader()){
			updateModelViewInShader(gl, s, modelViewMatrix, projectionMatrix);
		}
	}
	
	private void initShaderUniforms(GL3 gl){
		this.updateFloatInAllShader(gl, "lightPos", -3.0f, 2.0f, 5f);
		
		int prog = BuiltInShader.ANAGLYPH_TEXTURED.getShader().getProgram();
		BuiltInShader.ANAGLYPH_TEXTURED.getShader().enable(gl);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "left"), 0);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "right"), 1);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "back"), 2);
		
		BuiltInShader.DEFERRED_ADS_RENDERING.getShader().enable(gl);
		prog = BuiltInShader.DEFERRED_ADS_RENDERING.getShader().getProgram();
		gl.glUniform1i(gl.glGetUniformLocation(prog, "colorTexture"), Shader.FRAG_COLOR);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "normalTexture"), Shader.FRAG_NORMAL);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "posTexture"), Shader.FRAG_POSITION);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "noiseTexture"), 4);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoTotStrength"), 2.38f);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoStrength"), 0.15f);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoFalloff"), 0.0008f);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoRad"), 0.005f);
		
		Shader.disableLastUsedShader(gl);
	}
	
	private void updateIntInAllShader(GL3 gl, String uniformName, int ... a){
		Shader.pushShader();
		for (Shader s : Shader.getAllShader()){
			gl.glUseProgram(s.getProgram());
			if (a.length == 1){
				gl.glUniform1i(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0]);
			} else if (a.length == 2){
				gl.glUniform2i(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0], a[1]);
			} else if (a.length == 3){
				gl.glUniform3i(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0], a[1], a[2]);
			} else if (a.length == 4){
				gl.glUniform4i(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0], a[1], a[2], a[3]);
			}
		}
		Shader s = Shader.popShader();
		if (s != null)
			gl.glUseProgram(s.getProgram());
	}
	
	private void updateFloatInAllShader(GL3 gl, String uniformName, float ... a){
		Shader.pushShader();
		for (Shader s : Shader.getAllShader()){
			gl.glUseProgram(s.getProgram());
			if (a.length == 1){
				gl.glUniform1f(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0]);
			} else if (a.length == 2){
				gl.glUniform2f(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0], a[1]);
			} else if (a.length == 3){
				gl.glUniform3f(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0], a[1], a[2]);
			} else if (a.length == 4){
				gl.glUniform4f(gl.glGetUniformLocation(s.getProgram(), uniformName), a[0], a[1], a[2], a[3]);
			}
		}
		Shader s = Shader.popShader();
		if (s != null)
			gl.glUseProgram(s.getProgram());
	}
	
	
	@Override
	public void dispose(GLAutoDrawable arg0) {
		//Delete all GL context related objects
		GL3 gl = arg0.getGL().getGL3();
		if (sphereVboIndices != null)
			gl.glDeleteBuffers(3, sphereVboIndices, 0);
		sphereVboIndices = null;
		
		if (cellRenderRingBuffer != null){
			for (int i=0; i<cellRenderRingBuffer.size(); i++)
				cellRenderRingBuffer.getNext().dispose(gl);
		}
		cellRenderRingBuffer = null;

		SimpleGeometriesRenderer.dispose(gl);
		ArrowRenderer.dispose(gl);
		TubeRenderer.dispose(gl);
		VertexDataStorage.unbindAll(gl);
		Shader.dispose(gl);
		textRenderer.dispose(gl);
		
		if (fboDeferredBuffer != null)   fboDeferredBuffer.destroy(gl);
		if (fboLeft != null) 	   fboLeft.destroy(gl);
		if (fboRight != null) 	   fboRight.destroy(gl);
		if (fboBackground != null) fboBackground.destroy(gl);
	}
	
	private class CellRenderBuffer{
		int buffer;
		long bufferSize;
		long fence = -1;
		int bufferType;
		
		void init(GL3 gl, long size, int bufferType){
			this.bufferType = bufferType;
			int[] buf = new int[1];
			gl.glGenBuffers(1, buf, 0);
	
			gl.glBindBuffer(bufferType, buf[0]);
			gl.glBufferData(bufferType, size, null, GL3.GL_STREAM_DRAW);
			gl.glBindBuffer(bufferType, 0);
	
			this.buffer = buf[0];
			this.bufferSize = size;
		}
		
		void dispose(GL3 gl){
			int[] buffers = new int[1];
			buffers[0] = buffer;
			gl.glDeleteBuffers(1, buffers, 0);
			if (fence != -1)
				gl.glDeleteSync(fence);
		}
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		if ((e.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK))
				== (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)){		
			showRotationSphere = true;
			this.reDraw();
		}
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_SHIFT || e.getKeyCode() == KeyEvent.VK_CONTROL){
			showRotationSphere = false;
			this.reDraw();
		}
	}
	
	@Override
	public void keyTyped(KeyEvent e) {}
	
	private interface ColoringFunction{
		float[] getColor(Atom c);
		void update();
	}
	
	private class TypeColoringAndFilter implements AtomFilter, ColoringFunction{
		float[][] colors = null;
		int numEleColors;
		
		boolean isNeeded(){
			for (int i=0; i<ignoreTypes.length;i++)
				if (ignoreTypes[i] == true) return true;
			return false;
		}
		
		@Override
		public boolean accept(Atom a) {
			return !isTypeIgnored(a.getType());
		}
		
		@Override
		public float[] getColor(Atom c) {
			int shift = (c.getElement() % numEleColors); //Derive which color to select
			return colors[c.getType() * numEleColors + shift];
		}
		
		@Override
		public void update() {
			Tupel<float[][], Integer> colorsAndNumElements =
					ColorUtils.getColorShift(colorShiftForVElements, 
							Configuration.getCrystalStructure(), colorShiftForElements);
			colors = colorsAndNumElements.o1;
			numEleColors = colorsAndNumElements.o2;
		}
	}
	
	private class GrainColoringAndFilter implements AtomFilter, ColoringFunction{
		boolean isNeeded(){
			for (boolean b : ignoreGrain.values())
				if (b) return true;
			return false;
		}
		
		@Override
		public boolean accept(Atom a) {
			return !isGrainIgnored(a.getGrain());
		}
		
		@Override
		public void update() {}
		
		public float[] getColor(Atom c) {
			return getGrainColor(c.getGrain());
		};
	}
	
	private class ElementColoringAndFilter implements AtomFilter, ColoringFunction{
		float[][] colorTable = null;
		
		boolean isNeeded(){
			if (Configuration.getNumElements()==1) return false;
			for (int i=0; i<ignoreElement.length;i++)
				if (ignoreElement[i]) return true;
			return false;
		}
		
		@Override
		public boolean accept(Atom a) {
			return !isElementIgnored(a.getElement());
		}
		
		@Override
		public float[] getColor(Atom c) {
			return colorTable[c.getElement()];
		}
		
		@Override
		public void update() {
			colorTable = ColorTable.getColorTableForElements(Configuration.getNumElements());
		}
	}
	
	private class DataColoringAndFilter implements AtomFilter, ColoringFunction{
		boolean filter = false;
		boolean inversed = false;
		float min = 0f, max = 0f;
		int selected = 0;
		
		@Override
		public boolean accept(Atom a) {
		    return (!filter || (a.getData(selected)>=min && a.getData(selected)<=max) ^ inversed);
		}
		
		@Override
		public float[] getColor(Atom c) {
			return ColorTable.getIntensityGLColor(min, max, c.getData(selected));
		}
		
		@Override
		public void update() {
			DataColumnInfo dataInfo = Configuration.getSelectedColumn();
			selected = dataInfo.getColumn();
			
			min = dataInfo.getLowerLimit();
			max = dataInfo.getUpperLimit();
			filter = Configuration.isFilterRange();
			inversed = Configuration.isFilterInversed();
		}
	}
}