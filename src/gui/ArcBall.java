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

package gui;

import java.awt.*;

import common.Vec3;
/**
* The Arcball rotation interface sources were created by 
* Pepijn Van Eeckhoudt.
* 
* Original ArcBall C code from Ken Shoemake, Graphics Gems IV, 1993. 
*
* Modified by C.Begau
**/
class ArcBall {
	private static final float EPSILON = 1.0e-5f;
	private static final float SPHERESIZE = 0.8f;

	private Vec3 startPoint;
	private boolean startVecInsideSphere = false;
	private float widthScaling, heightScaling;

	public ArcBall(int width, int height) {
		startPoint = new Vec3();
		setSize(width, height);
	}

	public void setSize(int width, int height) {		
		this.widthScaling = 1.0f / ((width - 1.0f) * 0.5f);
		this.heightScaling = 1.0f / ((height - 1.0f) * 0.5f);
	}

	public void setRotationStartingPoint(Point point) {
		Vec3 scaledPointVec = new Vec3();

		//scaling coordinates
		scaledPointVec.x = (point.x * this.widthScaling) - 1.0f;
		scaledPointVec.y = 1.0f - (point.y * this.heightScaling);

		float length = scaledPointVec.getLength();

		//Startpoint inside or outside the ball (sphere)?
		if (length > SPHERESIZE) {
			float scaleFactor = (float) (SPHERESIZE / Math.sqrt(length));
			startPoint.x = scaledPointVec.x * scaleFactor;
			startPoint.y = scaledPointVec.y * scaleFactor;
			startPoint.z = 0.0f;
			startVecInsideSphere = false;
		} else {
			startPoint.x = scaledPointVec.x;
			startPoint.y = scaledPointVec.y;
			startPoint.z = (float)Math.sqrt(SPHERESIZE - length);
			startVecInsideSphere = true;
		}
	}

	/**
	 * Computes the rotation on the acrBall from the startpoint of rotation to a current point
	 * @param point Current point of the cursor on screen
	 * @return the quaternion as a float[4] or null if not on screen of rotation matrix is the identity
	 */
	public float[] drag(Point point) {
		Vec3 scaledPointVec = new Vec3();

		//scaling coordinates
		scaledPointVec.x = (point.x * this.widthScaling) - 1.0f;
		scaledPointVec.y = 1.0f - (point.y * this.heightScaling);
		scaledPointVec.z = 0f;
		
		float length = scaledPointVec.getLength();
		Vec3 endVec = new Vec3();
		
		if (length > SPHERESIZE) {
			float scaleFactor = (float) (SPHERESIZE / Math.sqrt(length));
			endVec.x = scaledPointVec.x * scaleFactor;
			endVec.y = scaledPointVec.y * scaleFactor;
			endVec.z = 0.0f;
		} else {
			endVec.x = scaledPointVec.x;
			endVec.y = scaledPointVec.y;
			endVec.z = (float)Math.sqrt(SPHERESIZE - length);
		}
		if (!startVecInsideSphere) endVec.z = 0f;

		// Return the quaternion equivalent to the rotation
		float[] quarternionRot = new float[4];
		Vec3 normVec = startPoint.cross(endVec);

		if (normVec.getLength() > EPSILON) {
			quarternionRot[0] = normVec.x;
			quarternionRot[1] = normVec.y;
			quarternionRot[2] = normVec.z;
			quarternionRot[3] = startPoint.dot(endVec);
		} else return null;
		return quarternionRot;
	}
}