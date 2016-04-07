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

package model;

import common.ColorTable.ColorBarScheme;

public class DataColumnInfo {
	
	public enum Component{
		MASS("Mass"), E_POT("Potential energy"),
		STRESS_XX("Stress (xx)"), STRESS_YY("Stress (yy)"), STRESS_ZZ("Stress (zz)"), 
		STRESS_YZ("Stress (yz)"), STRESS_ZX("Stress (zx)"), STRESS_XY("Stress (xy)"),
		VELOCITY_X("Velocity (x)"), VELOCITY_Y("Velocity (y)"), VELOCITY_Z("Velocity (z)"),
		FORCE_X("Force (x)"), FORCE_Y("Force (y)"), FORCE_Z("Force (z)"), PARTICLE_RADIUS("Particle radius"),
		OTHER("other");
		
		private boolean visible = true;
		private String text = "";
		
		Component(String text, boolean visible){
			this(text);
			this.visible = visible;
		}
		
		Component(String text){
			this.text = text;
		}
		
		public boolean isVisibleOption(){
			return visible;
		}
		
		@Override
		public String toString() {
			return text;
		}
	}
	
	private float upperLimit, lowerLimit;
	private String name, id, unit, vectorName;
	
	private Component component = Component.OTHER;

	private boolean fixedRange = false;
	private boolean initialRange = false;
	private float scalingFactor = 1f;
	private boolean initialized = false;
	
	private ColorBarScheme scheme = null;
	
	private boolean isVectorComponent = false;
	private final DataColumnInfo[] vectorComponentOrder = new DataColumnInfo[4];

	public DataColumnInfo(String name, String id, String unit) {
		this.unit = unit;
		this.name = name;
		if (this.unit.equals("-")) this.unit = "";
		this.id = id;
		//remove all whitespace from name, unit and id
		this.unit = this.unit.replaceAll("\\s+","");
		this.name = this.name.trim();
		this.name = this.name.replaceAll("\\s+","_");
		this.id = this.id.replaceAll("\\s+","");
	}
	
	public DataColumnInfo(String name, String id, String unit, float lowerLimit, float upperLimit, 
			boolean fixedRange) {
		this(name, id, unit);
		if (fixedRange) initialized = true;
		this.fixedRange = fixedRange;
		this.upperLimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.initialRange = true;
	}
	
	public DataColumnInfo(String name, String id, String unit, float lowerLimit, float upperLimit, 
			boolean fixedRange, ColorBarScheme scheme) {
		this(name, id, unit, lowerLimit, upperLimit, fixedRange);
		this.scheme = scheme;
	}
	
	public float getLowerLimit() {
		return lowerLimit;
	}
	
	public float getUpperLimit() {
		return upperLimit;
	}
	
	public void setUpperLimit(float value){
		if (!fixedRange)
			upperLimit = value;
	}
	
	public void setLowerLimit(float value){
		if (!fixedRange)
			lowerLimit = value;
	}
	
	public String getName() {
		return name;
	}

	public String getId() {
		return id;
	}

	public String getUnit() {
		return unit;
	}
	
	public float getScalingFactor() {
		return scalingFactor;
	}

	public void setScalingFactor(float factor){
		this.scalingFactor = factor;
	}
	
	public boolean isFixedRange() {
		return fixedRange;
	}
	
	public boolean isInitialRangeGiven() {
		return initialRange;
	}
	
	public ColorBarScheme getScheme() {
		return scheme;
	}
	
	public Component getComponent() {
		return component;
	}
	
	public void setComponent(Component component) {
		this.component = component;
	}
	
	public void setAsFirstVectorComponent(DataColumnInfo cy, DataColumnInfo cz, DataColumnInfo abs, String name){
		this.setAsFirstVectorComponent(cy, cz, abs, name, true);
	}
	
	/**
	 * Creates a vector out of three DataColumnInfo.
	 * The vector components are initialized by assigning the second and third component to
	 * the first one
	 * @param cy the second vector component, may not be null
	 * @param cz the third vector component, may not be null
	 * @name the name of the vector, each component is automatically renamed into a pattern
	 * name_x, name_y and name_z 
	 */
	public void setAsFirstVectorComponent(DataColumnInfo cy, DataColumnInfo cz, DataColumnInfo abs, String name, boolean overrideNames){
		assert(cy != null && cz != null && this != cy && this != cz && cy != cz);
		this.vectorComponentOrder[0] = this;
		this.vectorComponentOrder[1] = cy;
		this.vectorComponentOrder[2] = cz;
		this.vectorComponentOrder[3] = abs;
		this.isVectorComponent = true;
		if (overrideNames) this.name = name+"_x";
		this.vectorName = name;
		
		cy.vectorComponentOrder[0] = this;
		cy.vectorComponentOrder[1] = cy;
		cy.vectorComponentOrder[2] = cz;
		cy.vectorComponentOrder[3] = abs;
		cy.isVectorComponent = true;
		if (overrideNames) cy.name = name+"_y";
		cy.vectorName = name;
		
		cz.vectorComponentOrder[0] = this;
		cz.vectorComponentOrder[1] = cy;
		cz.vectorComponentOrder[2] = cz;
		cz.vectorComponentOrder[3] = abs;
		cz.isVectorComponent = true;
		if (overrideNames) cz.name = name+"_z";
		cz.vectorName = name;
		
		abs.vectorComponentOrder[0] = this;
		abs.vectorComponentOrder[1] = cy;
		abs.vectorComponentOrder[2] = cz;
		abs.vectorComponentOrder[3] = abs;
		abs.isVectorComponent = true;
		if (overrideNames) abs.name = name+"_abs";
		abs.vectorName = name;
	}
	
	/**
	 * True if this data column is part of a vector
	 * @return True if this data column is part of a vector
	 */
	public boolean isVectorComponent(){
		return isVectorComponent;
	}
	
	/**
	 * True if this data column is part of a vector and is the first component of it
	 * @return True if this data column is part of a vector and is the first component of it
	 */
	public boolean isFirstVectorComponent(){
		return this.vectorComponentOrder[0] == this;
	}
	
	/**
	 * Return the components of a vector.
	 * This function should only be called if @link{isVectorComponent()} return true
	 * @return
	 */
	public DataColumnInfo[] getVectorComponents(){
		assert(isVectorComponent);
		return this.vectorComponentOrder;
	}
	
	public void findRange(AtomData atomData, boolean global){
		if (fixedRange) return;
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		
		if (global){
			//rewind to beginning
			while (atomData.getPrevious()!=null)
				atomData = atomData.getPrevious();
			
			while (atomData!=null){
				int column = atomData.getDataColumnIndex(this);
				float[] f = atomData.getDataArray(column).getData();
				if (column != -1){
					for (float v : f){
						if (v < min) min = v;
						if (v > max) max = v;
					}
				}
				atomData = atomData.getNext();
			}
		} else {
			int column = atomData.getDataColumnIndex(this);
			if (column != -1){
				float[] f = atomData.getDataArray(column).getData();
				for (float v : f){
					if (v < min) min = v;
					if (v > max) max = v;
				}
			}
		}
		lowerLimit = Float.isInfinite(min) ? lowerLimit : min;
		upperLimit = Float.isInfinite(max) ? upperLimit : max;
		initialized = true;
	}
	
	public boolean isInitialized(){
		return initialized;
	}
	
	@Override
	public String toString(){
		return name;
	}
	
	public String getVectorName(){
		return vectorName;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DataColumnInfo)) return false;
		DataColumnInfo c = (DataColumnInfo)obj;
		return c.id.equals(this.id);
	}
	
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	
	/**
	 * Simple wrapper calls to display the vector name if the vector is needed in comboBoxes...
	 */
	public static class VectorDataColumnInfo{
		DataColumnInfo dci;
		public VectorDataColumnInfo(DataColumnInfo dci) {
			this.dci = dci;
		}
		
		public DataColumnInfo getDci() {
			return dci;
		}
		
		@Override
		public String toString() {
			return dci.getVectorName();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj instanceof DataColumnInfo){
				DataColumnInfo d = (DataColumnInfo)obj;
				return dci.equals(d);
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return dci.id.hashCode()+1;
		}
	}
}