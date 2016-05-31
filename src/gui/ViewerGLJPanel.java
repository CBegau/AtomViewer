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
import model.DataColumnInfo.Component;
import model.mesh.Mesh;
import model.polygrain.Grain;
import processingModules.DataContainer;
import processingModules.otherModules.BinningDataContainer;
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
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;

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
		}
		
		public boolean isEnabled(){
			return enabled;
		}
	}
	
	public enum AtomRenderType {
		TYPE, ELEMENTS, GRAINS, DATA, VECTOR_DATA, BINS
	};
	
	private static final long serialVersionUID = 1L;
	
	private AtomData atomData;
	private RenderRange renderInterval;
	
	private FrameBufferObject fboLeft;
	private FrameBufferObject fboRight;
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
	private Point startDragPosition;

	private Vec3 globalMaxBounds = new Vec3();
	
	private String[] legendLabels = new String[3];
	private boolean drawLegendThisFrame = false;
	
	private boolean reRenderTexture = true;
	//The index is the position in the list, therefore it is some kind of map
	private ArrayList<Pickable> pickList = new ArrayList<Pickable>();

	private boolean[] ignoreTypes = new boolean[0];
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
	
	private BinningDataContainer binnedData = new BinningDataContainer();
	
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
        gl.glClearColor(0f, 0f, 0f, 1f); //Must be this value for order independent rendering
        
        Shader.init(gl);
        this.initShaderUniforms(gl);

        
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 72);
        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (Font f : fonts){
        	if (f.getFontName().equals(RenderingConfiguration.defaultFont)){
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
		this.changeResolution(gl, arg3, arg4);
		reRenderTexture = true;
		this.arcBall.setSize(this.width, this.height);
	}
	
	private GLMatrix createOrthogonalProjectionMatrix() {
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
	private GLMatrix createPerspectiveProjectionMatrix(int eye) {
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

	private GLMatrix createFlatProjectionMatrix() {
		GLMatrix pm = new GLMatrix();
		pm.createOrtho(0f, width, 0f, height, -5f, +5f);
		return pm;
	}
	
	private GLMatrix createModelViewMatrix() {
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
		gl.glDisable(GL.GL_DEPTH_TEST);
		Shader s = (RenderOption.STEREO.isEnabled()?BuiltInShader.ANAGLYPH_TEXTURED:BuiltInShader.PLAIN_TEXTURED).getShader();
		GLMatrix proj = createFlatProjectionMatrix();
		s.enable(gl);
		updateModelViewInShader(gl, s, new GLMatrix(), proj);
		
		int c = gl.glGetUniformLocation(s.getProgram(), "Color");
		gl.glUniform4f(c, 1f, 1f, 1f, 1f);
	    
		gl.glActiveTexture(GL.GL_TEXTURE0);
		gl.glBindTexture(GL.GL_TEXTURE_2D, fboLeft.getColorTextureName());
		
		if (RenderOption.STEREO.isEnabled()){	//Bind second texture for stereo
			gl.glActiveTexture(GL.GL_TEXTURE1);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboRight.getColorTextureName());		    
			gl.glActiveTexture(GL.GL_TEXTURE0);
		}
	   
		fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		
		if (RenderingConfiguration.Options.FXAA.isEnabled()){
			//Draw the image in the FBO to the target using FXAA
			fboDeferredBuffer.unbind(gl);
			
			if (fbo!=null){
				fbo.bind(gl, false);
				gl.glViewport(0, 0, width, height);
			}
			
			updateModelViewInShader(gl, BuiltInShader.FXAA.getShader(), new GLMatrix(), proj);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getColorTextureName());
			BuiltInShader.FXAA.getShader().enable(gl);
			
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		}
		
		if (fbo!=null)
			fbo.unbind(gl);

		gl.glEnable(GL.GL_DEPTH_TEST);
	    Shader.disableLastUsedShader(gl);
	}
	
	private void renderScene(GL3 gl, boolean picking, int eye,  FrameBufferObject drawIntoFBO) {
		if (GLContext.getCurrent() == null) {
			System.out.println("Current context is null");
			System.exit(0);
		}
		
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		if (atomData == null) {
			drawBackground(gl);
			return;
		}
		
		//setup modelview and projection matrices
		modelViewMatrix = createModelViewMatrix();
		
		if (RenderOption.STEREO.isEnabled())
			projectionMatrix = createPerspectiveProjectionMatrix(eye);
		else if (RenderOption.PERSPECTIVE.isEnabled())
			projectionMatrix = createPerspectiveProjectionMatrix(0);
		else 
			projectionMatrix = createOrthogonalProjectionMatrix();
		 
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
		if (!picking) drawBackground(gl);
		
		if (drawIntoFBO != null)
			drawIntoFBO.unbind(gl);
		
		if (!picking)
			fboDeferredBuffer.bind(gl, false);
		
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
		//Render solid objects using deferred rendering
		drawSimulationBox(gl, picking);
		for (DataContainer dc : atomData.getAdditionalData())
			dc.drawSolidObjects(this, gl, renderInterval, picking, atomData);
		
		if (atomRenderType == AtomRenderType.BINS){
		    if (!binnedData.isTransparenceRenderingRequired()){
		        binnedData.drawSolidObjects(this, gl, renderInterval, picking, atomData);
		    }
		} else drawAtoms(gl, picking);
		
		
		if (!picking) fboDeferredBuffer.unbind(gl);
		
		if (drawIntoFBO != null)
			drawIntoFBO.bind(gl, !picking);
		
		if (!picking) drawFromDeferredBuffer(gl, picking, drawIntoFBO);
		
		drawTransparentObjects(gl, picking, drawIntoFBO);
		
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

	/**
	 * Draw all transparent object. This uses the weigthened order independent rendering method
	 * described in 
	 * McGuire & Bavoil: Weighted Blended Order-Independent Transparency, 
	 * Journal of Computer Graphics Techniques Vol. 2, No. 2, 2013
	 * @param gl
	 * @param picking
	 * @param drawIntoFBO
	 */
	public void drawTransparentObjects(GL3 gl, boolean picking, FrameBufferObject drawIntoFBO) {
		if (!picking) {
			//Prepare order independent rendering using the deferred rendering FBO that has all
			//solid objects already rendered into and thus holds the correct depth mask
			fboDeferredBuffer.bind(gl, false);
			gl.glEnable(GL.GL_BLEND);
			
			gl.glBlendFuncSeparate(GL.GL_ONE, GL.GL_ONE, GL.GL_ZERO, GL.GL_ONE_MINUS_SRC_ALPHA);
			
			gl.glDrawBuffers(3, new int[]{GL3.GL_NONE, GL3.GL_COLOR_ATTACHMENT1, GL3.GL_COLOR_ATTACHMENT2}, 0);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT);
			
			gl.glEnablei(GL.GL_BLEND, 1);
			gl.glEnablei(GL.GL_BLEND, 2);
			gl.glDepthMask(false);
//			gl.glDisable(GL.GL_CULL_FACE);
		}
		
		drawGrain(gl, picking);
		for (DataContainer dc : atomData.getAdditionalData())
			dc.drawTransparentObjects(this, gl, renderInterval, picking, atomData);

		if (atomRenderType == AtomRenderType.BINS && binnedData.isTransparenceRenderingRequired()){
            binnedData.drawTransparentObjects(this, gl, renderInterval, picking, atomData);
        }
		
		drawIndent(gl, picking);
		
		if (!picking) {
			//Blend accumulated data into the framebuffer
			fboDeferredBuffer.unbind(gl);
			
			if (drawIntoFBO != null)
				drawIntoFBO.bind(gl, !picking);
			
			gl.glDisablei(GL.GL_BLEND, 1);
			gl.glDisablei(GL.GL_BLEND, 2);
			gl.glBlendFunc(GL.GL_ONE_MINUS_SRC_ALPHA, GL.GL_SRC_ALPHA);
			
			Shader oidComposer = BuiltInShader.OID_COMPOSER.getShader();
			
			GLMatrix mvm = new GLMatrix();
			GLMatrix pm = createFlatProjectionMatrix();
			oidComposer.enable(gl);
			updateModelViewInShader(gl, oidComposer, mvm, pm);
			
			gl.glUniform1i(gl.glGetUniformLocation(oidComposer.getProgram(), "RevealageTexture"), Shader.FRAG_ALPHA_ACCU);
			gl.glUniform1i(gl.glGetUniformLocation(oidComposer.getProgram(), "AccuTexture"), Shader.FRAG_COLOR_ACCU);
			
			gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_ALPHA_ACCU);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getPositionTextureName());
			gl.glActiveTexture(GL.GL_TEXTURE0+Shader.FRAG_COLOR_ACCU);
			gl.glBindTexture(GL.GL_TEXTURE_2D, fboDeferredBuffer.getNormalTextureName());
			gl.glDepthFunc(GL.GL_ALWAYS);
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
			gl.glDepthFunc(GL.GL_LESS);
			
			gl.glDepthMask(true);
			gl.glEnable(GL.GL_CULL_FACE);
			gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
			gl.glDisable(GL.GL_BLEND);
		}
	}

	private void drawFromDeferredBuffer(GL3 gl, boolean picking, FrameBufferObject targetFbo) {
		GLMatrix pm = createFlatProjectionMatrix();
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
			
			ssaoFBO = new FrameBufferObject(width, height, gl, false, false);
			ssaoFBO.bind(gl, false);
			
			Shader ssaoShader = BuiltInShader.SSAO.getShader();
			ssaoShader.enable(gl);
			
			gl.glUniformMatrix4fv(gl.glGetUniformLocation(ssaoShader.getProgram(), "mvpm"), 1, false, pm.getMatrix());
			
			gl.glUniform1f(gl.glGetUniformLocation(ssaoShader.getProgram(), "ssaoOffset"), 5.25f*zoom);
			gl.glActiveTexture(GL.GL_TEXTURE0+4);
			gl.glBindTexture(GL.GL_TEXTURE_2D, noiseTexture.getTextureObject());
			
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
			
			//Blur the results, first horizontal, write results in temporary fbo
			FrameBufferObject blurFBO = new FrameBufferObject(width, height, gl, false, false);
			blurFBO.bind(gl, false);
			
			Shader blurShader = BuiltInShader.BLUR.getShader();
			blurShader.enable(gl);
			
			gl.glUniformMatrix4fv(gl.glGetUniformLocation(blurShader.getProgram(), "mvpm"), 1, false, pm.getMatrix());
			gl.glUniform2f(gl.glGetUniformLocation(blurShader.getProgram(), "dir"), 1f, 0f);
			gl.glBindTexture(GL.GL_TEXTURE_2D, ssaoFBO.getColorTextureName());
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
			
			//then once more in vertical direction, write final results into the ssao fbo
			ssaoFBO.bind(gl, false);
			gl.glUniform2f(gl.glGetUniformLocation(blurShader.getProgram(), "dir"), 0f, 1f);
			gl.glBindTexture(GL.GL_TEXTURE_2D, blurFBO.getColorTextureName());
			fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
			
			ssaoFBO.unbind(gl);
			blurFBO.destroy(gl);
		}
		
		if (targetFbo != null)
			targetFbo.bind(gl, !picking);
		
		Shader s = BuiltInShader.DEFERRED_ADS_RENDERING.getShader();
		int prog = s.getProgram();
		s.enable(gl);
		
		gl.glUniformMatrix4fv(gl.glGetUniformLocation(s.getProgram(), "mvpm"), 1, false, pm.getMatrix());
		
		if (!picking){
			gl.glUniform1i(gl.glGetUniformLocation(prog, "noShading"),
					RenderingConfiguration.Options.NO_SHADING.isEnabled()?1:0);
			gl.glUniform1i(gl.glGetUniformLocation(prog, "ambientOcclusion"), 
					RenderingConfiguration.Options.SSAO.isEnabled()?1:0);
			
			if (RenderingConfiguration.Options.SSAO.isEnabled()){
				gl.glActiveTexture(GL.GL_TEXTURE0+4);
				gl.glBindTexture(GL.GL_TEXTURE_2D, ssaoFBO.getColorTextureName());
			}
		}
		
		fullScreenQuad.draw(gl, GL.GL_TRIANGLE_STRIP);
		
		if (ssaoFBO != null) ssaoFBO.destroy(gl);
		if (targetFbo != null)	//rebind if necessary after destruction of ssaoFBO
			targetFbo.bind(gl, !picking);
	}
	
	private void drawSimulationBox(GL3 gl, boolean picking) {
		if (!RenderOption.BOUNDING_BOX.isEnabled() || picking) return;
		
		Vec3[] box = atomData.getBox().getBoxSize();
		Vec3[] v = new Vec3[]{	//Corners of the box to draw
			new Vec3(0f, 0f, 0f),	new Vec3(0f, 1f, 0f),
			new Vec3(0f, 1f, 0f),	new Vec3(1f, 1f, 0f),
			new Vec3(1f, 1f, 0f),	new Vec3(1f, 0f, 0f),
			new Vec3(1f, 0f, 0f),	new Vec3(0f, 0f, 0f),
			new Vec3(0f, 0f, 1f),	new Vec3(1f, 0f, 1f),
			new Vec3(1f, 0f, 1f),	new Vec3(1f, 1f, 1f),
			new Vec3(1f, 1f, 1f),	new Vec3(0f, 1f, 1f),
			new Vec3(0f, 1f, 1f),	new Vec3(0f, 0f, 1f),
			new Vec3(0f, 0f, 0f),	new Vec3(0f, 0f, 1f),
			new Vec3(0f, 1f, 0f),	new Vec3(0f, 1f, 1f),
			new Vec3(1f, 0f, 0f),	new Vec3(1f, 0f, 1f),
			new Vec3(1f, 1f, 0f),	new Vec3(1f, 1f, 1f),
		};
		//Scale to actual coordiate system
		for (int i=0; i<v.length; i++){
			float x = box[0].x * v[i].x + box[1].x * v[i].y + box[2].x * v[i].z;
			float y = box[0].y * v[i].x + box[1].y * v[i].y + box[2].y * v[i].z;
			float z = box[0].z * v[i].x + box[1].z * v[i].y + box[2].z * v[i].z;
			v[i].setTo(x,y,z);
		}
		//Draw the 12 segments each as a cylinder using the tube renderer 
		BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader().enable(gl);
		int colorUniform = gl.glGetUniformLocation(BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader().getProgram(), "Color");
		gl.glUniform4f(colorUniform, 0.7f, 0.7f, 0.7f, 1f);
		for (int i=0; i<v.length; i+=2){
			ArrayList<Vec3> points = new ArrayList<Vec3>();
			points.add(v[i]); points.add(v[i+1]);
			TubeRenderer.drawTube(gl, points, atomData.getBox().getHeight().maxComponent()/200f);
		}	
	}
	
	private void drawIndent(GL3 gl, boolean picking){
		if (!RenderOption.INDENTER.isEnabled()) return;
		
		Shader s = picking?BuiltInShader.VERTEX_ARRAY_COLOR_UNIFORM.getShader()
		        :BuiltInShader.OID_ADS_UNIFORM_COLOR.getShader();
		s.enable(gl);
			
		GLMatrix mvm = modelViewMatrix.clone();
		SimplePickable indenter = new SimplePickable();
		
		float[] color = new float[]{0f, 1f, 0.5f, 0.4f};;
		if(picking)
			color = getNextPickingColor(indenter);
		
		gl.glUniform4f(gl.glGetUniformLocation(s.getProgram(),"Color"), color[0], color[1], color[2], color[3]);
		
		//Test indenter geometry, sphere or cylinder
		Object o = atomData.getFileMetaData("extpot_cylinder");
		if (o != null) {
			double[] indent = null;
			if (o instanceof double[]) indent = (double[])o;
			else return;
			Vec3 p = new Vec3Double(indent[1], indent[2], indent[3]).toVec3();
			if (picking){
				indenter.setCenter(p);
				indenter.setText("Cylindrical indenter",
						CommonUtils.buildHTMLTableForKeyValue(new String[]{"Radius", "Position"},
								new Object[]{indent[4], p.toString()}));
			} 
			
			mvm.translate(p.x, p.y, p.z);
			
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
			mvm.scale((float)indent[4], (float)indent[4], length);
			
			updateModelViewInShader(gl, s, mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawCylinder(gl);
		}
		o = atomData.getFileMetaData("extpot");
		if (o != null) {
			double[] indent = null;
			if (o instanceof double[]) indent = (double[])o;
			else return;
			Vec3 p = new Vec3Double(indent[1], indent[2], indent[3]).toVec3();
			if (picking){
				indenter.setCenter(p);
				indenter.setText("Spherical indenter",
						CommonUtils.buildHTMLTableForKeyValue(new String[]{"Radius", "Position"},
								new Object[]{indent[4], p.toString()}));
			} 
			
			mvm.translate(p.x, p.y, p.z);
			mvm.scale((float)indent[4], (float)indent[4], (float)indent[4]);
			updateModelViewInShader(gl, s, mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawSphere(gl);
		}
		o = atomData.getFileMetaData("wall");
		if (o != null) {
			double[] indent = null;
			if (o instanceof double[]) indent = (double[]) o;
			else return;
			
			Vec3[] box = atomData.getBox().getBoxSize();
			float hx = box[0].x + box[1].x + box[2].x;
			float hy = box[0].y + box[1].y + box[2].y;
			
			if (picking){
				indenter.setCenter(new Vec3(hx/2f, hx/2f, (float)indent[1]));
				indenter.setText("Flat punch indenter",
						CommonUtils.buildHTMLTableForKeyValue(new String[]{"z-coordinate"}, new Object[]{indent[1]}));
			} 

			mvm.translate(0, 0, (float)(indent[1] - indent[2]));
			mvm.scale(hx, hy, 3f*(float)indent[2]);
			updateModelViewInShader(gl, s, mvm, projectionMatrix);
			SimpleGeometriesRenderer.drawCube(gl);
		}
		updateModelViewInShader(gl, s, modelViewMatrix, projectionMatrix);
	}
	

	public <T extends Vec3 & Pickable> void drawSpheres(GL3 gl, ObjectRenderData<T> ard, boolean picking){
		//Forward the call to a highly specialized routine
		sphereRenderer.drawSpheres(gl, ard, picking);
	}
	
	private void drawAtoms(GL3 gl, boolean picking){
		final CrystalStructure cs = atomData.getCrystalStructure();
		final int numEle = cs.getNumberOfElements();

		final FilterSet<Atom> atomFilterSet = RenderingConfiguration.getAtomFilterset();
		
		final float[] sphereSize = cs.getSphereSizeScalings();
		float maxSphereSize = 0f;
		for (int i=0; i<sphereSize.length; i++){
			sphereSize[i] *= defaultSphereSize;
			if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
		}
		
		//Update filterlists and the sets of atoms to be drawn if required
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
			
			final ColoringFilter<Atom> colFunc;
			switch (atomRenderType){
				case TYPE: colFunc = tf; break;
				case ELEMENTS: colFunc = ef; break;
				case GRAINS: colFunc = gf; break;
				case DATA:
				case VECTOR_DATA: colFunc = dataAtomFilter; break;
				default: colFunc = null;
			}
			colFunc.update();
			
			//Identify if individual particle radii are given
			final int radiusColumn = atomData.getComponentIndex(DataColumnInfo.Component.PARTICLE_RADIUS);
					
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
									//Assign default or individual particle radius
									cell.getSizeArray()[j] = radiusColumn == -1 ? sphereSize[c.getElement() % numEle] :
										c.getData(radiusColumn, atomData) * sphereSize[c.getElement() % numEle];
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
					DecimalFormat df = new DecimalFormat("#.######");
					drawLegendThisFrame(
							df.format(min)+" "+dataInfoColoring.getUnit(), 
							df.format((max+min)*0.5)+" "+dataInfoColoring.getUnit(),
							df.format(max)+" "+dataInfoColoring.getUnit()
							);
				}
			}
			
			//Draw the spheres using the pre-computed render cells
			if (atomRenderType == AtomRenderType.VECTOR_DATA){
				arrowRenderer.drawVectors(gl, renderData, picking, dataInfo, 
						RenderingConfiguration.getVectorDataScaling(),
						RenderingConfiguration.getVectorDataThickness(), 
						RenderingConfiguration.isNormalizedVectorData());
			} else sphereRenderer.drawSpheres(gl, renderData, picking);
		} else {
			//Render as RBVs
			final float[] lineDirColor = new float[]{0.5f, 0.5f, 0.5f};
			RBVStorage storage = atomData.getRbvStorage();
			for (int i=0, len = atomData.getAtoms().size(); i<len; i++){
				Atom c = atomData.getAtoms().get(i);
				if (atomFilterSet.accept(c)){
				    RBV rbv = storage.getRBV(c);
				    if (rbv != null){
    					float[] col;
    					if (picking) col = this.getNextPickingColor(c);
    					else col = cs.getGLColor(c.getType());
    
    					ArrowRenderer.renderArrow(gl, c, rbv.bv, 0.1f, col, true);
    					if (!picking) col = lineDirColor;
    					ArrowRenderer.renderArrow(gl, c, rbv.lineDirection, 0.05f, col, true);
				    }
				}
			}
		}
	}
	
	//TODO Put this method into a data-container
	private void drawGrain(GL3 gl, boolean picking){
		if (!RenderOption.GRAINS.isEnabled() || !atomData.isPolyCrystalline()) return;
		Shader s = picking?BuiltInShader.VERTEX_ARRAY_COLOR_UNIFORM.getShader():BuiltInShader.OID_ADS_UNIFORM_COLOR.getShader();
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
		float[] col = new float[]{0.8f,0.5f,0.5f, 1f};
		ArrowRenderer.renderArrow(gl, o, atomData.getBox().getBoxSize()[0].normalizeClone(), 0.02f, col, false);
		col = new float[]{0.5f,0.8f,0.5f, 1f};
		ArrowRenderer.renderArrow(gl, o, atomData.getBox().getBoxSize()[1].normalizeClone(), 0.02f, col, false);
		col = new float[]{0.5f,0.5f,0.8f, 1f};
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
		
		//Labels
		for (int i=0; i<3; i++){
			String s = "";
			if (evenNumbers)
				s = "=["+(int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].x)+" "
						+(int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].y)+" "
						+(int)Math.round(atomData.getCrystalRotation().getCrystalOrientation()[i].z)+"]";
			if (i==0) s = "x"+s;
			if (i==1) s = "y"+s;
			if (i==2) s = "z"+s;
			
			GLMatrix mvm_new = mvm.clone();
			Vec3 v = atomData.getBox().getBoxSize()[i].normalizeClone();
			mvm_new.translate(v.x, v.y, v.z);
			GLMatrix rotInverse = new GLMatrix(rotMatrix.getMatrix());
			rotInverse.inverse();
			mvm_new.mult(rotInverse);
			if (evenNumbers) mvm_new.translate(-0.4f, 0.03f, 0f);
			else mvm_new.translate(-0.05f, 0.03f, 0f);
			
			gl.glDisable(GL.GL_DEPTH_TEST);
			textRenderer.beginRendering(gl, 0f, 0f, 0f, 1f, mvm_new, pm);
	        textRenderer.draw(gl, s, 0f, 0f, 0f, 0.0025f);
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
		GLMatrix pm = createFlatProjectionMatrix();
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
        
		//Labels
		textRenderer.beginRendering(gl, 0f, 0f, 0f, 1f, mvm, pm);
    	textRenderer.draw(gl, this.legendLabels[0], width*0.01f+height*0.06f, height*0.01f, zoom*1.01f, 0.00035f*height);
   		textRenderer.draw(gl, this.legendLabels[1], width*0.01f+height*0.06f, height*0.13f, zoom*1.01f, 0.00035f*height);
    	textRenderer.draw(gl, this.legendLabels[2], width*0.01f+height*0.06f, height*0.25f, zoom*1.01f, 0.00035f*height);
	    textRenderer.endRendering(gl);
        gl.glEnable(GL.GL_DEPTH_TEST);
        drawLegendThisFrame = false;
	}
	
	private void drawLengthScale(GL3 gl){
		if (!RenderOption.LENGTH_SCALE.isEnabled()) return;
		if (RenderOption.STEREO.isEnabled() || RenderOption.PERSPECTIVE.isEnabled()) return;
		if (atomData == null) return;
		
		final int blocks = 4;
		float unitLengthInPixel = estimateUnitLengthInPixels();
		float unitsOnScreen = width/unitLengthInPixel;
		float a = unitsOnScreen*0.1f;
		
		//Determine best fitting size for the label
		//Possible values are 1*10^x, 2*10^x and 5*10^x, which x the nearest power
		float power = (float)Math.ceil(Math.log10(a));
		float b = a/(float)Math.pow(10, power);
		
		float size;
		if (b>0.5) size = 1;
		else if (b>0.33f) size = 0.5f;
		else size = 0.2f;
		size *= (int)Math.pow(10, power);
		String sizeString = String.format("%.1f",size);
		
		gl.glDisable(GL.GL_DEPTH_TEST);
		GLMatrix mvm = new GLMatrix();
		GLMatrix pm = createFlatProjectionMatrix();
		//The length scale box is placed 10% of the screen resolution away from the upper, left corner
		pm.translate(width/10 , height-(height/10), 0f); //
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		updateModelViewInShader(gl, BuiltInShader.NO_LIGHTING.getShader(), mvm, pm);
		
		float w = (size*unitLengthInPixel)/blocks;
		float h_scale = (height/20f)/10f;
		
		float textscale = 0.1f*h_scale;
		float border = 0.5f*h_scale;
		float textHeigh = textscale*textRenderer.getStringHeigh();
		float textWidth = textRenderer.getStringWidth(sizeString)*textscale;
		float minSize = Math.max(textWidth+5, w*blocks);
		
		VertexDataStorageLocal vds = new VertexDataStorageLocal(gl, 12 + 6*blocks, 2, 0, 0, 4, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		{			
			//Draw white rectangle into black rectangle -> white area + black border
			vds.setColor(0f, 0f, 0f, 1f); // Black background
			vds.setVertex(-10-border, -textHeigh-border); //Triangle 1
			vds.setVertex(minSize+10+border, -textHeigh-border);
			vds.setVertex(minSize+10+border, 10*h_scale+border);
			
			vds.setVertex(minSize+10+border, 10*h_scale+border); //Triangle 2
			vds.setVertex(-10-border, 10*h_scale+border);
			vds.setVertex(-10-border, -textHeigh-border);
			
			vds.setColor(1f, 1f, 1f, 1f); //White area inside
			vds.setVertex(-10, -textHeigh);
			vds.setVertex(minSize+10, -textHeigh);
			vds.setVertex(minSize+10, 10*h_scale);
			
			vds.setVertex(minSize+10, 10*h_scale);
			vds.setVertex(-10, 10*h_scale);
			vds.setVertex(-10, -textHeigh);
			
			//Add length scale bar
			float offset = Math.min(w*blocks-(textWidth+5), 0f)*-0.5f;
	    	for (int i=0; i<blocks; i++){
	    		float c = i%2==0? 0.7f: 0f;
	    		vds.setColor(c, c, c, 1f);
	    		
	    		vds.setVertex(offset + w*i,     8*h_scale);
	    		vds.setVertex(offset + w*i,     2*h_scale);
	    		vds.setVertex(offset + w*(i+1), 8*h_scale);
	    		
	    		vds.setVertex(offset + w*i,     2*h_scale);
	    		vds.setVertex(offset + w*(i+1), 2*h_scale);
	    		vds.setVertex(offset + w*(i+1), 8*h_scale);
	    	}
		}
		vds.endFillBuffer(gl);
		
		vds.draw(gl, GL.GL_TRIANGLES);
		vds.dispose(gl);
		
		//Add label
		textRenderer.beginRendering(gl, 0f, 0f, 0f, 1f, mvm, pm);
    	textRenderer.draw(gl, sizeString, minSize*0.5f-textWidth*0.5f,
    			2*h_scale-textRenderer.getStringHeigh()*textscale, 1f, textscale);
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
		this.dragMatrix = new GLMatrix();
		this.dragMatrix.mult(rotMatrix);
		this.arcBall.setRotationStartingPoint(arg0.getPoint());
		this.startDragPosition = arg0.getPoint();
	}

	public void mouseReleased(MouseEvent arg0) {}

	// endregion MouseListener

	// region MouseMotionListener
	@Override
	public void mouseDragged(MouseEvent e) {
		Point newDragPosition = e.getPoint();
		if (e.isAltDown() || e.isAltGraphDown()){
			zoom *= 1 + (newDragPosition.y - startDragPosition.y) / 50f;
			if (zoom>400f) zoom = 400f;
			if (zoom<0.05f) zoom = 0.05f;
		} else if (e.isControlDown()){
			float v = (globalMaxBounds.maxComponent()/globalMaxBounds.minComponent())/zoom;
			//TODO Modify this formula and the setup of the modelview matrix
			//This will break backwards compatibility, but the current implementation is just plain bad
			moveX -= ((newDragPosition.x - startDragPosition.x)/(float)width) * v;
			moveY += ((newDragPosition.y - startDragPosition.y)/(float)height) * v;
		} else {
			if (e.isShiftDown()){
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
		renderData = new ObjectRenderData<Atom>(atomData.getAtoms(), true, atomData);
		this.updateAtoms();
		
		if (atomData.getNumberOfElements() > ignoreElement.length){
			boolean[] newIgnore = new boolean[atomData.getNumberOfElements()];
			for (int i=0; i<ignoreElement.length;i++) newIgnore[i] = ignoreElement[i];
			ignoreElement = newIgnore;
		}
		
		if (atomData.getCrystalStructure().getNumberOfTypes() > ignoreTypes.length){
			boolean[] newIgnore = new boolean[atomData.getCrystalStructure().getNumberOfTypes()];
			for (int i=0; i<ignoreTypes.length;i++) newIgnore[i] = ignoreTypes[i];
			ignoreTypes = newIgnore;
		}
		
		if (reinit){
			//If individual sizes are given, use default multiplier of 1 for sphere size
			if (atomData.getComponentIndex(Component.PARTICLE_RADIUS) != -1) setSphereSize(1f);
			else setSphereSize(atomData.getCrystalStructure().getDistanceToNearestNeighbor()*0.55f);
			
			if (!atomData.isPolyCrystalline()) RenderOption.GRAINS.setEnabled(false);
			
			coordinateCenterOffset.setTo(0f, 0f, 0f);
			//Find global maximum boundaries
			globalMaxBounds.setTo(0f, 0f, 0f);
			for (AtomData d : Configuration.getAtomDataIterable(atomData)){
				Vec3 bounds = d.getBox().getHeight();
				globalMaxBounds.x = Math.max(bounds.x, globalMaxBounds.x);
				globalMaxBounds.y = Math.max(bounds.y, globalMaxBounds.y);
				globalMaxBounds.z = Math.max(bounds.z, globalMaxBounds.z);
			}
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
			grainColorTable.put(Atom.DEFAULT_GRAIN, colors[colors.length-2]);
		if (atomData.getGrains(Atom.IGNORED_GRAIN) == null)
			grainColorTable.put(Atom.IGNORED_GRAIN, colors[colors.length-1]);
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
		if (i>=ignoreElement.length) return true;
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
		final int picksize = 3;
		
		if (atomData==null) return;
		pickList.clear();
		
		boolean adjustPOVOnObject = false;
		if ((e.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK))
				== (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)){
			adjustPOVOnObject = true;
		}

		GL3 gl = this.getGLFromContext();
		updateIntInAllShader(gl, "noShading", 1);
		
		//Extract viewport
		int[] viewport = new int[4];
		gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);
		Point p = e.getPoint();
		
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		//Render in Picking mode
		gl.glEnable(GL.GL_SCISSOR_TEST);
		gl.glScissor(p.x-picksize/2, viewport[3] - p.y-picksize/2, picksize, picksize);
		renderScene(gl, true ,0, null);
		gl.glDisable(GL.GL_SCISSOR_TEST);
		
		if (pickList.size() > 16777214){
			JLogPanel.getJLogPanel().addError("Picking not possible", "Too many objects in the scene. Only 2^24 objects can be visible for picking."
					+ " Show less objects to enable picking");
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
			
			Tupel<String, String> m = picked.printMessage(e, atomData);
			JLogPanel.getJLogPanel().addInfo(m.o1, m.o2);
			
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
		
		updateIntInAllShader(gl,"noShading", 0);
		if (repaintRequired){
			reRenderTexture = true;
			this.reDraw();
		}
	}
    
	private void changeResolution(GL3 gl, int width, int height){
		this.width = width;
		this.height = height;
		gl.glViewport(0, 0, width, height);
		this.makeFullScreenQuad(gl);
		
		if (fboDeferredBuffer != null) fboDeferredBuffer.reset(gl, width, height, 0);
		else fboDeferredBuffer = new FrameBufferObject(width, height, gl, true, true);
		if (fboLeft != null) fboLeft.reset(gl, width, height, 0);
		else fboLeft = new FrameBufferObject(width, height, gl);
		if (fboRight != null) fboRight.reset(gl, width, height, 0);
		else fboRight = new FrameBufferObject(width, height, gl);
		
		//Update resolution in shader
		Shader.BuiltInShader.BLUR.getShader().enableAndPushOld(gl);
		gl.glUniform2f(gl.glGetUniformLocation(BuiltInShader.BLUR.getShader().getProgram(), "resolution"), width, height);
		Shader.popShader();
		Shader.BuiltInShader.FXAA.getShader().enableAndPushOld(gl);
		gl.glUniform2f(gl.glGetUniformLocation(BuiltInShader.FXAA.getShader().getProgram(), "resolution"), width, height);
		Shader.popAndEnableShader(gl);
	}
	
    //region export methods
	public void makeScreenshot(String filename, String type, boolean sequence, int w, int h) throws Exception{
		GL3 gl = this.getGLFromContext();
		
		int oldwidth = this.width;
		int oldheight = this.height;
		
		this.changeResolution(gl, w, h);
		FrameBufferObject screenshotFBO = new FrameBufferObject(w, h, gl);
		
		try {
			if (!sequence) {
				renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
				composeCompleteScene(gl, screenshotFBO);
				
				BufferedImage bi = screenshotFBO.textureToBufferedImage(w, h, gl);
				ImageOutput.writeScreenshotFile(bi, type, new File(filename), this.atomData, this);
			} else {
				AtomData currentAtomData = this.atomData;
				for (AtomData c : Configuration.getAtomDataIterable()){
					this.setAtomData(c, false);
					renderSceneIntoFBOs(gl, RenderOption.STEREO.isEnabled());
					composeCompleteScene(gl, screenshotFBO);

					BufferedImage bi = screenshotFBO.textureToBufferedImage(w, h, gl);
					ImageOutput.writeScreenshotFile(bi, type, new File(filename+atomData.getName()+"."+type),
							c, this);
				}	
				this.setAtomData(currentAtomData, false);
			}
		} catch (GLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			//restore old size
			screenshotFBO.destroy(gl);
			this.changeResolution(gl, oldwidth, oldheight);
			this.reDraw();
		}
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
	
	private void drawBackground(GL3 gl){
		//Use a gray to gray color gradient as background, otherwise use pure white
		GLMatrix mvm = new GLMatrix();
		GLMatrix pm = createFlatProjectionMatrix();
		gl.glDepthMask(false);
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
        gl.glDepthMask(true);
	}
	
	public float estimateUnitLengthInPixels(){
		if (atomData == null) return 1f;
		float boxSize = globalMaxBounds.maxComponent();
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
		gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "mvm"), 1, false, new GLMatrix().getMatrix());
		
		BuiltInShader.SSAO.getShader().enable(gl);
		prog = BuiltInShader.SSAO.getShader().getProgram();
		gl.glUniform1i(gl.glGetUniformLocation(prog, "colorTexture"), Shader.FRAG_COLOR);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "normalTexture"), Shader.FRAG_NORMAL);
		gl.glUniform1i(gl.glGetUniformLocation(prog, "noiseTexture"), 4);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoTotStrength"), 2.38f);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoStrength"), 0.15f);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoFalloff"), 0.0008f);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "ssaoRad"), 0.005f);
		
		BuiltInShader.BLUR.getShader().enable(gl);
		prog = BuiltInShader.BLUR.getShader().getProgram();
		gl.glUniform1i(gl.glGetUniformLocation(prog, "tex"), 4);
		gl.glUniform1f(gl.glGetUniformLocation(prog, "radius"), 4f);
		
		BuiltInShader.FXAA.getShader().enable(gl);
		prog = BuiltInShader.FXAA.getShader().getProgram();
		gl.glUniform1i(gl.glGetUniformLocation(prog, "Texture0"), 0);
		
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
	}
	
	private class TypeColoringAndFilter implements ColoringFilter<Atom> {
		float[][] colors = null;
		int numEleColors;
		boolean[] typesIgnored = null;
		
		boolean isNeeded(){
			for (int i=0; i<ignoreTypes.length;i++)
				if (ignoreTypes[i] == true) return true;
			return false;
		}
		
		public TypeColoringAndFilter() {
			update();
		}
		
		@Override
		public boolean accept(Atom a) {
			return !typesIgnored[a.getType()];
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
			typesIgnored = new boolean[ignoreTypes.length];
			for (int i=0; i<ignoreTypes.length; i++){
				typesIgnored[i] = isTypeIgnored(i);
			}
		}
	}
	
	private class GrainColoringAndFilter implements ColoringFilter<Atom> {
		HashMap<Integer, Boolean> ignoredGrains;
		public GrainColoringAndFilter() {
			update();
		}
		
		boolean isNeeded(){
			for (boolean b : ignoreGrain.values())
				if (b) return true;
			return false;
		}
		
		@Override
		public boolean accept(Atom a) {
			int grain = a.getGrain();
			if (ignoredGrains.containsKey(grain))
				return !ignoredGrains.get(grain);
			else return true;
		}
		
		@Override
		public void update() {
			this.ignoredGrains = new HashMap<Integer, Boolean>(ignoreGrain);
		}
		
		public float[] getColor(Atom c) {
			return getGrainColor(c.getGrain());
		};
	}
	
	private class ElementColoringAndFilter implements ColoringFilter<Atom> {
		float[][] colorTable = null;
		boolean[] elementsIgnored = null;
		
		public ElementColoringAndFilter() {
			update();
		}
		
		boolean isNeeded(){
			if (atomData.getNumberOfElements()==1) return false;
			for (int i=0; i<ignoreElement.length;i++)
				if (ignoreElement[i]) return true;
			return false;
		}
		
		@Override
		public boolean accept(Atom a) {
			return !elementsIgnored[a.getElement()];
		}
		
		@Override
		public float[] getColor(Atom c) {
			return colorTable[c.getElement()];
		}
		
		@Override
		public void update() {
			colorTable = ColorTable.getColorTableForElements(atomData.getNumberOfElements());
			elementsIgnored = new boolean[atomData.getNumberOfElements()];
			for (int i=0; i<elementsIgnored.length; i++){
				elementsIgnored[i] = isElementIgnored(i);
			}
		}
	}
	
	private class DataColoringAndFilter implements ColoringFilter<Atom> {
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
			if ((filterMin && a.getData(selected, atomData)<min) || (filterMax && a.getData(selected, atomData)>max))
					return inversed;
				return !inversed;
		}
		
		@Override
		public float[] getColor(Atom c) {
			return ColorTable.getIntensityGLColor(min, max, c.getData(selected, atomData));
		}
		
		@Override
		public void update() {
			DataColumnInfo dataInfo = isVector?
					RenderingConfiguration.getSelectedVectorColumn():
					RenderingConfiguration.getSelectedColumn();
			if (dataInfo == null) return;
			
			if (isVector){
				selected = atomData.getDataColumnIndex(dataInfo.getVectorComponents()[3]);
				min = dataInfo.getVectorComponents()[3].getLowerLimit();
				max = dataInfo.getVectorComponents()[3].getUpperLimit();
			} else { 
				selected = atomData.getDataColumnIndex(dataInfo);
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