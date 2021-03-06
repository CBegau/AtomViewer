// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
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

package model;

import java.awt.event.InputEvent;
import java.util.*;

import common.CommonUtils;
import common.Tupel;
import common.Vec3;
import crystalStructures.CrystalStructure;

/**
 * This is the basic class for storing data per atom
 * @author Christoph Begau
 *
 */
public final class Atom extends Vec3 implements Pickable {
	public static final int IGNORED_GRAIN = Short.MAX_VALUE;
	public static final int DEFAULT_GRAIN = Short.MAX_VALUE-1;
	
	private int ID;
	
	private int atomNumber;
	//TODO pack the grain data into AtomData and create only if needed
	private short grain = DEFAULT_GRAIN;
	private byte type, element;
	
	/**
	 * Creates a new atom
	 * @param p The position of the atom in space
	 * @param num The numeric ID of the atom. Not used internally, thus must not be unique. 
	 * @param element The atoms element. Uses the value of {@link CrystalStructure#getNumberOfElements()}
	 * to distinguish physical elements from logical elements by computing the modulo of both values 
	 */
	public Atom(Vec3 p, int num, byte element) {
		this.x = p.x;
		this.y = p.y;
		this.z = p.z;

		this.atomNumber = num;
		this.element = element;
	}
	
	
	void setID(int ID){
		this.ID = ID;
	}
	
	/**
	 * The ID of this atom to access the data stored in an enclosing instance of AtomData
	 * associated with this atom
	 * Instead of calling this.getData(i), it is possible to call atomData.getDataValueArray(i).get(this.getID)
	 * This is an internal ID to get data stored in arrays and is not related to an atomic identification number
	 * as provided by {@link #getNumber()}. The ID has no meaning for the user. 
	 * @return
	 */
	public int getID(){
		return ID;
	}
	
	/**
	 * Set or update data values (imported from file or computed)
	 * @see model.Configuration#getDataColumnInfo(int)  
	 * @param value The new value to be stored
	 * @param index the index to be retrieved, must be smaller than the value returned by
	 * @param data the AtomData the Atom belongs to access data  
	 * {@link model.Configuration#getSizeDataColumns()} 
	 */
	public void setData(float value, int index, AtomData data){
		data.getDataArray(index).setQuick(ID, value);
	};
	
	/**
	 * Access to data values (imported from file or computed)
	 * To retrieve information on the values 
	 * @see model.Configuration#getDataColumnInfo(int)  
	 * @param index the index to be retrieved, must be smaller than the value returned by 
	 * {@link model.Configuration#getSizeDataColumns()}
	 * @param data the AtomData the Atom belongs to access data   
	 * @return the value of data at the given index
	 */
	public float getData(int index, AtomData data){
		return data.getDataArray(index).getQuick(ID);
	}
	
	/**
	 * The element of this atom
	 * @return
	 */
	public int getElement() {
		return element;
	}
	
	/**
	 * The grain the atom is located in 
	 * @return the index of the grain the atom is placed inside
	 */
	public int getGrain() {
		return grain;
	}
	
	/**
	 * No value > 32768 and <0 is allowed
	 * (32766 & 32767 are reserved for IGNORED_GRAIN and DEFAULT_GRAIN)
	 * @param grain
	 */
	public void setGrain(int grain) {
		//Backwards compatibility for old files
		if (grain == 65535) grain = IGNORED_GRAIN;
		else if (grain == 65534) grain = DEFAULT_GRAIN;
		
		assert(grain>=0 && grain<Short.MAX_VALUE);
		
		this.grain = (short)grain;
	}
	
	/**
	 * No value > 127 and <0 is allowed
	 * @param type
	 */
	public void setType(int type) {
		assert(type>=0 && type<Byte.MAX_VALUE);
		this.type = (byte)type;
	}
	
	/**
	 * The classification type of the atom
	 * @return
	 */
	public int getType() {
		return type;
	}
	
	/**
	 * The atom number as read from the input file
	 * @return
	 */
	public int getNumber(){
		return atomNumber;
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
	public Tupel<String,String> printMessage(InputEvent ev, AtomData data) {
		if (ev!=null && (ev.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK){
			return new Tupel<String, String>("Neighbors graph "+atomNumber, data.plotNeighborsGraph(this).toString());
		}
		
		ArrayList<String> keys = new ArrayList<String>();
		ArrayList<String> values = new ArrayList<String>();
		
		Vec3 offset = data.getBox().getOffset();
		keys.add("Nr"); values.add(Integer.toString(getNumber()));
		keys.add("Position"); values.add(this.addClone(offset).toString());
		keys.add("Structure"); values.add(data.getCrystalStructure().getNameForType(getType()));
		
		keys.add("element");
		if (data.getNameOfElement(getElement()).isEmpty())
			values.add(Integer.toString(getElement()));
		else values.add(Integer.toString(getElement())+" "+data.getNameOfElement(getElement()));
		
		if (getGrain() != DEFAULT_GRAIN){ 
			keys.add("Grain"); values.add(getGrain()==IGNORED_GRAIN?"None":Integer.toString(getGrain()));
		}
		
		
		RBV rbv = data.getRbvStorage().getRBV(this);
		if (rbv != null){
			CrystalRotationTools crt = null;
			
			if (getGrain() == DEFAULT_GRAIN)
				crt = data.getCrystalRotation();
			else crt = data.getGrains(getGrain()).getCystalRotationTools();
			Vec3 bv = crt.getInCrystalCoordinates(rbv.bv);
			Vec3 ld = crt.getInCrystalCoordinates(rbv.lineDirection);
			
			keys.add("Resultant Burgers vector"); values.add(bv.toString());
			keys.add("Resultant Burgers vector magnitude"); values.add(Float.toString(bv.getLength()));
			keys.add("Dislocation line tangent"); values.add(ld.toString());
			BurgersVector tbv = crt.rbvToBurgersVector(rbv);
			keys.add("True Burgers vector"); values.add(tbv.toString());
		}
		
		
		List<DataColumnInfo> dci = data.getDataColumnInfos();
		
		for (DataColumnInfo c : dci){
			if (!c.isVectorComponent()){
				int index1 = data.getDataColumnIndex(c);
				keys.add(c.getName()); values.add(CommonUtils.outputDecimalFormatter.format(getData(index1, data))+" "+c.getUnit());
			} else if (c.isFirstVectorComponent()){
				keys.add(c.getVectorName());
				int index1 = data.getDataColumnIndex(c.getVectorComponents()[0]);
				int index2 = data.getDataColumnIndex(c.getVectorComponents()[1]);
				int index3 = data.getDataColumnIndex(c.getVectorComponents()[2]);
				Vec3 vec = new Vec3(getData(index1, data), getData(index2, data), getData(index3, data));
				values.add(vec.toString()+(!c.getUnit().isEmpty()?" "+c.getUnit():""));
				keys.add("Magnitude of "+c.getVectorName());
				values.add(Float.toString(vec.getLength())+" "+c.getUnit());
			}
		}
		
		return new Tupel<String, String>("Atom "+atomNumber, 
				CommonUtils.buildHTMLTableForKeyValue(
						keys.toArray(new String[keys.size()]), values.toArray(new String[values.size()])));
	}
	
	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		return this.clone();
	}
}