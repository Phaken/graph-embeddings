package org.uu.nl.embedding.kale.model;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.uu.nl.embedding.kale.struct.RuleSet;
import org.uu.nl.embedding.kale.struct.Triple;
import org.apache.log4j.Logger;
import org.uu.nl.embedding.kale.struct.KaleMatrix;
import org.uu.nl.embedding.kale.struct.TripleRule;
import org.uu.nl.embedding.kale.struct.TripleSet;
import org.uu.nl.embedding.kale.util.MetricMonitor;
import org.uu.nl.embedding.kale.util.NegativeRuleGenerator;
import org.uu.nl.embedding.kale.util.NegativeTripleGenerator;
import org.uu.nl.embedding.kale.util.StringSplitter;
import org.uu.nl.embedding.logic.util.FormulaSet;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class KaleModel {
	public TripleSet m_TrainingTriples;
	public TripleSet m_ValidateTriples;
	public TripleSet m_TestingTriples;
	public TripleSet m_Triples;
	public RuleSet m_TrainingRules;
	
	public KaleMatrix m_Entity_Factor_MatrixE;
	public KaleMatrix m_Relation_Factor_MatrixR;
	public KaleMatrix m_MatrixEGradient;
	public KaleMatrix m_MatrixRGradient;
	
	public KaleVectorMatrix kaleVectorMatrix;
	private InMemoryRdfGraph graph;
	private Configuration config;
	
	public int m_NumUniqueRelation;
	public int m_NumEntity;
	public int iBcvSize;
	public int m_NumGloveVecs;
	public String m_MatrixE_prefix = "";
	public String m_MatrixR_prefix = "";
	
	public int m_NumFactor = 20;
	public int m_NumMiniBatch = 100;
	public double m_Delta = 0.1;
	public double m_GammaE = 0.01;
	public double m_GammaR = 0.01;
	public int m_NumIteration = 1000;
	public int m_OutputIterSkip = 50;
	public double m_Weight = 0.01;
	
	public boolean isGlove;
	private ArrayList<Integer> coOccurrenceIdx_I = null;
	private ArrayList<Integer> coOccurrenceIdx_J = null;
	private ArrayList<Float> coOccurrenceValues = null;
	private ArrayList<Float> coOccurrenceGradient = null;
	private HashMap<Integer, Integer> orderedIdxMap = null;
	private TreeMap<Integer, Integer> edgeIdTypeMap = null;
	private ArrayList<Integer> iFirstEdges;
	private int maxNeighbors;
	
	/**
	 * 
	 */
	public String fileExtension = ".txt";
	
	java.text.DecimalFormat decimalFormat = new java.text.DecimalFormat("#.######");
    private final static Logger logger = Logger.getLogger("KaleModel");
	
	public KaleModel() {
	}
	
	/**
	 * 
	 * @param m_NumUniqueRelation
	 * @param iNumEntity
	 * @param fnTrainingTriples
	 * @param fnValidateTriples
	 * @param fnTestingTriples
	 * @param fnTrainingRules
	 * @throws Exception
	 * @author iieir-km, Euan Westenbroek
	 */
	public void Initialization(final int m_NumUniqueRelation, final int iNumEntity, final int iBcvSize,
			final String fnTrainingTriples, final String fnValidateTriples, final String fnTestingTriples,
			final String fnTrainingRules, final String fnGloveVectors, 
			final InMemoryRdfGraph graph, final Configuration config) throws Exception {
		
		logger.info("Start initializing KaleModel.");
		
		this.m_NumUniqueRelation = m_NumUniqueRelation;
		this.m_NumEntity = iNumEntity;
		this.iBcvSize = iBcvSize;
		this.m_NumGloveVecs = m_NumUniqueRelation + iNumEntity;
		this.orderedIdxMap = new HashMap<Integer, Integer>();
		this.maxNeighbors = 0;
		
		if (fnGloveVectors != null) this.isGlove = true;
		else this.isGlove = false;
		
		this.m_MatrixE_prefix = "MatrixE-k" + this.m_NumGloveVecs 
				+ "-d" + decimalFormat.format(m_Delta)
				+ "-ge" + decimalFormat.format(m_GammaE) 
				+ "-gr" + decimalFormat.format(m_GammaR)
				+ "-w" +  decimalFormat.format(m_Weight);
		this.m_MatrixR_prefix = "MatrixR-k" + this.m_NumGloveVecs 
				+ "-d" + decimalFormat.format(m_Delta)
				+ "-ge" + decimalFormat.format(m_GammaE) 
				+ "-gr" + decimalFormat.format(m_GammaR)
				+ "-w" +  decimalFormat.format(m_Weight);
		try {
			logger.info("Initialize loading training and validation triples.");
			this.m_TrainingTriples = new TripleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_ValidateTriples = new TripleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_Triples = new TripleSet();
			this.m_TrainingTriples.load(fnTrainingTriples);
			this.m_ValidateTriples.load(fnValidateTriples);
			this.m_Triples.loadStr(fnTrainingTriples);
			this.m_Triples.loadStr(fnValidateTriples);
			this.m_Triples.loadStr(fnTestingTriples);
			logger.info("Loading training and validation triples successful.");
			
			logger.info("Initialize loading grounding rules.");
			this.m_TrainingRules = new RuleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_TrainingRules.loadTimeLogic(fnTrainingRules);
			/*
//			 * Do stuff met die formulaSets
			 */
			ArrayList<FormulaSet> formulaSets = this.m_TrainingRules.getFormulaSets();
			logger.info("Loading grounding rules successful.");		
			
			logger.info("Initializing matrix E and matrix R randomly.");
			/*System.out.println("Entity Matrix size: " + (this.iBcvSize-this.m_NumUniqueRelation) + "x" + this.iBcvSize + "\n"
					+ "Predicate Matrix size: " + (this.m_NumUniqueRelation) + "x" + this.iBcvSize + "\n");*/
			this.m_Entity_Factor_MatrixE = new KaleMatrix(this.m_NumEntity, this.iBcvSize);
			this.m_Relation_Factor_MatrixR = new KaleMatrix(this.m_NumUniqueRelation, this.iBcvSize);
			if (this.isGlove)  {
				loadGloveVectors(fnGloveVectors);
				this.m_Entity_Factor_MatrixE = loadGloveEntityVectors();
				this.m_Relation_Factor_MatrixR = loadGloveRelationVectors();
				/*
				 *
				this.m_Entity_Factor_MatrixE.normalizeByRow();
				this.m_Relation_Factor_MatrixR.normalizeByRow(); 
				 */
			}
			else {
				this.m_Entity_Factor_MatrixE.setToRandom();
				this.m_Relation_Factor_MatrixR.setToRandom();
				this.m_Entity_Factor_MatrixE.normalizeByRow();
				this.m_Relation_Factor_MatrixR.normalizeByRow();
			}
			logger.info("Initializion matrix E and matrix R successful.");

			logger.info("Initializing gradients of matrix E and matrix R.");
			this.m_MatrixEGradient = new KaleMatrix(this.m_Entity_Factor_MatrixE.rows(), this.m_Entity_Factor_MatrixE.columns());
			this.m_MatrixRGradient = new KaleMatrix(this.m_Relation_Factor_MatrixR.rows(), this.m_Relation_Factor_MatrixR.columns());
			logger.info("Initialization gradients of matrix E and matrix R successfull.");
			logger.info("Model initialization successful.");
		} catch (Exception ex) { ex.printStackTrace(); }
	}
	
	/**
	 * 
	 * @param m_NumUniqueRelation
	 * @param iNumEntity
	 * @param fnTrainingTriples
	 * @param fnValidateTriples
	 * @param fnTestingTriples
	 * @param fnTrainingRules
	 * @throws Exception
	 * @author iieir-km, Euan Westenbroek
	 */
	public void Initialization(final int m_NumUniqueRelation, final int iNumEntity, final int iBcvSize,
			final String fnTrainingTriples, final String fnValidateTriples, final String fnTestingTriples,
			final String fnTrainingRules, final String fnGloveVectors, 
			final InMemoryRdfGraph graph, final Configuration config,
			final TreeMap<Integer, Integer> edgeIdTypeMap) throws Exception {
		
		logger.info("Start initializing KaleModel.");
		
		this.m_NumUniqueRelation = m_NumUniqueRelation;
		this.m_NumEntity = iNumEntity;
		this.iBcvSize = iBcvSize;
		this.m_NumGloveVecs = m_NumUniqueRelation + iNumEntity;
		this.edgeIdTypeMap = edgeIdTypeMap;
		this.orderedIdxMap = new HashMap<Integer, Integer>();
		this.iFirstEdges = new ArrayList<Integer>();
		this.maxNeighbors = 0;
		
		if (fnGloveVectors != null) this.isGlove = true;
		else this.isGlove = false;
		
		this.m_MatrixE_prefix = "MatrixE-k" + this.m_NumGloveVecs 
				+ "-d" + decimalFormat.format(m_Delta)
				+ "-ge" + decimalFormat.format(m_GammaE) 
				+ "-gr" + decimalFormat.format(m_GammaR)
				+ "-w" +  decimalFormat.format(m_Weight);
		this.m_MatrixR_prefix = "MatrixR-k" + this.m_NumGloveVecs 
				+ "-d" + decimalFormat.format(m_Delta)
				+ "-ge" + decimalFormat.format(m_GammaE) 
				+ "-gr" + decimalFormat.format(m_GammaR)
				+ "-w" +  decimalFormat.format(m_Weight);
		try {
			logger.info("Initialize loading training and validation triples.");
			this.m_TrainingTriples = new TripleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_ValidateTriples = new TripleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			/*
			 * STARTTEMP
			 */
			m_TrainingTriples.setOutverts(graph.getOutNeighborhoods());
			m_ValidateTriples.setOutverts(graph.getOutNeighborhoods());
			/*
			 * ENDTEMP
			 */
			this.m_Triples = new TripleSet();
			this.m_TrainingTriples.load(fnTrainingTriples);
			this.m_ValidateTriples.load(fnValidateTriples);
			this.m_Triples.loadStr(fnTrainingTriples);
			this.m_Triples.loadStr(fnValidateTriples);
			this.m_Triples.loadStr(fnTestingTriples);
			logger.info("Loading training and validation triples successful.");
			
			logger.info("Initialize loading grounding rules.");
			this.m_TrainingRules = new RuleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_TrainingRules.loadTimeLogic(fnTrainingRules);
			/*
//			 * Do stuff met die formulaSets
			 */
			ArrayList<FormulaSet> formulaSets = this.m_TrainingRules.getFormulaSets();
			logger.info("Loading grounding rules successful.");		
			
			System.out.println("Entity Matrix size: " + (this.iBcvSize-this.m_NumUniqueRelation) + "x" + this.iBcvSize + "\n"
					+ "Predicate Matrix size: " + (this.m_NumUniqueRelation) + "x" + this.iBcvSize + "\n");
			this.m_Entity_Factor_MatrixE = new KaleMatrix((this.iBcvSize-this.m_NumUniqueRelation), this.iBcvSize);
			this.m_Relation_Factor_MatrixR = new KaleMatrix(this.m_NumUniqueRelation, this.iBcvSize);
			if (this.isGlove)  {
				loadGloveVectors(fnGloveVectors);
				this.m_Entity_Factor_MatrixE = loadGloveEntityVectors();
				this.m_Relation_Factor_MatrixR = loadGloveRelationVectors();
				/*
				 *
				this.m_Entity_Factor_MatrixE.normalizeByRow();
				this.m_Relation_Factor_MatrixR.normalizeByRow(); 
				 */
			}
			else {
				logger.info("Initializing matrix E and matrix R randomly.");
				this.m_Entity_Factor_MatrixE.setToRandom();
				this.m_Relation_Factor_MatrixR.setToRandom();
				this.m_Entity_Factor_MatrixE.normalizeByRow();
				this.m_Relation_Factor_MatrixR.normalizeByRow();
			}
			logger.info("Initializion matrix E and matrix R successful.");

			logger.info("Initializing gradients of matrix E and matrix R.");
			this.m_MatrixEGradient = new KaleMatrix(this.m_Entity_Factor_MatrixE.rows(), this.m_Entity_Factor_MatrixE.columns());
			this.m_MatrixRGradient = new KaleMatrix(this.m_Relation_Factor_MatrixR.rows(), this.m_Relation_Factor_MatrixR.columns());
			logger.info("Initialization gradients of matrix E and matrix R successfull.");
			logger.info("Model initialization successful.");
		} catch (Exception ex) { ex.printStackTrace(); }
	}
	
	public void Initialization(String strNumRelation, String strNumEntity, String strIBcvSize,
			String fnTrainingTriples, String fnValidateTriples, String fnTestingTriples,
			String fnTrainingRules, final String fnGloveVectors, 
			final InMemoryRdfGraph graph, final Configuration config) throws Exception {
		
		m_NumUniqueRelation = Integer.parseInt(strNumRelation);
		m_NumEntity = Integer.parseInt(strNumEntity);
		iBcvSize = Integer.parseInt(strIBcvSize);
		
		Initialization(m_NumUniqueRelation, 
				m_NumEntity,
				iBcvSize,
				fnTrainingTriples, 
				fnValidateTriples, 
				fnTestingTriples, 
				fnTrainingRules,
				fnGloveVectors,
				graph,
				config); 
	}
	
	/**
	 * 
	 * @param fnGloveVectors
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void loadGloveVectors(final String fnGloveVectors) throws Exception {
		
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnGloveVectors), "UTF-8"));) {
			// Initialize matrix.
			this.coOccurrenceIdx_I = new ArrayList<Integer>();
			this.coOccurrenceIdx_J = new ArrayList<Integer>();
			this.coOccurrenceValues = new ArrayList<Float>();
			
			/*
			 * FILE FORMAT:
			 * 
			 * line1 <- [NEIGHBORS nodeID1 neighborID1 neighborID2 ... neighborIDn]
			 * line2 <- [VALUES nodeID1 value1 value2 ... value_n]
			 * ...
			 */
			
			String line = "";
			int nodeID, neighborID;
			float value;
			String[] strNeighbor = new String[2];
			int vecCounter = 0, nodeCntr = 0;
			/*
			 * START TEMP
			 */
			System.out.println("edgeIdTypeMap.size()=" +edgeIdTypeMap.size());
			System.out.println("edgeIdTypeMap.get(47028)=" +edgeIdTypeMap.get(47028));
			/*
			 * END TEMP
			 */
			while ((line = reader.readLine()) != null) {
				
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				
				String[] tokens = line.split("\t");
				/*
				String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
						.split("\t ", line));
				String temp = "";
				for(String token : tokens) temp += "token: " + token + " ";
				System.out.println("tokens: " + temp);*/
				

				// Parse nodeID and check.
				nodeID = Integer.parseInt(tokens[0].split(":")[0].trim());
				if (nodeID < 0 /*|| nodeID >= this.m_NumGloveVecs*/) {
					throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid nodeID.");
				}
				// Add current nodeID and each neighbor to matrix.
				for (int col = 1; col < tokens.length; col++) {
					if (tokens[col] == null || tokens[col].equals("")) continue;
					
					strNeighbor = tokens[col].split(":");
					neighborID = Integer.parseInt(strNeighbor[0].trim());
					value = Float.parseFloat(strNeighbor[1].trim());
					if (neighborID < 0 /*|| neighborID >= this.m_NumGloveVecs*/) {
						throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid neighborID.");
					}
					/*
					 * START TEMP
					 */
					if (this.edgeIdTypeMap.containsKey(nodeID)) System.out.println("edge in map.");
					/*
					 * END TEMP
					 */
					this.coOccurrenceIdx_I.add(nodeID);
					this.orderedIdxMap.put(nodeID, this.coOccurrenceIdx_I.size());
					this.coOccurrenceIdx_J.add(neighborID);
					this.coOccurrenceValues.add(value);
					this.maxNeighbors++;
				}
				vecCounter++;
			}
			
			// Check if number of vectors adds up.
			/*if (vecCounter != this.m_NumGloveVecs) {
				throw new Exception("Loading error in KaleModel.loadGloveVectors(): vecCounter does not match expected number of vectors.\n"
						+ "Expected: " + this.m_NumGloveVecs + ", but received: " + vecCounter);
			}*/
			
			reader.close();
		} catch (Exception ex) { ex.printStackTrace(); }
	}

	/**
	 * 
	 * @return
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public KaleMatrix loadGloveEntityVectors() throws Exception {
		//logger.info("WARNING: Wrong method used -> loadGloveEntityVectors()");
		//System.out.println("this.orderedIdxMap.size(): " +this.orderedIdxMap.size());
		logger.info("Start generating entity GloVe matrix.");
		KaleMatrix kMatrix = new KaleMatrix(this.iBcvSize, this.iBcvSize);
		
		int nodeID, neighborID, matrixIdx_I;
		int v = 0, nodeCntr = 0;
		float value;
		try {
			// Get matrix values and add to KaleMatrix.
			/*
			 * Edges still have their 'nodeID' here.
			 */
			while (v < this.coOccurrenceIdx_I.size() && nodeCntr < this.iBcvSize) {
				nodeID = this.coOccurrenceIdx_I.get(v);
				neighborID = this.coOccurrenceIdx_J.get(v);
				value = this.coOccurrenceValues.get(v);
				
				//matrixIdx_I = this.orderedIdxMap.get(nodeID);
				//kMatrix.addValue(matrixIdx_I, neighborID, value);
				if (!this.edgeIdTypeMap.containsKey(nodeID)) { 
					kMatrix.addValue(nodeID, neighborID, value);
					if (this.coOccurrenceIdx_I.get(v+1) != nodeID) nodeCntr++;
				}
				else { this.iFirstEdges.add(v); System.out.println("Edge found and added: " + v); }
				v++;
				// If next node is different than current node:
				// increment node counter.
			}
		} catch (Exception ex) { ex.printStackTrace(); }
		
		//System.out.println("loadGloveEntityVectors():\n" + kMatrix.toString());

		logger.info("Finished generating entity GloVe matrix.");
		return kMatrix;
	}
	
	/**
	 * 
	 * @return
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public KaleMatrix loadGloveRelationVectors() throws Exception {
		//logger.info("WARNING: Wrong method used -> loadGloveRelationVectors()");
		logger.info("Start generating edge GloVe matrix.");
		KaleMatrix kMatrix = new KaleMatrix(this.m_NumUniqueRelation, this.iBcvSize);
		
		int e = 0, id;
		int edgeID, neighborID;
		float value;
		try {
			// Get matrix values and add to KaleMatrix.
			/*
			 * Edges still have their 'nodeID' here.
			 */
			while (e < this.iFirstEdges.size()) {
				id = this.iFirstEdges.get(e);
				
				edgeID = this.coOccurrenceIdx_I.get(id);
				neighborID = this.coOccurrenceIdx_J.get(id);
				value = this.coOccurrenceValues.get(id);
				
				if (this.edgeIdTypeMap.containsKey(edgeID)) edgeID = this.edgeIdTypeMap.get(edgeID);
				else throw new Exception("Wrong edgeID in loadGloveRelationVectors().");
	
				kMatrix.addValue(edgeID, neighborID, value);
				
				e++;
				// If next node is different than current node:
				// increment node counter.
				//if (!this.coOccurrenceIdx_I.contains(e) && (this.coOccurrenceIdx_I.get(e) != edgeID)) edgeCntr++;
				//if (this.coOccurrenceIdx_I.get(e) != edgeID) edgeCntr++;
			}
		} catch (Exception ex) { ex.printStackTrace(); }
		
		System.out.println("loadGloveRelationVectors():\n" + kMatrix.toString());

		logger.info("Finished generating edge GloVe matrix.");
		return kMatrix;
	}
	
	/**
	 * 
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void CochezLearn() throws Exception {
		if (orderedIdxMap == null) throw new Exception("Current instatiation of this KaleModel does not contain needed data.");
		
		HashMap<Integer, ArrayList<Triple>> lstPosTriples = new HashMap<Integer, ArrayList<Triple>>();
		HashMap<Integer, ArrayList<Triple>> lstHeadNegTriples = new HashMap<Integer, ArrayList<Triple>>();
		HashMap<Integer, ArrayList<Triple>> lstTailNegTriples = new HashMap<Integer, ArrayList<Triple>>();
		HashMap<Integer, ArrayList<TripleRule>> lstRules = new HashMap<Integer, ArrayList<TripleRule>>();
		HashMap<Integer, ArrayList<TripleRule>> lstSndRelNegRules = new HashMap<Integer, ArrayList<TripleRule>>();
		
		KaleMatrix bestEntityVectors;
		KaleMatrix bestRelationVectors;
		
		// Generate logging file path.
		String PATHLOG = "result-k" + this.iBcvSize 
				+ "-d" +  this.decimalFormat.format(this.m_Delta)
				+ "-ge" + this.decimalFormat.format(this.m_GammaE) 
				+ "-gr" + this.decimalFormat.format(this.m_GammaR)
				+ "-w" +  this.decimalFormat.format(this.m_Weight) + this.fileExtension;
		
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(PATHLOG), "UTF-8"));) {
		
			// Initialize iteration counter and write to file and console.
			int iIterCntr = 0;
			writer.write("Complete iteration #" + iIterCntr + ":\n");
			System.out.println("Complete iteration #" + iIterCntr + ":");
			// Initialize metric monitor and calculate metrics.
			MetricMonitor firstMetric = new MetricMonitor(
					this.m_ValidateTriples,
					this.m_Triples.tripleSet(),
					this.m_Entity_Factor_MatrixE,
					this.m_Relation_Factor_MatrixR,
					this.orderedIdxMap,
					this.isGlove);
			
			/*MetricMonitor firstMetric = new MetricMonitor(
					this.m_ValidateTriples,
					this.m_Triples.tripleSet(),
					this.coOccurrenceIdx_I,
					this.coOccurrenceIdx_J,
					this.coOccurrenceValues,
					this.coOccurrenceGradient,
					this.orderedIdxMap,
					this.maxNeighbors,
					this.isGlove);*/
			firstMetric.calculateMetrics();
			double dCurrentHits = firstMetric.dHits;
			double dCurrentMRR = firstMetric.dMRR;
			bestEntityVectors = this.m_Entity_Factor_MatrixE;
			bestRelationVectors = this.m_Relation_Factor_MatrixR;
			// Write first results to file and save as initial best results.
			writer.write("------Current MRR:"+ dCurrentMRR + "\tCurrent Hits@10:" + dCurrentHits + "\n");
			System.out.print("\n");
			double dBestHits = firstMetric.dHits;
			double dBestMRR = firstMetric.dMRR;
			// Variable to save the best iteration.
			int iBestIter = 0;
			
			// Initialize start of training time and start training process.
			long startTime = System.currentTimeMillis();
			while (iIterCntr < this.m_NumIteration) {
				
				// Loop through training triples and generate negative versions (alterations).
				this.m_TrainingTriples.randomShuffle();
				for (int iTriple = 0; iTriple < this.m_TrainingTriples.nTriples(); iTriple++) {
					// Check if head is in entity matrix.
					if (!this.m_Entity_Factor_MatrixE.containsKey(this.m_TrainingTriples.get(iTriple).head())) continue;
					
					// Get triple and generate negative alterations.
					Triple PosTriple = this.m_TrainingTriples.get(iTriple);
					NegativeTripleGenerator negTripGen = new NegativeTripleGenerator(
							PosTriple, this.m_NumEntity, this.m_NumUniqueRelation);
					Triple headNegTriple = negTripGen.generateHeadNegTriple();
					Triple tailNegTriple = negTripGen.generateTailNegTriple();
					
					// Determine triple ID within batch.
					int iTripleID = iTriple % this.m_NumMiniBatch;
					// If positive triples list doesn't contain current triple,
					// add new triple to triples lists. Else add newly found triple
					// and its alterations to their respective lists.
					if (!lstPosTriples.containsKey(iTripleID)) {
						ArrayList<Triple> tmpPosLst = new ArrayList<Triple>();
						ArrayList<Triple> tmpHeadNegLst = new ArrayList<Triple>();
						ArrayList<Triple> tmpTailNegLst = new ArrayList<Triple>();
						tmpPosLst.add(PosTriple);
						tmpHeadNegLst.add(headNegTriple);
						tmpTailNegLst.add(tailNegTriple);
						lstPosTriples.put(iTripleID, tmpPosLst);
						lstHeadNegTriples.put(iTripleID, tmpHeadNegLst);
						lstTailNegTriples.put(iTripleID, tmpTailNegLst);
					} else {
						lstPosTriples.get(iTripleID).add(PosTriple);
						lstHeadNegTriples.get(iTripleID).add(headNegTriple);
						lstTailNegTriples.get(iTripleID).add(tailNegTriple);
					}
				}
				
				// Repeat above process for training rules.
				// Loop through training rules and generate negative versions (alterations).
				/*
				 * this.m_TrainingRules.randomShuffle();
				 */
				for (int iRule = 0; iRule < this.m_TrainingRules.rules(); iRule++) {
					// Get triple and generate negative alterations.
					TripleRule rule = this.m_TrainingRules.get(iRule);
					NegativeRuleGenerator negRuleGen = new NegativeRuleGenerator(
							rule, this.m_NumUniqueRelation);
					TripleRule sndRelNegrule = negRuleGen.generateSndNegRule();			
	
					// Determine triple ID within batch.
					int iRuleID = iRule % this.m_NumMiniBatch;
					// If positive rules list doesn't contain current rule,
					// add new rule to rules lists. Else add newly found rule
					// and its alterations to their respective lists.
					if (!lstRules.containsKey(iRuleID)) {
						ArrayList<TripleRule> tmpLst = new ArrayList<TripleRule>();
						ArrayList<TripleRule> tmpsndRelNegLst = new ArrayList<TripleRule>();
						tmpLst.add(rule);
						tmpsndRelNegLst.add(sndRelNegrule);
						lstRules.put(iRuleID, tmpLst);
						lstSndRelNegRules.put(iRuleID, tmpsndRelNegLst);
						
					} else {
						lstRules.get(iRuleID).add(rule);
						lstSndRelNegRules.get(iRuleID).add(sndRelNegrule);
					}
				}
				
				// Update stochastically.
				for (int iID = 0; iID < this.m_NumMiniBatch; iID++) {
					StochasticUpdater stochasticUpdate = new StochasticUpdater(
							lstPosTriples.get(iID),
							lstHeadNegTriples.get(iID),
							lstTailNegTriples.get(iID),
							lstRules.get(iID),
							lstSndRelNegRules.get(iID),
							this.m_Entity_Factor_MatrixE,
							this.m_Relation_Factor_MatrixR,
							this.m_MatrixEGradient,
							this.m_MatrixRGradient,
	//	###					this.learning rate
							this.m_GammaE,
							this.m_GammaR,
	//	###					this.margin
							this.m_Delta,
	//	###					this.weight
							this.m_Weight,
							this.isGlove);
					stochasticUpdate.stochasticIterationGlove();
				}
				
				// Reset lists for next iteration.
				lstPosTriples = new HashMap<Integer, ArrayList<Triple>>();
				lstHeadNegTriples = new HashMap<Integer, ArrayList<Triple>>();
				lstTailNegTriples = new HashMap<Integer, ArrayList<Triple>>();
	
				lstRules = new HashMap<Integer, ArrayList<TripleRule>>();
				lstSndRelNegRules = new HashMap<Integer, ArrayList<TripleRule>>();
				
				// Increment iteration counter and print to console.
				iIterCntr++;
				System.out.println("Complete iteration #" + iIterCntr + ":");
				
				// If current iteration is an nth-fold of this.m_OutputIterSkip
				// write current iteration results to file.
				if (iIterCntr % this.m_OutputIterSkip == 0) {
					writer.write("Complete iteration #" + iIterCntr + ":\n");
					System.out.println("Complete iteration #" + iIterCntr + ":");
					MetricMonitor metric = new MetricMonitor(
							this.m_ValidateTriples,
							this.m_Triples.tripleSet(),
							this.m_Entity_Factor_MatrixE,
							this.m_Relation_Factor_MatrixR,
							this.orderedIdxMap,
							this.isGlove);
					metric.calculateMetrics();
					dCurrentHits = metric.dHits;
					dCurrentMRR = metric.dMRR;
					writer.write("------Current MRR:"+ dCurrentMRR + "\tCurrent Hits@10:" + dCurrentHits + "\n");
					// Save current statistics if better than previous best results.
					if (dCurrentMRR > dBestMRR) {
						this.m_Relation_Factor_MatrixR.output(this.m_MatrixR_prefix + ".best");
						this.m_Entity_Factor_MatrixE.output(this.m_MatrixE_prefix + ".best");
						bestEntityVectors = this.m_Entity_Factor_MatrixE;
						bestRelationVectors = this.m_Relation_Factor_MatrixR;
						dBestHits = dCurrentHits;
						dBestMRR = dCurrentMRR;
						iBestIter = iIterCntr;
					}
					writer.write("------Best iteration #" + iBestIter + "\t" + dBestMRR + "\t" + dBestHits+"\n");
					writer.flush();
					System.out.println("------\tBest iteration #" + iBestIter + "\tBest MRR:" + dBestMRR + "Best \tHits@10:" + dBestHits);
					writer.flush();
				}
			}
			// Print end of training time to console and close writer.
			long endTime = System.currentTimeMillis();
			System.out.println("All running time:" + (endTime-startTime)+"ms");
			writer.close();
			this.kaleVectorMatrix = new KaleVectorMatrix(this.graph, this.config, bestEntityVectors, bestRelationVectors);
			
		} catch (Exception ex) { ex.printStackTrace(); }		
	}
	
	public void TransE_Learn() throws Exception {
		HashMap<Integer, ArrayList<Triple>> lstPosTriples = new HashMap<Integer, ArrayList<Triple>>();
		HashMap<Integer, ArrayList<Triple>> lstHeadNegTriples = new HashMap<Integer, ArrayList<Triple>>();
		HashMap<Integer, ArrayList<Triple>> lstTailNegTriples = new HashMap<Integer, ArrayList<Triple>>();
		HashMap<Integer, ArrayList<TripleRule>> lstRules = new HashMap<Integer, ArrayList<TripleRule>>();
		HashMap<Integer, ArrayList<TripleRule>> lstSndRelNegRules = new HashMap<Integer, ArrayList<TripleRule>>();
		
		
		String PATHLOG = "result-k" + m_NumFactor 
				+ "-d" + decimalFormat.format(m_Delta)
				+ "-ge" + decimalFormat.format(m_GammaE) 
				+ "-gr" + decimalFormat.format(m_GammaR)
				+ "-w" +  decimalFormat.format(m_Weight) + this.fileExtension;
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(PATHLOG), "UTF-8"));
		
		int iIter = 0;
		writer.write("Complete iteration #" + iIter + ":\n");
		System.out.println("Complete iteration #" + iIter + ":");
		MetricMonitor first_metrics = new MetricMonitor(
				m_ValidateTriples,
				m_Triples.tripleSet(),
				m_Entity_Factor_MatrixE,
				m_Relation_Factor_MatrixR,
				this.isGlove);
		first_metrics.calculateMetrics();
		double dCurrentHits = first_metrics.dHits;
		double dCurrentMRR = first_metrics.dMRR;
		writer.write("------Current MRR:"+ dCurrentMRR + "\tCurrent Hits@10:" + dCurrentHits + "\n");
		System.out.print("\n");
		double dBestHits = first_metrics.dHits;
		double dBestMRR = first_metrics.dMRR;
		int iBestIter = 0;
		
		
		long startTime = System.currentTimeMillis();
		while (iIter < m_NumIteration) {
			m_TrainingTriples.randomShuffle();
			for (int iIndex = 0; iIndex < m_TrainingTriples.nTriples(); iIndex++) {
				Triple PosTriple = m_TrainingTriples.get(iIndex);
				NegativeTripleGenerator negTripGen = new NegativeTripleGenerator(
						PosTriple, m_NumEntity, m_NumUniqueRelation);
				Triple headNegTriple = negTripGen.generateHeadNegTriple();
				Triple tailNegTriple = negTripGen.generateTailNegTriple();
				
				int iID = iIndex % m_NumMiniBatch;
				if (!lstPosTriples.containsKey(iID)) {
					ArrayList<Triple> tmpPosLst = new ArrayList<Triple>();
					ArrayList<Triple> tmpHeadNegLst = new ArrayList<Triple>();
					ArrayList<Triple> tmpTailNegLst = new ArrayList<Triple>();
					tmpPosLst.add(PosTriple);
					tmpHeadNegLst.add(headNegTriple);
					tmpTailNegLst.add(tailNegTriple);
					lstPosTriples.put(iID, tmpPosLst);
					lstHeadNegTriples.put(iID, tmpHeadNegLst);
					lstTailNegTriples.put(iID, tmpTailNegLst);
				} else {
					lstPosTriples.get(iID).add(PosTriple);
					lstHeadNegTriples.get(iID).add(headNegTriple);
					lstTailNegTriples.get(iID).add(tailNegTriple);
				}
			}
			
			m_TrainingRules.randomShuffle();
			for (int iIndex = 0; iIndex < m_TrainingRules.rules(); iIndex++) {
				TripleRule rule = m_TrainingRules.get(iIndex);
				
				NegativeRuleGenerator negRuleGen = new NegativeRuleGenerator(
						rule,  m_NumUniqueRelation);
				TripleRule sndRelNegrule = negRuleGen.generateSndNegRule();			

				int iID = iIndex % m_NumMiniBatch;
				if (!lstRules.containsKey(iID)) {
					ArrayList<TripleRule> tmpLst = new ArrayList<TripleRule>();
					ArrayList<TripleRule> tmpsndRelNegLst = new ArrayList<TripleRule>();
					tmpLst.add(rule);
					tmpsndRelNegLst.add(sndRelNegrule);
					lstRules.put(iID, tmpLst);
					lstSndRelNegRules.put(iID, tmpsndRelNegLst);
					
				} else {
					lstRules.get(iID).add(rule);
					lstSndRelNegRules.get(iID).add(sndRelNegrule);
				}
			}
			
			double m_BatchSize = m_TrainingTriples.nTriples()/(double)m_NumMiniBatch;
			for (int iID = 0; iID < m_NumMiniBatch; iID++) {
				StochasticUpdater stochasticUpdate = new StochasticUpdater(
						lstPosTriples.get(iID),
						lstHeadNegTriples.get(iID),
						lstTailNegTriples.get(iID),
						lstRules.get(iID),
						lstSndRelNegRules.get(iID),
						m_Entity_Factor_MatrixE,
						m_Relation_Factor_MatrixR,
						m_MatrixEGradient,
						m_MatrixRGradient,
//	###					learning rate
						m_GammaE,
						m_GammaR,
//	###					margin
						m_Delta,
//	###					weight
						m_Weight,
						this.isGlove);
				stochasticUpdate.stochasticIteration();
			}

			
			lstPosTriples = new HashMap<Integer, ArrayList<Triple>>();
			lstHeadNegTriples = new HashMap<Integer, ArrayList<Triple>>();
			lstTailNegTriples = new HashMap<Integer, ArrayList<Triple>>();

			lstRules = new HashMap<Integer, ArrayList<TripleRule>>();
			lstSndRelNegRules = new HashMap<Integer, ArrayList<TripleRule>>();
			
			iIter++;
			System.out.println("Complete iteration #" + iIter + ":");
			
			if (iIter % m_OutputIterSkip == 0) {
				writer.write("Complete iteration #" + iIter + ":\n");
				System.out.println("Complete iteration #" + iIter + ":");
				MetricMonitor metric = new MetricMonitor(
						m_ValidateTriples,
						m_Triples.tripleSet(),
						m_Entity_Factor_MatrixE,
						m_Relation_Factor_MatrixR,
						this.isGlove);
				metric.calculateMetrics();
				dCurrentHits = metric.dHits;
				dCurrentMRR = metric.dMRR;
				writer.write("------Current MRR:"+ dCurrentMRR + "\tCurrent Hits@10:" + dCurrentHits + "\n");
				if (dCurrentMRR > dBestMRR) {
					m_Relation_Factor_MatrixR.output(m_MatrixR_prefix + ".best");
					m_Entity_Factor_MatrixE.output(m_MatrixE_prefix + ".best");
					dBestHits = dCurrentHits;
					dBestMRR = dCurrentMRR;
					iBestIter = iIter;
				}
				writer.write("------Best iteration #" + iBestIter + "\t" + dBestMRR + "\t" + dBestHits+"\n");
				writer.flush();
				System.out.println("------\tBest iteration #" + iBestIter + "\tBest MRR:" + dBestMRR + "Best \tHits@10:" + dBestHits);
				writer.flush();
			}
		}
		long endTime = System.currentTimeMillis();
		System.out.println("All running time:" + (endTime-startTime)+"ms");
		writer.close();
	}
	/**
	 * 
	 * @param fnGloveVectors
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void loadGloveVectors(final String fnGloveVectors, boolean isOld) throws Exception {
		
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnGloveVectors), "UTF-8"));) {
			// Initialize matrix.
			this.coOccurrenceIdx_I = new ArrayList<Integer>();
			this.coOccurrenceIdx_J = new ArrayList<Integer>();
			this.coOccurrenceValues = new ArrayList<Float>();
			
			/*
			 * FILE FORMAT:
			 * 
			 * line1 <- [NEIGHBORS nodeID1 neighborID1 neighborID2 ... neighborIDn]
			 * line2 <- [VALUES nodeID1 value1 value2 ... value_n]
			 * ...
			 */
			
			String line = "";
			int nodeID, neighborID;
			int vecCounter = 0;
			float value;
			boolean neighborsLine = true;
			while ((line = reader.readLine()) != null) {
				
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				
				String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
						.split("\t ", line));
	
				if (tokens[0] == "NEIGHBORS") {
					// Check if correct line order is maintained.
					if (!neighborsLine) throw new Exception("Loading error in KaleModel.loadGloveVectors(): neighborsLine expected.");
					// Parse nodeID and check.
					nodeID = Integer.parseInt(tokens[1]);
					if (nodeID < 0 || nodeID >= this.m_NumGloveVecs) {
						throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid nodeID.");
					}
					//this.orderedIdxMap.put(nodeID);
					// Add current nodeID and each neighbor to matrix.
					for (int col = 2; col < tokens.length; col++) {
						neighborID = Integer.parseInt(tokens[col]);
						if (neighborID < 0 || neighborID >= this.m_NumGloveVecs) {
							throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid neighborID.");
						}
						this.coOccurrenceIdx_I.add(nodeID);
						this.coOccurrenceIdx_J.add(neighborID);
					}
					// Update expected line type.
					neighborsLine = false;
					
				} else if (tokens[0] == "VALUES") {
					// Check if correct line order is maintained.
					if (neighborsLine) throw new Exception("Loading error in KaleModel.loadGloveVectors(): values line expected (i.e. not neighborsLine expected).");
					// Parse nodeID and check.
					nodeID = Integer.parseInt(tokens[1]);
					if (nodeID != this.coOccurrenceIdx_I.get(this.coOccurrenceIdx_I.size()-1)) {
						throw new Exception("Loading error in KaleModel.loadGloveVectors(): current nodeID does not match expected nodeID.");
					}
					if (nodeID < 0 || nodeID >= this.m_NumGloveVecs) {
						throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid nodeID.");
					}
					// Add current nodeID and each neighbor to matrix.
					for (int col = 2; col < tokens.length; col++) {
						value = Float.parseFloat(tokens[col]);
						this.coOccurrenceValues.add(value);
					}
					// Update expected line type and increment counter.
					neighborsLine = true;
					vecCounter++;
					
				} else { throw new Exception("Loading error in KaleModel.loadGloveVectors(): invalid row header.\n"
						+ "Expected 'NEIGHBORS' or 'VALUE', in stead received: " + tokens[0]); }
			}
			// Check if number of vectors adds up.
			if (vecCounter != this.m_NumGloveVecs) {
				throw new Exception("Loading error in KaleModel.loadGloveVectors(): vecCounter does not match expected number of vectors.");
			}
			
			reader.close();
		} catch (Exception ex) { ex.printStackTrace(); }
	}
}