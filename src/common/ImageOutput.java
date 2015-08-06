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
package common;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;

import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;

import gui.JMainWindow;
import gui.ViewerGLJPanel;
import model.AtomData;

public class ImageOutput {

	/**
	 * Writes a screenshot as a BufferedImage to a file and includes metadata
	 * The metadata includes the original data filename, the AtomViewer version and the Point of View
	 * string that describes the perspective to recreate the picture if needed.
	 * Metadata is only included in compatible formats (PNG, JPEG)
	 * @param bim The image to be saved
	 * @param format A format string, the format is used to detect a compatible image writer by
	 * {@link ImageIO#getImageWritersByFormatName(String)}
	 * @param file The file where the image is to be stored
	 * @param data the instance of which the screenshot is taken, the original data-filename is read from this
	 * @param viewer the instance which is used to take the screenshot, the POV is read from this
	 * @throws Exception
	 */
	public static void writeScreenshotFile(BufferedImage bim, String format, File file, AtomData data, ViewerGLJPanel viewer) throws Exception{
		ImageWriter writer = null;
		IIOMetadata metadata = null;
		ImageWriteParam writeParam = null;
		
		if (format.equals("png")){
			writer = ImageIO.getImageWritersByFormatName("png").next();
	
		    writeParam = writer.getDefaultWriteParam();
		    ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
	
		    //adding metadata
		    metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);
	
		    IIOMetadataNode textEntry = new IIOMetadataNode("tEXtEntry");
		    textEntry.setAttribute("keyword", "Software");
		    textEntry.setAttribute("value", "AtomViewer "+JMainWindow.VERSION+" (build "+JMainWindow.buildVersion+")");
	
		    IIOMetadataNode text = new IIOMetadataNode("tEXt");
		    text.appendChild(textEntry);
		    
		    textEntry = new IIOMetadataNode("tEXtEntry");
		    textEntry.setAttribute("keyword", "Title");
		    textEntry.setAttribute("value", data.getName());
		    text.appendChild(textEntry);
		    
		    float[] pov = viewer.getPov();
		    StringBuilder povString = new StringBuilder();
		    for (float p : pov){
		    	povString.append(p);
		    	povString.append(";");
		    }
		    
		    textEntry = new IIOMetadataNode("tEXtEntry");
		    textEntry.setAttribute("keyword", "Comment");
		    textEntry.setAttribute("value", "Point of view "+povString.toString());
		    text.appendChild(textEntry);
	
		    IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
		    root.appendChild(text);
	
		    metadata.mergeTree("javax_imageio_png_1.0", root);
		} else 
			writer = ImageIO.getImageWritersByFormatName(format).next();

	    //writing the data
	    FileOutputStream baos = new FileOutputStream(file);
	    ImageOutputStream stream = ImageIO.createImageOutputStream(baos);
	    writer.setOutput(stream);
	    writer.write(metadata, new IIOImage(bim, null, metadata), writeParam);		
	    stream.close();
	}
	
}
