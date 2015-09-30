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

package model;

import java.awt.event.InputEvent;
import java.util.Collection;

import common.Tupel;
import common.Vec3;

/**
 * A simple implementation of Pickable.
 * The text output during picking and the center will be identical to the arguments given
 * during construction or provided by {@link #setCenter(Vec3)} and {@link SimplePickable#setText(String)}.
 * Highlighting is not supported
 */
public class SimplePickable implements Pickable {
	
	private Vec3 center = new Vec3();
	private String text ="";
	private String detailText ="";
	
	public SimplePickable() {}
	
	public SimplePickable(String text, String detailText, Vec3 center) {
		this.center = center;
		this.text = text;
		this.detailText = detailText;
	}
	
	public void setText(String text, String detailText) {
		this.text = text;
		this.detailText = detailText;
	}
	
	public void setCenter(Vec3 center) {
		this.center = center.clone();
	}
	
	@Override
	public boolean isHighlightable() {
		return false;
	}
	
	@Override
	public Collection<?> getHighlightedObjects() {
		return null;
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		return center;
	}
	
	@Override
	public Tupel<String, String> printMessage(InputEvent ev, AtomData data) {
		return new Tupel<String, String>(text, detailText);
	}
}
