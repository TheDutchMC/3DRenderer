package nl.thedutchmc.renderer;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;

public class Display extends Canvas implements Runnable {
	private static final long serialVersionUID = 1L;

	private Thread thread;
	private JFrame frame;
	private static String title = "Renderer";
	public static final int WIDTH = 1200;
	public static final int HEIGHT = 900;
	private static boolean running = false;
	
	private static final int FRAME_RATE = 60;
	
	private float fTheta;
	private Mesh meshCube;
	Matrix4x4 matProj;
	Vector3d vCamera;
	
	public Display() {
		this.frame = new JFrame();
		
		Dimension size = new Dimension(WIDTH, HEIGHT);
		this.setPreferredSize(size);
		
		this.frame.setTitle(title);
		this.frame.add(this);
		this.frame.pack();
		this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.frame.setLocationRelativeTo(null);
		this.frame.setResizable(false);
		this.frame.getContentPane().setBackground(Color.black);
		this.frame.setVisible(true);
	}
	
	public synchronized void start() {
		
		if(running == true) return;
		running = true;
		
		//Create the mesh from an object file
		meshCube = new Mesh("teapot.obj");
		
		//variables needed for the projection matrix
		float fNear = 0.1f;
		float fFar = 1000.0f;
		float fFov = 90.0f;
		float fAspectRatio = (float) HEIGHT / (float) WIDTH;
		float fFovRad = 1.0f / (float) Math.tan((double) fFov * 0.5 / 180.0 * Math.PI);
		
		//The projection matrix
		matProj = new Matrix4x4();
		
		matProj.m[0][0] = fAspectRatio * fFovRad;
		matProj.m[1][1] = fFovRad;
		matProj.m[2][2] = fFar / (fFar - fNear);
		matProj.m[3][2] = (-fFar * fNear) / (fFar - fNear);
		matProj.m[2][3] = 1.0f;
		matProj.m[3][3] = 0.0f;
		
		//Camera, from where the viewer observers, locked to (0, 0, 0) for now
		vCamera = new Vector3d(0, 0, 0);
		
		//Start the display thread
		this.thread = new Thread(this, "DisplayThread");
		this.thread.start();
	}
	
	public synchronized void stop() {
		
		if(running == false) return;
		running = false;
		
		try {
			this.thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		
		//Time task to make it a repeating task.
		TimerTask drawTask = new TimerTask() {
			
			float index = 0.0f;
			
			public void run() {
				index += 0.1f;
				update((float) index);
				
			}
		};
		
		
		Timer drawTimer = new Timer("drawTimer");
		long delay = 10L;
		long period = FRAME_RATE;
		drawTimer.scheduleAtFixedRate(drawTask, delay, period);
		
		stop();
	}
	
	private void update(float fTimeElapsed) {
		
		//Get the buffered strategy, if its null, create it and return;
		BufferStrategy bs = this.getBufferStrategy();
		if(bs == null) {
			this.createBufferStrategy(3);
			return;
		}
		
		//Get the graphics, to which we draw
		Graphics g = bs.getDrawGraphics();
		
		//Clear the screen
		g.clearRect(0, 0, WIDTH * 2, HEIGHT * 2);

		//Calculate Theta
		fTheta = 1f * fTimeElapsed;

		//Create the two matrixes used for rotation
		Matrix4x4 matRotZ = new Matrix4x4(), 
				matRotX = new Matrix4x4();
		
		//Rotation on Z axis
		matRotZ.m[0][0] = (float) Math.cos((double) fTheta);
		matRotZ.m[0][1] = (float) Math.sin((double) fTheta);
		matRotZ.m[1][0] = (float) -Math.sin((double) fTheta);
		matRotZ.m[1][1] = (float) Math.cos((double) fTheta);
		matRotZ.m[2][2] = 1;
		matRotZ.m[3][3] = 1;
		
		//Rotation on X axis
		matRotX.m[0][0] = 1;
		matRotX.m[1][1] = (float) Math.cos((double) fTheta * 0.5f);
		matRotX.m[1][2] = (float) Math.sin((double) fTheta * 0.5f);
		matRotX.m[2][1] = (float) -Math.sin((double) fTheta * 0.5f);
		matRotX.m[2][2] = (float) Math.cos((double) fTheta * 0.5f);
		matRotX.m[3][3] = 1;
		
		List<Triangle> trisToRaster = new ArrayList<>();
		
		//Draw the triangles
		for(Triangle tri : meshCube.tris) {
			Triangle triProjected = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0)), 
					triTranslated = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0)),
					triRotatedZ = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0)),
					triRotatedZX = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));
			
			//Rotate in Z-axis
			multiplyMatrixVector(tri.vectors[0], triRotatedZ.vectors[0], matRotZ);
			multiplyMatrixVector(tri.vectors[1], triRotatedZ.vectors[1], matRotZ);
			multiplyMatrixVector(tri.vectors[2], triRotatedZ.vectors[2], matRotZ);
			
			//Rotate in X-axis
			multiplyMatrixVector(triRotatedZ.vectors[0], triRotatedZX.vectors[0], matRotX);
		    multiplyMatrixVector(triRotatedZ.vectors[1], triRotatedZX.vectors[1], matRotX);
			multiplyMatrixVector(triRotatedZ.vectors[2], triRotatedZX.vectors[2], matRotX);
			
			//offset into the screen
			triTranslated = triRotatedZX;
			triTranslated.vectors[0].z = triRotatedZX.vectors[0].z + 8.0f;
			triTranslated.vectors[1].z = triRotatedZX.vectors[1].z + 8.0f;
			triTranslated.vectors[2].z = triRotatedZX.vectors[2].z + 8.0f;
			
			//Get the normals
			Vector3d normal = new Vector3d(0,0,0), line1 = new Vector3d(0,0,0), line2 = new Vector3d(0,0,0);
			line1.x = triTranslated.vectors[1].x - triTranslated.vectors[0].x;
			line1.y = triTranslated.vectors[1].y - triTranslated.vectors[0].y;
			line1.z = triTranslated.vectors[1].z - triTranslated.vectors[0].z;
			
			line2.x = triTranslated.vectors[2].x - triTranslated.vectors[0].x;
			line2.y = triTranslated.vectors[2].y - triTranslated.vectors[0].y;
			line2.z = triTranslated.vectors[2].z - triTranslated.vectors[0].z;

			normal.x = line1.y * line2.z - line1.z * line2.y;
			normal.y = line1.z * line2.x - line1.x * line2.z;
			normal.z = line1.x * line2.y - line1.y * line2.x;

			float l = (float) Math.sqrt(Math.pow(normal.x, 2) + Math.pow(normal.y, 2) + Math.pow(normal.z, 2));
			normal.x /= l; normal.y /= l; normal.z /= l;
						
			//Check if we should be able to see this triangle
			if(normal.x * (triTranslated.vectors[0].x - vCamera.x) +
					normal.y * (triTranslated.vectors[0].y - vCamera.y) +
					normal.z * (triTranslated.vectors[0].z - vCamera.z) < 0.0f) {
				
				
				//Set the light direction, and calculate the length using pythagoras
				Vector3d lightDirection = new Vector3d(0.0f, 0.0f, -1.0f);
				l = (float) Math.sqrt(Math.pow(lightDirection.x, 2) + Math.pow(lightDirection.y, 2) + Math.pow(lightDirection.z, 2));
				lightDirection.x /= 1; lightDirection.y /= 1; lightDirection.z /= 1;
				
				//calculate the dotpoint of the normal and the light direction
				float dp = normal.x * lightDirection.x + normal.y * lightDirection.y + normal.z * lightDirection.z;				
				
				//Project triangles from 3D -> 2D
				multiplyMatrixVector(triTranslated.vectors[0], triProjected.vectors[0], matProj);
				multiplyMatrixVector(triTranslated.vectors[1], triProjected.vectors[1], matProj);
				multiplyMatrixVector(triTranslated.vectors[2], triProjected.vectors[2], matProj);

				// Scale into view
				triProjected.vectors[0].x += 1.0f; triProjected.vectors[0].y += 1.0f;
				triProjected.vectors[1].x += 1.0f; triProjected.vectors[1].y += 1.0f;
				triProjected.vectors[2].x += 1.0f; triProjected.vectors[2].y += 1.0f;
				triProjected.vectors[0].x *= 0.5f * (float) WIDTH;
				triProjected.vectors[0].y *= 0.5f * (float) HEIGHT;
				triProjected.vectors[1].x *= 0.5f * (float) WIDTH;
				triProjected.vectors[1].y *= 0.5f * (float) HEIGHT;
				triProjected.vectors[2].x *= 0.5f * (float) WIDTH;
				triProjected.vectors[2].y *= 0.5f * (float) HEIGHT;
				
				triProjected.col = getShadingColor(dp);
				
				//Store triangles for sorting
				trisToRaster.add(triProjected);
			}
		}
		
		//Sort the list based on the z value, furthest to closest
		Collections.sort(trisToRaster, new Comparator<Triangle>() {
			@Override
			public int compare(Triangle t1, Triangle t2) {
				float z1 = (t1.vectors[0].z + t1.vectors[1].z + t1.vectors[2].z) / 3.0f;
				float z2 = (t2.vectors[0].z + t2.vectors[1].z + t2.vectors[2].z) / 3.0f;
				
				return (int) (z1 - z2);
			}
		});
		
		//Draw the triangles
		for(Triangle tri : trisToRaster) {
			g.setColor(tri.col);
			g.fillPolygon(new int[] {(int) tri.vectors[0].x, (int) tri.vectors[1].x, (int) tri.vectors[2].x},
			new int[] {(int) tri.vectors[0].y, (int) tri.vectors[1].y, (int) tri.vectors[2].y}, 3);
		}
		
		g.dispose();
		bs.show();
		
	}
	
	//method used to multiply a vector with a matrix
	/**
	 * @param i Input Vector3d
	 * @param o Output Vector3d
	 * @param m Matrix4x4 to multiply by
	 */
	void multiplyMatrixVector(Vector3d i, Vector3d o, Matrix4x4 m) {
		o.x = i.x * m.m[0][0] + i.y * m.m[1][0] + i.z * m.m[2][0] + m.m[3][0];
		o.y = i.x * m.m[0][1] + i.y * m.m[1][1] + i.z * m.m[2][1] + m.m[3][1];
		o.z = i.x * m.m[0][2] + i.y * m.m[1][2] + i.z * m.m[2][2] + m.m[3][2];
		
		float w = i.x * m.m[0][3] + i.y * m.m[1][3] + i.z * m.m[2][3] + m.m[3][3];

		if (w != 0.0f)
		{
			o.x /= w; o.y /= w; o.z /= w;
		}
	}
	
	Color getShadingColor(float dotProduct) {
		
		int pixelBw = (int) (11.0f*dotProduct);
		
		switch(pixelBw) {
		case 0: return hex2Rgb("#000000");
		case 1: return hex2Rgb("#202020");
		case 2: return hex2Rgb("#404040");
		case 3: return hex2Rgb("#606060");
		case 4: return hex2Rgb("#787878");
		case 5: return hex2Rgb("#989898");
		case 6: return hex2Rgb("#B0B0B0");
		case 7: return hex2Rgb("#C8C8C8"); 
		case 8: return hex2Rgb("#DCDCDC"); 
		case 9: return hex2Rgb("#F5F5F5");
		case 10: return hex2Rgb("#FFFFFF");
		}
		
		return hex2Rgb("#B0B0B0");
	}
	
	public static Color hex2Rgb(String colorStr) {
	    return new Color(
	            Integer.valueOf( colorStr.substring( 1, 3 ), 16 ),
	            Integer.valueOf( colorStr.substring( 3, 5 ), 16 ),
	            Integer.valueOf( colorStr.substring( 5, 7 ), 16 ) );
	}
}
