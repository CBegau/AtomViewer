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

import java.awt.event.InputEvent;
import java.util.Collection;

import common.Vec3;

public interface Pickable {
	public Collection<?> getHighlightedObjects();
	public boolean isHighlightable();
	
	/**
	 * Get a string describing the state and properties of the object.
	 * It is possible to adjust the text if certain keys are pressed
	 * as indicated in the inputModifier.  
	 * @param ev The input event when the message is requested. May be null
	 * @param ev An instance of AtomData to which the object to print a message belongs to
	 * @return
	 */
	public String printMessage(InputEvent ev, AtomData data);
	
	/**
	 * Returns the centroid or a similiar property of the object that can be focuses on
	 * May return null if the operation is not supported by this kind of object 
	 * @return
	 */
	public Vec3 getCenterOfObject();
}
