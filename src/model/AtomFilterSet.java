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

import java.util.ArrayList;

public class AtomFilterSet implements AtomFilter{

	ArrayList<AtomFilter> filter = new ArrayList<AtomFilter>();
	
	@Override
	public boolean accept(Atom a) {
		for (int i=0; i<filter.size(); i++)
			if (!filter.get(i).accept(a)) return false;
		return true;
	}
	
	public void addFilter(AtomFilter af){
		if (!filter.contains(af))
			filter.add(af);
	}
	
	public void removeFilter(AtomFilter af){
		filter.remove(af);
	}
	
	public void clear(){
		filter.clear();
	}
}
