package nl.thedutchmc.renderer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;

public class Mesh {

	public Vector<Triangle> tris = new Vector<Triangle>();
	
	public Mesh(String fileName) {
			
		File file = new File(fileName);
				
		try {
			Scanner scanner = new Scanner(file);
			
			List<Vector3d> vectors = new ArrayList<>();
			
			while(scanner.hasNextLine()) {
				final String scanned = scanner.nextLine();
				final String[] parts = scanned.split(" ");
								
				if(parts[0].equals("v")) {
					final float x = Float.valueOf(parts[1]);
					final float y = Float.valueOf(parts[2]);
					final float z = Float.valueOf(parts[3]);
					
					vectors.add(new Vector3d(x, y, z));
				}
				
				if(parts[0].equals("f")) {
					int[] vec = {Integer.valueOf(parts[1]), Integer.valueOf(parts[2]), Integer.valueOf(parts[3])};
					
					this.tris.add(new Triangle(vectors.get(vec[0] - 1), vectors.get(vec[1] - 1), vectors.get(vec[2] - 1)));
				}
			}
			
			scanner.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.out.println(file.getAbsolutePath());
		}
		
	}
}
