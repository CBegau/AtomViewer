// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2016  ICAMS, Ruhr-Universität Bochum
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

package gui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import model.AtomData;
import model.Configuration;

public class KeyActionCollection {
	public static Action getActionNextData(){
		return new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent e) {
				if (Configuration.getCurrentAtomData().getNext()!=null){
					AtomData next = Configuration.getCurrentAtomData().getNext();
					Configuration.setCurrentAtomData(next, true, false);
				}
			}
		};
	}
	
	public static Action getActionPreviousData(){
		return new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent e) {
				if (Configuration.getCurrentAtomData().getPrevious() != null) {
					AtomData previous = Configuration.getCurrentAtomData().getPrevious();
					Configuration.setCurrentAtomData(previous, true, false);
				}
			}
		};
	}
	
	public static Action getActionFirstData(){
		return new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent e) {
				 AtomData first = Configuration.getCurrentAtomData();
				 while (first.getPrevious() != null)
					 first = first.getPrevious();
				 Configuration.setCurrentAtomData(first, true, false);
			}
		};
	}
	
	public static Action getActionLastData(){
		return new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent e) {
				AtomData last = Configuration.getCurrentAtomData();
				while (last.getNext() != null)
					last = last.getNext();
				Configuration.setCurrentAtomData(last, true, false);
			}
		};
	}
}
