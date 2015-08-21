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
import model.Configuration.AtomDataChangedEvent;
import model.Configuration.AtomDataChangedListener;
import model.mesh.Mesh;
import model.polygrain.Grain;
import processingModules.DataContainer;
import gui.glUtils.*;
import gui.glUtils.Shader.BuiltInShader;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
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
	MouseWheelListener, GLEventListener, AtomDataChangedListener {
	
	public enum RenderOption {
		INDENTER(false), GRAINS(false), LEGEND(true), COORDINATE_SYSTEM(false), THOMPSON_TETRAEDER(false),
		PRINTING_MODE(false), STEREO(false), BOUNDING_BOX(true), PERSPECTIVE(false), LENGTH_SCALE(false);
		
		private boolean enabled;
		
		private RenderOption(boolean enabled){
			this.enabled = enabled;
		}
		
		public void setEnabled(boolean enabled){
			this.enabled = enabled;
			if (RenderingConfiguration.getViewer()!=null) RenderingConfiguration.getViewer().reDraw();
			if (this == RenderOption.PRINTING_MODE){
				RenderingConfiguration.getViewer().makeBackground();
			}
		}
		
		public boolean isEnabled(){
			return enabled;
		}
	}
	
	public enum AtomRenderType {
		TYPE, ELEMENTS, GRAINS, DATA, VECTOR_DATA
	};
	
	private static final long serialVersionUID = 1L;
	
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

	private Vec3 globalMaxBounds = new Vec3();
	
	private String[] legendLabels = new String[3];
	private boolean drawLegendThisFrame = false;
	
	private boolean reRenderTexture = true;
	//The index is the position in the list, therefore it is some kind of map
	private ArrayList<Pickable> pickList = new ArrayList<Pickable>();

	private boolean[] ignoreTypes;
	private boolean[] ignoreElement = new boolean[0];
	private HashMap<Integer,Boolean> ignoreGrain = new HashMap<Integer, Boolean>();
	private HashMap<Integer,float[]> grainColorTable = new HashMap<Integer, float[]>();
	private boolean renderingAtomsAsRBV;
	
	private AtomRenderType atomRenderType = AtomRenderType.TYPE;
	private float defaultSphereSize = 1.5f;
	
	private ArrayList<Object> highLightObjects = new ArrayList<Object>();
	
	private int width, height;
	
	private GLMatrix projectionMatrix = new GLMatrix();
	private GLMatrix modelViewMatrix = new GLMatrix();
	private TextRenderer textRenderer = null;
	private SphereRenderer sphereRenderer = null;
	private ArrowRenderer arrowRenderer = null;
	
	private boolean updateRenderContent = true;
	
	private ObjectRenderData<Atom> renderData;
	
	public static double openGLVersion = 0.;
	private GLAutoDrawable glDrawable = null;
	
	private Vec3 colorShiftForElements = new Vec3();
	/**
	 * If true, each virtual element can get a slightly shifted color for showing types
	 * Else, only physically different elements are shifted in color 
	 */
	private boolean colorShiftForVElements = true;
	
	// Time it took to render the last frame in nanoseconds
	private long timeToRenderFrame = 0l;
	
	private int defaultVAO = -1;
	
	public ViewerGLJPanel(int width, int height, GLCapabilities caps) {
		super(caps);
		
		RenderingConfiguration.setViewer(this);
		
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		addGLEventListener(this);
		Configuration.addAtomDataListener(this);
		
		this.setPreferredSize(new Dimension(width, height));
		this.setMinimumSize(new Dimension(100, 100));
		
		this.setFocusable(true);
	}
	
	@Override
	public void display(GLAutoDrawable arg0) {
		boolean frameCounter = false;
		long fence = 0L;
		
		this.glDrawable = arg0;
		GL3 gl = arg0.getGL().getGL3();
		
		if (frameCounter){
			fence = gl.glFenceSync(GL3.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
			this.timeToRenderFrame = System.nanoTime();
		}
		
		if (reRenderTexture){
			updateIntInAllShader(gl, "ads", RenderingConfiguration.Options.ADS_SHADING.isEnabled() ? 1 : 0);
			renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
			reRenderTexture = false;
		}
		
		composeCompleteScene(gl, null);
		
		//FrameCounter
		if (frameCounter){
			gl.glClientWaitSync(fence, GL3.GL_SYNC_FLUSH_COMMANDS_BIT, GL3.GL_TIMEOUT_IGNORED);
			gl.glDeleteSync(fence);
			this.timeToRenderFrame = System.nanoTime()-this.timeToRenderFrame;
			System.out.println(1000000000./(this.timeToRenderFrame));
		}
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

        int[] buf = new int[1];
        gl.glGenVertexArrays(1, buf, 0);
        this.defaultVAO = buf[0];
        gl.glBindVertexArray(defaultVAO);
		
		if (sphereRenderer == null)
			sphereRenderer = new SphereRenderer(this, gl);
		if (arrowRenderer == null)
			arrowRenderer = new ArrowRenderer(this, gl);
		
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glCullFace(GL.GL_BACK);
        gl.glEnable(GL.GL_CULL_FACE);
        
		gl.glDisable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glEnable(GL3.GL_DEPTH_CLAMP);
        gl.glClearColor(0f, 0f, 0f, 0f);
        
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
		
		if (fboDeferredBuffer != null) fboDeferredBuffer.reset(gl, width, height);
		else fboDeferredBuffer = new FrameBufferObject(width, height, gl, true);
		if (fboLeft != null) fboLeft.reset(gl, width, height);
		else fboLeft = new FrameBufferObject(width, height, gl);
		if (fboRight != null) fboRight.reset(gl, width, height);
		else fboRight = new FrameBufferObject(width, height, gl);
		if (fboBackground != null) fboBackground.reset(gl, width, height);
		else fboBackground = new FrameBufferObject(width, height, gl);

		this.makeBackground();
		this.arcBall.setSize(this.width, this.height);
		this.makeFullScreenQuad(gl);
	}
	
	private GLMatrix setupProjectionOrthogonal() {
		GLMatrix pm = new GLMatrix();
		// Correct the screen-aspect
		float aspect = width / (float) height;
		if (aspect > 1) {
			pm.createOrtho(-aspect, aspect, -1f, 1f, -2, +2);
		} else {
			pm.createOrtho(-1f, 1f, (-1f / aspect), (1f / aspect), -2, +2);
		}
		
		pm.scale(this.zoom, this.zoom, 1f);
		return pm;
	}
	
	/**
	 * 
	 * @param eye -1: left eye,1 right eye, 0 centered (no shift) 
	 */
	private GLMatrix setupProjectionPerspective(int eye) {
		GLMatrix pm = new GLMatrix();
		
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

        pm.createFrustum(left, right, bottom, top, zNear, zFar);

        //Eye Position
        pm.translate(eyeDist*eye, 0f, -3.2f);
        pm.scale(this.zoom, this.zoom, 1f);
        return pm;
	}

	private GLMatrix setupProjectionFlatMatrix() {
		GLMatrix pm = new GLMatrix();
		pm.createOrtho(0f, width, 0f, height, -5f, +5f);
		return pm;
	}
	
	private GLMatrix setupModelView() {
		GLMatrix mv = new GLMatrix();
		
		mv.translate(-moveX, -moveY, 0);
		mv.mult(rotMatrix);	
		float scale = 1f / globalMaxBounds.maxComponent();
		mv.scale(scale, scale, scale);
		Vec3 bounds = atomData.getBox().getHeight();
		//Shift to the center of the box
		mv.translate(-bounds.x*0.5f, -bounds.y*0.5f, -bounds.z*0.5f);
		//Shift again if the focus is placed on some object
		mv.translate(-coordinateCenterOffset.x, -coordinateCenterOffset.y, -coordinateCenterOffset.z);
	
		return mv;
	}

	private void renderSceneIntoFBOs(GL3 gl, boolean stereo){
		//Render content
		if (stereo) {
			fboLeft.bind(gl, true);
			renderScene(gl, false, -1,fboLeft); //left eye
			fboRight.bind(gl, true);
			renderScene(gl, false, 1, fboRight); //right eye
			fboRight.unbind(gl);
		} else {
			fboLeft.bind(gl, true);
			renderScene(gl, false, 0, fboLeft); 	//central view
			fboLeft.unbind(gl);
		}
	}

	/**
	 * Composes the scene into the given framebuffer or directly to the screen if fbo is null.
	 * The scene must be rendered to textures by calling
	 * "renderSceneToTexture(gl)" before. The textures are then placed on a
	 * large quad into the current framebuffer. 
	 * @param gl
	 * @param fbo
	 */
	private void composeCompleteScene(GL3 gl, FrameBufferObject fbo) {
		if (GLContext.getCurrent() == null) {
			System.out.println("Current context is null");
			System.exit(0);
		}
		
		if (RenderingConfiguration.Options.FXAA.isEnabled()) //Draw into an FBO if anti-aliasing is required
			fboDeferredBuffer.bind(gl, false);
		else if (fbo!=null){
			fbo.bind(gl, false);
			gl.glViewport(0, 0, width, height);
		}
		
		GLMatrix proj = setupProjectionFlatMatrix();
		updateModelViewInShader(gl, BuiltInShader.ANAGLYPH_TEXTURED.getShader(), new GLMatrix(), proj);
		
		gl.glDisable(GL.GL_DEPTH_TEST);
	    BuiltInShader.ANAGLYPH_TEXTURED.getShader().enable(gl);
	    
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboLeft.getColorTextureName());
		
		int uniLocation = gl.glGetUniformLocation(BuiltInShader.ANAGLYPH_TEXTURED.getShader().getProgram(), "stereo");
		gl.glUniform1i(uniLocation, 0);
		if (RenderOption.STEREO.isEnabled()){	//Bind second texture
			gl.glActiveTexture(GL.GL_TEXTURE1);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboRight.getColorTextureName());		    
		    gl.glUniform1i(uniLocation, 1);
		}
		//Bind background
		gl.glActiveTexture(GL.GL_TEXTURE2);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboBackground.getColorTextureName());
	   
		fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		
		if (RenderingConfiguration.Options.FXAA.isEnabled()){
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
		
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		if (atomData == null) return;
		
		
		//setup modelview and projection matrices
		modelViewMatrix = setupModelView();
		
		if (RenderOption.STEREO.isEnabled())
			projectionMatrix = setupProjectionPerspective(eye);
		else if (RenderOption.PERSPECTIVE.isEnabled())
			projectionMatrix = setupProjectionPerspective(0);
		else 
			projectionMatrix = setupProjectionOrthogonal();
		 
		updateModelViewInAllShader(gl);
		
		sphereRenderer.updateSphereRenderData(gl, atomData);
		
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
		
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		//Render solid objects using deferred rendering
		drawSimulationBox(gl, picking);
		for (DataContainer dc : atomData.getAdditionalData())
			dc.drawSolidObjects(this, gl, renderInterval, picking, atomData.getBox());
		
		drawAtoms(gl, picking);
		
		if (!picking) fboDeferredBuffer.unbind(gl);
		
		if (drawIntoFBO != null)
			drawIntoFBO.bind(gl, !picking);
		
		if (!picking) drawFromDeferredBuffer(gl, picking, drawIntoFBO);
		
		//Using forward rendering
		//Renderpass 2 for transparent objects
		if (!picking) gl.glEnable(GL.GL_BLEND);
		if (!picking) gl.glDepthMask(false);	
		
		drawGrain(gl, picking);
		for (DataContainer dc : atomData.getAdditionalData())
			dc.drawTransparentObjects(this, gl, renderInterval, picking, atomData.getBox());
		
		gl.glDepthMask(true);
		drawGrain(gl, picking);
		drawIndent(gl, picking);
		
		//Additional objects
		//Some are placed on top of the scene as 2D objects
		if (!picking){
			drawCoordinateSystem(gl);
			drawThompsonTetraeder(gl);			
			drawLegend(gl);
			drawLengthScale(gl);
		}
		
		this.updateRenderContent = false;
		Shader.disableLastUsedShader(gl);
	}

	private void drawFromDeferredBuffer(GL3 gl, boolean picking, FrameBufferObject targetFbo) {
		GLMatrix pm = setupProjectionFlatMatrix();
		FrameBufferObject ssaoFBO = null;
		
		gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_COLOR);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getColorTextureName());
		gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_NORMAL);
		gl.glBindTexture(GL.GL_TEXTURE_2D,  fboDeferredBuffer.getNormalTextureName());
		gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_POSITION);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getPositionTextureName());

		if (RenderingConfiguration.Options.SSAO.isEnabled() & !picking){
			//Switch to the SSAO shader, render into new FBO
			if (targetFbo != null)
				targetFbo.unbind(gl);
			
			ssaoFBO = new FrameBufferObject(width, height, gl);
			ssaoFBO.bind(gl, !picking);
			gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
			
			Shader ssaoShader = BuiltInShader.SSAO.getShader();
			ssaoShader.enable(gl);
			
			updateModelViewInShader(gl, ssaoShader, new GLMatrix(), setupProjectionFlatMatrix());
			
			gl.glUniform1f(gl.glGetUniformLocation(ssaoShader.getProgram(), "ssaoOffset"), 5.25f*zoom);
			gl.glActiveTexture(GL.GL_TEXTURE0+4);
			gl.glBindTexture(GL.GL_TEXTURE_2D, noiseTexture.getTextureObject());
			
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
			
			ssaoFBO.unbind(gl);
		}
		
		if (targetFbo != null)
			targetFbo.bind(gl, !picking);
		
		Shader s = BuiltInShader.DEFERRED_ADS_RENDERING.getShader();
		int prog = s.getProgram();
		s.enable(gl);
		
		updateModelViewInShader(gl, s, modelViewMatrix, projectionMatrix);
		gl.glUniformMatrix4fv(gl.glGetUniformLocation(s.getProgram(), "mvpm"), 1, false, pm.getMatrix());
		
		if (!picking){
			if (RenderingConfiguration.Options.NO_SHADING.isEnabled())
				gl.glUniform1i(gl.glGetUniformLocation(prog, "picking"), 1);
			else gl.glUniform1i(gl.glGetUniformLocation(prog, "picking"), 0);
			
			if (RenderingConfiguration.Options.SSAO.isEnabled()){
				gl.glActiveTexture(GL.GL_TEXTURE0+4);
				gl.glBindTexture(GL.GL_TEXTURE_2D, ssaoFBO.getColorTextureName());
			}
			gl.glUniform1i(gl.glGetUniformLocation(prog, "ambientOcclusion"), 
					RenderingConfiguration.Options.SSAO.isEnabled()?1:0);
		}
		
		fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		
		if (ssaoFBO != null) ssaoFBO.destroy(gl);
	}

//	private void drawRotationSphere(GL3 gl){
//		float thickness = atomData.getBox().getHeight().maxComponent()/300f;
//		
//		Shader s = BuiltInShader.ADS_UNIFORM_COLOR.getShader();
//		int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
//		s.enable(gl);
//		gl.glUniform4f(colorUniform, 0.0f, 0.0f, 0.0f, 1f);
//		gl.glUniform1i(gl.glGetUniformLocation(s.getProgram(), "ads"), 0);
//		
//		Vec3 bounds = atomData.getBox().getHeight();
//		float maxSize = bounds.maxComponent()*0.866f;
//		Vec3 h = coordinateCenterOffset.multiplyClone(-globalMaxBounds.maxComponent()).add(bounds.multiplyClone(0.5f));
//				
//		//three rings, aligned at the x,y,z axes, centered around the coordinate system
//		ArrayList<Vec3> path = new ArrayList<Vec3>();
//		ArrayList<Vec3> path2 = new ArrayList<Vec3>();
//		ArrayList<Vec3> path3 = new ArrayList<Vec3>();
//		for (int i=0; i<=64; i++){
//			double a = 2*Math.PI*(i/64.);
//			float sin = (float)Math.sin(a)*maxSize;
//			float cos = (float)Math.cos(a)*maxSize;
//			path.add(new Vec3(sin+h.x, cos+h.y, h.z));
//			path2.add(new Vec3(sin+h.x, h.y, cos+h.z));
//			path3.add(new Vec3(h.x, sin+h.y, cos+h.z));
//		}
//		
//		TubeRenderer.drawTube(gl, path, thickness);
//		TubeRenderer.drawTube(gl, path2, thickness);
//		TubeRenderer.drawTube(gl, path3, thickness);
//		
//		//Straight lines along x,y,z direction in the center of the coordinate system
//		path.clear();
//		path.add(new Vec3(maxSize+h.x, h.y, h.z)); path.add(new Vec3(-maxSize+h.x, h.y, h.z));
//		TubeRenderer.drawTube(gl, path, thickness);
//		
//		path.clear();
//		path.add(new Vec3(h.x, maxSize+h.y, h.z)); path.add(new Vec3(h.x, -maxSize+h.y, h.z));
//		TubeRenderer.drawTube(gl, path, thickness);
//		
//		path.clear();
//		path.add(new Vec3(h.x, h.y, maxSize+h.z)); path.add(new Vec3(h.x, h.y, -maxSize+h.z));
//		TubeRenderer.drawTube(gl, path, thickness);
//		
//		//Draw a sphere centered on the coordinate system
//		gl.glUniform4f(colorUniform, 0.5f, 0.5f, 1.0f, 0.8f);
//		GLMatrix mvm = modelViewMatrix.clone();
//		mvm.translate(h.x, h.y, h.z);
//		mvm.scale(maxSize, maxSize, maxSize);
//		updateModelViewInShader(gl, s, mvm, projectionMatrix);
//		SimpleGeometriesRenderer.drawSphere(gl);
//		updateModelViewInShader(gl, s, modelViewMatrix, projectionMatrix);
//		
//		gl.glUniform1i(gl.glGetUniformLocation(s.getProgram(), "ads"), 
//				RenderingConfiguration.Options.ADS_SHADING.isEnabled() ? 1 : 0);
//	}
	
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
		
		BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader().enable(gl);
		int colorUniform = gl.glGetUniformLocation(BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader().getProgram(), "Color");
		gl.glUniform4f(colorUniform, 0.7f, 0.7f, 0.7f, 1f);
		for (int i=0; i<12; i++){
			ArrayList<Vec3> points = new ArrayList<Vec3>();
			points.add(new Vec3(boxVertices[i*6], boxVertices[i*6+1], boxVertices[i*6+2]));
			points.add(new Vec3(boxVertices[i*6+3], boxVertices[i*6+4], boxVertices[i*6+5]));
			TubeRenderer.drawTube(gl, points, atomData.getBox().getHeight().maxComponent()/200f);
		}	
	}
	
	private void drawIndent(GL3 gl, boolean picking){
		if (!RenderOption.INDENTER.isEnabled()) return;
		
		BuiltInShader.ADS_UNIFORM_COLOR.getShader().enable(gl);
		GLMatrix mvm = modelViewMatrix.clone();
		SimplePickable indenter = new SimplePickable();
		
		float[] color = new float[]{0f, 1f, 0.5f, 0.4f};;
		if(picking)
			color = getNextPickingColor(indenter);
		
		gl.glUniform4f(gl.glGetUniformLocation(
				BuiltInShader.ADS_UNIFORM_COLOR.getShader().getProgram(),"Color"), color[0], color[1], color[2], color[3]);
		
		//Test indenter geometry, sphere or cylinder
		Object o = atomData.getFileMetaData("extpot_cylinder");
		if (o != null) {
			float[] indent = null;
			if (o instanceof float[]) indent = (float[])o;
			else return;
			if (picking){
				Vec3 p;
				indenter.setCenter(p=new Vec3(indent[1], indent[2], indent[3]));
				indenter.setText(String.format("Cylindrical indenter (r=%f) at %s", indent[4], p.toString()));
			} 
			
			mvm.translate(indent[1], indent[2], indent[3]);
			
			Vec3 bounds = atomData.getBox().getHeight();
			float length = 0f;
			if (indent[1] == 0f){
				length = bounds.x * 2f;
				mvm.rotate(90f, 0f, -1f, 0f);
			} else if (indent[2] == 0f){
				length = bounds.y * 2f;
				mvm.rotate(90f, -1f, 0f, 0f);
			} else if (indent[3] == 0f){
				length = bounds.z * 2f;
			}
			mvm.scale(indent[4], indent[4], length);
			
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawCylinder(gl);
		}
		o = atomData.getFileMetaData("extpot");
		if (o != null) {
			float[] indent = null;
			if (o instanceof float[]) indent = (float[])o;
			else return;
			if (picking){
				Vec3 p;
				indenter.setCenter(p=new Vec3(indent[1], indent[2], indent[3]));
				indenter.setText(String.format("Spherical indenter (r=%f) at %s", indent[4], p.toString()));
			} 
			
			mvm.translate(indent[1], indent[2], indent[3]);
			mvm.scale(indent[4], indent[4], indent[4]);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawSphere(gl);
		}
		o = atomData.getFileMetaData("wall");
		if (o != null) {
			float[] indent = null;
			if (o instanceof float[]) indent = (float[]) o;
			else return;
			
			Vec3[] box = atomData.getBox().getBoxSize();
			float hx = box[0].x + box[1].x + box[2].x;
			float hy = box[0].y + box[1].y + box[2].y;
			
			if (picking){
				indenter.setCenter(new Vec3(hx/2f, hx/2f, indent[1]));
				indenter.setText(String.format("Flat punch indenter at z=%f", indent[1]));
			} 

			mvm.translate(0, 0, indent[1] - indent[2]);
			mvm.scale(hx, hy, 3*indent[2]);
			updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawCube(gl);
		}
		updateModelViewInShader(gl, BuiltInShader.ADS_UNIFORM_COLOR.getShader(), modelViewMatrix, projectionMatrix);
	}
	

	public void drawSpheres(GL3 gl, ObjectRenderData<?> ard, boolean picking){
		//Forward the call to a highly specialized routine
		sphereRenderer.drawSpheres(gl, ard, picking);
	}
	
	private void drawAtoms(GL3 gl, boolean picking){
		final CrystalStructure cs = atomData.getCrystalStructure();
		final int numEle = cs.getNumberOfElements();

		final AtomFilterSet atomFilterSet = RenderingConfiguration.getAtomFilterset();
		
		final float[] sphereSize = cs.getSphereSizeScalings();
		float maxSphereSize = 0f;
		for (int i=0; i<sphereSize.length; i++){
			sphereSize[i] *= defaultSphereSize;
			if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
		}
			
		if (!renderingAtomsAsRBV || !atomData.isRbvAvailable()){
			DataColumnInfo dataInfo = null;
			
			if (atomRenderType == AtomRenderType.DATA || atomRenderType == AtomRenderType.VECTOR_DATA){
				DataColumnInfo dataInfoColoring = null;
				
				if (atomRenderType == AtomRenderType.DATA){
					dataInfo = RenderingConfiguration.getSelectedColumn();
					if (dataInfo == null) return;
					dataInfoColoring = dataInfo;
				} else if (atomRenderType == AtomRenderType.VECTOR_DATA){
					dataInfo = RenderingConfiguration.getSelectedVectorColumn();
					if (dataInfo == null) return;
					dataInfoColoring = dataInfo.getVectorComponents()[3];
				}
				
				final float min = dataInfoColoring.getLowerLimit();
				final float max = dataInfoColoring.getUpperLimit();
				
				//Set custom color scheme of the data value if present
				ColorBarScheme scheme = ColorTable.getColorBarScheme();
				boolean swapped = ColorTable.isColorBarSwapped();
				if (dataInfoColoring.getScheme()!=null){
					ColorTable.setColorBarScheme(dataInfoColoring.getScheme());
					ColorTable.setColorBarSwapped(false);
				}
				
				if (dataInfoColoring.getScheme()!=null){
					ColorTable.setColorBarScheme(scheme);
					ColorTable.setColorBarSwapped(swapped);
				} else if (RenderOption.LEGEND.isEnabled()){
					drawLegendThisFrame(
							Float.toString(min)+" "+dataInfoColoring.getUnit(), 
							Float.toString((max+min)*0.5f)+" "+dataInfoColoring.getUnit(),
							Float.toString(max)+" "+dataInfoColoring.getUnit()
							);
				}
			}
			
			if (updateRenderContent){
				DataColoringAndFilter dataAtomFilter = new DataColoringAndFilter(atomRenderType == AtomRenderType.VECTOR_DATA);
				
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
				if ((RenderingConfiguration.isFilterMin() || RenderingConfiguration.isFilterMax()) 
						&& !atomData.getDataColumnInfos().isEmpty())
					atomFilterSet.addFilter(dataAtomFilter);
				
				final ColoringFunction colFunc;
				switch (atomRenderType){
					case TYPE: colFunc = new TypeColoringAndFilter(); break;
					case ELEMENTS: colFunc = new ElementColoringAndFilter(); break;
					case GRAINS: colFunc = new GrainColoringAndFilter(); break;
					case DATA:
					case VECTOR_DATA: colFunc = dataAtomFilter; break;
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
								ObjectRenderData<Atom>.Cell cell = renderData.getRenderableCells().get(i);
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
			if (atomRenderType == AtomRenderType.VECTOR_DATA){
				int v1 = atomData.getIndexForCustomColumn(dataInfo.getVectorComponents()[0]);
				int v2 = atomData.getIndexForCustomColumn(dataInfo.getVectorComponents()[1]);
				int v3 = atomData.getIndexForCustomColumn(dataInfo.getVectorComponents()[2]);
				arrowRenderer.drawVectors(gl, renderData, picking, v1, v2, v3, 
						RenderingConfiguration.getVectorDataScaling(),
						RenderingConfiguration.getVectorDataThickness(), 
						RenderingConfiguration.isNormalizedVectorData());
			} else sphereRenderer.drawSpheres(gl, renderData, picking);
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
		if (!RenderOption.GRAINS.isEnabled() || !atomData.isPolyCrystalline()) return;
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
		
		Vec3[] a = atomData.getCrystalRotation().getThompsonTetraeder();
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
		
		boolean evenNumbers = true;
		for (int i=0; i<3; i++){
			float x = (int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].x);
			if (Math.abs(x-atomData.getCrystalRotation().getCrystalOrientation()[i].x) > 1e-5f) evenNumbers = false;
			float y = (int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].y);
			if (Math.abs(y-atomData.getCrystalRotation().getCrystalOrientation()[i].y) > 1e-5f) evenNumbers = false;
			float z = (int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].z);
			if (Math.abs(z-atomData.getCrystalRotation().getCrystalOrientation()[i].z) > 1e-5f) evenNumbers = false;
		}
		
		textRenderer.setColor(gl, 0f, 0f, 0f, 1f);
		
		//Labels
		for (int i=0; i<3; i++){
			String s = "";
			if (evenNumbers)
				s = "=[" +(int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].x)+" "
						+(int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].y)+" "
						+(int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].z) +"]";
			if (i==0) s = "x"+s;
			if (i==1) s = "y"+s;
			if (i==2) s = "z"+s;
			
			GLMatrix mvm_new = mvm.clone();
			Vec3 v = atomData.getBox().getBoxSize()[i].normalizeClone();
			mvm_new.translate(v.x, v.y, v.z);
			GLMatrix rotInverse = new GLMatrix(rotMatrix.getMatrix());
			rotInverse.inverse();
			mvm_new.mult(rotInverse);
			if (evenNumbers) mvm_new.translate(-0.5f, 0.1f, 0f);
			else mvm_new.translate(-0.05f, 0.1f, 0f);
			
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
		GLMatrix pm = setupProjectionFlatMatrix();
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
		if (atomData == null) return;
		
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
		
		String sizeString = String.format("%.1f",size);
		int blocks = 4;
		
		int xshift = width/10;
		int yshift = height-2*(height/20);
		
		gl.glDisable(GL.GL_DEPTH_TEST);
		
		GLMatrix mvm = new GLMatrix();
		GLMatrix pm = setupProjectionFlatMatrix();
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), mvm, pm);
		
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		
		float w = (size*unitLengthInPixel)/blocks;
		float h_scale = (height/20f)/10f;
		
		float textscale = 0.1f*h_scale;
		float border = 0.5f*h_scale;
		float textHeigh = textscale*textRenderer.getStringHeigh();
		float textWidth = textRenderer.getStringWidth(sizeString)*textscale;
		float minSize = Math.max(textWidth+5, w*blocks);
		
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, 4, 2, 0, 0, 4, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		
		vds.setColor(0f, 0f, 0f, 1f); vds.setVertex(xshift-10-border, yshift-textHeigh-border);
		vds.setColor(0f, 0f, 0f, 1f); vds.setVertex(xshift+minSize+10+border, yshift-textHeigh-border);
		vds.setColor(0f, 0f, 0f, 1f); vds.setVertex(xshift-10-border, yshift+10*h_scale+border);
		vds.setColor(0f, 0f, 0f, 1f); vds.setVertex(xshift+minSize+10+border, yshift+10*h_scale+border);
			
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
				if (zoom>400f) zoom = 400f;
				if (zoom<0.05f) zoom = 0.05f;
			} else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK){
				moveX -= (newDragPosition.x - startDragPosition.x) / (globalMaxBounds.minComponent()*zoom);
				moveY += (newDragPosition.y - startDragPosition.y) / (globalMaxBounds.minComponent()*zoom);
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
		if (zoom>400f) zoom = 400f;
		if (zoom<0.05f) zoom = 0.05f;
		this.reDraw();
	}

	// endregion MouseWheelListener
	
	@Override
	public void atomDataChanged(AtomDataChangedEvent e) {
		setAtomData(e.getNewAtomData(), e.isResetGUI());
	}
	
	private void setAtomData(AtomData atomData, boolean reinit){
		this.atomData = atomData;
		if (atomData == null) {
			renderData = null;
			makeBackground();
			this.reDraw();
			return;
		}
		
		//TODO switching between ortho & non-ortho
		if (this.renderInterval==null || reinit)
			this.renderInterval = new RenderRange(atomData.getBox().getHeight());
		else {
			renderInterval.setGlobalLimit(3, this.atomData.getBox().getHeight().x);
			renderInterval.setGlobalLimit(4, this.atomData.getBox().getHeight().y);
			renderInterval.setGlobalLimit(5, this.atomData.getBox().getHeight().z);
			if (reinit || renderInterval.isNoLimiting()) renderInterval.reset();		
		}
		renderData = new ObjectRenderData<Atom>(atomData.getAtoms(), true, atomData.getBox());
		this.updateAtoms();
		
		if (atomData.getNumberOfElements() > ignoreElement.length){
			boolean[] newIgnore = new boolean[atomData.getNumberOfElements()];
			for (int i=0; i<ignoreElement.length;i++) newIgnore[i] = ignoreElement[i];
			ignoreElement = newIgnore;
		}
		
		if (reinit){
			ignoreTypes = new boolean[atomData.getCrystalStructure().getNumberOfTypes()];
			ignoreElement = new boolean[atomData.getNumberOfElements()];
			setSphereSize(atomData.getCrystalStructure().getDistanceToNearestNeighbor()*0.55f);
			if (!atomData.isPolyCrystalline()) RenderOption.GRAINS.enabled = false;
			
			//Find global maximum boundaries
			globalMaxBounds = new Vec3();
			coordinateCenterOffset.setTo(0f, 0f, 0f);
			AtomData tmp = atomData;
			//Iterate over the whole set
			while (tmp.getPrevious()!=null) tmp = tmp.getPrevious();
			do {
				Vec3 bounds = tmp.getBox().getHeight();
				if (bounds.x>globalMaxBounds.x) globalMaxBounds.x = bounds.x;
				if (bounds.y>globalMaxBounds.y) globalMaxBounds.y = bounds.y;
				if (bounds.z>globalMaxBounds.z) globalMaxBounds.z = bounds.z;
				tmp = tmp.getNext();
			} while (tmp != null);
		}
		
		if (atomData.isPolyCrystalline()){
			ignoreGrain.clear();
			if (atomData.getGrains().size()!=0){
				for (Grain g : atomData.getGrains()){
					ignoreGrain.put(g.getGrainNumber(), false);
				}
				ignoreGrain.put(Atom.IGNORED_GRAIN, false);
				ignoreGrain.put(Atom.DEFAULT_GRAIN, false);
			}
			createGrainColorTable();
		}
	}

	private void createGrainColorTable() {
		grainColorTable.clear();
		if (atomData == null) return;
		
		ArrayList<Integer> sortGrainIndices = new ArrayList<Integer>(atomData.getGrains().size());
		for (Grain g : atomData.getGrains())
			sortGrainIndices.add(g.getGrainNumber());
		Collections.sort(sortGrainIndices);
		float[][] colors = ColorTable.createColorTable(sortGrainIndices.size()+2, 0.5f);
		int j=0;
		for (int i: sortGrainIndices){
			grainColorTable.put(i, colors[j]);
			j++;
		}
		if (atomData.getGrains(Atom.DEFAULT_GRAIN) == null)
			grainColorTable.put(Atom.DEFAULT_GRAIN, colors[sortGrainIndices.size()-2]);
		if (atomData.getGrains(Atom.IGNORED_GRAIN) == null)
			grainColorTable.put(Atom.IGNORED_GRAIN, colors[sortGrainIndices.size()-1]);
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
		if (i>atomData.getCrystalStructure().getNumberOfTypes()) return;
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
	
	public GLMatrix getModelViewMatrix() {
		return modelViewMatrix;
	}
	
	public GLMatrix getProjectionMatrix() {
		return projectionMatrix;
	}
	
	public GLMatrix getRotationMatrix() {
		return rotMatrix;
	}
	
	public int getDefaultVAO(){
		return defaultVAO;
	}
	
	public ArrayList<Object> getHighLightObjects() {
		return highLightObjects;
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
		pickList.clear();
		
		boolean adjustPOVOnObject = false;
		if ((e.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK))
				== (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)){
			adjustPOVOnObject = true;
		}

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
			
			//Modify the parameters for the modelview matrix to focus on
			//the picked object
			if (adjustPOVOnObject){
				Vec3 pov = picked.getCenterOfObject();
				if (pov != null){
					Vec3 bounds = atomData.getBox().getHeight();
					pov.add(bounds.multiplyClone(-0.5f));
					coordinateCenterOffset.setTo(pov);
					moveX = 0f; moveY = 0f;
					repaintRequired = true;
					break;
				}
			}
			
			JLogPanel.getJLogPanel().addLog(picked.printMessage(e, atomData));
			
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
    
    //region export methods
	public void makeScreenshot(String filename, String type, boolean sequence, int w, int h) throws Exception{
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
			
			gl.glViewport(0, 0, w, h);
			fboDeferredBuffer= new FrameBufferObject(w, h, gl, true);
			fboLeft = new FrameBufferObject(w, h, gl);
			fboRight = new FrameBufferObject(w, h, gl);
			fboBackground = new FrameBufferObject(w, h, gl);
			this.makeBackground();
			this.makeFullScreenQuad(gl);
			
			if (!sequence) {
				renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
				composeCompleteScene(gl, screenshotFBO);
				
				BufferedImage bi = screenshotFBO.textureToBufferedImage(w, h, gl);
				ImageOutput.writeScreenshotFile(bi, type, new File(filename), this.atomData, this);
			} else {
				AtomData currentAtomData = this.atomData;
				for (AtomData c : Configuration.getAtomDataIterable(currentAtomData)){
					this.setAtomData(c, false);
					renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
					composeCompleteScene(gl, screenshotFBO);

					BufferedImage bi = screenshotFBO.textureToBufferedImage(w, h, gl);
					ImageOutput.writeScreenshotFile(bi, type, new File(filename+atomData.getName()+"."+type),
							c, this);
				}	
				this.setAtomData(currentAtomData, false);
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
			gl.glViewport(0, 0, this.width, this.height);
			this.makeFullScreenQuad(gl);
		}
		this.reDraw();
	}
	
	//endregion export methods
	
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
		GLMatrix pm = setupProjectionFlatMatrix();
		
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
	
	public float estimateUnitLengthInPixels(){
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
			//Perspective projection can only be an approximation
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
	
	public void updateModelViewInShader(GL3 gl, Shader shader, GLMatrix modelView, GLMatrix projection){
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
	
	void updateModelViewInAllShader(GL3 gl){
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
		gl.glUniform1i(gl.glGetUniformLocation(prog, "occlusionTexture"), 4);
		
		BuiltInShader.SSAO.getShader().enable(gl);
		prog = BuiltInShader.SSAO.getShader().getProgram();
		gl.glUniform1i(gl.glGetUniformLocation(prog, "colorTexture"), Shader.FRAG_COLOR);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "normalTexture"), Shader.FRAG_NORMAL);
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
		
		sphereRenderer.dispose(gl);
		sphereRenderer = null;
		arrowRenderer.dispose(gl);
		arrowRenderer = null;

		SimpleGeometriesRenderer.dispose(gl);
		TubeRenderer.dispose(gl);
		VertexDataStorage.unbindAll(gl);
		Shader.dispose(gl);
		textRenderer.dispose(gl);
		
        gl.glGenVertexArrays(1, new int[]{defaultVAO}, 0);
        gl.glBindVertexArray(0);
		
		if (fboDeferredBuffer != null)   fboDeferredBuffer.destroy(gl);
		if (fboLeft != null) 	   fboLeft.destroy(gl);
		if (fboRight != null) 	   fboRight.destroy(gl);
		if (fboBackground != null) fboBackground.destroy(gl);
	}
	
	private interface ColoringFunction{
		float[] getColor(Atom c);
		void update();
	}
	
	private class TypeColoringAndFilter implements Filter<Atom>, ColoringFunction{
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
					ColorUtils.getColorShift(atomData.getNumberOfElements(), colorShiftForVElements, 
							atomData.getCrystalStructure(), colorShiftForElements);
			colors = colorsAndNumElements.o1;
			numEleColors = colorsAndNumElements.o2;
		}
	}
	
	private class GrainColoringAndFilter implements Filter<Atom>, ColoringFunction{
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
	
	private class ElementColoringAndFilter implements Filter<Atom>, ColoringFunction{
		float[][] colorTable = null;
		
		boolean isNeeded(){
			if (atomData.getNumberOfElements()==1) return false;
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
			colorTable = ColorTable.getColorTableForElements(atomData.getNumberOfElements());
		}
	}
	
	private class DataColoringAndFilter implements Filter<Atom>, ColoringFunction{
		boolean filterMin = false;
		boolean filterMax = false;
		boolean inversed = false;
		float min = 0f, max = 0f;
		int selected = 0;
		boolean isVector;
		
		public DataColoringAndFilter(boolean isVector) {
			this.isVector = isVector;
		}
		
		@Override
		public boolean accept(Atom a) {
			if ((filterMin && a.getData(selected)<min) || (filterMax && a.getData(selected)>max))
					return inversed;
				return !inversed;
		}
		
		@Override
		public float[] getColor(Atom c) {
			return ColorTable.getIntensityGLColor(min, max, c.getData(selected));
		}
		
		@Override
		public void update() {
			DataColumnInfo dataInfo = isVector?
					RenderingConfiguration.getSelectedVectorColumn():
					RenderingConfiguration.getSelectedColumn();
			if (dataInfo == null) return;
			
			if (isVector){
				selected = atomData.getIndexForCustomColumn(dataInfo.getVectorComponents()[3]);
				min = dataInfo.getVectorComponents()[3].getLowerLimit();
				max = dataInfo.getVectorComponents()[3].getUpperLimit();
			} else { 
				selected = atomData.getIndexForCustomColumn(dataInfo);
				min = dataInfo.getLowerLimit();
				max = dataInfo.getUpperLimit();
			}
			if (selected == -1) return;
			
			
			filterMin = RenderingConfiguration.isFilterMin();
			filterMax = RenderingConfiguration.isFilterMax();
			inversed = RenderingConfiguration.isFilterInversed();
		}
	}
}