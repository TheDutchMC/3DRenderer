package nl.thedutchmc.renderer;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
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
	
	Mesh meshCube;
	private float fTheta;
	Matrix4x4 matProj;
	Vector3d vCamera;
	Vector3d vLookDir;
	float fYaw;
	
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
		meshCube = new Mesh("axis.obj");
		
		matProj = matrixMakeProjection(90f, (float) HEIGHT / (float) WIDTH, 0.f, 1000.0f);
		
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
		
		vCamera = new Vector3d(0, 10 ,0);
		fTheta = 0.0f;
		
		
		Matrix4x4 matRotZ = matrixMakeRotationZ(fTheta * 0.f);
		Matrix4x4 matRotX = matrixMakeRotationX(fTheta);
		
		Matrix4x4 matTrans = matrixMakeTranslation(0.0f, 0.0f, 5.0f);
		
		Matrix4x4 matWorld = matrixMakeIdentity();
		matWorld = matrixMultiplyMatrix(matRotZ, matRotX);
		matWorld = matrixMultiplyMatrix(matWorld, matTrans);

		//"PointAt" matrix for camera
		Vector3d vUp = new Vector3d(0f, 1f, 0f);
		Vector3d vTarget = new Vector3d(0f, 0f, 1f);
		
		Matrix4x4 matCameraRot = matrixMakeRotationY(fYaw);
		vLookDir = matrixMultiplyVector(matCameraRot, vTarget);
		vTarget = vectorAdd(vCamera, vLookDir);
		
		Matrix4x4 matCamera = matrixPointAt(vCamera, vTarget, vUp);
		
		//View matrix for camera
		Matrix4x4 matView = matrixQuickInverse(matCamera);
		
		List<Triangle> trisToRaster = new ArrayList<>();
		
		//Draw the triangles
		for(Triangle tri : meshCube.tris) {
			
			Triangle triProjected = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));
			Triangle triTransformed = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));
			Triangle triViewed = new Triangle(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 0, 0));

			//World matrix transform
			triTransformed.vectors[0] = matrixMultiplyVector(matWorld, tri.vectors[0]);
			triTransformed.vectors[1] = matrixMultiplyVector(matWorld, tri.vectors[1]);
			triTransformed.vectors[2] = matrixMultiplyVector(matWorld, tri.vectors[2]);

			//Get lines on either side of the triangle
			Vector3d normal, line1, line2;	
			line1 = vectorSub(triTransformed.vectors[1], triTransformed.vectors[0]);
			line2 = vectorSub(triTransformed.vectors[2], triTransformed.vectors[0]);
			
			//Take the cross product of the lines to get the normal to the triangle surface
			normal = vectorCrossProduct(line1, line2);
			
			//Need to normalize the normal
			normal = vectorNormalise(normal);
						
			//Get a ray from triangle to camera
			Vector3d vCameraRay = vectorSub(triTransformed.vectors[0], vCamera);
			
			//If the ray is aligned with the normal, then the triangle is visible
			if(vectorDotProduct(normal, vCameraRay) < 0.0f) {
				
				//Illumination
				Vector3d lightDirection = new Vector3d(0.0f, 1.0f, -1.0f);
				lightDirection = vectorNormalise(lightDirection);
				
				//Calculate the dotProduct
				//How "aligned" are the light direction and triangle surface normal
				float dotProduct = (float) Math.max(0.1, (double) vectorDotProduct(lightDirection, normal));
				
				//Choose color
				triTransformed.col = getShadingColor(dotProduct);
				
				//Convert world space --> view space
				triViewed.vectors[0] = matrixMultiplyVector(matView, triTransformed.vectors[0]);
				triViewed.vectors[1] = matrixMultiplyVector(matView, triTransformed.vectors[1]);
				triViewed.vectors[2] = matrixMultiplyVector(matView, triTransformed.vectors[2]);
				triViewed.col = triTransformed.col;
				
				//Clip the viewed triangle against near plane, this can form 2 additional triangles
				int nClippedTriangles = 0;
				Triangle[] clipped = {new Triangle(new Vector3d(0,0,0), new Vector3d(0,0,0), new Vector3d(0,0,0)), new Triangle(new Vector3d(0,0,0), new Vector3d(0,0,0), new Vector3d(0,0,0))};
				nClippedTriangles = triangleClipAgainstPlane(new Vector3d(0.0f, 0.0f, 0.1f), new Vector3d(0.0f, 0.0f, 1.0f), triViewed, clipped[0], clipped[1]);
								
				for(int n = 0; n < nClippedTriangles; n++) {
					
					//Project triangles from 3D -> 2D
					triProjected.vectors[0] = matrixMultiplyVector(matProj, clipped[n].vectors[0]);
					triProjected.vectors[1] = matrixMultiplyVector(matProj, clipped[n].vectors[1]);
					triProjected.vectors[2] = matrixMultiplyVector(matProj, clipped[n].vectors[2]);
					triProjected.col = clipped[n].col;
					
					//Scale into view.
					triProjected.vectors[0] = vectorDivide(triProjected.vectors[0], triProjected.vectors[0].w);
					triProjected.vectors[1] = vectorDivide(triProjected.vectors[1], triProjected.vectors[1].w);
					triProjected.vectors[2] = vectorDivide(triProjected.vectors[2], triProjected.vectors[2].w);

					// X and Y are inverted, so fix this
					triProjected.vectors[0].x *= -1.0f;
					triProjected.vectors[1].x *= -1.0f;
					triProjected.vectors[2].x *= -1.0f;
					triProjected.vectors[0].y *= -1.0f;
					triProjected.vectors[1].y *= -1.0f;
					triProjected.vectors[2].y *= -1.0f;

					//Offset verts into visible normalized space
					Vector3d vOffsetView = new Vector3d(1.0f, 1.0f, 1.0f);
					triProjected.vectors[0] = vectorAdd(triProjected.vectors[0], vOffsetView);
					triProjected.vectors[1] = vectorAdd(triProjected.vectors[1], vOffsetView);
					triProjected.vectors[2] = vectorAdd(triProjected.vectors[2], vOffsetView);
					
					triProjected.vectors[0].x *= 0.5f * (float) WIDTH;
					triProjected.vectors[0].y *= 0.5f * (float) HEIGHT;
					triProjected.vectors[1].x *= 0.5f * (float) WIDTH;
					triProjected.vectors[1].y *= 0.5f * (float) HEIGHT;
					triProjected.vectors[2].z *= 0.5f * (float) WIDTH;
					triProjected.vectors[2].z *= 0.5f * (float) HEIGHT;
					
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
			
			for(Triangle triToRaster : trisToRaster) {
				
				System.out.println("tri");
				Triangle clipped[] = {new Triangle(new Vector3d(0,0,0), new Vector3d(0,0,0), new Vector3d(0,0,0)), new Triangle(new Vector3d(0,0,0), new Vector3d(0,0,0), new Vector3d(0,0,0))};
				List<Triangle> listTriangles = new ArrayList<>();
								
				listTriangles.add(triToRaster);
				int nNewTriangles = 1;
				
				for(int p = 0; p < 4; p++) {
					
					int nTrisToAdd = 0;
					while(nNewTriangles > 0) {
						
						Iterator<Triangle> i = listTriangles.iterator();
						Triangle test = i.next();
						int index = listTriangles.indexOf(test);
						listTriangles.remove(index);
						
						nNewTriangles--;
												
						switch(p) {
						
						case 0: nTrisToAdd = triangleClipAgainstPlane(new Vector3d(0.0f, 0.0f, 0.0f), new Vector3d(0.0f, 1.0f, 0.0f), test, clipped[0], clipped[1]); break;
						case 1: nTrisToAdd = triangleClipAgainstPlane(new Vector3d(0.0f, (float) HEIGHT - 1, 0.0f), new Vector3d(0.0f, -1.0f, 0.0f), test, clipped[0], clipped[1]); break;
						case 2: nTrisToAdd = triangleClipAgainstPlane(new Vector3d(0.0f, 0.0f, 0.0f), new Vector3d(1.0f, 0.0f, 0.0f), test, clipped[0], clipped[1]); break;
						case 3: nTrisToAdd = triangleClipAgainstPlane(new Vector3d((float) WIDTH -1, 0.0f, 0.0f), new Vector3d(-1.0f, 0.0f, 0.0f), test, clipped[0], clipped[1]); break;
						}
						
						for(int w = 0; w < nTrisToAdd; w++) {
							listTriangles.add(clipped[w]);
							System.out.println(listTriangles.size());
						}
					}
					nNewTriangles = listTriangles.size();
				}
				
				//Draw the triangles
				for(Triangle t : listTriangles) {
					System.out.println("Triangle");
					g.setColor(tri.col);
					g.fillPolygon(new int[] {(int) t.vectors[0].x, (int) t.vectors[1].x, (int) t.vectors[2].x},
					new int[] {(int) t.vectors[0].y, (int) t.vectors[1].y, (int) t.vectors[2].y}, 3);
				}
			}

			g.dispose();
			bs.show();
		}
	}
	
	Vector3d matrixMultiplyVector(Matrix4x4 m, Vector3d i) {
		float x = i.x * m.m[0][0] + i.y * m.m[1][0] + i.z * m.m[2][0] + i.w * m.m[3][0];
		float y = i.x * m.m[0][1] + i.y * m.m[1][1] + i.z * m.m[2][1] + i.w * m.m[3][1];
		float z = i.x * m.m[0][2] + i.y * m.m[1][2] + i.z * m.m[2][2] + i.w * m.m[3][2];
		float w = i.x * m.m[0][3] + i.y * m.m[1][3] + i.z * m.m[2][3] + i.w * m.m[3][3];
		
		Vector3d v = new Vector3d(x, y, z);
		v.w = w;
		
		return v;
	}
	
	Matrix4x4 matrixMakeIdentity() {
		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = 1.0f;
		matrix.m[1][1] = 1.0f;
		matrix.m[2][2] = 1.0f;
		matrix.m[3][3] = 1.0f;
		
		return matrix;
	}
	
	Matrix4x4 matrixMakeRotationX(float fAngleRad) {
		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = 1.0f;
		matrix.m[1][1] = (float) Math.cos((double) fAngleRad);
		matrix.m[1][2] = (float) Math.sin((double) fAngleRad); 
		matrix.m[2][1] = (float) -Math.sin((double) fAngleRad);
		matrix.m[2][2] = (float) Math.cos((double) fAngleRad);
		matrix.m[3][3] = 1.0f;
		
		return matrix;
	}
	
	Matrix4x4 matrixMakeRotationY(float fAngleRad) {
		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = (float) Math.cos((double) fAngleRad);
		matrix.m[0][2] = (float) Math.sin((double) fAngleRad); 
		matrix.m[2][0] = (float) -Math.sin((double) fAngleRad); 
		matrix.m[1][1] = 1.0f;
		matrix.m[2][2] = (float) Math.cos((double) fAngleRad);
		matrix.m[3][3] = 1.0f;
		
		return matrix;
	}
	
	Matrix4x4 matrixMakeRotationZ(float fAngleRad) {
		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = (float) Math.cos((double) fAngleRad);
		matrix.m[0][1] = (float) Math.sin((double) fAngleRad); 
		matrix.m[1][0] = (float) -Math.sin((double) fAngleRad);
		matrix.m[1][1] = (float) Math.cos((double) fAngleRad);
		matrix.m[2][2] = 1.0f;
		matrix.m[3][3] = 1.0f;
		
		return matrix;
	}
	
	Matrix4x4 matrixMakeTranslation(float x, float y, float z) {
		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = 1.0f;
		matrix.m[1][1] = 1.0f;
		matrix.m[2][2] = 1.0f;
		matrix.m[3][3] = 1.0f;
		matrix.m[3][0] = x;
		matrix.m[3][1] = y;
		matrix.m[3][2] = z;
		
		return matrix;
	}
	
	Matrix4x4 matrixMakeProjection(float fFovDegrees, float fAspectRatio, float fNear, float fFar) {
		float fFovRad = 1.0f / (float) Math.tan((double) fFovDegrees * 0.5 / 180.0 * Math.PI);

		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = fAspectRatio * fFovRad;
		matrix.m[1][1] = fFovRad;
		matrix.m[2][2] = fFar / (fFar - fNear);
		matrix.m[3][2] = (-fFar * fNear) / (fFar - fNear);
		matrix.m[2][3] = 1.0f;
		matrix.m[3][3] = 0.0f;

		return matrix;
	}
	
	Matrix4x4 matrixMultiplyMatrix(Matrix4x4 m1, Matrix4x4 m2) {
		Matrix4x4 matrix = new Matrix4x4();
		
		for (int c = 0; c < 4; c++)
			for (int r = 0; r < 4; r++)
				matrix.m[r][c] = m1.m[r][0] * m2.m[0][c] + m1.m[r][1] * m2.m[1][c] + m1.m[r][2] * m2.m[2][c] + m1.m[r][3] * m2.m[3][c];
		
		return matrix;	
	}
	
	Matrix4x4 matrixPointAt(Vector3d pos, Vector3d target, Vector3d up)
	{
		// Calculate new forward direction
		Vector3d newForward = vectorSub(target, pos);
		newForward = vectorNormalise(newForward);

		// Calculate new Up direction
		Vector3d a = vectorMultiply(newForward, vectorDotProduct(up, newForward));
		Vector3d newUp = vectorSub(up, a);
		newUp = vectorNormalise(newUp);

		// New Right direction is easy, its just cross product
		Vector3d newRight = vectorCrossProduct(newUp, newForward);

		// Construct Dimensioning and Translation Matrix	
		Matrix4x4 matrix = new Matrix4x4();
		
		matrix.m[0][0] = newRight.x;	matrix.m[0][1] = newRight.y;	matrix.m[0][2] = newRight.z;	matrix.m[0][3] = 0.0f;
		matrix.m[1][0] = newUp.x;		matrix.m[1][1] = newUp.y;		matrix.m[1][2] = newUp.z;		matrix.m[1][3] = 0.0f;
		matrix.m[2][0] = newForward.x;	matrix.m[2][1] = newForward.y;	matrix.m[2][2] = newForward.z;	matrix.m[2][3] = 0.0f;
		matrix.m[3][0] = pos.x;			matrix.m[3][1] = pos.y;			matrix.m[3][2] = pos.z;			matrix.m[3][3] = 1.0f;
		
		return matrix;
	}
	
	Matrix4x4 matrixQuickInverse(Matrix4x4 m) // Only for Rotation/Translation Matrices
	{
		Matrix4x4 matrix = new Matrix4x4();
		matrix.m[0][0] = m.m[0][0]; matrix.m[0][1] = m.m[1][0]; matrix.m[0][2] = m.m[2][0]; matrix.m[0][3] = 0.0f;
		matrix.m[1][0] = m.m[0][1]; matrix.m[1][1] = m.m[1][1]; matrix.m[1][2] = m.m[2][1]; matrix.m[1][3] = 0.0f;
		matrix.m[2][0] = m.m[0][2]; matrix.m[2][1] = m.m[1][2]; matrix.m[2][2] = m.m[2][2]; matrix.m[2][3] = 0.0f;
		matrix.m[3][0] = -(m.m[3][0] * matrix.m[0][0] + m.m[3][1] * matrix.m[1][0] + m.m[3][2] * matrix.m[2][0]);
		matrix.m[3][1] = -(m.m[3][0] * matrix.m[0][1] + m.m[3][1] * matrix.m[1][1] + m.m[3][2] * matrix.m[2][1]);
		matrix.m[3][2] = -(m.m[3][0] * matrix.m[0][2] + m.m[3][1] * matrix.m[1][2] + m.m[3][2] * matrix.m[2][2]);
		matrix.m[3][3] = 1.0f;
		return matrix;
	}
	
	Vector3d vectorAdd(Vector3d v1, Vector3d v2) {
		return new Vector3d(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z);
	}
	
	Vector3d vectorSub(Vector3d v1, Vector3d v2) {
		return new Vector3d(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
	}
	
	Vector3d vectorMultiply(Vector3d v, float k) {
		return new Vector3d(v.x * k, v.y * k, v.z * k);
	}
	
	Vector3d vectorDivide(Vector3d v, float k) {
		return new Vector3d(v.x / k, v.y / k, v.z / k);
	}
	
	float vectorDotProduct(Vector3d v1, Vector3d v2) {
		return (v1.x * v2.x + v1.y * v1.y + v1.z * v2.z);
	}
	
	float vectorLength(Vector3d v) {
		return (float) Math.sqrt((double) vectorDotProduct(v, v));
	}
	
	Vector3d vectorNormalise(Vector3d v) {
		float l = vectorLength(v);
		return new Vector3d(v.x / l, v.y / l, v.z / l);
	}
	
	Vector3d vectorCrossProduct(Vector3d v1, Vector3d v2) {
		float x = v1.y * v2.z - v1.z * v2.y;
		float y = v1.z * v2.x - v1.x * v2.z;
		float z = v1.x * v2.y - v1.y * v2.x;
		
		return new Vector3d(x, y, z);
	}
	
	Vector3d vectorIntersectPlane(Vector3d plane_p, Vector3d plane_n, Vector3d lineStart, Vector3d lineEnd) {
		plane_n = vectorNormalise(plane_n);
		float plane_d = -vectorDotProduct(plane_n, plane_p);
		float ad = vectorDotProduct(lineStart, plane_n);
		float bd = vectorDotProduct(lineEnd, plane_n);
		float t = (-plane_d - ad) / (bd - ad);
		Vector3d lineStartToEnd = vectorSub(lineEnd, lineStart);
		Vector3d lineToIntersect = vectorMultiply(lineStartToEnd, t);
		
		return vectorAdd(lineStart, lineToIntersect);
	}
	
	float vectorDistance(Vector3d v, Vector3d plane_n, Vector3d plane_p) {
		Vector3d n = v;
		float vD = (plane_n.x * n.x + plane_n.y * n.y + plane_n.z * n.z - vectorDotProduct(plane_n, plane_p));
		return vD;
	}
	
	int triangleClipAgainstPlane(Vector3d plane_p, Vector3d plane_n, Triangle inTri, Triangle outTri1, Triangle outTri2) {
		
		// Make sure plane normal is indeed normal
		plane_n = vectorNormalise(plane_n);

		// Create two temporary storage arrays to classify points either side of plane
		// If distance sign is positive, point lies on "inside" of plane
		Vector3d inside_points[] = new Vector3d[3];
		Vector3d outside_points[] = new Vector3d[3];
		
		int nInsidePointCount = 0;
		int nOutsidePointCount = 0;
		
		// Get signed distance of each point in triangle to plane
		float d0 = vectorDistance(inTri.vectors[0], plane_n, plane_p);
		float d1 = vectorDistance(inTri.vectors[1], plane_n, plane_p);
		float d2 = vectorDistance(inTri.vectors[2], plane_n, plane_p);

		if (d0 >= 0) {
			inside_points[nInsidePointCount] = inTri.vectors[0];
			nInsidePointCount++;
		} else {
			outside_points[nOutsidePointCount] = inTri.vectors[0];
			nOutsidePointCount++;
		}
				
		if (d1 >= 0) {
			inside_points[nInsidePointCount] = inTri.vectors[1];
			nInsidePointCount++;
		} else { 
			outside_points[nOutsidePointCount] = inTri.vectors[1];
			nOutsidePointCount++;
		}
				
		if (d2 >= 0) {
			inside_points[nInsidePointCount] = inTri.vectors[2];
			nInsidePointCount++;
		} else { 
			outside_points[nOutsidePointCount] = inTri.vectors[2]; 
			nOutsidePointCount++;
		}
		
		// Now classify triangle points, and break the input triangle into 
		// smaller output triangles if required. There are four possible
		// outcomes...
		if (nInsidePointCount == 0) {
			// All points lie on the outside of plane, so clip whole triangle
			// It ceases to exist

			return 0; // No returned triangles are valid
		}

		if (nInsidePointCount == 3) {
			// All points lie on the inside of plane, so do nothing
			// and allow the triangle to simply pass through
			outTri1 = inTri;

			return 1; // Just the one returned original triangle is valid
		}

		if (nInsidePointCount == 1 && nOutsidePointCount == 2) {
			// Triangle should be clipped. As two points lie outside
			// the plane, the triangle simply becomes a smaller triangle
			
			// Copy appearance info to new triangle
			outTri1.col =  inTri.col;

			// The inside point is valid, so keep that...
			outTri1.vectors[0] = inside_points[0];

			// but the two new points are at the locations where the 
			// original sides of the triangle (lines) intersect with the plane
			outTri1.vectors[1] = vectorIntersectPlane(plane_p, plane_n, inside_points[0], outside_points[0]);
			outTri1.vectors[2] = vectorIntersectPlane(plane_p, plane_n, inside_points[0], outside_points[1]);

			return 1; // Return the newly formed single triangle
		}

		if (nInsidePointCount == 2 && nOutsidePointCount == 1) {
			// Triangle should be clipped. As two points lie inside the plane,
			// the clipped triangle becomes a "quad". Fortunately, we can
			// represent a quad with two new triangles

			// Copy appearance info to new triangles
			outTri1.col =  inTri.col;

			outTri2.col =  inTri.col;

			// The first triangle consists of the two inside points and a new
			// point determined by the location where one side of the triangle
			// intersects with the plane
			outTri1.vectors[0] = inside_points[0];
			outTri1.vectors[1] = inside_points[1];
			outTri1.vectors[2] = vectorIntersectPlane(plane_p, plane_n, inside_points[0], outside_points[0]);

			// The second triangle is composed of one of he inside points, a
			// new point determined by the intersection of the other side of the 
			// triangle and the plane, and the newly created point above
			outTri2.vectors[0] = inside_points[1];
			outTri2.vectors[1] = outTri1.vectors[2];
			outTri2.vectors[2] = vectorIntersectPlane(plane_p, plane_n, inside_points[1], outside_points[0]);

			return 2; // Return two newly formed triangles which form a quad
		}
		
		return 0;
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
