package org.uu.nl.embedding.util.read;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.log4j.Logger;

public class EmbeddingTextReader {

    private static Logger logger;
	
	final String file;
	final Embedding[] embeddings;
	final int iDim;
	final int[] orderedIDs;
	final float[] flEmbed;
	
	public EmbeddingTextReader(final String file) throws Exception {
		logger = Logger.getLogger("EmbeddingTextReader(" +file+ ")");
		
		this.file = file;
		this.embeddings = loadEmbeddingFromFile();
		
		this.orderedIDs = new int[embeddings.length];
		this.iDim = embeddings[0].vector.length;
		this.flEmbed = new float[ (embeddings.length * this.iDim) ];
		

		for (int i  = 0; i < embeddings.length; i++) {
			this.orderedIDs[i] = this.embeddings[i].ID;
			
			for (int j = 0; j < this.embeddings[i].vector.length; j++) {
				this.flEmbed[ ((i*this.iDim) + j) ] = this.embeddings[i].vector[j];
			}
		}
		
	}
	
	private Embedding[] loadEmbeddingFromFile() throws Exception {
		logger.info("Start loading embedding from file.");
		
		ArrayList<Embedding> lstEmbeddings = new ArrayList<Embedding>();
		System.out.println("EmbeddingTextReader.loadEmbeddingFromFile()");
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(this.file), "UTF-8"));) {
			
			String line = "";
			int ID;
			float value;
			float[] vector;
			while ((line = reader.readLine()) != null) {
				
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				System.out.println(line);
				String[] tokens = line.split("\t");
				vector = new float[tokens.length-1];
				// Parse nodeID and check.
				ID = Integer.parseInt(tokens[0].split(":")[0].trim());
				if (ID < 0) {
					throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid nodeID. => " + ID);
				}
				// Add current nodeID and each neighbor to matrix.
				for (int col = 1; col < tokens.length; col++) {
					if (tokens[col] == null || tokens[col].equals("")) continue;
					
					value = Float.parseFloat(tokens[col].trim());
					vector[col-1] = value;
				}
				
				Embedding embedding = new Embedding(ID, vector);
				lstEmbeddings.add(embedding);
			}

			reader.close();
			
			Embedding[] embeddings = new Embedding[lstEmbeddings.size()];
			for (int i = 0; i < lstEmbeddings.size(); i++) {
				embeddings[i] = lstEmbeddings.get(i);
			}
			
			logger.info("Finished loadEmbeddingFromFile() method.");
			return embeddings;
		} catch (Exception ex) { ex.printStackTrace(); }
		return new Embedding[0];
	}
	
	public int[] getOrderedIDs() {
		return this.orderedIDs;
	}

	public float[] getFloatEmbeddings() {
		return this.flEmbed;
	}
	
	public int getDimension() {
		return this.iDim;
	}
	
	public String getFile() {
		return this.file;
	}
	
	class Embedding {
		
		public final int ID;
		public final float[] vector;
		
		public Embedding(final int ID, final float[] vector) {
			this.ID = ID;
			this.vector = vector;
		}
	}
}
