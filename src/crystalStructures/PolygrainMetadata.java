// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2013  ICAMS, Ruhr-Universität Bochum
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

package crystalStructures;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Hashtable;
import java.util.Map;

import common.Vec3;

import model.io.MDFileLoader.ImportDataContainer;

public class PolygrainMetadata{
	Hashtable<Integer, Vec3[]> grainOrientation = new Hashtable<Integer, Vec3[]>();
	Hashtable<Integer, MeshData> meshes = new Hashtable<Integer, MeshData>();
	Hashtable<Integer, Integer> numAtoms = new Hashtable<Integer, Integer>(); 
	
	static class MeshData{
		int[] triangles;
		float[] vertices;
	}
	
	
	public static boolean processMetadataLine(String s, Map<String, Object> metaContainer,
			LineNumberReader lnr, ImportDataContainer idc) throws IOException{
		if (s.startsWith("##grain")){
			PolygrainMetadata meta;
			Object o = metaContainer.get("grain");
			if (o!=null)
				meta = (PolygrainMetadata)o;
			else {
				meta = new PolygrainMetadata();
				metaContainer.put("grain", meta);
			}
			
			String[] p = s.split(" +");
			Integer num = Integer.parseInt(p[1]);
			
			Vec3[] r = new Vec3[3];
			r[0] = new Vec3(); r[1] = new Vec3(); r[2] = new Vec3();   
			r[0].x = Float.parseFloat(p[2]); r[0].y = Float.parseFloat(p[3]); r[0].z = Float.parseFloat(p[4]);
			r[1].x = Float.parseFloat(p[5]); r[1].y = Float.parseFloat(p[6]); r[1].z = Float.parseFloat(p[7]);
			r[2].x = Float.parseFloat(p[8]); r[2].y = Float.parseFloat(p[9]); r[2].z = Float.parseFloat(p[10]);
			
			meta.grainOrientation.put(num, r);
			return true;
		}
		if (s.startsWith("##atomsInGrain")){
			PolygrainMetadata meta;
			Object o = metaContainer.get("grain");
			if (o!=null)
				meta = (PolygrainMetadata)o;
			else {
				meta = new PolygrainMetadata();
				metaContainer.put("grain", meta);
			}
			
			String[] p = s.split(" +");
			Integer grain = Integer.parseInt(p[1]);
			Integer num = Integer.parseInt(p[2]);
			meta.numAtoms.put(grain, num);
			return true;
		}
		if (s.startsWith("##mesh")){
			PolygrainMetadata meta;
			Object o = metaContainer.get("grain");
			if (o!=null)
				meta = (PolygrainMetadata)o;
			else {
				meta = new PolygrainMetadata();
				metaContainer.put("grain", meta);
			}
			
			String[] p = s.split(" +");
			Integer num = Integer.parseInt(p[1]);
			
			s = lnr.readLine();
			MeshData md = new MeshData();
			
			if (s.startsWith("###triangles")){
				p = s.split(" +");
				int size = Integer.parseInt(p[1]);
				md.triangles = new int[size];
				
				for (int i = 0; i < size; i++){
					md.triangles[i] = Integer.parseInt(p[i+2]);
				}
			}
			s = lnr.readLine();
			if (s.startsWith("###vertices")){
				p = s.split(" +");
				int size = Integer.parseInt(p[1]);
				md.vertices = new float[size];
				
				for (int i = 0; i < size; i++){
					md.vertices[i] = Float.parseFloat(p[i+2]);
				}
			}
			
			meta.meshes.put(num, md);
			return true;
		}
		return false;
	}
}