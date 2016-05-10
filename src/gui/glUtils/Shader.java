package gui.glUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Stack;

import com.jogamp.opengl.GL3;

import com.jogamp.common.nio.Buffers;

public class Shader {
	public final static int ATTRIB_VERTEX = 0;
	public final static int ATTRIB_COLOR = 1;
	public final static int ATTRIB_TEX0 = 2;
	public final static int ATTRIB_NORMAL = 3;
	public final static int ATTRIB_CUSTOM0 = 4;
	public final static int ATTRIB_CUSTOM1 = 5;
	public final static int ATTRIB_CUSTOM2 = 6;
	public final static int ATTRIB_CUSTOM3 = 7;	
	public final static int ATTRIB_VERTEX_OFFSET = 8;
	
	public final static int FRAG_COLOR = 0;
	public final static int FRAG_NORMAL = 1;
	public final static int FRAG_POSITION = 2;
	public final static int FRAG_COLOR_ACCU = 1;
	public final static int FRAG_ALPHA_ACCU = 2;
	
	private final static String defaultVertexShader = 
        "#ifdef UNIFORM_COLOR\n"+
        "uniform vec4 Color;\n"+
        "#else\n"+
        "in vec4 Color;\n"+
        "#endif\n"+
	        
		"in vec3 v;" +
		"in vec2 Tex;" +
		"out vec4 FrontColor;" +
		"out vec2 TexCoord0;" +
		
		"uniform mat4 mvpm;"+
		
		"void main(void)"+
		"{"+
		"  gl_Position = mvpm * vec4(v,1);"+
		"  FrontColor  = Color;"+
		"  TexCoord0   = Tex;"+
		"}"
	;
	
	private final static String anaglyphFragmentShader = 
		"uniform sampler2D left;"+
		"uniform sampler2D right;"+
		"in vec2 TexCoord0;"+
		"out vec4 vFragColor;"+
	 
		"void main(void)"+
		"{"+
		"vec4 ColorL = texture(left, TexCoord0.st);"+
		"vec4 ColorR = texture(right, TexCoord0.st);"+
		"float GrayR = dot(vec3(0.3, 0.59, 0.11), vec3(ColorR));"+
		"vFragColor  = vec4( GrayR, ColorL.g, ColorL.b, 1.) + vec4(1.-ColorR.a, 1.-ColorL.a, 1.-ColorL.a, 1);"+
		"vFragColor.a = 1.0;"+
		"}"
	;
		
	private final static String simpleColorFragmentShader = 
		"in vec4 FrontColor;"+
		"out vec4 vFragColor;"+
		
		"void main(void) {"+
			"vFragColor   = FrontColor;"+
		"}"
	;
	
	private final static String simpleTextureShader = 
		"uniform sampler2D Texture0;"+
		"in vec2 TexCoord0;"+
		"in vec4 FrontColor;"+
		"out vec4 vFragColor;"+
		
		"void main(void) {"+
			"vFragColor   = texture(Texture0, TexCoord0.xy)*FrontColor;"+
		"}"
	;	
	
	private final static String pplVertexShader = 
		"in vec3 v;\n"+
		"in vec3 norm;\n"+
		"#ifdef UNIFORM_COLOR\n"+
		"uniform vec4 Color;\n"+
		"#else\n"+
		"in vec4 Color;\n"+
		"#endif\n"+
		"out vec3 lightvec;"+
		"out vec4 FrontColor;"+
		"out vec3 normal;"+
		"uniform mat4 mvm;"+
		"uniform mat4 mvpm;"+
		"uniform mat3 nm;"+
		"uniform vec3 lightPos;"+

		"void main(void) {"+
		"  FrontColor     = Color;\n"+
		"  vec4 vp        = vec4(v, 1.);\n"+
		"  normal         = normalize(nm * norm);\n"+
		"  lightvec       = normalize(lightPos - (mvm * vp).xyz);\n"+
		"  gl_Position    = mvpm * vp;\n"+
		"}"
	;
	
	private final static String passThroughDeferredVertexShader = 
		"in vec3 v;"+
		"in vec3 norm;"+
		"uniform vec4 Color;"+
		
		"out vec4 FrontColor;"+
		"out vec3 normal;"+
		"out vec4 position;"+
		
		"uniform mat3 nm;"+
		"uniform mat4 mvpm;"+

		"void main(void) {"+
		"  FrontColor     = Color;\n"+
		"  position       = vec4(v, 1.);\n"+
		"  normal         = normalize(nm*norm);\n"+
		"  gl_Position    = mvpm * position;\n"+
		"}"
	;
	
	private final static String passThroughDeferredColorVertexShader = 
		"in vec3 v;"+
		"in vec3 norm;"+
		"in vec3 Color;"+
		
		"out vec4 FrontColor;"+
		"out vec3 normal;"+
		"out vec4 position;"+
		
		"uniform mat3 nm;"+
		"uniform mat4 mvpm;"+

		"void main(void) {"+
		"  FrontColor     = vec4(Color,1.);\n"+
		"  position       = vec4(v, 1.);\n"+
		"  normal         = normalize(nm*norm);\n"+
		"  gl_Position    = mvpm * position;\n"+
		"}"
	;
	
	private final static String pplFragmentwithADSShader = 
		"in vec3 lightvec;"+
		"in vec3 normal;"+
		"in vec4 FrontColor;"+
		"out vec4 vFragColor;"+
		
		"uniform int noShading;"+
		"uniform int ads;"+
			
		"void main(void) {"+
		"  vFragColor = FrontColor;\n"+
		"  if (noShading != 1){\n"+
		"    vec3 norm = normalize(normal);"+
		"    vec3 lv = normalize(lightvec);"+
		"    float ambient = 0.5 - ads*0.2;"+
		"    if (ads == 1){\n"+
		"      float diff = max(0.0, dot(norm, lv));"+
		"      vec3 vReflection = normalize(reflect(-lv, norm));"+
		"      float spec = max(0.0, dot(norm, vReflection));"+
		"      float fSpec = pow(spec, 96.0);"+
		"      vFragColor.rgb *= vec3(diff+ambient);"+
		"      vFragColor.rgb += vec3(fSpec);"+
		"    } else {\n"+
		"      vFragColor.rgb *= (max(dot(norm, lv), 0.) + ambient);"+
		"    }\n"+
		"  }\n"+
		"}"
	;
	
	private final static String ssaoFragmentShader = 
		"uniform sampler2D normalTexture;"+
		"uniform sampler2D colorTexture;"+
		
		"in vec2 TexCoord0;"+
		"out vec4 vFragColor;"+
		
		//SSAO uniforms
        "uniform sampler2D noiseTexture;"+
        "uniform float ssaoTotStrength;"+
        "uniform float ssaoStrength;"+
        "uniform float ssaoFalloff;"+
        "uniform float ssaoRad;\n"+
        "uniform float ssaoOffset = 5.25;"+
        
		"const int SAMPLES = 10;"+
		"const float invSamples = 1.0/SAMPLES;"+
		

		"// these are the random vectors inside a unit sphere\n"+
		"const vec3 pSphere[10] = vec3[](vec3(-0.010735935, 0.01647018, 0.0062425877),vec3(-0.06533369, 0.3647007, -0.13746321),"+
		"      vec3(-0.6539235, -0.016726388, -0.53000957),vec3(0.40958285, 0.0052428036, -0.5591124),"+
		"      vec3(-0.1465366, 0.09899267, 0.15571679),vec3(-0.44122112, -0.5458797, 0.04912532),"+
		"      vec3(0.03755566, -0.10961345, -0.33040273),vec3(0.019100213, 0.29652783, 0.066237666),"+
		"      vec3(0.8765323, 0.011236004, 0.28265962),vec3(0.29264435, -0.40794238, 0.15964167));"+
		
		"float occlusion(in vec4 normTexel){"+
		"   // grab a normal for reflecting the sample rays later on \n"+
		"   vec3 fres = normalize((texture(noiseTexture,TexCoord0*ssaoOffset).xyz*2.0) - vec3(1.0));"+
		
		"   float currentPixelDepth = normTexel.a;"+
		
		"   // current fragment coords in screen space\n "+
		"   vec3 ep = vec3(TexCoord0.xy,currentPixelDepth); "+
		"   vec3 norm = normTexel.xyz; "+
		
		"   float bl = 0.0;"+
		"   // adjust for the depth ( not shure if this is good..)\n"+
		"   float radD = ssaoRad/currentPixelDepth;"+
		
		"   for(int i=0; i<SAMPLES;++i){ "+
		"      vec3 ray = radD*reflect(pSphere[i],fres); "+
		
		"      // if the ray is outside the hemisphere then change direction\n "+
		"      vec3 se = ep + sign(dot(ray,norm) )*ray; "+
		"      vec4 occluderFragment = texture(normalTexture,se.xy); "+
		"      vec3 occNorm = occluderFragment.xyz; "+
		
		"      float depthDifference = currentPixelDepth-occluderFragment.a; "+
		"      // if depthDifference is negative = occluder is behind current fragment\n "+
		"      if ( depthDifference>0.)"+
		"      // the falloff equation, starts at falloff and is kind of 1/x^2 falling\n "+
		"        bl += step(ssaoFalloff,depthDifference)*(1.0-dot(occNorm,norm))"+
		"			   *(1.0-smoothstep(ssaoFalloff,ssaoStrength,depthDifference)); "+
		"   } "+
		
		"   // output the result\n "+
		"   return (ssaoTotStrength*bl*invSamples); "+
		"}"+
		
		"void main(void) {"+
		"  vec4 FrontColor = texture(colorTexture, TexCoord0.st);"+
		"  vec4 normTexel = texture(normalTexture, TexCoord0.st);"+
		"  vFragColor.a = 1.;"+
		//Assign color if alpha > 0.05, otherwise the value is 0.
		"  vFragColor.rgb = vec3(occlusion(normTexel) * FrontColor.a) * step(0.05, FrontColor.a);"+
		"}"
	;
	
	
	private final static String blurShader = 
		"in vec2 TexCoord0;"+
		"out vec4 vFragColor;"+

		//declare uniforms
		"uniform sampler2D tex; "+
		"uniform vec2 resolution; "+
		"uniform float radius; "+
		"uniform vec2 dir; \n"+

		"void main() {																					"+
		"    vec4 sum = vec4(0.0);                                                                      "+
        "                                                                                               "+
		"    vec2 tc =TexCoord0;                                                                        "+
        "                                                                                               "+
		"    float blur = radius/dot(resolution,dir);                                                   "+
        "                                                                                               "+
		"    float hstep = dir.x;                                                                       "+
		"    float vstep = dir.y;                                                                       "+
        "                                                                                               "+
		"    sum += texture(tex, vec2(tc.x - 4.0*blur*hstep, tc.y - 4.0*blur*vstep)) * 0.0162162162;    "+
		"    sum += texture(tex, vec2(tc.x - 3.0*blur*hstep, tc.y - 3.0*blur*vstep)) * 0.0540540541;    "+
		"    sum += texture(tex, vec2(tc.x - 2.0*blur*hstep, tc.y - 2.0*blur*vstep)) * 0.1216216216;    "+
		"    sum += texture(tex, vec2(tc.x - 1.0*blur*hstep, tc.y - 1.0*blur*vstep)) * 0.1945945946;    "+
        "                                                                                               "+
		"    sum += texture(tex, vec2(tc.x, tc.y)) * 0.2270270270;                                      "+
        "                                                                                               "+
		"    sum += texture(tex, vec2(tc.x + 1.0*blur*hstep, tc.y + 1.0*blur*vstep)) * 0.1945945946;    "+
		"    sum += texture(tex, vec2(tc.x + 2.0*blur*hstep, tc.y + 2.0*blur*vstep)) * 0.1216216216;    "+
		"    sum += texture(tex, vec2(tc.x + 3.0*blur*hstep, tc.y + 3.0*blur*vstep)) * 0.0540540541;    "+
		"    sum += texture(tex, vec2(tc.x + 4.0*blur*hstep, tc.y + 4.0*blur*vstep)) * 0.0162162162;    "+
        "                                                                                               "+
		"    vFragColor = vec4(sum.rgb, 1.0);                                                           "+
		"}                                                                                              "
	;
	
	private final static String deferredADSFragmentShader = 
		"uniform sampler2D posTexture;"+
		"uniform sampler2D normalTexture;"+
		"uniform sampler2D colorTexture;"+
		
		"in vec2 TexCoord0;"+
		"out vec4 vFragColor;"+
		
		"uniform int noShading;"+
		"uniform int ads;"+
		
		"uniform mat4 mvm;"+
		"uniform vec3 lightPos;"+
		
		"uniform int ambientOcclusion = 0;"+
		"uniform sampler2D occlusionTexture;"+
			
		"void main(void) {"+
		"  vec4 FrontColor = texture(colorTexture, TexCoord0.st);"+
		"  vec4 normTexel = texture(normalTexture, TexCoord0.st);"+
		"  vFragColor = FrontColor;\n"+
		"  gl_FragDepth = 1.;"+
		
		"  if (noShading != 1) { \n"+
		"    vec4 position = texture(posTexture, TexCoord0.st);"+
		"    vec3 norm = normTexel.xyz;"+
		"    vec3 v = (mvm*position).xyz;"+
		"    vec3 lightvec = normalize(lightPos - v);"+
		//ambient, diffusion and occlusion factors for lighting
		"    float diff = max(0.0, dot(norm, lightvec));"+
	    //In case of specular lighting use an ambient factor of 0.5 otherwise 0.3
	    "    float ambient = 0.5 - ads*0.2;"+
		"    float occ = ambientOcclusion==1 ? texture(occlusionTexture, TexCoord0.st).r : 0.;"+
		"    vec3 vReflection = normalize(reflect(-lightvec, norm));"+
	    //Multiply specular value with the ads uniform, disables effect if required
		"    float spec = max(0.0, dot(norm, vReflection)) * ads;"+
		"    float fSpec = pow(spec, 96.0);"+
		"    vFragColor.rgb = (vFragColor.rgb*(diff+ambient) + fSpec)-occ;"+
		
		"  }\n"+
		"  gl_FragDepth = FrontColor.a>0 ? normTexel[3] : 1.;"+			// normal[3] is the depth value
		"}"
	;
	
	private final static String toGBufferFragmentShader = 
		"in vec3 normal;"+
		"in vec4 FrontColor;"+
		"in vec4 position;"+
		"out vec4 vFragColor;"+
		"out vec4 vFragNormal;"+
		"out vec4 vFragPosition;"+
		
		"void main(void) {"+
		"  vFragColor = FrontColor;\n"+
		"  vFragNormal = vec4(normalize(normal), gl_FragCoord.z);"+
		"  vFragPosition = position;\n"+		
		"}"
	;
	
	private final static String translateBillboardVertexShaderDeferred = 
		"#ifdef INSTANCED\n"+
		"  in vec4 Move;"+
		"  in vec4 Color;\n"+
		"#else\n"+
		"  uniform vec4 Move;"+
		"  uniform vec4 Color;\n"+
		"#endif\n"+		

		"in vec3 p;"+
		"in vec2 tex;"+
		"noperspective out vec2 vTexCoord;"+
		"flat out vec4 FrontColor;"+
		"flat out vec4 pos;"+
		
		"uniform mat4 mvpm;"+

		"void main(void) {"+
		"  FrontColor     = Color;"+
		"  gl_Position    = mvpm * (vec4(p*Move[3] + Move.xyz,1.));"+
		"  vTexCoord      = tex;"+
		"  pos            = Move;"+
		"}"
	;
	
	private final static String billboardFragmentShaderDeferred = 
		"#if __VERSION__>=420\n"+	
		"layout(early_fragment_tests) in;\n"+
		"#endif\n"+
		"flat in vec4 pos;"+
		"flat in vec4 FrontColor;"+
		"noperspective in vec2 vTexCoord;"+
		
		"out vec4 vFragNormal;"+
		"out vec4 vFragColor;"+
		"out vec4 vFragPosition;"+
		
		"void main(void)"+
		"{"+
		"  vec2 a = vTexCoord.xy;"+
		"  float t = dot(a, a);"+	//Test if coordinate is inside a sphere of size r=1
		"  if (t>1.) {discard;}" +
		
		"  vFragColor    = FrontColor;\n"+
		"  vec3 normal = vec3(a.x, a.y, sqrt(1.-t));"+
		"  vFragPosition = vec4(pos.xyz ,1.) ;\n"+
		"  vFragNormal   = vec4(normal, gl_FragCoord.z);\n"+
		"}"
	;
	
	private final static String billboardFragmentShaderDeferredPerfectSphere = 
		"#if __VERSION__>=420\n"+	
		"layout (depth_less) out float gl_FragDepth;\n"+
		"#endif\n"+
		"flat in vec4 pos;"+
		"flat in vec4 FrontColor;"+
		"noperspective in vec2 vTexCoord;"+
		
		"out vec4 vFragNormal;"+
		"out vec4 vFragColor;"+
		"out vec4 vFragPosition;"+
		
		"uniform mat4 mvpm;"+
		"uniform mat3 inv_rot;"+
		
		"void main(void)"+
		"{"+
		"  vec2 a = vTexCoord.xy;"+
		"  float t = dot(a, a);"+	//Test if coordinate is inside a sphere of size r=1
		"  if (t>1.) {discard;}" +
		
		"  vFragColor    = FrontColor;\n"+
		
		//Recompute z-coordinate, the billboard is shifted
		"  vec3 normal = vec3(a.x, a.y, sqrt(1.-t));"+
		//Rotate normal in screen space back to user space
		"  vec3 shift = inv_rot*normal;"+
		//Compute the exact position on the sphere, pos.xyz is the center, pos[3] the radius
		"  vec3 p = pos.xyz + shift*pos[3];"+
		
		"  vFragPosition = vec4(p ,1.) ;\n"+
		
		"  float far=gl_DepthRange.far; float near=gl_DepthRange.near;"+

		"  vec4 z = (mvpm * vFragPosition);"+
		"  float depth = ((far-near) * (z.z/z.w) + near + far) * 0.5;"+
		"  gl_FragDepth = depth;"+
		
		"  vFragNormal   = vec4(normal, depth);\n"+
		"}"
	;
	
	private final static String arrowVertexShader = 
		"uniform vec3 Direction;"+
		"uniform vec3 Origin;"+
		"uniform vec4 Color;"+
		"uniform vec4 Dimensions;"+
		
		"in vec2 p;"+
		"in vec3 norm;"+
		"in vec4 scalings;"+
		"out vec3 lightvec;"+
		"out vec3 normal;"+
		"out vec4 FrontColor;"+
		
		"uniform mat4 mvm;"+
		"uniform mat4 mvpm;"+
		"uniform mat3 nm;"+
		"uniform vec3 lightPos;"+
		

		"void main(void) {"+
		
		"  vec3 u;\n"+
		"  vec3 d = normalize(Direction);\n"+

		//Create a normal vector u on d, that is not the null vector 
		//unless d is the null vector
//		Implementation using branching		
//		"  if (abs(d[0]) >= abs(d[1])) u = vec3(d[2], 0., -d[0]);"
//		"  else u = vec3(0, d[2], u[2]);"
		//Implementation without branching
		"  float s = step(d[1], d[0]);"+
		"  u[0] = ( d[2] * s) + (  0.  * (1.-s));\n"+
		"  u[1] = (  0.  * s) + ( d[2] * (1.-s));\n"+
		"  u[2] = (-d[0] * s) + (-d[1] * (1.-s));\n"+
		
		"  u = normalize(u);\n"+
		"  vec3 v = normalize(cross(Direction,u));\n"+
		"  vec3 shift = d * (Dimensions[3]-2.*Dimensions[1]);\n"+
		
		"  vec3 scaledP   = p.x*u + p.y*v;\n"+
		"  vec3 pos       = scaledP * (Dimensions[0]*scalings[0] + Dimensions[1]*scalings[1]);\n"+
		"  pos            = pos + (Dimensions[2]*scalings[2]*d + Dimensions[3]*scalings[3]*d);\n"+
		"  vec3 vp        = pos + Origin; \n"+
		"  vec3 vert      = vec3(mvm * vec4(vp,1.));\n"+
		"  FrontColor     = Color;\n"+
		"  vec3 n = normalize(norm[0]*d + norm[2]*scaledP + norm[1]*scaledP);\n"+
		"  normal         = normalize(nm * n);\n"+
		"  lightvec       = normalize(lightPos - vert);\n"+
		"  gl_Position    = mvpm * vec4(vp,1.);\n"+
		"}"
	;
	
	private final static String arrowVertexShaderDeferred = 
		"#ifdef INSTANCED\n"+
		"  in vec3 Direction;"+
		"  in vec3 Origin;"+
		"  in vec4 Color;"+
		"  in vec4 Dimensions;\n"+
		"#else\n"+
		"  uniform vec3 Direction;"+
		"  uniform vec3 Origin;"+
		"  uniform vec4 Color;"+
		"  uniform vec4 Dimensions;\n"+
		"#endif\n"+
		"in vec2 p;"+
		"in vec3 norm;"+
		"in vec4 scalings;"+
		"out vec4 position;"+
		"out vec3 normal;"+
		"out vec4 FrontColor;"+
		"uniform mat3 nm;"+
		"uniform mat4 mvpm;"+
		
		"void main(void) {"+
		
		"  vec3 u;\n"+
		"  vec3 d = normalize(Direction);\n"+
		//Create a normal vector u on d, that is not the null vector 
		//unless d is the null vector
//		Implementation using branching		
//		"  if (abs(d[0]) >= abs(d[1])) u = vec3(d[2], 0., -d[0]);"
//		"  else u = vec3(0, d[2], u[2]);"
		//Implementation without branching
		"  float s = step(d[1], d[0]);"+
		"  u[0] = ( d[2] * s) + (  0.  * (1.-s));\n"+
		"  u[1] = (  0.  * s) + ( d[2] * (1.-s));\n"+
		"  u[2] = (-d[0] * s) + (-d[1] * (1.-s));\n"+
		"  u = normalize(u);\n"+
		
		"  vec3 v = normalize(cross(Direction,u));\n"+
		"  vec3 shift = d * (Dimensions[3]-2.*Dimensions[1]);\n"+
		
		"  vec3 scaledP   = p.x*u + p.y*v;\n"+
		"  vec3 pos       = scaledP * (Dimensions[0]*scalings[0] + Dimensions[1]*scalings[1]);\n"+
		"  pos            = pos + (Dimensions[2]*scalings[2]*d + Dimensions[3]*scalings[3]*d);\n"+
		"  vec3 vp        = pos + Origin; \n"+
		
		"  FrontColor     = Color;\n"+
		"  normal         = normalize(nm*normalize(norm[0]*d + norm[2]*scaledP + norm[1]*scaledP));\n"+
		"  position       = vec4(vp,1.);\n"+
		"  gl_Position    = mvpm * position;\n"+
		"}"
	;
	
	private final static String fxaaVertexShader =
		"in vec3 v;" +
		"in vec2 Tex;" +
		"out vec2 TexCoord0;" +
		"out vec4 posPos;"+
		"out vec2 rcpFrame;"+
		
		"uniform mat4 mvpm;"+
		
		"uniform float FXAA_SUBPIX_SHIFT = 1.0/4.0;"+
		"uniform vec2 resolution; "+
		
		"void main(void)"+
		"{"+
		"gl_Position = mvpm * vec4(v,1);"+
		"TexCoord0   = Tex;"+
		"rcpFrame = 1.0/resolution;"+
		"posPos.xy = TexCoord0.xy;"+
		"posPos.zw = TexCoord0.xy - (rcpFrame * (0.5 + FXAA_SUBPIX_SHIFT));"+
		"}"
	;
	
	private final static String fxaaFragmentShader =
		"in vec2 TexCoord0;"+
		"in vec4 posPos;\n"+
		"in vec2 rcpFrame;"+
		"out vec4 vFragColor;"+
		
		"uniform sampler2D Texture0;"+
		
		"uniform vec2 resolution; "+
		"uniform float FXAA_SPAN_MAX = 8.0;\n"+
		"uniform float FXAA_REDUCE_MUL = 1.0/8.0;\n"+

		"#define FxaaInt2 ivec2\n"+
		"#define FxaaFloat2 vec2\n"+
		
		"#define FxaaTexLod0(t, p) textureLod(t, p, 0.0)\n"+
		"#define FxaaTexOff(t, p, o, r) textureLodOffset(t, p, 0.0, o)\n"+
		
		"vec3 FxaaPixelShader( \n"+
		"  vec4 posPos, // Output of FxaaVertexShader interpolated across screen.\n"+
		"  sampler2D tex, // Input texture.\n"+
		"  vec2 rcpFrame) // Constant {1.0/frameWidth, 1.0/frameHeight}.\n"+
		"{   \n"+
		
		"    #define FXAA_REDUCE_MIN   (1.0/128.0)\n"+
		
		"    vec3 rgbNW = FxaaTexLod0(tex, posPos.zw).xyz;\n"+
		"    vec3 rgbNE = FxaaTexOff(tex, posPos.zw, FxaaInt2(1,0), rcpFrame.xy).xyz;\n"+
		"    vec3 rgbSW = FxaaTexOff(tex, posPos.zw, FxaaInt2(0,1), rcpFrame.xy).xyz;\n"+
		"    vec3 rgbSE = FxaaTexOff(tex, posPos.zw, FxaaInt2(1,1), rcpFrame.xy).xyz;\n"+
		"    vec3 rgbM  = FxaaTexLod0(tex, posPos.xy).xyz;\n"+
		
		"    vec3 luma = vec3(0.299, 0.587, 0.114);\n"+
		"    float lumaNW = dot(rgbNW, luma);\n"+
		"    float lumaNE = dot(rgbNE, luma);\n"+
		"    float lumaSW = dot(rgbSW, luma);\n"+
		"    float lumaSE = dot(rgbSE, luma);\n"+
		"    float lumaM  = dot(rgbM,  luma);\n"+
		
		"    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));\n"+
		"    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));\n"+
		
		"    vec2 dir; \n"+
		"    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));\n"+
		"    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));\n"+
		
		"    float dirReduce = max(\n"+
		"        (lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL),\n"+
		"        FXAA_REDUCE_MIN);\n"+
		"    float rcpDirMin = 1.0/(min(abs(dir.x), abs(dir.y)) + dirReduce);\n"+
		"    dir = min(FxaaFloat2( FXAA_SPAN_MAX,  FXAA_SPAN_MAX),\n"+
		"          max(FxaaFloat2(-FXAA_SPAN_MAX, -FXAA_SPAN_MAX),\n"+ 
		"          dir * rcpDirMin)) * rcpFrame.xy;\n"+
		
		"    vec3 rgbA = (1.0/2.0) * (\n"+
		"       FxaaTexLod0(tex, posPos.xy + dir * (1.0/3.0 - 0.5)).xyz +\n"+
		"       FxaaTexLod0(tex, posPos.xy + dir * (2.0/3.0 - 0.5)).xyz);\n"+
		"    vec3 rgbB = rgbA * (1.0/2.0) + (1.0/4.0) * (\n"+
		"        FxaaTexLod0(tex, posPos.xy + dir * (0.0/3.0 - 0.5)).xyz +\n"+
		"        FxaaTexLod0(tex, posPos.xy + dir * (3.0/3.0 - 0.5)).xyz);\n"+
		"    float lumaB = dot(rgbB, luma);\n"+
		"    if((lumaB < lumaMin) || (lumaB > lumaMax)) return rgbA;\n"+
		"    return rgbB; }\n"+
		
		"vec4 PostFX(sampler2D tex, vec2 uv)\n"+
		"{\n"+
		"  vec4 c = vec4(0.0);\n"+
		"  c.rgb = FxaaPixelShader(posPos, tex, rcpFrame);\n"+
		"  c.a = 1.0;\n"+
		"  return c;\n"+
		"}\n"+
		
		"void main(void)"+
		"{"+
		"vFragColor =  PostFX(Texture0, TexCoord0.st);"+
		"vFragColor.a =  1.0;"+
		"}"
	;
	
	private final static String oidTransparencyFragmentShader = 
		"in vec4 FrontColor;\n"+
		"out vec4 vFragAlphaAccu;\n"+
		"out vec4 vFragColorAccu;\n"+
		"#ifdef NO_LIGHTING\n"+
		"const int noShading = 1;\n"+
		"const int ads = 0;\n"+
		"const vec3 lightvec = vec3(0.);\n"+
		"const vec3 normal = vec3(0.);\n"+
		"#else \n"+
		"in vec3 lightvec;\n"+
		"in vec3 normal;\n"+
		"uniform int noShading = 0;\n"+
		"uniform int ads = 1;\n"+
		"#endif\n"+
		"void main(void) {"+
		"  vFragColorAccu = FrontColor;\n"+
		"  vec3 norm = normalize(normal);"+
		"  vec3 lv = normalize(lightvec);"+
		"  float ambient = 0.5 - ads*0.2;"+
	    "  float diff = max(0.0, dot(norm, lv));"+
	    "  vec3 vReflection = normalize(reflect(-lv, norm));"+
        "  float spec = max(0.0, dot(norm, vReflection)) * ads ;"+
        "  float fSpec = pow(spec, 96.0);"+
	    "  vFragColorAccu.rgb *= (noShading==1 ? 1 : (diff + ambient));"+
		"  vFragColorAccu.rgb += fSpec * (1 - noShading);"+

//		"  float w = FrontColor.a*max(0.01, 3000.*(1.-gl_FragCoord.z)*(1.-gl_FragCoord.z)*(1.-gl_FragCoord.z));\n"+
		"  float w = FrontColor.a*max(0.01, (1./gl_FragCoord.z)*(1./gl_FragCoord.z));\n"+
		"  vFragColorAccu = vec4(vFragColorAccu.rgb*FrontColor.a*w, (FrontColor.a));\n"+
		"  vFragAlphaAccu = vec4(FrontColor.a)*w;\n"+	
		"}"
	;
	
	private final static String oidTransparencyComposer = 
		"uniform sampler2D RevealageTexture;"+
		"uniform sampler2D AccuTexture;"+
		
		"in vec2 TexCoord0;"+
		"out vec4 vFragColor;"+
		
		"void main(void) {"+
		"  vec4 accum = texture(AccuTexture, TexCoord0.st);"+
		"  float r = accum.a;\n"+
		"  accum.a = texture(RevealageTexture, TexCoord0.st).r;"+
		"  vFragColor = vec4(accum.rgb / clamp(accum.a, 1e-4, 5e4), r);\n"+
		"}"
	;
	
    private final static String instancedMacro = 
            "#define INSTANCED 1\n"
    ;
    private final static String uniformColorMacro =
            "#define UNIFORM_COLOR 1\n"
    ;

	private static Shader lastUsedShader = null;
	
	private static final Stack<Shader> shaderStack = new Stack<Shader>();
	private static final ArrayList<Shader> allKnownShader = new ArrayList<Shader>();
	
	private static boolean initializedShader = false;
	
	private int shaderProgram = -1;
	private String[] vertexShader, fragmentShader;
	private int[] indices;
	private String[] attribs;
	private int alternatePathGLSLVersion = 150;
	
	public Shader(String[] vertexShader, String[] fragmentShader, int[] indices, String[] attribs){
		this.vertexShader = vertexShader;
		this.indices= indices;
		this.fragmentShader = fragmentShader;
		this.attribs = attribs;
	}
	
	public Shader(String[] vertexShader, String[] fragmentShader, int[] indices, String[] attribs, int alternatePathGLSLVersion){
		this(vertexShader, fragmentShader, indices,attribs);
		this.alternatePathGLSLVersion = alternatePathGLSLVersion;
	}
	
	public void compile(GL3 gl){
		String glVersion = gl.glGetString(GL3.GL_VERSION);
		glVersion = glVersion.substring(0, 3);
		int openGLVersion = (int)(Double.parseDouble(glVersion)*100);
		
		int version = 150;
		if (openGLVersion >= alternatePathGLSLVersion)
			version = alternatePathGLSLVersion;
			
		shaderProgram = createShaderProgram(vertexShader, fragmentShader, gl, indices, attribs, version);
	}
	
	public void delete(GL3 gl){
		if (shaderProgram != -1) gl.glDeleteProgram(shaderProgram);
	}
	
	public int getProgram(){
		return shaderProgram;
	}
	
	public String[] getAttribs() {
		return attribs;
	}
	
	public void enableAndPushOld(GL3 gl){
		shaderStack.push(lastUsedShader);
		this.enable(gl);
	}
	
	public void enable(GL3 gl){
		if (lastUsedShader == this) return;
		
		//Figure out which VertexAttribArrays must be enabled and which disabled
		ArrayList<Integer> toEnable = new ArrayList<Integer>();
		ArrayList<Integer> toDisable = new ArrayList<Integer>();
		if (lastUsedShader!=null) for (int i: lastUsedShader.indices) toDisable.add(i);
		for (int i: this.indices) toEnable.add(i);

		toDisable.removeAll(toEnable);
		toEnable.removeAll(toDisable);

		for (int i: toDisable)
			gl.glDisableVertexAttribArray(i);
		
		for (int i: toEnable)
			gl.glEnableVertexAttribArray(i);
		
		gl.glUseProgram(this.shaderProgram);
		lastUsedShader = this;
	}
	
	public static Shader popShader(){
		if (shaderStack.isEmpty()) return null;
		return shaderStack.pop();
	}
	
	public static Shader popAndEnableShader(GL3 gl){
		Shader s = shaderStack.pop();
		if (s != null)
			s.enable(gl);
		return s;
	}
	
	public static void pushShader(){
		if (lastUsedShader!=null)
			shaderStack.push(lastUsedShader);
	}
	
	public static void disableLastUsedShader(GL3 gl){
		if (lastUsedShader == null) return;
		for (int i: lastUsedShader.indices){
			gl.glDisableVertexAttribArray(i);
		}
		lastUsedShader = null;
	}
	
	public static void init(GL3 gl){
		if (initializedShader){
			//Reinit
			for (Shader s: allKnownShader) s.delete(gl);
			allKnownShader.clear();
		}
		
		for (BuiltInShader s : BuiltInShader.values()){			
			s.getShader().compile(gl);
			allKnownShader.add(s.getShader());
		}
		
		initializedShader = true;
	}
	
	public static void dispose(GL3 gl){
		if (initializedShader){
			//Reinit
			for (Shader s: allKnownShader) s.delete(gl);
			allKnownShader.clear();
		}
		initializedShader = false;
	}
	
	public static ArrayList<Shader> getAllShader(){
		return allKnownShader;
	}
	
	private static int createShaderProgram(String[] vertexShader, String[] fragmentShader, GL3 gl){
		return createShaderProgram(vertexShader, fragmentShader, gl, new int[0], new String[0], 150);
	}
	
	
	private static int createShaderProgram(String[] vertexShader, String[] fragmentShader, GL3 gl,
			int[] indices, String[] attribs, int version){
		if (indices.length != attribs.length){
			System.out.println("Number of  indices mismatches number of attributes");
			System.exit(1);
		}

		//Include shader version at the first position
		String[] vs = new String[vertexShader.length+1];
		String[] fs = new String[fragmentShader.length+1];
		vs[0] = "#version "+Integer.toString(version)+"\n";
		fs[0] = "#version "+Integer.toString(version)+"\n";
		
		for (int i=1; i<vs.length; i++) 
			vs[i] = vertexShader[i-1];
		for (int i=1; i<fs.length; i++) 
			fs[i] = fragmentShader[i-1];
		
		int v = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
		int f = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);
		int program = gl.glCreateProgram();
		gl.glShaderSource(v, vs.length, vs, null);
		gl.glCompileShader(v);
		gl.glShaderSource(f, fs.length, fs, null);
		gl.glCompileShader(f);
		gl.glAttachShader(program, v);
		gl.glAttachShader(program, f);
		
		for (int i=0; i<attribs.length; i++){
			gl.glBindAttribLocation(program, indices[i], attribs[i]);
		}
		
		gl.glBindFragDataLocation(program, FRAG_COLOR, "vFragColor");
		gl.glBindFragDataLocation(program, FRAG_NORMAL, "vFragNormal");
		gl.glBindFragDataLocation(program, FRAG_POSITION, "vFragPosition");
		gl.glBindFragDataLocation(program, FRAG_COLOR_ACCU, "vFragColorAccu");
		gl.glBindFragDataLocation(program, FRAG_ALPHA_ACCU, "vFragAlphaAccu");
		
		gl.glLinkProgram(program);
		gl.glValidateProgram(program);
		
		checkShaderLogInfo(gl,v,vs);
		checkShaderLogInfo(gl,f,fs);
		checkShaderLinkingLogInfo(gl, program, vs, fs);
		
		gl.glDeleteShader(v);
		gl.glDeleteShader(f);
		
		return program;
	}
	
	
	private static void checkShaderLogInfo(GL3 gl, int shaderID, String[] shaderCode) {
		IntBuffer info = Buffers.newDirectIntBuffer(1);
		gl.glGetShaderiv(shaderID, GL3.GL_COMPILE_STATUS, info);
		if (info.get(0) == GL3.GL_FALSE) {
			gl.glGetShaderiv(shaderID, GL3.GL_INFO_LOG_LENGTH, info);
			final int length = info.get(0);
			String out = null;
			if (length > 0) {
				final ByteBuffer infoLog = Buffers.newDirectByteBuffer(length);
				gl.glGetShaderInfoLog(shaderID, infoLog.limit(), info, infoLog);
				final byte[] infoBytes = new byte[length];
				infoLog.get(infoBytes);
				out = new String(infoBytes);
				
				System.out.println("ERROR: Shader Compilation Error\n");
				System.out.print(out);
				System.out.println();
				for (String s : shaderCode)
					System.out.print(s);
				System.exit(0);
			}
		}
	}
	
	private static void checkShaderLinkingLogInfo(GL3 gl, int programID, String[] vShaderCode, String[] fShaderCode) {
		IntBuffer info = Buffers.newDirectIntBuffer(1);
		gl.glGetProgramiv(programID, GL3.GL_LINK_STATUS, info);
		if (info.get(0) == GL3.GL_FALSE) {
			gl.glGetShaderiv(programID, GL3.GL_INFO_LOG_LENGTH, info);
			final int length = info.get(0);
			String out = null;
			if (length > 0) {
				final ByteBuffer infoLog = Buffers.newDirectByteBuffer(length);
				gl.glGetShaderInfoLog(programID, infoLog.limit(), info, infoLog);
				final byte[] infoBytes = new byte[length];
				infoLog.get(infoBytes);
				out = new String(infoBytes);
				
				System.out.println("ERROR: Shader Linking Error\n");
				System.out.print(out);
				System.out.println();
				for (String s : vShaderCode)
					System.out.print(s);
				for (String s : fShaderCode)
					System.out.print(s);
				System.exit(0);
			}
		}
	}
	
	public static int loadShader(File vertexShader, File fragmentShader, GL3 gl) throws IOException{
		String[] vsrc = {""};
		String[] fsrc = {""};
		BufferedReader brv = null;
		BufferedReader brf = null;
		
		try {
			brf = new BufferedReader(new FileReader(vertexShader));
			
			String line;
			while ((line=brf.readLine()) != null)
			  fsrc[0] += line + "\n";
			
			brv = new BufferedReader(new FileReader(vertexShader));
			
			while ((line=brv.readLine()) != null)
			  vsrc[0] += line + "\n";
		} catch (IOException ex){
			throw ex; 
		}
		finally {
			if (brv != null) brv.close();
			if (brf != null) brf.close();
		}
		
		return createShaderProgram(vsrc, fsrc, gl);
	}
	
	public static void deleteShader(int shaderProgram, GL3 gl){
		gl.glDeleteProgram(shaderProgram);
	}
	
	
	public enum BuiltInShader {
		//Shader for forward rendering
		VERTEX_ARRAY_COLOR_UNIFORM(
		        new String[]{uniformColorMacro, defaultVertexShader},
				new String[]{simpleColorFragmentShader},
				new int[]{ATTRIB_VERTEX}, 
				new String[]{"v"}),
		NO_LIGHTING(
				new String[]{defaultVertexShader},
				new String[]{simpleColorFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_COLOR}, 
				new String[]{"v", "Color"}),
		PLAIN_TEXTURED(
		        new String[]{uniformColorMacro, defaultVertexShader},
				new String[]{simpleTextureShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		ADS_UNIFORM_COLOR(
		        new String[]{uniformColorMacro, pplVertexShader},
                new String[]{pplFragmentwithADSShader},
                new int[]{ATTRIB_VERTEX, ATTRIB_NORMAL},
                new String[]{"v", "norm"}),
        ADS_VERTEX_COLOR(
                new String[]{pplVertexShader},
                new String[]{pplFragmentwithADSShader},
                new int[]{ATTRIB_VERTEX, ATTRIB_NORMAL, ATTRIB_COLOR},
                new String[]{"v", "norm", "Color"}),
		
		//Render from deferred buffers
		DEFERRED_ADS_RENDERING(
				new String[]{defaultVertexShader},
				new String[]{deferredADSFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		//Rendering into deferred buffer
		UNIFORM_COLOR_DEFERRED(
				new String[]{passThroughDeferredVertexShader},
				new String[]{toGBufferFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_NORMAL},
				new String[]{"v", "norm"}),
		VERTEX_COLOR_DEFERRED(
				new String[]{passThroughDeferredColorVertexShader},
				new String[]{toGBufferFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_NORMAL, ATTRIB_COLOR},
				new String[]{"v", "norm", "Color"}),		
		
		//Shader for billboarded spheres, depth coordinate approximated by polygon meshes
		SPHERE_INSTANCED_DEFERRED(
				new String[]{instancedMacro, translateBillboardVertexShaderDeferred}, 
				new String[]{billboardFragmentShaderDeferred}, 
				new int[]{ATTRIB_VERTEX, ATTRIB_COLOR, ATTRIB_VERTEX_OFFSET, ATTRIB_TEX0}, 
				new String[]{"p", "Color", "Move", "tex"}),
		SPHERE_DEFERRED(
				new String[]{translateBillboardVertexShaderDeferred},
				new String[]{billboardFragmentShaderDeferred}, 
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"p", "tex"}),
		//Shader for billboarded spheres, depth coordinate computed accurately by distance to center
		SPHERE_INSTANCED_DEFERRED_PERFECT(
				new String[]{instancedMacro, translateBillboardVertexShaderDeferred},
				new String[]{billboardFragmentShaderDeferredPerfectSphere}, 
				new int[]{ATTRIB_VERTEX, ATTRIB_COLOR, ATTRIB_VERTEX_OFFSET, ATTRIB_TEX0}, 
				new String[]{"p", "Color", "Move", "tex"}, 
				420),
		SPHERE_DEFERRED_PERFECT(
				new String[]{translateBillboardVertexShaderDeferred},
				new String[]{billboardFragmentShaderDeferredPerfectSphere}, 
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"p", "tex"}),
		
		//Different shaders for arrows
		ARROW(
				new String[]{arrowVertexShader},
				new String[]{pplFragmentwithADSShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_CUSTOM1, ATTRIB_CUSTOM0}, 
				new String[]{"p", "norm", "scalings"}),
		ARROW_DEFERRED(
				new String[]{arrowVertexShaderDeferred},
				new String[]{toGBufferFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_CUSTOM1, ATTRIB_CUSTOM0},
				new String[]{"p", "norm", "scalings"}),
		ARROW_INSTANCED_DEFERRED(
				new String[]{instancedMacro, arrowVertexShaderDeferred},
				new String[]{toGBufferFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_CUSTOM1, ATTRIB_CUSTOM0, ATTRIB_COLOR, ATTRIB_VERTEX_OFFSET, ATTRIB_CUSTOM2, ATTRIB_CUSTOM3}, 
				new String[]{"p", "norm", "scalings", "Color", "Origin", "Direction", "Dimensions"}),
		//Full screen processing shader
		ANAGLYPH_TEXTURED(
				new String[]{defaultVertexShader},
				new String[]{anaglyphFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		FXAA(
				new String[]{fxaaVertexShader},
				new String[]{fxaaFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		SSAO(
				new String[]{defaultVertexShader},
				new String[]{ssaoFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		BLUR(
				new String[]{defaultVertexShader},
				new String[]{blurShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		
		
		OID_COMPOSER(
				new String[]{defaultVertexShader},
				new String[]{oidTransparencyComposer},
				new int[]{ATTRIB_VERTEX, ATTRIB_TEX0},
				new String[]{"v", "Tex"}),
		OID_VERTEX_ARRAY_NO_LIGHTING(
				new String[]{uniformColorMacro, defaultVertexShader}, 
				new String[]{"#define NO_LIGHTING 1\n",oidTransparencyFragmentShader},
				new int[]{ATTRIB_VERTEX}, 
				new String[]{"v"}),
		OID_ADS_UNIFORM_COLOR(
				new String[]{uniformColorMacro, pplVertexShader},
				new String[]{oidTransparencyFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_NORMAL}, 
				new String[]{"v", "norm"}),
		OID_ADS_VERTEX_COLOR(
				new String[]{pplVertexShader},
				new String[]{oidTransparencyFragmentShader},
				new int[]{ATTRIB_VERTEX, ATTRIB_NORMAL, ATTRIB_COLOR},
				new String[]{"v", "norm", "Color"}),
		
		;
		private Shader s;
		
		private BuiltInShader(String[] vertexShader, String[] fragmentShader, int[] indices, String[] attribs){
			s = new Shader(vertexShader, fragmentShader, indices, attribs);
		}
		
		private BuiltInShader(String[] vertexShader, String[] fragmentShader, int[] indices, String[] attribs, int glslVersion){
			s = new Shader(vertexShader, fragmentShader, indices, attribs, glslVersion);
		}
		
		public Shader getShader(){
			return s;
		}
	}
}
