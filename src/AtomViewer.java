import java.io.File;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import gui.JMainWindow;


public class AtomViewer {
	public static void main(String[] args) {
		//Read the build date from the manifest
		try {
			URL url = AtomViewer.class.getProtectionDomain().getCodeSource().getLocation();
			File f = new File(url.toURI());
			if (!f.isDirectory()){
				JarFile jar = new JarFile(f);
			    Manifest manifest = jar.getManifest();
			    Attributes attributes = manifest.getMainAttributes();
			    JMainWindow.buildVersion = " Build: "+attributes.getValue("Build-Date")+" Rev."+attributes.getValue("Revision");
			    jar.close();
			}
		} catch (Exception e){}
		
		JMainWindow.main(args);
	}
}
