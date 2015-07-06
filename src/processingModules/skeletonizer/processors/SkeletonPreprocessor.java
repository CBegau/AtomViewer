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

package processingModules.skeletonizer.processors;

import processingModules.skeletonizer.Skeletonizer;

/**
 * An instance of  SkeletonPreprocessor is supposed to modify the initial mesh
 * before the skeleton algorithm is applied
 * @author begauc9f
 */
public interface SkeletonPreprocessor {
	/**
	 * Perform a preprocessing operation
	 * @param skel
	 */
	public void preProcess(Skeletonizer skel);
}
