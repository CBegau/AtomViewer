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
		MASS("Mass"), VELOCITY_X("Velocity (x)"), VELOCITY_Y("Velocity (y)"), VELOCITY_Z("Velocity (z)"),
		STRESS_XX("Stress (xx)"), STRESS_YY("Stress (yy)"), STRESS_ZZ("Stress (zz)"), 
		STRESS_YZ("Stress (yz)"), STRESS_ZX("Stress (zx)"), STRESS_XY("Stress (xy)"),
		FORCE_X("Force (x)"), FORCE_Y("Force (y)"), FORCE_Z("Force (z)"),
		UNDEFINED("undefined");
		
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
	
	private float upperlimit, lowerLimit;
	private String name, id, unit;
	private int column;
	
	private Component component = Component.UNDEFINED;
	
	/**
	 * Some columns have a special meaning as the martensite variants of lattice rotation angles
	 * They are stored in the same data array as user imported values, but are not show explicitly in dialogs 
	 */
	private boolean specialColoumn = false;
	private boolean fixedRange = false;
	private boolean initialRange = false;
	private float scalingFactor = 1f;
	
	private ColorBarScheme scheme = null;
	
	private boolean deriveAverageValues = false;
	private float averagingRadius = 0f;

	public DataColumnInfo(String name, String id, String unit, float scalingFactor) {
		this.unit = unit;
		if (this.unit.equals("-")) this.unit = "";
		this.name = name.replace(" ", "");
		this.id = id;
		this.scalingFactor = scalingFactor;
		//remove all whitespace from name, unit and id
		this.unit.replaceAll("\\s","");
		this.name.replaceAll("\\s","");
		this.id.replaceAll("\\s","");
	}
	
	public DataColumnInfo(String name, String id, String unit, boolean special, float scalingFactor) {
		this(name, id, unit, scalingFactor);
		this.specialColoumn = special;
	}
	
	public DataColumnInfo(String name, String id, String unit, float scalingFactor, float averagingRadius){
		this(name, id, unit, scalingFactor);
		if (averagingRadius>0f){
			this.averagingRadius = averagingRadius;
			this.deriveAverageValues = true;
		}
	}
	
	public DataColumnInfo(String name, String id, String unit, float lowerLimit, float upperLimit, 
			boolean fixedRange, float scalingFactor) {
		this(name, id, unit, scalingFactor);
		this.fixedRange = fixedRange;
		this.upperlimit = upperLimit;
		this.lowerLimit = lowerLimit;
		this.initialRange = true;
	}
	
	public DataColumnInfo(String name, String id, String unit, float lowerLimit, float upperLimit, 
			boolean fixedRange, float scalingFactor, ColorBarScheme scheme) {
		this(name, id, unit, lowerLimit, upperLimit, fixedRange, scalingFactor);
		this.scheme = scheme;
	}
	
	void setColumn(int column){
		this.column = column;
	}
	
	public float getLowerLimit() {
		return lowerLimit;
	}
	
	public float getUpperLimit() {
		return upperlimit;
	}
	
	public void setUpperLimit(float value){
		if (!fixedRange)
			upperlimit = value;
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
	
	public int getColumn() {
		return column;
	}
	
	public float getScalingFactor() {
		return scalingFactor;
	}

	public boolean isSpecialColoumn() {
		return specialColoumn;
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
	
	public boolean isValueToBeSpatiallyAveraged() {
		return deriveAverageValues;
	}
	
	public float getSpatiallyAveragingRadius() {
		return averagingRadius;
	}
	
	public void findRange(boolean global){
		if (fixedRange) return;
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		
		AtomData atomData = Configuration.getCurrentAtomData();
		if (global){
			//rewind to beginning
			while (atomData.getPrevious()!=null)
				atomData = atomData.getPrevious();
			
			while (atomData!=null){
				for (int i=0; i < atomData.getAtoms().size(); i++){
					float v = atomData.getAtoms().get(i).getData(column);
					if (v < min) min = v;
					if (v > max) max = v;
				}
				atomData = atomData.getNext();
			}
		} else {
			for (int i=0; i < atomData.getAtoms().size(); i++){
				float v = atomData.getAtoms().get(i).getData(column);
				if (v < min) min = v;
				if (v > max) max = v;
			}
		}
		lowerLimit = min;
		upperlimit = max;
	}
	
	@Override
	public String toString(){
		return name;
	}
}