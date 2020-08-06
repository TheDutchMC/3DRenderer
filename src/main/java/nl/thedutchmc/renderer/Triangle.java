package nl.thedutchmc.renderer;

import java.awt.Color;

public class Triangle {

	public Vector3d[] vectors = new Vector3d[3];
	public Color col;
	
	public Triangle(Vector3d... vectors) {
		this.vectors = vectors;
	}
}
