package org.uu.nl.embedding.kale.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.bca.BookmarkColoring;
import org.uu.nl.embedding.bca.models.BookmarkColoringEdges;
import org.uu.nl.embedding.bca.models.BookmarkColoringNodes;
import org.uu.nl.embedding.bca.util.BCV;
import org.uu.nl.embedding.convert.util.InEdgeNeighborhoodAlgorithm;
import org.uu.nl.embedding.convert.util.OutEdgeNeighborhoodAlgorithm;
import org.uu.nl.embedding.kale.struct.KaleTriple;
import org.uu.nl.embedding.kale.struct.RuleSet;
import org.uu.nl.embedding.kale.struct.TripleRule;
import org.uu.nl.embedding.logic.util.FormulaSet;
import org.uu.nl.embedding.logic.util.SimpleDate;
import org.uu.nl.embedding.util.ArrayUtils;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.config.Configuration.SimilarityGroup.Time;
import org.uu.nl.embedding.util.similarity.DateDays;
import org.uu.nl.embedding.util.write.KaleEmbeddingTextWriter;

import me.tongfei.progressbar.ProgressBar;

public class DataGenerator {

    private static Logger logger = Logger.getLogger(DataGenerator.class);
	
	private final InMemoryRdfGraph graph;
	private final Configuration config;
	private final int[][] inVerts;
	private final int[][] outVerts;
	private final int[][] inEdges;
	private final int[][] outEdges;
	private int[][] allOut;
	private int[][] allIn;
	/*
	 * allInVert and allOutVert are only the non-empty vertices.
	 */
	private int[][] allInVert;
	private int[][] allOutVert;
	private int[] nonEmptyVerts;
	private int[] nonEmptyAllVerts;
	
	ArrayList<TreeMap<Integer, int[]>> neighborMaps;
	TreeMap<Integer, int[]> neighborVertsIn;
	TreeMap<Integer, int[]> neighborVertsOut;
	TreeMap<Integer, int[]> neighborEdgesIn;
	TreeMap<Integer, int[]> neighborEdgesOut;
	
	private TreeMap<Integer, Integer> edgeIdMap;
	
	private double validationPercentage;
	private double testingPercentage;
	private int[] trainSet, validSet, testSet;
	
	private boolean undirected;

	private String FILEPATH = "/Users/euan/eclipse-workspace/graphLogicEmbed/data/input";
	private String fileExtension = null;
	private String fnTrainingTriples;
	private String fnValidateTriples;
	private String fnTestingTriples;
	private String fnTrainingRules;
	private String fnGloveVectors;
	private String fnNodeBCAVectors;
	private String fnEdgeBCAVectors;
	
	private ArrayList<int[]> trainingTriples;
	private RuleSet properRuleSet = null;
	private HashMap<String, Integer> uniqueRelationTypes;
	private HashMap<Integer, String> uniqueTypeRelations = null;
	private int approxDays;
	
	private String sep;
	private String nl;

	
	public DataGenerator(final InMemoryRdfGraph graph, final Configuration config, 
							final boolean undirected,
							final int[][] inVerts, final int[][] outVerts,
							final int[][] inEdges, final int[][] outEdges,
							final TreeMap<Integer, Integer> edgeIdMap) throws Exception {
		this.graph = graph;
		this.config = config;
		
		this.inVerts = inVerts;
		this.outVerts = outVerts;
		this.inEdges = inEdges;
		this.outEdges = outEdges;
		this.edgeIdMap = edgeIdMap;
	    this.trainingTriples = new ArrayList<int[]>();

    	neighborMaps = determineEdgeNeighbors(this.inVerts,
										this.outVerts,
										this.inEdges,
										this.outEdges);
		/*
		 * Resulting maps are of the edgeIDs, *NOT* original edge indices!
		 */
		neighborVertsIn = neighborMaps.get(0);
		neighborVertsOut = neighborMaps.get(1);
		neighborEdgesIn = neighborMaps.get(2);
		neighborEdgesOut = neighborMaps.get(3);
		determineAllInOut();
		
		this.undirected = undirected;
		
		this.validationPercentage = 0.005;
		this.testingPercentage = 0.01;
		
		this.sep = "\t";
		this.nl = "\n";
		logger.info("New DataGenerator class successfully creatd.");
	}
	
	public DataGenerator(final InMemoryRdfGraph graph, final Configuration config, 
					final boolean undirected, final BookmarkColoringNodes BCA,
					final HashMap<String, Integer> uniqueRelationTypes) throws Exception {
		this.graph = graph;
		this.config = config;

//###			//this.approxDays = config.approximateDays;
		this.approxDays = 7;
		
		this.inVerts = BCA.getInVertices();
		this.outVerts = BCA.getOutVertices();
		this.inEdges = BCA.getInEdges();
		this.outEdges = BCA.getOutEdges();
		if (uniqueRelationTypes == null || uniqueRelationTypes.size() == 0) 
			throw new Exception("Variable map uniqueRelationTypes not properly initialized.");
		this.uniqueRelationTypes = uniqueRelationTypes;
		this.uniqueTypeRelations = new HashMap<Integer, String>();
	    this.trainingTriples = new ArrayList<int[]>();
		reverseMap();

    	neighborMaps = determineEdgeNeighbors(this.inVerts,
										this.outVerts,
										this.inEdges,
										this.outEdges);
		/*
		 * Resulting maps are of the edgeIDs, *NOT* original edge indices!
		 */
		neighborVertsIn = neighborMaps.get(0);
		neighborVertsOut = neighborMaps.get(1);
		neighborEdgesIn = neighborMaps.get(2);
		neighborEdgesOut = neighborMaps.get(3);
		determineAllInOut();
		
		
		this.undirected = undirected;
		
		this.validationPercentage = 0.2;
		this.testingPercentage = 0.3;
		
		this.sep = "\t";
		this.nl = "\n";
		logger.info("New DataGenerator class successfully created.");
	}
	
	public DataGenerator(final InMemoryRdfGraph graph, final Configuration config, 
					final boolean undirected,
					final HashMap<String, Integer> uniqueRelationTypes,
					final String FILEPATH, final String fileExtension,
					final BookmarkColoringNodes bcaNodes, 
					final BookmarkColoringEdges bcaEdges) throws Exception {
		this.graph = graph;
		this.config = config;
		this.FILEPATH = FILEPATH;
		this.fileExtension = fileExtension;

//###			//this.approxDays = config.approximateDays;
		this.approxDays = 7;
		
		this.inVerts = bcaNodes.getInVertices();
		this.outVerts = bcaNodes.getOutVertices();
		this.inEdges = bcaNodes.getInEdges();
		this.outEdges = bcaNodes.getOutEdges();

		try {
			if (uniqueRelationTypes == null || uniqueRelationTypes.size() == 0) 
				throw new Exception("Variable map uniqueRelationTypes not properly initialized.");
			this.uniqueRelationTypes = uniqueRelationTypes;
			
			this.uniqueTypeRelations = new HashMap<Integer, String>();
		    this.trainingTriples = new ArrayList<int[]>();
			reverseMap();
	
	    	neighborMaps = determineEdgeNeighbors(this.inVerts,
											this.outVerts,
											this.inEdges,
											this.outEdges);
			/*
			 * Resulting maps are of the edgeIDs, *NOT* original edge indices!
			 */
			neighborVertsIn = neighborMaps.get(0);
			neighborVertsOut = neighborMaps.get(1);
			neighborEdgesIn = neighborMaps.get(2);
			neighborEdgesOut = neighborMaps.get(3);
			determineAllInOut();
			
			
			this.undirected = undirected;
			
			this.validationPercentage = 0.2;
			this.testingPercentage = 0.3;
			
			this.sep = "\t";
			this.nl = "\n";

			randomDataSeed();
			
			generateTrainingTriples();
			generateValidateTriples();
			generateTestingTriples();
			generateTrainingRules();
			generateNodeBCAVectors(bcaNodes, true);
			generateEdgeBCAVectors(bcaEdges, true);

			logger.info("New DataGenerator class successfully created.");
		}catch (Exception e) { e.printStackTrace(); }
	}
	
	
	/**
	 * 
	 * @param FILEPATH
	 * @param fileExtension
	 * @param BCA
	 * @throws Exception
	 */
	public void Initialize(final String FILEPATH, final String fileExtension,
							final BookmarkColoringNodes bcaNodes, 
							final BookmarkColoringEdges bcaEdges) throws Exception {	

		randomDataSeed();
		
		this.FILEPATH = FILEPATH;
		this.fileExtension = fileExtension;
		
		generateTrainingTriples();
		generateValidateTriples();
		generateTestingTriples();
		//generateTrainingRules();
		generateTrainingRules();
		generateNodeBCAVectors(bcaNodes, true);
		generateEdgeBCAVectors(bcaEdges, true);
		
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
	    
	    logger.info("Sarting generating random data seed.");
	    try {
		    Random rand = new Random();
		    int[] verts = graph.getVertices().toIntArray();
		    int iNumVerts = verts.length;
		    
		    ArrayList<Integer> takenInts = new ArrayList<Integer>();
		    
		    // Calculate number of elements per dataset.
		    int iNumValidElems = (int) (iNumVerts * this.validationPercentage);
		    int iNumTestElems = (int) (iNumVerts * this.testingPercentage);
		    int iNumTrainElems = iNumVerts - (iNumValidElems + iNumTestElems);
		    // Initialize datasets.
		    this.trainSet = new int[iNumTrainElems];
		    this.validSet = new int[iNumValidElems];
		    this.testSet = new int[iNumTestElems];
		    
		    int[] triple;
		    
		    // Fill training set with random vertices. Omitting
		    // duplicates.
		    int i = 0, randIdx;
		    while (i < iNumTrainElems) {
		    	randIdx = verts[rand.nextInt(iNumVerts)];
		    	if (this.outVerts[randIdx].length > 0 && !takenInts.contains(randIdx)) {
		    		this.trainSet[i] = randIdx;
		    		takenInts.add(randIdx);
		    		i++;
		    		
		    		triple = new int[3];
					for (int j = 0; j < this.outVerts[randIdx].length; j++) {
						triple[0] = randIdx;
						triple[1] = graph.getEdgeTypeProperty().getValueAsInt(this.outEdges[randIdx][j]);
						triple[2] = this.outVerts[randIdx][j];
					}
					this.trainingTriples.add(triple);
					
					if (this.inVerts[randIdx].length > 0) {
			    		triple = new int[3];
						for (int j = 0; j < this.inVerts[randIdx].length; j++) {
							triple[0] = randIdx;
							triple[1] = graph.getEdgeTypeProperty().getValueAsInt(this.inEdges[randIdx][j]);
							triple[2] = this.inVerts[randIdx][j];
						}
						this.trainingTriples.add(triple);
					}
	    		}
	    		else if (this.inVerts[randIdx].length > 0 && !takenInts.contains(randIdx)) {
		    		this.trainSet[i] = randIdx;
		    		takenInts.add(randIdx);
		    		i++;
		    		
		    		triple = new int[3];
					for (int j = 0; j < this.inVerts[randIdx].length; j++) {
						triple[0] = randIdx;
						triple[1] = graph.getEdgeTypeProperty().getValueAsInt(this.inEdges[randIdx][j]);
						triple[2] = this.inVerts[randIdx][j];
					}
					this.trainingTriples.add(triple);
	    		}
		    }
		    // Fill validation set with random vertices. Omitting
		    // duplicates.
		    i = 0;
		    while (i < iNumValidElems) {
		    	randIdx = verts[rand.nextInt(iNumVerts)];
		    	if (this.outVerts[randIdx].length > 0 && !takenInts.contains(randIdx)) {
		    		this.validSet[i] = randIdx;
		    		takenInts.add(randIdx);
		    		i++;
	    		} 
	    		else if (this.inVerts[randIdx].length > 0 && !takenInts.contains(randIdx)) {
		    		this.validSet[i] = randIdx;
		    		takenInts.add(randIdx);
		    		i++;
	    		} 
		    }
		    // Fill test set with remaining vertices. Omitting
		    // duplicates.
		    int idx;
		    i = 0;
		    while (i < iNumTestElems) {
		    	for (int j = 0; j < iNumVerts; j++) {
		    		idx = verts[j];
		    		if (this.outVerts[idx].length > 0 && !takenInts.contains(idx)) {
		    			this.testSet[i] = idx;
		    			i++;
		    		}
		    		else if (this.inVerts[idx].length > 0 && !takenInts.contains(idx)) {
		    			this.testSet[i] = idx;
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
		    logger.info("Finished randomDataSeed(). Size of this.trainingTriples = "+this.trainingTriples.size());
	    } catch (Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * Reads the graph and converts them to file specific format.
	 * @throws Exception
	 */
	public void generateTrainingTriples() throws Exception {
		logger.info("Generating Training Triples initiated.");
		this.fnTrainingTriples = generateFileDir("training_triples");
		//BufferedWriter writer = new BufferedWriter(new FileWriter(this.fnTrainingTriples));
		
		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		boolean fillTripleList = false;
		if (this.trainingTriples.size() == 0) fillTripleList = true;

		try {
			//try (ProgressBar pb = Configuration.progressBar("Writing training triples to file", nrLines, "triples")) {
			//writer.write("this.trainSet.length = " + this.trainSet.length + nl);
			int node, edge;
			int tripleCntr = 0;
			int[] triple = new int[3];
			for (int i = 0; i < this.trainSet.length; i++) {
				node = this.trainSet[i];
				for (int j = 0; j < this.outVerts[node].length; j++) {					
					line = Integer.toString(node) + sep;
					edge = this.outEdges[node][j];
					//line += Integer.toString(this.edgeIdMap.get(edge)) + sep;
					line += Integer.toString(graph.getEdgeTypeProperty().getValueAsInt(edge)) + sep;
					line += Integer.toString(this.outVerts[node][j]) + nl;
					lines.add(line);
					
					triple[0] = node;
					triple[1] = graph.getEdgeTypeProperty().getValueAsInt(edge);
					triple[2] = this.outVerts[node][j];
				}
				if (fillTripleList) this.trainingTriples.add(triple);
			}
			if (this.undirected) {
				lines.add("#reversed direction" + nl);
				for (int i = 0; i < this.trainSet.length; i++) {
					for (int j = 0; j < this.inVerts[this.trainSet[i]].length; j++) {
						line = Integer.toString(this.trainSet[i]) + sep;
						//line += Integer.toString(this.edgeIdMap.get(this.inEdges[this.trainSet[i]][j])) + sep;
						line += Integer.toString(graph.getEdgeTypeProperty().getValueAsInt(this.inEdges[this.trainSet[i]][j])) + sep;
						line += Integer.toString(this.inVerts[this.trainSet[i]][j]) + nl;
						lines.add(line);
						
						triple[0] = this.trainSet[i];
						triple[1] = graph.getEdgeTypeProperty().getValueAsInt(this.inEdges[this.trainSet[i]][j]);
						triple[2] = this.inVerts[this.trainSet[i]][j];
					}
					if (fillTripleList) this.trainingTriples.add(triple);
				}
			}
			if (lines.size() <= 0) throw new Exception("DataGenerator.generateTrainingTriples() does not generate triples.");
		} catch (Exception e) { e.printStackTrace(); }
		//}
		//writer.close();
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTrainingTriples, this.config, this.fileExtension);
		kaleWriter.write(lines);
		logger.info("Training Triples generated and written to file.");
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	public void generateValidateTriples() throws Exception {
		this.fnValidateTriples = generateFileDir("validation_triples");

		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		int node, edge;
		try {
			for (int i = 0; i < this.validSet.length; i++) {
				node = this.validSet[i];
				for (int j = 0; j < this.outVerts[node].length; j++) {
					line = Integer.toString(node) + sep;
					edge = this.outEdges[node][j];
					//line += Integer.toString(this.edgeIdMap.get(edge)) + sep;
					line += Integer.toString(graph.getEdgeTypeProperty().getValueAsInt(edge)) + sep;
					line += Integer.toString(this.outVerts[node][j]) + nl;
					lines.add(line);
				}
			}
			if (this.undirected) {
				lines.add("#reversed direction" + nl);
				for (int i = 0; i < this.validSet.length; i++) {
					node = this.validSet[i];
					for (int j = 0; j < this.inVerts[node].length; j++) {
						line = Integer.toString(node) + sep;
						//line += Integer.toString(this.edgeIdMap.get(this.inEdges[node][j])) + sep;
						line += Integer.toString(graph.getEdgeTypeProperty().getValueAsInt(this.inEdges[node][j])) + sep;
						line += Integer.toString(this.inVerts[node][j]) + nl;
						lines.add(line);
					}
				}
			}
			if (lines.size() <= 0) throw new Exception("DataGenerator.generateValidateTriples() does not generate triples.");
		} catch (Exception e) { e.printStackTrace(); }

		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnValidateTriples, this.config, this.fileExtension);
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
			int node, edge, edgeType;
			for (int i = 0; i < this.testSet.length; i++) {
				node = this.testSet[i];
				for (int j = 0; j < this.outVerts[node].length; j++) {
					line = Integer.toString(this.testSet[i]) + sep;
					edge = this.outEdges[node][j];
					//line += Integer.toString(this.edgeIdMap.get(edge)) + sep;
					line += Integer.toString(graph.getEdgeTypeProperty().getValueAsInt(edge)) + sep;
					line += Integer.toString(this.outVerts[node][j]) + nl;
					lines.add(line);
				}
			}
			if (this.undirected) {
				lines.add("#reversed direction" + nl);
				for (int i = 0; i < this.testSet.length; i++) {
					for (int j = 0; j < this.inVerts[this.testSet[i]].length; j++) {
						line = Integer.toString(this.testSet[i]) + sep;
						//line += Integer.toString(this.edgeIdMap.get(this.inEdges[this.testSet[i]][j])) + sep;
						line += Integer.toString(graph.getEdgeTypeProperty().getValueAsInt(this.inEdges[this.testSet[i]][j])) + sep;
						line += Integer.toString(this.inVerts[this.testSet[i]][j]) + nl;
						lines.add(line);
					}
				}
			}
			if (lines.size() <= 0) throw new Exception("DataGenerator.generateTestingTriples() does not generate triples.");
		} catch (Exception e) { e.printStackTrace(); }

		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTestingTriples, this.config, this.fileExtension);
		kaleWriter.write(lines);
	}
	
	/*public void generateTrainingRules() throws Exception {
		this.fnTrainingRules = generateFileDir("training_rules");
		// Create list with rules.
		ArrayList<String> rulesList = new ArrayList<String>();
		ArrayList<String> properRulesList = new ArrayList<String>();
		
		rulesList.add("birth_date_approx(x,y)" + this.sep + "&" + this.sep + "death_date_approx(x,z)" + this.sep + "==>" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("birth_date_approx(x,y)" + this.sep + "==>" + this.sep + "death_date_approx(x,z)" + this.sep + "&" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("!" + this.sep + "_is_same_or_before(y,z)" + this.sep + "==>" + this.sep + 
				"!" + this.sep + "birth_date_approx(x,y)" + this.sep + 
				"|" + this.sep + "!" + this.sep + "death_date_approx(x,z)" + nl);
		
		rulesList.add("birth_date_approx(x,y)" + this.sep + "&" + this.sep + "baptism_date_approx(x,z)" + this.sep + "==>" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("birth_date_approx(x,y)" + this.sep + "==>" + this.sep + "baptism_date_approx(x,z)" + this.sep + "&" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("!" + this.sep + "_is_same_or_before(y,z)" + this.sep + "==>" + this.sep + 
				"!" + this.sep + "birth_date_approx(x,y)" + this.sep + 
				"|" + this.sep + "!" + this.sep + "baptism_date_approx(x,z)" + nl);
		
		rulesList.add("baptism_date_approx(x,y)" + this.sep + "&" + this.sep + "death_date_approx(x,z)" + this.sep + "==>" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("baptism_date_approx(x,y)" + this.sep + "==>" + this.sep + "death_date_approx(x,z)" + this.sep + "&" + this.sep + "_is_same_or_before(y,z)" + nl);
		rulesList.add("!" + this.sep + "_is_same_or_before(y,z)" + this.sep + "==>" + this.sep + 
				"!" + this.sep + "baptism_date_approx(x,y)" + this.sep + 
				"|" + this.sep + "!" + this.sep + "death_date_approx(x,z)" + nl);
		
		/*
		 * De rules die moeten worden weggeschreven moeten kloppende rules zijn die
		 * in de graaf daadwerkelijk voorkomen.
		 

		//RuleSet proxyRuleSet = new RuleSet(rulesList, this.uniqueRelationTypes);
		//properRulesList = generateTrainingRuless();
		//this.properRuleSet = new RuleSet(properRulesList, this.uniqueRelationTypes);
		
		
		/*KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTrainingRules, this.config);
		kaleWriter.write(properRulesList);
	}*/
	
	private void generateTrainingRules() throws Exception {
		try {
			this.fnTrainingRules = generateFileDir("training_rules");
		
			ArrayList<String> properTrainingRules = new ArrayList<String>();
			final String PERSON_URI = "/person";
			
			int personCntr = 0;
			Person person;
			String strRule = "";
			int[] triple = new int[3];
			if (this.trainingTriples.size() == 0)
				throw new Exception("DataGenerator.generateTrainingRules() has received invalid amount of training triples: "+this.trainingTriples.size());
			
			for (int i = 0; i < this.trainingTriples.size(); i++) {
				if (properTrainingRules.size() >= 1000) break;

				triple = this.trainingTriples.get(i);
				
				if(graph.getVertexLabelProperty().getValueAsString(triple[0]).contains(PERSON_URI)) {
					person = new Person(triple[0], graph, this.inVerts, this.outVerts, this.inEdges, this.outEdges);
					if (person.isProperPerson()) personCntr++;
				} else continue;
				
				strRule = "";
				// Birth date and death date.
				if (person.getBirthDate() != "" && person.getDeathDate() != "") {
					strRule = "birth_date_approx(" + person.getVertex() + "," + person.getVertBirthDate() +")";
					
					if (true/*person.birthBeforeDeath(this.approxDays)*/) {
						strRule += this.sep + "&" + this.sep;
						strRule += "death_date_approx(" + person.getVertex() + "," + person.getVertDeathDate() +")";
						strRule += this.sep + "==>" + this.sep;
						strRule += "is_same_or_before(" + person.getVertBirthDate() + "," + person.getVertDeathDate() +")";
						strRule +=  this.nl;
						properTrainingRules.add(strRule);
					}
				} // Birth date and baptism date.
				/*if (person.getBirthDate() != "" && person.getBaptismDate() != "") {
					strRule = "birth_date_approx(" + person.getVertex() + "," + person.getVertBirthDate() +")";
					
					if (true/*person.birthBeforeBaptism(this.approxDays)*) {
						strRule += this.sep + "&" + this.sep;
						strRule += "baptism_date_approx(" + person.getVertex() + "," + person.getVertBaptismDate() +")";
						strRule += this.sep + "==>" + this.sep;
						strRule += "is_same_or_before(" + person.getVertBirthDate() + "," + person.getVertBaptismDate() +")";
						strRule +=  this.nl;
						properTrainingRules.add(strRule);
					}
				} // Baptism date and death date.
				/*if (person.getBaptismDate() != "" && person.getDeathDate() != "") {
					strRule = "baptism_date_approx(" + person.getVertex() + "," + person.getVertBaptismDate() +")";
					
					if (true/*person.birthBeforeDeath(this.approxDays)*) {
						strRule += this.sep + "&" + this.sep;
						strRule += "death_date_approx(" + person.getVertex() + "," + person.getVertDeathDate() +")";
						strRule += this.sep + "==>" + this.sep;
						strRule += "is_same_or_before(" + person.getVertBaptismDate() + "," + person.getVertDeathDate() +")";
						strRule +=  this.nl;
						properTrainingRules.add(strRule);
					}
				}*/
			}
			
			if (personCntr <= 0)
				throw new Exception("DataGenerator.generateTrainingRules() never received any person vertices");
			if (properTrainingRules.size() <= 0)
				throw new Exception("DataGenerator.generateTrainingRules() received "+ personCntr +" persons, but properTrainingRules.size() = "+properTrainingRules.size());
			
			//return properTrainingRules;
			/*if (!this.uniqueRelationTypes.containsKey("is_same_or_before")) {
				int newType = this.uniqueRelationTypes.size();
				while (this.uniqueRelationTypes.containsValue(newType))
					newType++;
				this.uniqueRelationTypes.put("is_same_or_before", newType);
			}*/
			
			this.properRuleSet = new RuleSet(properTrainingRules, this.uniqueRelationTypes);
			logger.info("Number of training rules generated: "+properTrainingRules.size());
			KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnTrainingRules, this.config, this.fileExtension);
			kaleWriter.write(properTrainingRules);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	class Person {
		
        String name = "";
        int vertex;
        String strBirthDate = "";
        String strDeathDate = "";
        String strBaptismDate = "";
        int vertBirthDate;
        int vertDeathDate;
        int vertBaptismDate;
        LocalDate birthDate;
        LocalDate deathDate;
        LocalDate baptismDate;
        //int[] birthDate;
        //int[] deathDate;
        //int[] baptismDate;
        //DateDays dateFormatter;
       // DateDays deathDate;
        //DateDays baptismDate;
        
        double smooth = 3;
        double distance;
        private final Configuration.SimilarityGroup.Time timeDirection;
        private String time = "BIDIRECTIONAL";
        DateTimeFormatter format;
        String pattern = "yyyy-MM-dd";
        
        boolean hasEnoughDates = true;

        public Person(int vert, InMemoryRdfGraph graph, int[][] inVertex, int[][] outVertex, int[][] inEdge, int[][] outEdge) {
            // construct values from graph here
        	format = pattern.equals("iso") ? DateTimeFormatter.BASIC_ISO_DATE : DateTimeFormatter.ofPattern(pattern);
        	timeDirection = getTimeEnum();
        	
            for (int i = 0; i < outVertex[vert].length; i++) {
            	int outVert = outVertex[vert][i];
                int edge = outEdge[vert][i];
                
                String predicate = graph.getEdgeLabelProperty().getValueAsString(edge).toLowerCase();
                String value = graph.getVertexLabelProperty().getValueAsString(outVert);
                
                if(predicate.contains("name")) {
                    this.name = value;
                } else if (predicate.contains("birth_date")) {
                    /* Check org.uu.nl.embedding.util.similarity.Date.java to see how to 
                     * get a specific formatter for a pattern (e.g. yyyy-MM-dd)
                     */
                    this.birthDate = parseToDate(value);
                	this.strBirthDate = this.birthDate.toString();
                	this.vertBirthDate = outVert;
                	
                } else if (predicate.contains("death_date")) {
                    //this.deathDate = LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
                    this.deathDate = parseToDate(value);
                	this.strDeathDate = this.deathDate.toString();
                	this.vertDeathDate = outVert;
                	
                } else if (predicate.contains("baptism_date")) {
                    this.baptismDate = parseToDate(value);
                	this.strBaptismDate = this.baptismDate.toString();
                	this.vertBaptismDate = outVert;
                }
            }
            int emptyCntr = 0;
            if (this.strBirthDate == "") emptyCntr++;
            if (this.strBirthDate == "") emptyCntr++;
            if (this.strBirthDate == "") emptyCntr++;
            
            if (emptyCntr > 1) hasEnoughDates = false;
        }
        
        public boolean birthBeforeDeath(final int daysAccuracy) {
        	double similarity = daysBetween(this.birthDate, this.deathDate, false);
        	//long daysBirthBeforeDeath = SimpleDate.calculateDaysBetween(this.birthDate, this.deathDate);
        	if (similarity > 0 && similarity < (double) daysAccuracy) return true;
        	if (similarity < 0 && (similarity * -1) < (double) daysAccuracy) return true;
        	return false;
        }
        
        public boolean birthBeforeBaptism(final int daysAccuracy) {
        	double similarity = daysBetween(this.birthDate, this.baptismDate, false);
        	//long daysBirthBeforeBaptism = SimpleDate.calculateDaysBetween(this.birthDate, this.baptismDate);
        	if (similarity > 0 && similarity < (double) daysAccuracy) return true;
        	if (similarity < 0 && (similarity * -1) < (double) daysAccuracy) return true;
        	return false;
        }
        
        public boolean baptismBeforeDeath(final int daysAccuracy) {
        	double similarity = daysBetween(this.birthDate, this.baptismDate, false);
        	//long daysBaptismBeforeDeath = SimpleDate.calculateDaysBetween(this.baptismDate, this.deathDate);
        	if (similarity > 0 && similarity < (double) daysAccuracy) return true;
        	if (similarity < 0 && (similarity * -1) < (double) daysAccuracy) return true;
        	return false;
        }
        
        public boolean isProperPerson() {
        	return this.hasEnoughDates;
        }
        
        public String getName() {
        	return this.name;
        }
        
        public int getVertex() {
        	return this.vertex;
        }
        
        public String getBirthDate() {
        	return this.strBirthDate;
        }
        
        public int getVertBirthDate() {
        	return this.vertBirthDate;
        }
        
        public String getDeathDate() {
        	return this.strDeathDate;
        }
        
        public int getVertDeathDate() {
        	return this.vertDeathDate;
        }
        
        public String getBaptismDate() {
        	return this.strBaptismDate;
        }
        
        public int getVertBaptismDate() {
        	return this.vertBaptismDate;
        }
        
        private Time getTimeEnum() {
            return Time.valueOf(this.time.toUpperCase());
        }
        
        private LocalDate parseToDate(String strDate) {
        	
            final int s1hat = strDate.indexOf('^');
            if(s1hat != -1) strDate = strDate.substring(0, s1hat);
            return LocalDate.parse(strDate, format);
        }
        
        private double daysBetween(LocalDate d1, LocalDate d2, boolean doSmooth) {

            switch (timeDirection) {
                case BACKWARDS:
                    if(d1.isAfter(d2)) return 0;
                    break;
                case FORWARDS:
                    if(d1.isBefore(d2)) return 0;
                    break;
                case BIDIRECTIONAL:
                	break;
            }
            if(doSmooth) return Math.pow(Math.abs(Math.abs((double) unit().between(d1, d2)) - distance) + 1, smooth - 1);
            return (double) unit().between(d1, d2);
        }

        private ChronoUnit unit() {
            return ChronoUnit.DAYS;
        }
	
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
	
	/*
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
		
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnGloveVectors, this.config, this.fileExtension);
		kaleWriter.write(lines);
	}

	public void generateNodeBCAVectors(final BookmarkColoringNodes BCA, final boolean singleLines) throws Exception {
		/*
		 * FILE FORMAT:
		 * 
		 * line <- "nodeID1\t neighborID1: value1\t neighborID2: value2\t ...\t neighborIDn: valueN"
		 * ...
		 */
		this.fnNodeBCAVectors = generateFileDir("node_bca_vectors");

		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		for (int i = 0; i < BCA.getBCVs().size(); i++) {
			// Write line to file.
			line = BCA.getBCVs().get(i).toString() + nl;
			lines.add(line);
		}
		
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnNodeBCAVectors, this.config, this.fileExtension);
		kaleWriter.write(lines);
	}
	
	public void generateEdgeBCAVectors(final BookmarkColoringEdges BCA, final boolean singleLines) throws Exception {
		/*
		 * FILE FORMAT:
		 * 
		 * line <- "nodeID1\t neighborID1: value1\t neighborID2: value2\t ...\t neighborIDn: valueN"
		 * ...
		 */
		this.fnEdgeBCAVectors = generateFileDir("edge_bca_vectors");

		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		for (int i = 0; i < BCA.getBCVs().size(); i++) {
			// Write line to file.
			line = BCA.getBCVs().get(i).toString() + nl;
			lines.add(line);
		}
		
		KaleEmbeddingTextWriter kaleWriter = new KaleEmbeddingTextWriter(this.fnEdgeBCAVectors, this.config, this.fileExtension);
		kaleWriter.write(lines);
	}
	
	/**
	 * Generates file directory without overwriting existing
	 * files by iterating through numbers at start of file.
	 * 
	 * @param fnDirName
	 * @return
	 */
	public String generateFileDir(final String fnDirName) throws Exception {
		if (this.fileExtension == null) throw new Exception("File type not initialized.");
		
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
	
	public static String generateFileDir(final String FILEPATH,
											final String fileExtension,
											final String fnDirName) throws Exception {
		if (fileExtension == null) throw new Exception("File type not initialized.");
		if (FILEPATH == null) throw new Exception("File path not initialized.");
		if (fnDirName == null) throw new Exception("Directory name not initialized.");
		// Check for existence of directory.
		File pathDir = new File(FILEPATH);
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
		String fnDir = FILEPATH + "/" + num + "_" + fnDirName + "_KALE_GLOVE" + fileExtension;
		//String fnDir = num + "_" + fnDirName + "_KALE_GLOVE";
		File f = new File(fnDir);
		while (f.exists()) {
			num++;
			fnDir = FILEPATH + "/" + num + "_" + fnDirName + "_KALE_GLOVE" + fileExtension;
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
	
	public static String checkFileExists(final String FILEPATH, final String fileExtension, final String fnDirName, final int num) {
		
		String fnDir = FILEPATH + "/" + num + "_" + fnDirName + "_KALE_GLOVE" + fileExtension;
		File f = new File(fnDir);
		
		if (f.exists()) return fnDir;
		else return "";
	}
	
	public static ArrayList<TreeMap<Integer, int[]>> determineEdgeNeighbors(final int[][] inVerts, final int[][] outVerts, 
																		final int[][] inEdges, final int[][] outEdges) throws Exception {
		
		ArrayList<TreeMap<Integer, int[]>> neighborMaps = new ArrayList<TreeMap<Integer, int[]>>();

		TreeMap<Integer, int[]> neighborVertsIn = new TreeMap<Integer, int[]>();
		TreeMap<Integer, int[]> neighborVertsOut = new TreeMap<Integer, int[]>();
		TreeMap<Integer, int[]> neighborEdgesIn = new TreeMap<Integer, int[]>();
		TreeMap<Integer, int[]> neighborEdgesOut = new TreeMap<Integer, int[]>();
		
		int edge, cntr;
		int[] neighbors, edges, verts;
		int[] neighborsOld, neighborsNew;
		
		for (int i = 0; i < inEdges.length; i++) {
			verts = inVerts[i];
			edges = inEdges[i];
			for (int j = 0; j < edges.length; j++) {
				edge = edges[j];
				neighbors = new int[1];
				neighbors[0] = i;
				//System.out.println("verts[j] =  " + verts[j]);
				//neighbors[1] = verts[j];
				neighborVertsIn.put(edge, neighbors);
				/*if (neighborVertsIn.containsKey(edge)) {
					System.out.println("neighborVertsIn contained key: " + edge);
					neighborsOld = neighborEdgesIn.get(edge);
					neighborsNew = new int[neighborsOld.length + neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborVertsIn.put(edge, neighborsNew);
				} else neighborVertsIn.put(edge, neighbors);*/
				
				cntr = 0;
				neighbors = new int[inEdges[i].length + inEdges[verts[j]].length];
				for (int k = 0; k < inEdges[i].length; k++, cntr++) {
					neighbors[cntr] = inEdges[i][k];
				}
				for (int k = 0; k < inEdges[verts[j]].length; k++) {
					neighbors[cntr++] = inEdges[verts[j]][k];
				}
				if (neighborEdgesIn.containsKey(edge)) {
					System.out.println("neighborEdgesIn contained key: " + edge);
					neighborsOld = neighborEdgesIn.get(edge);
					neighborsNew = new int[neighborsOld.length + neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborEdgesIn.put(edge, neighborsNew);
				} else neighborEdgesIn.put(edge, neighbors);
			}
		}
		
		for (int i = 0; i < outEdges.length; i++) {
			verts = outVerts[i];
			edges = outEdges[i];
			for (int j = 0; j < edges.length; j++) {
				edge = edges[j];
				neighbors = new int[1];
				neighbors[0] = i;
				//neighbors[1] = verts[j];
				neighborVertsOut.put(edge, neighbors);
				
				cntr = 0;
				neighbors = new int[outEdges[i].length + outEdges[verts[j]].length];
				for (int k = 0; k < outEdges[i].length; k++, cntr++) {
					neighbors[cntr] = outEdges[i][k];
				}
				for (int k = 0; k < outEdges[verts[j]].length; k++) {
					neighbors[cntr++] = outEdges[verts[j]][k];
				}
				if (neighborEdgesOut.containsKey(edge)) {
					System.out.println("neighborEdgesIn contained key: " + edge);
					neighborsOld = neighborEdgesIn.get(edge);
					neighborsNew = new int[neighborsOld.length+neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborEdgesOut.put(edge, neighborsNew);
				} else neighborEdgesOut.put(edge, neighbors);
			}
		}
		
		neighborMaps.add(neighborVertsIn);
		neighborMaps.add(neighborVertsOut);
		neighborMaps.add(neighborEdgesIn);
		neighborMaps.add(neighborEdgesOut);
		/*try {
			String str1 = "";
			//for (int i = 0; i < neighborVertsIn.size(); i++) str1 += "in: " + neighborVertsIn.get(i)[0] + ", out: " + neighborVertsOut.get(i)[0] + "\n"; 
			//System.out.println("DataGenerator.determineEdgeNeighbors() - neighborVerts:\n" + str1);
			String str2 = "";
			for (int i = 0; i < neighborEdgesIn.size(); i++) {
				if (neighborEdgesIn.get(i).length > 10 && neighborEdgesIn.get(i).length < 15) { 
					for (int j = 0; j < neighborEdgesIn.get(i).length; j++) {
						str2 += "in: " + neighborEdgesIn.get(i)[j] + " - "; 
					} str2 += "\n";
				}
			}
			System.out.println("DataGenerator.determineEdgeNeighbors() - neighborEdges:\n" + str2);
		} catch (Exception ex) {ex.printStackTrace();}*/
		
		return neighborMaps;
	}
	
	public ArrayList<TreeMap<Integer, int[]>> determineEdgeNeighbors() throws Exception {
	
		ArrayList<TreeMap<Integer, int[]>> neighborMaps = new ArrayList<TreeMap<Integer, int[]>>();
		
		TreeMap<Integer, int[]> neighborVertsIn = new TreeMap<Integer, int[]>();
		TreeMap<Integer, int[]> neighborVertsOut = new TreeMap<Integer, int[]>();
		TreeMap<Integer, int[]> neighborEdgesIn = new TreeMap<Integer, int[]>();
		TreeMap<Integer, int[]> neighborEdgesOut = new TreeMap<Integer, int[]>();
		
		int edge, cntr;
		int[] neighbors, edges, verts;
		int[] neighborsOld, neighborsNew;
		
		for (int i = 0; i < inEdges.length; i++) {
			verts = inVerts[i];
			edges = inEdges[i];
			for (int j = 0; j < edges.length; j++) {
				edge = this.edgeIdMap.get(edges[j]);
				neighbors = new int[1];
				neighbors[0] = i;
				//System.out.println("verts[j] =  " + verts[j]);
				//neighbors[1] = verts[j];
				if (neighborVertsIn.containsKey(edge)) {
					System.out.println("neighborVertsIn contained key: " + edge);
					neighborsOld = neighborVertsIn.get(edge);
					neighborsNew = new int[neighborsOld.length + neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborVertsIn.put(edge, neighborsNew);
				} else neighborVertsIn.put(edge, neighbors);
				
				cntr = 0;
				neighbors = new int[inEdges[i].length + inEdges[verts[j]].length];
				for (int k = 0; k < inEdges[i].length; k++) {
					neighbors[cntr++] = inEdges[i][k];
				}
				for (int k = 0; k < inEdges[verts[j]].length; k++) {
					neighbors[cntr++] = inEdges[verts[j]][k];
				}
				if (neighborEdgesIn.containsKey(edge)) {
					System.out.println("neighborEdgesIn contained key: " + edge);
					neighborsOld = neighborEdgesIn.get(edge);
					neighborsNew = new int[neighborsOld.length + neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborEdgesIn.put(edge, neighborsNew);
				} else neighborEdgesIn.put(edge, neighbors);
			}
		}
		
		for (int i = 0; i < outEdges.length; i++) {
			verts = outVerts[i];
			edges = outEdges[i];
			for (int j = 0; j < edges.length; j++) {
				edge = this.edgeIdMap.get(edges[j]);
				neighbors = new int[1];
				neighbors[0] = i;
				//neighbors[1] = verts[j];
				if (neighborVertsOut.containsKey(edge)) {
					System.out.println("neighborVertsIn contained key: " + edge);
					neighborsOld = neighborVertsOut.get(edge);
					neighborsNew = new int[neighborsOld.length + neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborVertsOut.put(edge, neighborsNew);
				} else neighborVertsOut.put(edge, neighbors);
				
				cntr = 0;
				neighbors = new int[outEdges[i].length + outEdges[verts[j]].length];
				for (int k = 0; k < outEdges[i].length; k++) {
					neighbors[cntr++] = outEdges[i][k];
				}
				for (int k = 0; k < outEdges[verts[j]].length; k++) {
					neighbors[cntr++] = outEdges[verts[j]][k];
				}
				if (neighborEdgesOut.containsKey(edge)) {
					System.out.println("neighborEdgesIn contained key: " + edge);
					neighborsOld = neighborEdgesIn.get(edge);
					neighborsNew = new int[neighborsOld.length+neighbors.length];
					for (int l = 0; l < neighborsOld.length; l++) neighborsNew[l] = neighborsOld[l];
					for (int l = 0; l < neighbors.length; l++) neighborsNew[l] = neighbors[l];
					neighborEdgesOut.put(edge, neighborsNew);
				} else neighborEdgesOut.put(edge, neighbors);
			}
		}
		
		neighborMaps.add(neighborVertsIn);
		neighborMaps.add(neighborVertsOut);
		neighborMaps.add(neighborEdgesIn);
		neighborMaps.add(neighborEdgesOut);
		/*try {
			String str1 = "";
			//for (int i = 0; i < neighborVertsIn.size(); i++) str1 += "in: " + neighborVertsIn.get(i)[0] + ", out: " + neighborVertsOut.get(i)[0] + "\n"; 
			//System.out.println("DataGenerator.determineEdgeNeighbors() - neighborVerts:\n" + str1);
			String str2 = "";
			for (int i = 0; i < neighborEdgesIn.size(); i++) {
				if (neighborEdgesIn.get(i).length > 10 && neighborEdgesIn.get(i).length < 15) { 
					for (int j = 0; j < neighborEdgesIn.get(i).length; j++) {
						str2 += "in: " + neighborEdgesIn.get(i)[j] + " - "; 
					} str2 += "\n";
				}
			}
			System.out.println("DataGenerator.determineEdgeNeighbors() - neighborEdges:\n" + str2);
		} catch (Exception ex) {ex.printStackTrace();}*/
		
		return neighborMaps;
	}
	
    public void determineAllInOut() throws Exception {
    	
    	TreeMap<Integer, int[]> lstAllIn = new TreeMap<Integer, int[]>();
    	TreeMap<Integer, int[]> lstAllOut = new TreeMap<Integer, int[]>();
    	ArrayList<Integer> lstNonEmptyVerts = new ArrayList<Integer>();
    	
    	for (int i = 0; i < this.outVerts.length; i++) {
			// Give new values if there is a BCV for this ID.
    		lstAllIn.put(i, this.inVerts[i]);
    		lstAllOut.put(i, this.outVerts[i]);
    		if (this.outVerts[i].length > 0) lstNonEmptyVerts.add(i);
    	}
		int typeHolder = 0;
    	this.nonEmptyVerts = ArrayUtils.toArray(lstNonEmptyVerts, typeHolder);
    	
    	int edge;
    	int[] neighbors;
		for (Map.Entry<Integer, int[]> entry : neighborEdgesIn.entrySet()) {
			edge = (int) entry.getKey();
			neighbors = neighborEdgesIn.get(edge);
    		lstAllIn.put(edge, neighbors);
		}
		for (Map.Entry<Integer, int[]> entry : neighborEdgesOut.entrySet()) {
			edge = (int) entry.getKey();
			neighbors = neighborEdgesOut.get(edge);
    		lstAllOut.put(edge, neighbors);
    	}

		this.allIn = new int[lstAllIn.size()][];
		this.allOut = new int[lstAllOut.size()][];
    	TreeMap<Integer, int[]> lstInVerts = new TreeMap<Integer, int[]>();
    	TreeMap<Integer, int[]> lstOutVerts = new TreeMap<Integer, int[]>();
		
		int i;
		for (Map.Entry<Integer, int[]> entry : lstAllIn.entrySet()) {
			i = (int) entry.getKey();
			if (lstAllIn.get(i) != null && lstAllIn.get(i).length > 0) {
				this.allIn[i] = lstAllIn.get(i);
				if (i < this.outVerts.length) {
					lstInVerts.put(i, lstAllIn.get(i));
				}
			}
			else { 
				this.allIn[i] = new int[0];
				if (i < this.outVerts.length) lstInVerts.put(i, lstAllIn.get(i));
			}
		}
		
    	ArrayList<Integer> lstNonEmptyAllVerts = new ArrayList<Integer>();
		for (Map.Entry<Integer, int[]> entry : lstAllOut.entrySet()) {
			i = (int) entry.getKey();
			if (lstAllOut.get(i) != null && lstAllOut.get(i).length > 0) {
				this.allOut[i] = lstAllOut.get(i);
				if (i < this.outVerts.length) {
					lstOutVerts.put(i, lstAllOut.get(i));
					lstNonEmptyAllVerts.add(i);
				}
			}
			else { 
				this.allOut[i] = new int[0];
				if (i < this.outVerts.length) lstOutVerts.put(i, lstAllOut.get(i));
			}
		}

		/*
		 * allInVert and allOutVert are only the non-empty vertices.
		 */
		this.allInVert = ArrayUtils.toArray(lstInVerts, typeHolder);
		this.allOutVert = ArrayUtils.toArray(lstOutVerts, typeHolder);
    	this.nonEmptyAllVerts = ArrayUtils.toArray(lstNonEmptyAllVerts, typeHolder);
    	
    	
    	
    	
    	
    	/*
    	int[][] allInInter = new int[dictSize][];
    	int[][] allOutInter = new int[dictSize][];
    	ArrayList<Integer> lstNonEmptyVerts = new ArrayList<Integer>();
		
    	int counter = 0;
    	for (int i = 0; i < this.outVerts.length; i++) {
			// Give new values if there is a BCV for this ID.
        	allInInter[i] = this.inVerts[i];
        	allOutInter[i] = this.outVerts[i];
        	counter++;
    	}
    	
    	int edge;
    	int[] neighbors;
    	int counterSaverIn = counter, counterSaverOut = counter;
		for (Map.Entry entry : neighborEdgesIn.entrySet()) {
			edge = (int) entry.getKey();
			neighbors = neighborEdgesIn.get(edge);
        	allInInter[edge] = neighbors;
        	counterSaverIn++;
		}
		for (Map.Entry entry : neighborEdgesOut.entrySet()) {
			edge = (int) entry.getKey();
			neighbors = neighborEdgesOut.get(edge);
        	allOutInter[edge] = neighbors;
        	counterSaverOut++;
    	}

    	int[][] allInInter2 = new int[counterSaverIn][];
    	int[][] allOutInter2 = new int[counterSaverOut][];
    	
		int inCntr = 0, outCntr = 0;
		int inCntrVert = 0, outCntrVert = 0;
		int i = 0;
		while (i < counterSaverIn) {
			if (allInInter[i] != null && allInInter[i].length > 0) {
				allInInter2[inCntr++] = allInInter[i++];
				if (i < this.outVerts.length) inCntrVert++;
			} else {
				i++;
				continue;
			}
		}
		i = 0;
		while (i < counterSaverOut) {
			if (allOutInter[i] != null && allOutInter[i].length > 0) {
				allOutInter2[outCntr++] = allOutInter[i++];
				if (i < this.outVerts.length) {
					lstNonEmptyVerts.add(i);
					outCntrVert++;
				}
			} else {
				i++;
				continue;
			}
		}
		
		/*int tempCntrIn = 0, tempCntrOut = 0;
		int inCntr = 0, outCntr = 0;
		for (int i = 0; i < counterSaverIn; i++) 
			if (allInInter[i] != null && allInInter[i].length > 0) {
				this.allIn[i] = allInInter[i];
				inCntr++;
			} else {
				this.allIn[i] = new int[0];
				tempCntrIn++;
			}
		for (int i = 0; i < counterSaverOut; i++) 
			if (allOutInter[i] != null && allOutInter[i].length > 0) {
				this.allOut[i] = allOutInter[i]; 
				outCntr++;
			} else {
				this.allOut[i] = new int[0];
				tempCntrOut++;
			}
		System.out.println("DataGenerator.determineAllInOut() - tempCntrIn for null = " + tempCntrIn);
		System.out.println("DataGenerator.determineAllInOut() - tempCntrOut for null = " + tempCntrOut);
		System.out.println("DataGenerator.determineAllInOut() - In counter for non-null = " + inCntr);
		System.out.println("DataGenerator.determineAllInOut() - Out counter for non-null = " + outCntr);
		System.out.println("DataGenerator.determineAllInOut() - lstNonEmptyVerts.size() = " + lstNonEmptyVerts.size());
		
		this.allIn = new int[inCntr][];
		this.allOut = new int[outCntr][];
		this.allInVert = new int[inCntrVert][];
		this.allOutVert = new int[outCntrVert][];
		int j = 0;
		for (i = 0; i < inCntr; i++) {
			if (allInInter2[i] != null && allInInter2[i].length > 0) {
				this.allIn[i] = allInInter2[i];
				if (j < inCntrVert && i < this.outVerts.length) this.allInVert[j++] = allInInter2[i];
			}
			else { this.allIn[i] = new int[0]; }
		}
		j = 0;
		for (i = 0; i < outCntr; i++) {
			if (allOutInter2[i] != null && allOutInter2[i].length > 0) {
				this.allOut[i] = allOutInter2[i];
				if (j < outCntrVert && i < this.outVerts.length) this.allOutVert[j++] = allOutInter2[i];
			}
			else { this.allOut[i] = new int[0]; }
		}

    	this.nonEmptyVerts = ArrayUtils.toArray(lstNonEmptyVerts, 0);*/
    }
	
    /**
     * 
     * @param outVerts
     * @return
     */
	public static int calculateDictSize(final int[][] outVerts) {
		int dictSize = 0;
		for (int i = 0; i < outVerts.length; i++) {
			dictSize += outVerts[i].length * 2;
		}
		return dictSize;
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
	 * @return
	 */
	public RuleSet getTrainingRules() throws Exception {
		if (this.properRuleSet == null) throw new Exception("RuleSet not initialized.");
		return this.properRuleSet;
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
	 * @return
	 */
	public int[] getNonEmptyVerts() {
		return this.nonEmptyVerts;
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
	public String getFilePath() throws Exception {
		if (this.FILEPATH == null)
			throw new Exception("DataGenerator.getFilePath() returns null.");
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
	public String getFileExtension() throws Exception {
		if (this.fileExtension == null)
			throw new Exception("DataGenerator.getFileExtension() returns null.");
		return this.fileExtension;
	}

	public String getFileTrainingTriples() throws Exception {
		if (this.fnTrainingTriples == null)
			throw new Exception("DataGenerator.getFileTrainingTriples() returns null.");
		return this.fnTrainingTriples;
	}

	public String getFileValidateTriples() throws Exception {
		if (this.fnValidateTriples == null)
			throw new Exception("DataGenerator.getFileValidateTriples() returns null.");
		return this.fnValidateTriples;
	}

	public String getFileTestingTriples() throws Exception {
		if (this.fnTestingTriples == null)
			throw new Exception("DataGenerator.getFileTestingTriples() returns null.");
		return this.fnTestingTriples;
	}

	public String getFileTrainingRules() throws Exception {
		if (this.fnTrainingRules == null)
			throw new Exception("DataGenerator.getFileTrainingRules() returns null.");
		return this.fnTrainingRules;
	}

	public String getFileGloveVectors() throws Exception {
		if (this.fnGloveVectors == null)
			throw new Exception("DataGenerator.getFileGloveVectors() returns null.");
		return this.fnGloveVectors;
	}

	public String getFileNodeBCVs() throws Exception {
		if (this.fnNodeBCAVectors == null)
			throw new Exception("DataGenerator.getFileNodeBCVs() returns null.");
		return this.fnNodeBCAVectors;
	}

	public String getFileEdgeBCVs() throws Exception {
		if (this.fnEdgeBCAVectors == null)
			throw new Exception("DataGenerator.getFileEdgeBCVs() returns null.");
		return this.fnEdgeBCAVectors;
	}
	
	private void reverseMap() {
		if (this.uniqueTypeRelations == null) this.uniqueTypeRelations = new HashMap<Integer, String>();
		
		for (Entry<String, Integer> entry : this.uniqueRelationTypes.entrySet()) {
			this.uniqueTypeRelations.put(entry.getValue(), entry.getKey());
		}
	}

}
