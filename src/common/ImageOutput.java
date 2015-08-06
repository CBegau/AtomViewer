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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
		
		writer = ImageIO.getImageWritersByFormatName(format).next();
		if (writer == null)
			throw new Exception(String.format("No ImageWriter found for format %s", format));
		
		//Get the metadata object for supported formats
		if (format.equals("png") || format.equals("jpg") || format.equals("jpeg")){
		    writeParam = writer.getDefaultWriteParam();
		    ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
	
		    //adding metadata
		    metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);
		}
		
		//Write metadata if possible
		if (metadata != null){
			 //POV as a text string
		    float[] pov = viewer.getPov();
		    StringBuilder povString = new StringBuilder();
		    for (float p : pov){
		    	povString.append(p);
		    	povString.append(";");
		    }
		    
		    if (format.equals("png")){
		    	IIOMetadataNode root = new IIOMetadataNode(metadata.getNativeMetadataFormatName());
			    //Write software and version
			    IIOMetadataNode textEntry = new IIOMetadataNode("tEXtEntry");
			    textEntry.setAttribute("keyword", "Software");
			    textEntry.setAttribute("value", "AtomViewer "+JMainWindow.VERSION+" (build "+JMainWindow.buildVersion+")");
		
			    IIOMetadataNode text = new IIOMetadataNode("tEXt");
			    text.appendChild(textEntry);
			    //The original data filename or identifier
			    textEntry = new IIOMetadataNode("tEXtEntry");
			    textEntry.setAttribute("keyword", "Title");
			    textEntry.setAttribute("value", data.getName());
			    text.appendChild(textEntry);
			   
			    textEntry = new IIOMetadataNode("tEXtEntry");
			    textEntry.setAttribute("keyword", "Comment");
			    textEntry.setAttribute("value", "Point of view "+povString.toString());
			    text.appendChild(textEntry);
		
			    root.appendChild(text);
			    metadata.mergeTree(metadata.getNativeMetadataFormatName(), root);
			} 
		    else if (format.equals("jpg") || format.equals("jpeg")){
		    	//Jpeg 
		    	StringBuilder sb = new StringBuilder();
		    	sb.append(String.format("created by AtomViewer %s (build %s)\n", JMainWindow.VERSION, JMainWindow.buildVersion));
		    	sb.append(String.format("Image created from file: %s\n", data.getName()));
		    	sb.append(String.format("Point of view %s", povString.toString()));
		    	
		    	Element tree = (Element) metadata.getAsTree(metadata.getNativeMetadataFormatName());
		    	NodeList comNL = tree.getElementsByTagName("com");
		    	IIOMetadataNode comNode;
		    	if (comNL.getLength() == 0) {
		    	    comNode = new IIOMetadataNode("com");
		    	    Node markerSequenceNode = tree.getElementsByTagName("markerSequence").item(0);
		    	    markerSequenceNode.insertBefore(comNode,markerSequenceNode.getFirstChild());
		    	} else {
		    	    comNode = (IIOMetadataNode) comNL.item(0);
		    	}
		    	comNode.setUserObject((sb.toString()).getBytes("ISO-8859-1"));
		    	metadata.setFromTree(metadata.getNativeMetadataFormatName(), tree);
			}
		}
		
	    //save the image including metadata if available
	    FileOutputStream baos = new FileOutputStream(file);
	    ImageOutputStream stream = ImageIO.createImageOutputStream(baos);
	    writer.setOutput(stream);
	    writer.write(metadata, new IIOImage(bim, null, metadata), writeParam);
	    writer.dispose();
	    stream.close();
	}
	
}
