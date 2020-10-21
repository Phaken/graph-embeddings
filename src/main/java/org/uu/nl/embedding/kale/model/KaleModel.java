package org.uu.nl.embedding.kale.model;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import org.uu.nl.embedding.kale.struct.RuleSet;
import org.uu.nl.embedding.kale.struct.KaleTriple;
import org.apache.log4j.Logger;
import org.uu.nl.embedding.bca.models.BookmarkColoringEdges;
import org.uu.nl.embedding.bca.models.BookmarkColoringNodes;
import org.uu.nl.embedding.kale.struct.KaleMatrix;
import org.uu.nl.embedding.kale.struct.TripleRule;
import org.uu.nl.embedding.kale.struct.TripleSet;
import org.uu.nl.embedding.kale.util.DataGenerator;
import org.uu.nl.embedding.kale.util.MetricMonitor;
import org.uu.nl.embedding.kale.util.NegativeRuleGenerator;
import org.uu.nl.embedding.kale.util.NegativeTripleGenerator;
import org.uu.nl.embedding.logic.util.FormulaSet;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.Permutation;

import org.uu.nl.embedding.util.write.KaleEmbeddingTextWriter;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class KaleModel {
	private String FILE_TYPE;
	private String FILEPATH;
	
	public TripleSet m_TrainingTriples;
	public TripleSet m_ValidateTriples;
	public TripleSet m_TestingTriples;
	public TripleSet m_Triples;
	public RuleSet m_TrainingRules;
	
	//private int iNumTriplesTrain;
	//private int iNumRulesTrain;
	
	public KaleMatrix m_Entity_Factor_MatrixE;
	public KaleMatrix m_Relation_Factor_MatrixR;
	public KaleMatrix m_MatrixEGradient;
	public KaleMatrix m_MatrixRGradient;
	
	public KaleVectorMatrix kaleVectorMatrix;
	private InMemoryRdfGraph graph;
	private Configuration config;
	private BookmarkColoringNodes bcaNodes;
	private BookmarkColoringEdges bcaEdges;
	
	public int m_NumUniqueRelation;
	public int m_NumEntity;
	public int iNumTotal;
	
	private float[] gloveArrayNodes;
	private float[] gloveArrayEdges;
	
	public int m_NumGloveVecs;
	private int[] nonEmptyVerts;
	public String m_MatrixE_prefix = "";
	public String m_MatrixR_prefix = "";
	
	public int iDim = 30;
	public int m_NumFactor = 30;
	public int m_NumMiniBatch = 10;
	public double m_Delta = 0.1;
	public double m_GammaE = 0.01;
	public double m_GammaR = 0.01;
	public int m_NumEpochs = 100; // = epochs
	public int m_OutputIterSkip = 5;
	public double m_Weight = 0.01;
	public double learnThreshold = 1e-4d;
	
	private ArrayList<FormulaSet> formulaSets;
	private HashMap<String, Integer> uniqueRelationTypes;
	private DataGenerator dataGenerator;
	/* Hier verder: in CochezLearn() TrainingRules moeten de edgeType mee krijgen om de relations van
	 * de rules te vergelijken met de training triples. */
	
	public boolean isGlove;
	private ArrayList<Integer> coOccurrenceIdx_I = null;
	private ArrayList<Integer> coOccurrenceIdx_J = null;
	private ArrayList<Float> coOccurrenceValues = null;
	
	private ArrayList<Integer> coOccurrenceIdx_I_edges = null;
	private ArrayList<Integer> coOccurrenceIdx_J_edges = null;
	private ArrayList<Float> coOccurrenceValues_edges = null;
	
	private ArrayList<Float> coOccurrenceGradient = null;
	private HashMap<Integer, Integer> orderedIdxMap = null;
	private TreeMap<Integer, Integer> edgeIdTypeMap = null;
	private ArrayList<Integer> iFirstEdges;
	private int maxNeighbors;
	
	/**
	 * 
	 */
	public String fileExtension = ".tsv";
	
	java.text.DecimalFormat decimalFormat = new java.text.DecimalFormat("#.######");
    private final static Logger logger = Logger.getLogger(KaleModel.class);

	
	public /*void*/ KaleModel(final int iNumEntity, final int iNumTotal,
			final float[] gloveArrayNodes, final float[] gloveArrayEdges,
			final String fnTrainingTriples, final String fnValidateTriples,
			final String fnTestingTriples, /*final String fnTrainingRules,*/
			final String FILE_TYPE,
			final HashMap<String, Integer> uniqueRelationTypes,
			final InMemoryRdfGraph graph, final Configuration config,
			final DataGenerator dataGenerator,
			final BookmarkColoringNodes bcaNodes, final BookmarkColoringEdges bcaEdges) throws Exception {
		
		logger.info("Start initializing KaleModel.");
		
		this.graph = graph;
		this.config = config;
		this.dataGenerator = dataGenerator;
		this.FILE_TYPE = FILE_TYPE;
		this.FILEPATH = "/Users/euan/eclipse-workspace/graphLogicEmbed/data/input";

		/*
		 * Should be taken from config file.
		 *
		this.iNumTriplesTrain = 3;
		this.iNumRulesTrain = 3;
		this.m_NumIteration = 3;*/
		
		this.iDim = config.getDim();
		this.m_NumEntity = iNumEntity;
		this.iNumTotal = iNumTotal;
		this.m_NumUniqueRelation = uniqueRelationTypes.size();
		
		this.gloveArrayNodes = gloveArrayNodes;
		this.gloveArrayEdges = gloveArrayEdges;
		this.isGlove = true;
		
		//if (dataGenerator.getRuleSet() == null) throw new Exception("No rules set initialized.");
		//this.m_TrainingRules = dataGenerator.getRuleSet();
		try { 
			this.m_TrainingRules = dataGenerator.getTrainingRules();
			this.uniqueRelationTypes = uniqueRelationTypes;
			
			this.bcaNodes = bcaNodes;
			this.bcaEdges = bcaEdges;
			
			this.orderedIdxMap = new HashMap<Integer, Integer>();
			this.iFirstEdges = new ArrayList<Integer>();
			this.maxNeighbors = 0;
			
			/*if (fnGloveVectors != null) this.isGlove = true;
			else this.isGlove = false;*/
			
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
			logger.info("Initialize loading training and validation triples.");
			
			this.m_TrainingTriples = new TripleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_ValidateTriples = new TripleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_TestingTriples = new TripleSet(fnTestingTriples, this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_Triples = graph.getTripleSet();
			this.m_TrainingTriples.load(fnTrainingTriples);
			this.m_ValidateTriples.load(fnValidateTriples);
			/*this.m_Triples.loadStr(fnTrainingTriples);
			this.m_Triples.loadStr(fnValidateTriples);
			this.m_Triples.loadStr(fnTestingTriples);*/
			
			logger.info("Loading training and validation triples successful.");
			
			logger.info("Initialize loading grounding rules.");
			
			/*this.m_TrainingRules = new RuleSet(this.m_NumEntity, this.m_NumUniqueRelation);
			this.m_TrainingRules.loadTimeLogic(fnTrainingRules);
			this.m_TrainingRules.setRelationTypes(this.uniqueRelationTypes);*/
			/*
	//				 * Do stuff met die formulaSets
					 */
			//this.formulaSets = this.m_TrainingRules.getFormulaSets();
			logger.info("Loading grounding rules successful.");
			
			if (this.isGlove)  {
				this.m_Entity_Factor_MatrixE = new KaleMatrix(this.gloveArrayNodes, bcaNodes.getOrderedIDs(), this.iDim, false);
				this.m_Relation_Factor_MatrixR = new KaleMatrix(this.gloveArrayEdges, bcaEdges.getOrderedIDs(), this.iDim, true);
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
			logger.info("Initialization matrix E and matrix R successful.");
	
			logger.info("Initializing gradients of matrix E and matrix R.");
			float[] zeroArrayE = new float[this.gloveArrayNodes.length];
			this.m_MatrixEGradient = new KaleMatrix(zeroArrayE, bcaNodes.getOrderedIDs(), this.iDim, false, true);
			float[] zeroArrayR = new float[this.gloveArrayEdges.length];
			this.m_MatrixRGradient = new KaleMatrix(zeroArrayR, bcaEdges.getOrderedIDs(), this.iDim, true, true);
			//this.m_MatrixEGradient = this.m_Entity_Factor_MatrixE.generateGradientMatrix();
			//this.m_MatrixRGradient = this.m_Relation_Factor_MatrixR.generateGradientMatrix();
			/*this.m_MatrixEGradient = new KaleMatrix(this.m_Entity_Factor_MatrixE.rows(),
												this.m_Entity_Factor_MatrixE.columns(), false);
			this.m_MatrixRGradient = new KaleMatrix(this.m_Relation_Factor_MatrixR.rows(),
												this.m_Relation_Factor_MatrixR.columns(), true);*/
			logger.info("Initialization gradients of matrix E and matrix R successfull.");
			logger.info("Model initialization successful.");
		} catch (Exception ex) { ex.printStackTrace(); }
	}
	
	public void CochezLearn(final boolean isNew) throws Exception {
		HashMap<Integer, ArrayList<KaleTriple>> lstPosTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
		HashMap<Integer, ArrayList<KaleTriple>> lstHeadNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
		HashMap<Integer, ArrayList<KaleTriple>> lstTailNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
		HashMap<Integer, ArrayList<TripleRule>> lstRules = new HashMap<Integer, ArrayList<TripleRule>>();
		HashMap<Integer, ArrayList<TripleRule>> lstSndRelNegRules = new HashMap<Integer, ArrayList<TripleRule>>();
		

		if (this.m_TrainingTriples == null || this.m_TrainingTriples.nTriples() == 0) 
			throw new Exception("Training triples not properly initialized.");
		if (this.m_TrainingRules == null || this.m_TrainingRules.rules() == 0) 
			throw new Exception("Training rules not properly initialized.");
		
		System.out.println("KaleModel.CochezLearn() - this.m_TrainingTriples.nTriples() ==> " + this.m_TrainingTriples.nTriples());
		System.out.println("KaleModel.CochezLearn() - this.m_TrainingRules.rules() ==> " + this.m_TrainingRules.rules());
		/*for (int i = 0; i < this.m_TrainingRules.rules(); i++) {
			System.out.println(this.m_TrainingRules.get(i).toString());
		}
		System.out.println("KaleModel.CochezLearn() - this.m_TrainingRules.rules() ==> " + this.m_TrainingRules.rules());*/
		KaleMatrix bestEntityVectors;
		KaleMatrix bestRelationVectors;
		
		// Generate logging file path.
		String PATHLOG = "result-k" + this.iNumTotal 
				+ "-d" +  this.decimalFormat.format(this.m_Delta)
				+ "-ge" + this.decimalFormat.format(this.m_GammaE) 
				+ "-gr" + this.decimalFormat.format(this.m_GammaR)
				+ "-w" +  this.decimalFormat.format(this.m_Weight) + this.fileExtension;
		PATHLOG = generateFile(PATHLOG);
		String fnCheckFile = "";
		KaleEmbeddingTextWriter checkWriter = new KaleEmbeddingTextWriter(config, this.fileExtension, true);
		ArrayList<String> checkFileLines = new ArrayList<String>();
		/*
		if (this.m_TrainingTriples.nTriples() < this.iNumTriplesTrain)
			this.iNumTriplesTrain = this.m_TrainingTriples.nTriples();
		if (this.m_TrainingRules.rules() < this.iNumRulesTrain) 
			this.iNumRulesTrain = this.m_TrainingRules.rules();*/

		try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(PATHLOG), "UTF-8")); ) {

			logger.info("Progression bar initialized.");
			logger.info("Start learning using the Cochez method.");
			// Initialize iteration counter and write to file and console.
			int iEpochCntr = 0;
			double learningRate = 100d;

			double dMeanResultE = 0d;
			double dMeanResultR = 0d;
			double oldMeanResultE = 0d;
			writer.write("Start iteration #" + iEpochCntr + ":\n");
			logger.info("\n\tStart iteration #" + iEpochCntr + ":");
			// Initialize metric monitor and calculate metrics.
			MetricMonitor firstMetric = new MetricMonitor(
					this.m_ValidateTriples,
					this.m_Triples.tripleSet(),
					this.m_Entity_Factor_MatrixE,
					this.m_Relation_Factor_MatrixR,
					/*this.isGlove*/true);
			firstMetric.calculateMetrics();

			double dCurrentHits = firstMetric.dHits;
			double dCurrentMRR = firstMetric.dMRR;
			bestEntityVectors = this.m_Entity_Factor_MatrixE;
			bestRelationVectors = this.m_Relation_Factor_MatrixR;
			// Write first results to file and save as initial best results.
			writer.write("------Current MRR:" + dCurrentMRR + "\tCurrent Hits@10:" + dCurrentHits + "\n");
			System.out.print("\n");
			double dBestHits = firstMetric.dHits;
			double dBestMRR = firstMetric.dMRR;
			// Variable to save the best iteration.
			int iBestIter = 0;

			// Initialize start of training time and start training process.
			long startTime = System.currentTimeMillis();
			//float trainPercentage = 0f;
			//try (ProgressBar pb = Configuration.progressBar("KaleModel Cochez-Learning", this.m_NumIteration, "iterations\n");) {
			while (iEpochCntr < this.m_NumEpochs && learningRate > this.learnThreshold) {
			//while (iEpochCntr < 1 && learningRate > this.learnThreshold) {

				//float trainPercentage = ((iEpochCntr / this.m_NumIteration) * 100);
				try {
					//System.out.println("KaleModel.CochezLearn(boolean) - Iteratie gestart.");
					// Loop through training triples and generate negative versions (alterations).
					this.m_TrainingTriples.randomShuffle();
					// checkWriter
					logger.info("\nCurrent iteration: " + iEpochCntr);
					if (iEpochCntr % this.m_OutputIterSkip == 0) {
						checkFileLines = new ArrayList<String>();
					}
					checkFileLines.add("\n# ---- CURRENT ITERATION: " + iEpochCntr + " ---- #\n");

					int accessID;
					Permutation triplePermutation = new Permutation(this.m_TrainingTriples.nTriples());
					triplePermutation.shuffle();

					logger.info("\n\tStarting loop training triples.");
					for (int iTriple = 0; iTriple < this.m_TrainingTriples.nTriples() /*this.iNumTriplesTrain*/; iTriple++) {
						if (iTriple % 1000 == 0) logger.info("Time: "+((System.currentTimeMillis()-startTime)/(1000*60))+" min | Cochez-learning: "/*+trainPercentage
								+"% | "*/+iEpochCntr+"/" +this.m_NumEpochs +" iteration --> "+iTriple+"/"+this.m_TrainingTriples.nTriples()+" triples");
						// Check if head is in entity matrix.

						//System.out.println("KaleModel.CochezLearn(boolean) - WARNING: loop should be this.m_TrainingTriples.nTriples()");
						//System.out.println("KaleModel.CochezLearn(boolean) - Volgende triple: "+ iTriple);
						if (!this.m_Entity_Factor_MatrixE.containsKey(this.m_TrainingTriples.get(iTriple).head())) {
							logger.info("\nVertex not in matrix, thus skipped: " + this.m_TrainingTriples.get(iTriple).head() + "\n");
							continue;
						}

						// Get triple and generate negative alterations.
						accessID = triplePermutation.randomAccess(iTriple);
						KaleTriple PosTriple = this.m_TrainingTriples.get(accessID);
						//System.out.println("KaleModel.CochezLearn() - this.m_TrainingTriples.get(iTriple) ==> iTriple=" + iTriple);
						//System.out.println("KaleModel.CochezLearn() - PosTriple ==> " + PosTriple);
						NegativeTripleGenerator negTripGen = new NegativeTripleGenerator(
								PosTriple, this.m_NumEntity, this.m_NumUniqueRelation, this.m_Entity_Factor_MatrixE);
						KaleTriple headNegTriple = negTripGen.generateHeadNegTriple();
						KaleTriple tailNegTriple = negTripGen.generateTailNegTriple();

						// Determine triple ID within batch.
						int iTripleID = iTriple % this.m_NumMiniBatch;
						// If positive triples list doesn't contain current triple,
						// add new triple to triples lists. Else add newly found triple
						// and its alterations to their respective lists.
						if (!lstPosTriples.containsKey(iTripleID)) {
							ArrayList<KaleTriple> tmpPosLst = new ArrayList<KaleTriple>();
							ArrayList<KaleTriple> tmpHeadNegLst = new ArrayList<KaleTriple>();
							ArrayList<KaleTriple> tmpTailNegLst = new ArrayList<KaleTriple>();
							tmpPosLst.add(PosTriple);
							tmpHeadNegLst.add(headNegTriple);
							tmpTailNegLst.add(tailNegTriple);
							lstPosTriples.put(iTripleID, tmpPosLst);
							//System.out.println("KaleModel.CochezLearn() - lstPosTriples.put(iTripleID, tmpPosLst) ==> iTripleID=" + iTripleID);
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
					int accessRuleID;
					Permutation rulePermutation = new Permutation(this.m_TrainingRules.rules());
					rulePermutation.shuffle();

					//System.out.println("KaleModel.CochezLearn(boolean) - WARNING: loop should be this.iNumRulesTrain");
					logger.info("\n\tStarting loop training rules.");
					for (int iRule = 0; iRule < this.m_TrainingRules.rules() /*this.iNumRulesTrain*/; iRule++) {
						if (iRule % 100 == 0) logger.info("Time: "+((System.currentTimeMillis()-startTime)/(1000*60))+" min | Cochez-learning: "/*+trainPercentage
								+"% | "*/+iEpochCntr+"/" +this.m_NumEpochs +" iteration --> "+iRule+"/"+this.m_TrainingRules.rules()+" rules");

						//System.out.println("KaleModel.CochezLearn(boolean) - Volgende rule: "+ iRule);
						accessRuleID = rulePermutation.randomAccess(iRule);
						// Get triple and generate negative alterations.
						TripleRule rule = this.m_TrainingRules.get(accessRuleID);
						NegativeRuleGenerator negRuleGen = new NegativeRuleGenerator(
								rule, this.m_NumUniqueRelation, this.uniqueRelationTypes);
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
					String lineE = "", lineR = "";
					oldMeanResultE = dMeanResultE;
					dMeanResultE = 0d;
					dMeanResultR = 0d;

					logger.info("\n\tStarting mini-batch.");
					for (int iID = 0; iID < this.m_NumMiniBatch; iID++) {
						logger.info("Time: "+((System.currentTimeMillis()-startTime)/(1000*60))+" min | Cochez-learning: "/*+trainPercentage
								+"% | "*/+iEpochCntr+"/" +this.m_NumEpochs +" iteration --> "+iID+"/"+this.m_NumMiniBatch+" in mini-batch");

						//System.out.println("KaleModel.CochezLearn(boolean) - Volgende stochastic updater: "+ iID);
						checkFileLines.add("# Within miniBatch: #" + iID);
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
						dMeanResultE += stochasticUpdate.learnedRateMeanEntity();
						dMeanResultR += stochasticUpdate.learnedRateMeanRelation();
						lineE = "Iteration " + iEpochCntr + "\tMean learned rate E:\t" + String.valueOf(stochasticUpdate.learnedRateMeanEntity());
						lineR = "Iteration " + iEpochCntr + "\tMean learned rate R:\t" + String.valueOf(stochasticUpdate.learnedRateMeanRelation());
						checkFileLines.add(lineE);
						checkFileLines.add(lineR);
					}
					dMeanResultE /= this.m_NumMiniBatch;
					dMeanResultR /= this.m_NumMiniBatch;
					logger.info("\n\tIteration mean result E = " + String.valueOf(dMeanResultE));
					logger.info("\n\tIteration mean result R = " + String.valueOf(dMeanResultR));

					// Reset lists for next iteration.
					lstPosTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
					lstHeadNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
					lstTailNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();

					lstRules = new HashMap<Integer, ArrayList<TripleRule>>();
					lstSndRelNegRules = new HashMap<Integer, ArrayList<TripleRule>>();

					// Increment iteration counter and print to console.
					iEpochCntr++;
					//System.out.println("Complete iteration #" + iEpochCntr + ":");

					// If current iteration is an nth-fold of this.m_OutputIterSkip
					// write current iteration results to file.
					if (iEpochCntr % this.m_OutputIterSkip == 0) {
						writer.write("Complete iteration #" + iEpochCntr + ":\n");
						System.out.println("\n\tComplete iteration #" + iEpochCntr + ".");
						MetricMonitor metric = new MetricMonitor(
								this.m_ValidateTriples,
								this.m_Triples.tripleSet(),
								this.m_Entity_Factor_MatrixE,
								this.m_Relation_Factor_MatrixR,
								this.isGlove);
						metric.calculateMetrics();
						dCurrentHits = metric.dHits;
						dCurrentMRR = metric.dMRR;
						writer.write("------Current MRR:" + dCurrentMRR + "\tCurrent Hits@10:" + dCurrentHits + "\n");
						System.out.println("------Current MRR and Hits@10 written to file.");


						// Save current statistics if better than previous best results.
						if (dCurrentMRR > dBestMRR) {
							System.out.println("------New best MRR found; Outputting matrix and setting new values.");

							String fnOutputE = (this.m_MatrixE_prefix + "_best" + this.fileExtension);
							fnOutputE = generateFile(fnOutputE);
							this.m_Entity_Factor_MatrixE.output(fnOutputE);

							String fnOutputR = (this.m_MatrixR_prefix + "_best" + this.fileExtension);
							fnOutputR = generateFile(fnOutputR);
							this.m_Relation_Factor_MatrixR.output(fnOutputR);

							bestEntityVectors = this.m_Entity_Factor_MatrixE;
							bestRelationVectors = this.m_Relation_Factor_MatrixR;

							dBestHits = dCurrentHits;
							dBestMRR = dCurrentMRR;
							iBestIter = iEpochCntr;
							System.out.println("------New values set.");
						}
						writer.write("------Best iteration #" + iBestIter + "\t" + dBestMRR + "\t" + dBestHits + "\n");
						writer.flush();
						System.out.println("\n------\tBest iteration #" + iBestIter + "\tBest MRR:" + dBestMRR + "Best \tHits@10:" + dBestHits);

						// Create intermediate data file.
						fnCheckFile = "intermediate_kale_progression";
						fnCheckFile = generateFile(fnCheckFile);

						logger.info("Initializing intermediate data file: " + fnCheckFile);
						checkWriter = new KaleEmbeddingTextWriter(config, this.fileExtension, true);
						checkWriter.writeIntermediate(fnCheckFile, checkFileLines);
						logger.info("Data written to: " + fnCheckFile);
					}

					// Check difference with previous mean result.
					learningRate = dMeanResultE - oldMeanResultE;
					oldMeanResultE = dMeanResultE;

				} catch (InterruptedException | ExecutionException e) {
					writer.close();
					logger.info("Failed training step KaleModel.");
					e.printStackTrace();
				} finally {
					System.out.println("\nKaleModel.CochezLearn(boolean) - End iteration.");
					//pb.step();
				}
			}
			//}

			// Print end of training time to console and close writer.
			long endTime = System.currentTimeMillis();
			System.out.println("All running time:" + (endTime - startTime) + "ms");
		}

		String fnEntities = generateFile("bestEntityVectors" + this.fileExtension);
		bestEntityVectors.output(fnEntities);
		String fnRelations = generateFile("bestRelationVectors" + this.fileExtension);
		bestRelationVectors.output(fnRelations);

		logger.info("Finished training the model.");
		//writer.flush();

		//} catch (Exception ex) { ex.printStackTrace(); }
		logger.info("Shutting down.");
	}
	
	public void TransE_Learn() throws Exception {
		HashMap<Integer, ArrayList<KaleTriple>> lstPosTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
		HashMap<Integer, ArrayList<KaleTriple>> lstHeadNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
		HashMap<Integer, ArrayList<KaleTriple>> lstTailNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
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
		while (iIter < m_NumEpochs) {
			m_TrainingTriples.randomShuffle();
			for (int iIndex = 0; iIndex < m_TrainingTriples.nTriples(); iIndex++) {
				KaleTriple PosTriple = m_TrainingTriples.get(iIndex);
				NegativeTripleGenerator negTripGen = new NegativeTripleGenerator(
						PosTriple, m_NumEntity, m_NumUniqueRelation);
				KaleTriple headNegTriple = negTripGen.generateHeadNegTriple();
				KaleTriple tailNegTriple = negTripGen.generateTailNegTriple();
				
				int iID = iIndex % m_NumMiniBatch;
				if (!lstPosTriples.containsKey(iID)) {
					ArrayList<KaleTriple> tmpPosLst = new ArrayList<KaleTriple>();
					ArrayList<KaleTriple> tmpHeadNegLst = new ArrayList<KaleTriple>();
					ArrayList<KaleTriple> tmpTailNegLst = new ArrayList<KaleTriple>();
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
			
			//m_TrainingRules.randomShuffle();
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

			
			lstPosTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
			lstHeadNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();
			lstTailNegTriples = new HashMap<Integer, ArrayList<KaleTriple>>();

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

	public String generateFile(final String fnDirName) throws Exception {
		String resPath = "";

		int num = 0;
		resPath = this.FILEPATH + "/" + num + "_" + fnDirName;
		File f = new File(resPath);
		while (f.exists()) {
			num++;
			resPath = this.FILEPATH + "/" + num + "_" + fnDirName;
			f = new File(resPath);
		}
		return resPath;
	}
}