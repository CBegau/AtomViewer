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

public class FilterSet<T> implements Filter<T>{
    ArrayList<Filter<T>> filter = new ArrayList<Filter<T>>();
    
    @Override
    public boolean accept(T a) {
        for (int i=0; i<filter.size(); i++)
            if (!filter.get(i).accept(a)) return false;
        return true;
    }
    
    public FilterSet<T> addFilter(Filter<T> af){
        if (af != null && !filter.contains(af))
            filter.add(af);
        return this;
    }
    
    public void removeFilter(Filter<T> af){
        filter.remove(af);
    }
    
    public void clear(){
        filter.clear();
    }
}