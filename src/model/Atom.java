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
import common.Vec3;
import crystalStructures.CrystalStructure;

/**
 * This is the basic class for storing data per atom
 * @author Christoph Begau
 *
 */
public class Atom extends Vec3 implements Pickable {
	public static final int IGNORED_GRAIN = Short.MAX_VALUE;
	public static final int DEFAULT_GRAIN = Short.MAX_VALUE-1;
	
	private RBV rbv;
	private float[] dataValues;
	private int atomNumber;
	private short grain = DEFAULT_GRAIN;
	private byte type, element;
	
	/**
	 * Creates a new atom
	 * @param p The position of the atom in space
	 * @param type its type, required for many steps in the postprocessing routines.
	 * May be a default value if it is later characterized and set by {@link #setType(int)} 
	 * @param num The numeric ID of the atom. Not used internally, thus must not be unique. 
	 * @param element The atoms element. Uses the value of {@link CrystalStructure#getNumberOfElements()}
	 * to distinguish physical elements from logical elements by computing the modulo of both values 
	 */
	public Atom(Vec3 p, byte type, int num, byte element) {
		this.x = p.x;
		this.y = p.y;
		this.z = p.z;

		this.type = type;
		this.atomNumber = num;
		this.element = element;
		
		if (ImportConfiguration.getInstance().getDataColumns().size() != 0)
			dataValues = new float[ImportConfiguration.getInstance().getDataColumns().size()];
	}
	
	/**
	 * The resultant burgers vector associated with this atom
	 * @return the RBV or null no value exists
	 */
	public RBV getRBV() {
		return rbv;
	}
	
	
	/**
	 * Increase the size of the array dataValues by n values
	 * New entries are initialized with 0f.  
	 * @param n 
	 */
	void extendDataValuesFields(int n){
		assert (n>=0);
		if (dataValues == null)
			dataValues = new float[n];
		else
			dataValues = Arrays.copyOf(dataValues, dataValues.length+n);
	}
	
	/**
	 * Remove the entry in dataValue at the given index
	 * All following entries are shifted by one
	 * @param index
	 */
	void deleteDataValueField(int index){
		assert (index < dataValues.length);
		if (dataValues.length == 1){
			dataValues = null;
			return;
		}
		//Create a copy of the array not containing the value at the index
		float[] d = new float[dataValues.length-1];
	    System.arraycopy(dataValues, 0, d, 0, index );
	    System.arraycopy(dataValues, index+1, d, index, dataValues.length - index-1);
		dataValues = d;
	}
	
	
	/**
	 * Sets the values for the resultant Burgers vector and the line direction to this atom
	 * If one of these values is null the existing reference is nulled
	 * @param rbv The resultant Burgers vector
	 * @param lineDirection the lineDirection, should be a unit vector.
	 * If it is the null-vector, no reference to a RBV is created 
	 */
	public void setRBV( Vec3 rbv, Vec3 lineDirection ){
		if (rbv == null || lineDirection == null){
			this.rbv = null;
		} else {
			if (lineDirection.dot(lineDirection)>0)  
				this.rbv = new model.RBV(rbv, lineDirection);
		}
	}
	
	/**
	 * Set or update data values (imported from file or computed)
	 * @see model.Configuration#getDataColumnInfo(int)  
	 * @param value The new value to be stored
	 * @param index the index to be retrieved, must be smaller than the value returned by 
	 * {@link model.Configuration#getSizeDataColumns()} 
	 */
	public void setData(float value, int index){
		assert(index<dataValues.length);
		dataValues[index]=value;
	};
	
	/**
	 * Access to data values (imported from file or computed)
	 * To retrieve information on the values 
	 * @see model.Configuration#getDataColumnInfo(int)  
	 * @param index the index to be retrieved, must be smaller than the value returned by 
	 * {@link model.Configuration#getSizeDataColumns()} 
	 * @return the value of data at the given index
	 */
	public float getData(int index){
		assert(index<dataValues.length);
		return dataValues[index];
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
		if (grain == IGNORED_GRAIN) rbv = null;
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
	public String printMessage(InputEvent ev, AtomData data) {
		StringBuilder sb = new StringBuilder();
		
		Vec3 offset = data.getBox().getOffset();
		sb.append(String.format("Nr=%d, ", getNumber()));
		sb.append(String.format("xyz=( %.4f, %.4f, %.4f ),", x+offset.x, y+offset.y, z+offset.z));
		sb.append(String.format(" (%s),", data.getCrystalStructure().getNameForType(getType())));
		if (data.getNameOfElement(getElement()).isEmpty())
			sb.append(String.format(" Element=%d,", getElement()));
		else sb.append(String.format(" Element=%d (%s),", getElement(), data.getNameOfElement(getElement())));
		
		if (getGrain() != DEFAULT_GRAIN) 
			sb.append(String.format(" Grain=%s", getGrain()==IGNORED_GRAIN?"None":Integer.toString(getGrain())));
		if (getRBV()!=null) {
			CrystalRotationTools crt = null;
			
			if (getGrain() == DEFAULT_GRAIN)
				crt = data.getCrystalRotation();
			else crt = data.getGrains(getGrain()).getCystalRotationTools();
			
			Vec3 bv = crt.getInCrystalCoordinates(this.getRBV().bv);
			sb.append(String.format(" RBV=[ %.3f | %.3f | %.3f ],", bv.x, bv.y, bv.z));
			sb.append(String.format(" Lenght=%.3f", this.getRBV().bv.getLength()));
			Vec3 ld = crt.getInCrystalCoordinates(this.getRBV().lineDirection);
			sb.append(String.format(" LineDir=[ %.3f | %.3f | %.3f ],", ld.x, ld.y, ld.z));
			
			BurgersVector tbv = crt.rbvToBurgersVector(this.getRBV());
			sb.append(" TBV="+tbv.toString());
		}
		
		List<DataColumnInfo> dci = data.getDataColumnInfos(); 
		for (int i=0; i < dci.size(); i++){
			DataColumnInfo c = dci.get(i);
			sb.append(String.format(" %s=%s%s", c.getName(), 
					CommonUtils.outputDecimalFormatter.format(getData(i)),
					c.getUnit()));	
		}
		
		if (ev!=null && (ev.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK){
			sb.append("\n");
			sb.append(data.plotNeighborsGraph(this));
		}
		return sb.toString();
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