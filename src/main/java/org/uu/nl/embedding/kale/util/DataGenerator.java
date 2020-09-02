package org.uu.nl.embedding.kale.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.bca.BookmarkColoring;
import org.uu.nl.embedding.bca.util.BCV;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.write.KaleEmbeddingTextWriter;

import me.tongfei.progressbar.ProgressBar;

public class DataGenerator {

    private static Logger logger = Logger.getLogger("DataGenerator");
	
	private final InMemoryRdfGraph graph;
	private final Configuration config;
	private final int[][] inVerts;
	private final int[][] outVerts;
	private final int[][] inEdges;
	private final int[][] outEdges;
	
	private double validationPercentage;
	private double testingPercentage;
	private int[] trainSet, validSet, testSet;
	
	private boolean undirected;

	private String FILEPATH = "/Users/euan/eclipse-workspace/graphLogicEmbed/data/input";
	private String fileExtension;
	public String fnTrainingTriples;
	public String fnValidateTriples;
	public String fnTestingTriples;
	public String fnTrainingRules;
	public String fnGloveVectors;
	
	private String sep;
	private String nl;
	
	public DataGenerator(final InMemoryRdfGraph graph, final Configuration config, 
							final boolean undirected,
							final int[][] inVerts, final int[][] outVerts,
							final int[][] inEdges, final int[][] outEdges) {
		this.graph = graph;
		this.config = config;
		
		this.inVerts = inVerts;
		this.outVerts = outVerts;
		this.inEdges = inEdges;
		this.outEdges = outEdges;
		
		this.undirected = undirected;
		
		this.validationPercentage = 0.2;
		this.testingPercentage = 0.3;
		
		this.sep = "\t";
		this.nl = "\n";
		logger.info("New DataGenerator class successfully creatd.");
	}
	
	public void Initialize(final String FILEPATH, final String fileExtension, final BookmarkColoring BCA) throws Exception {
		randomDataSeed();
		
		this.FILEPATH = FILEPATH;
		this.fileExtension = fileExtension;
		
		generateTrainingTriples();
		generateValidateTriples();
		generateTestingTriples();
		generateTrainingRules();
		generateGloveVectors(BCA, true);
		
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	private void randomDataSeed() throws Exception {
	    if ((this.validationPercentage + this.testingPercentage) > 0.9) 
	    	throw new Exception("Combination of validation and testing percentage of "
	    						+ "the dataset is too large: It may not exceed a total "
	    						+ "of 0.9 amount of the dataset.");
	    
	    Random rand = new Random();
	    int iNumVerts = this.outVerts.length;
	    ArrayList<Integer> takenInts = new ArrayList<Integer>();
	    
	    // Calculate number of elements per dataset.
	    int iNumValidElems = (int) (iNumVerts * this.validationPercentage);
	    int iNumTestElems = (int) (iNumVerts * this.testingPercentage);
	    int iNumTrainElems = iNumVerts - (iNumValidElems + iNumTestElems);
	    // Initialize datasets.
	    this.trainSet = new int[iNumTrainElems];
	    this.validSet = new int[iNumValidElems];
	    this.testSet = new int[iNumTestElems];
	    
	    // Fill training set with random vertices. Omitting
	    // duplicates.
	    int i = 0, randIdx;
	    while (i < iNumTrainElems) {
	    	randIdx = rand.nextInt(iNumVerts);
	    	if (!takenInts.contains(randIdx)) {
	    		this.trainSet[i] = randIdx;
	    		takenInts.add(randIdx);
	    		i++;
	    	}
	    }
	    // Fill validation set with random vertices. Omitting
	    // duplicates.
	    i = 0;
	    while (i < iNumValidElems) {
	    	randIdx = rand.nextInt(iNumVerts);
	    	if (!takenInts.contains(randIdx)) {
	    		this.validSet[i] = randIdx;
	    		takenInts.add(randIdx);
	    		i++;
	    	}
	    }
	    // Fill test set with remaining vertices. Omitting
	    // duplicates.
	    i = 0;
	    while (i < iNumTestElems) {
	    	for (int j = 0; j < iNumVerts; j++) {
	    		if (!takenInts.contains(j)) {
	    			this.testSet[i] = j;
	    			i++;
	    		}
	    	}
	    }
	    /*
	    String strT = "", strTe = "", strV = "";
	    for (int k = 0; k < 10; k++) {
	    	strT += this.trainSet[k] + " - ";
	    	strTe += this.testSet[k] + " - ";
	    	strV += this.validSet[k] + " - ";
	    }
	    System.out.println("\nstrT:\n" + strT + "\nstrTe:\n" + strTe + "\nstrV:\n" + strV + "\n");
	    */
	}
	
	/**
	 * Reads the graph and converts them to file specific format.
	 * @throws Exception
	 */
	public void generateTrainingTriples() throws Exception {
		this.fnTrainingTriples = generateFileDir("training_triples");
		//BufferedWriter writer = new BufferedWriter(new FileWriter(this.fnTrainingTriples));
		
		ArrayList<String> lines = new ArrayList<String>();
		String line = "";

		try {
			//try (ProgressBar pb = Configuration.progressBar("Writing training triples to file", nrLines, "triples")) {
			//writer.write("this.trainSet.length = " + this.trainSet.length + nl);
			for (int i = 0; i < this.trainSet.length; i++) {
				for (int j = 0; j < this.outVerts[this.trainSet[i]].length; j++) {
					line = Integer.toString(this.trainSet[i]) + sep;
					line += Integer.toString(this.outVerts[this.trainSet[i]][j]) + sep;
					line += Integer.toString(this.outEdges[this.trainSet[i]][j]) + nl;
					lines.add(line);
				}
				//writer.write(line);
				//pb.step();
			}
			//System.out.println("this.undirected: " + this.undirected);
			if (this.undirected) {
				//writer.write("this.undirected: " + this.undirected + nl);
				lines.add("#reversed direction" + nl);
				for (int i = 0; i < this.trainSet.length; i++) {
					for (int j = 0; j < this.inVerts[this.trainSet[i]].length; j++) {
						line = Integer.toString(this.trainSet[i]) + sep;
						line += Integer.toString(this.inVerts[this.trainSet[i]][j]) + sep;
						line += Integer.toString(this.inEdges[this.trainSet[i]][j]) + nl;
						lines.add(line);
					}
					//writer.write(line);
					//pb.step();
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
		//}
		//writer.close();
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTrainingTriples, this.config);
		kaleWriter.write(lines);
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	public void generateValidateTriples() throws Exception {
		this.fnValidateTriples = generateFileDir("validation_triples");

		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		try {
			for (int i = 0; i < this.validSet.length; i++) {
				for (int j = 0; j < this.outVerts[this.validSet[i]].length; j++) {
					line = Integer.toString(this.validSet[i]) + sep;
					line += Integer.toString(this.outVerts[this.validSet[i]][j]) + sep;
					line += Integer.toString(this.outEdges[this.validSet[i]][j]) + nl;
					lines.add(line);
				}
			}
			if (this.undirected) {
				lines.add("#reversed direction" + nl);
				for (int i = 0; i < this.validSet.length; i++) {
					for (int j = 0; j < this.inVerts[this.validSet[i]].length; j++) {
						line = Integer.toString(this.validSet[i]) + sep;
						line += Integer.toString(this.inVerts[this.validSet[i]][j]) + sep;
						line += Integer.toString(this.inEdges[this.validSet[i]][j]) + nl;
						lines.add(line);
					}
				}
			}
		} catch (Exception e) { e.printStackTrace(); }

		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnValidateTriples, this.config);
		kaleWriter.write(lines);
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	public void generateTestingTriples() throws Exception {
		this.fnTestingTriples = generateFileDir("testing_triples");

		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		try {
			for (int i = 0; i < this.testSet.length; i++) {
				for (int j = 0; j < this.outVerts[this.testSet[i]].length; j++) {
					line = Integer.toString(this.testSet[i]) + sep;
					line += Integer.toString(this.outVerts[this.testSet[i]][j]) + sep;
					line += Integer.toString(this.outEdges[this.testSet[i]][j]) + nl;
					lines.add(line);
				}
			}
			if (this.undirected) {
				lines.add("#reversed direction" + nl);
				for (int i = 0; i < this.testSet.length; i++) {
					for (int j = 0; j < this.inVerts[this.testSet[i]].length; j++) {
						line = Integer.toString(this.testSet[i]) + sep;
						line += Integer.toString(this.inVerts[this.testSet[i]][j]) + sep;
						line += Integer.toString(this.inEdges[this.testSet[i]][j]) + nl;
						lines.add(line);
					}
				}
			}
		} catch (Exception e) { e.printStackTrace(); }

		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTestingTriples, this.config);
		kaleWriter.write(lines);
	}
	
	public void generateTrainingRules() throws Exception {
		this.fnTrainingRules = generateFileDir("training_rules");
		// Create list with rules.
		ArrayList<String> rulesList = new ArrayList<String>();
		
		rulesList.add("_birthdate(x,y)" + this.sep + "&" + this.sep + "_deathdate(x,z)" + this.sep + "==>" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("_birthdate(x,y)" + this.sep + "==>" + this.sep + "_deathdate(x,z)" + this.sep + "&" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("!" + this.sep + "_is_same_or_before(y,z)" + this.sep + "==>" + this.sep + 
				"!" + this.sep + "_birthdate(x,y)" + this.sep + 
				"|" + this.sep + "!" + this.sep + "_deathdate(x,z)" + nl);
		
		rulesList.add("_birthdate(x,y)" + this.sep + "&" + this.sep + "_baptised_on(x,z)" + this.sep + "==>" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("_birthdate(x,y)" + this.sep + "==>" + this.sep + "_baptised_on(x,z)" + this.sep + "&" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("!" + this.sep + "_is_same_or_before(y,z)" + this.sep + "==>" + this.sep + 
				"!" + this.sep + "_birthdate(x,y)" + this.sep + 
				"|" + this.sep + "!" + this.sep + "_baptised_on(x,z)" + nl);
		
		rulesList.add("_baptised_on(x,y)" + this.sep + "&" + this.sep + "_deathdate(x,z)" + this.sep + "==>" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("_baptised_on(x,y)" + this.sep + "==>" + this.sep + "_deathdate(x,z)" + this.sep + "&" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("!" + this.sep + "_is_same_or_before(y,z)" + this.sep + "==>" + this.sep + 
				"!" + this.sep + "_baptised_on(x,y)" + this.sep + 
				"|" + this.sep + "!" + this.sep + "_deathdate(x,z)" + nl);
		
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTrainingRules, this.config);
		kaleWriter.write(rulesList);
	}
	
	/**
	 * 
	 * @param bcvs
	 * @throws Exception
	 */
	public void generateGloveVectors(final BCV[] bcvs) throws Exception {
		/*
		 * FILE FORMAT:
		 * 
		 * line1 <- [NEIGHBORS\t nodeID1\t neighborID1\t neighborID2\t ...\t neighborIDn]
		 * line2 <- [VALUES\t nodeID1\t value1\t value2\t ...\t value_n]
		 * ...
		 */
		this.fnGloveVectors = generateFileDir("glove_vectors");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(this.fnGloveVectors), "UTF-8"));
		
		String line1, line2;
		for (int i = 0; i < bcvs.length; i++) {
			line1 = "[NEIGHBORS" + this.sep + bcvs[i].getRootNode();
			line2 = "[VALUES" + this.sep + bcvs[i].getRootNode();
			for (int j = 0; j < bcvs[i].size(); j++) {
				line1 += this.sep + j;
				line2 += this.sep + bcvs[i].get(j);
			}
			// Finish the lines.
			line1 += "]";
			line2 += "]";
			// Write lines to file.
			writer.write(line1.trim());
			writer.write(line2.trim());
		}
		writer.close();
	}
	
	/**
	 * 
	 * @param bcvs
	 * @param singleLines
	 * @throws Exception
	 */
	public void generateGloveVectors(final BookmarkColoring BCA, final boolean singleLines) throws Exception {
		/*
		 * FILE FORMAT:
		 * 
		 * line <- "nodeID1\t neighborID1: value1\t neighborID2: value2\t ...\t neighborIDn: valueN"
		 * ...
		 */
		this.fnGloveVectors = generateFileDir("glove_vectors");

		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		for (int i = 0; i < BCA.getBCVs().size(); i++) {
			// Write line to file.
			line = BCA.getBCVs().get(i).toString() + nl;
			lines.add(line);
		}
		
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnGloveVectors, this.config);
		kaleWriter.write(lines);
	}
	
	/**
	 * Generates file directory without overwriting existing
	 * files by iterating through numbers at start of file.
	 * 
	 * @param fnDirName
	 * @return
	 */
	public String generateFileDir(final String fnDirName) {
		// Check for existence of directory.
		File pathDir = new File(this.FILEPATH);
		if (!pathDir.exists()) {
			logger.info("Creating directory: " + pathDir.getName());
			boolean result = false;
			
			try {
				pathDir.mkdir();
				result = true;
			} catch (SecurityException se) {
				se.printStackTrace();
			}
			if (result) { logger.info("Directory created successfully."); }
		}
		
		// Create the file to write to.
		int num = 0;
		String fnDir = this.FILEPATH + "/" + num + "_" + fnDirName + "_KALE_GLOVE" + this.fileExtension;
		//String fnDir = num + "_" + fnDirName + "_KALE_GLOVE";
		File f = new File(fnDir);
		while (f.exists()) {
			num++;
			fnDir = this.FILEPATH + "/" + num + "_" + fnDirName + "_KALE_GLOVE" + this.fileExtension;
			//fnDir = num + "_" + fnDirName + "_KALE_GLOVE";
			f = new File(fnDir);
		}
		try {
			if(f.createNewFile()) { logger.info("File created: " + f.getName()); }
		} catch (IOException ioe) { 
			logger.info("File could not be created.");
			ioe.printStackTrace();
		}
		return fnDir;
	}
	
	
	
	/*
	 * Below: getter and setter methods.
	 */
	
	/**
	 * 
	 * @return
	 */
	public int[] getTrainSet() {
		return this.trainSet;
	}
	
	/**
	 * 
	 * @return
	 */
	public int[] getValidationSet() {
		return this.validSet;
	}
	/**
	 * 
	 * @return
	 */
	public int[] getTestSet() {
		return this.testSet;
	}
	
	/**
	 * 
	 * @param isUndirected
	 */
	public void setUndirected(final boolean isUndirected) {
		this.undirected = isUndirected;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isUndirected() {
		return this.undirected;
	}
	
	/**
	 * 
	 * @param percentage
	 */
	public void setValidationPercentage(final double percentage) throws Exception {
		this.validationPercentage = percentage;
		randomDataSeed();
	}
	
	/**
	 * 
	 * @return
	 */
	public double getValidationPercentage() {
		return this.validationPercentage;
	}
	
	/**
	 * 
	 * @param percentage
	 */
	public void setTestingPercentage(final double percentage) throws Exception {
		this.testingPercentage = percentage;
		randomDataSeed();
	}
	
	/**
	 * 
	 * @return
	 */
	public double getTestingPercentage() {
		return this.testingPercentage;
	}
	
	/**
	 * 
	 * @param separator
	 */
	public void setSeparator(final String separator) {
		this.sep = separator;
	}
	
	/**
	 * 
	 * @return
	 */
	public String getSeparator() {
		return this.sep;
	}
	
	/**
	 * 
	 * @param filePath
	 * @throws Exception
	 */
	public void setFilePath(final String filePath) throws Exception {

		File f = new File(filePath);
		// Simple check
		if (!f.exists()) { 
			throw new Exception("Provided directory in DataGenerator does not exist.");
		}
		this.FILEPATH = filePath;

		this.fnTrainingTriples = generateFileDir("training_triples");
		this.fnValidateTriples = generateFileDir("validation_triples");
		this.fnTestingTriples = generateFileDir("testing_triples");
		this.fnTrainingRules = generateFileDir("training_rules");
		this.fnGloveVectors = generateFileDir("glove_vectors");
	}
	
	/**
	 * 
	 * @return
	 */
	public String getFilePath() {
		return this.FILEPATH;
	}
	
	/**
	 * 
	 * @param fileExtension
	 * @throws Exception
	 */
	public void setFileExtension(final String fileExtension) throws Exception {
		// Simple check
		if (fileExtension.charAt(0) != '.') { 
			throw new Exception("Provided file extension in KaleRunner has invalid format.");
		}
		this.fileExtension = fileExtension;

		this.fnTrainingTriples = generateFileDir("training_triples");
		this.fnValidateTriples = generateFileDir("validation_triples");
		this.fnTestingTriples = generateFileDir("testing_triples");
		this.fnTrainingRules = generateFileDir("training_rules");
		this.fnGloveVectors = generateFileDir("glove_vectors");
	}
	
	/**
	 * 
	 * @return
	 */
	public String getFileExtension() {
		return this.fileExtension;
	}

}
