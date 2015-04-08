package gui.glUtils;

import model.Pickable;
import common.Vec3;

public class RenderableObject<T extends Vec3 & Pickable>{
		public T object;
		public float[] color;
		public float size;
		
		public RenderableObject(T object, float[] color, float size) {
			this.object = object;
			this.color = color;
			this.size = size;
		}
		
		public void setValues(T object, float[] color, float size) {
			this.object = object;
			this.color = color;
			this.size = size;
		}
	}
