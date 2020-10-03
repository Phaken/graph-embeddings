package org.uu.nl.embedding.kale;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.log4j.Logger;
//import org.uu.nl.embedding.KaleOptimizer;
import org.uu.nl.embedding.bca.BookmarkColoring;
import org.uu.nl.embedding.bca.models.BookmarkColoringEdges;
import org.uu.nl.embedding.bca.models.BookmarkColoringNodes;
import org.uu.nl.embedding.kale.model.KaleModel;
import org.uu.nl.embedding.kale.util.DataGenerator;
import org.uu.nl.embedding.opt.CostFunction;
import org.uu.nl.embedding.opt.GloveCost;
import org.uu.nl.embedding.opt.IOptimizer;
import org.uu.nl.embedding.opt.Optimum;
import org.uu.nl.embedding.opt.PGloveCost;
import org.uu.nl.embedding.opt.grad.AMSGrad;
import org.uu.nl.embedding.opt.grad.AMSGradKale;
import org.uu.nl.embedding.opt.grad.Adagrad;
import org.uu.nl.embedding.opt.grad.AdagradKale;
import org.uu.nl.embedding.opt.grad.Adam;
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.config.Configuration.BCA;
import org.uu.nl.embedding.util.read.EmbeddingTextReader;
import org.uu.nl.embedding.util.write.EmbeddingTextWriter;
import org.uu.nl.embedding.util.write.EmbeddingWriter;
import org.uu.nl.embedding.util.write.KaleEmbeddingTextWriter;

public class KaleRunner {
	
	java.text.DecimalFormat decimalFormat = new java.text.DecimalFormat("#.######");
    private final static Logger logger = Logger.getLogger("KaleRunner");
	
    /*
	private final KaleModel kale;
	private final BookmarkColoring BCA;
	private final InMemoryRdfGraph graph;
	private final Configuration config;
	private final int iNumEntities;
	private final int iNumRelations;
	private final int dictSize;
	private final int iBcvSize;
	private final HashSet<String> uniqueRelationTypes;
	private final TreeMap<Integer, Integer> edgeTypeMap;
	private final int iNumUniqueRelations;*/

	private KaleModel kale;
	private BookmarkColoringNodes bcaNodes;
	private BookmarkColoringEdges bcaEdges;
	private InMemoryRdfGraph graph;
	private Configuration config;
	private int iNumVerts;
	private int iNumEdges;
	private int iNumTotal;
	private int iDim;
	
	private float[] gloveArrayNodes;
	private int[] orderedNodes;
	private float[] gloveArrayEdges;
	private int[] orderedEdges;
	
	private HashMap<String, Integer> uniqueRelationTypes;
	private TreeMap<Integer, Integer> edgeIdTypeMap;
	private int iNumUniqueRelations;
	
	private int m_NumFactor = 20;
	private int m_NumMiniBatch = 100;
	private double m_Delta = 0.1;
	private double m_GammaE = 0.01;
	private double m_GammaR = 0.01;
	private int m_NumIteration = 1000;
	private int m_OutputIterSkip = 50;
	private double m_Weight = 0.01;
	

	private String FILEPATH = "/Users/euan/eclipse-workspace/graphLogicEmbed/data/input";
	private String fileExtension;
	private String fnSaveFile;
	private String fnTrainingTriples;
	private String fnValidateTriples;
	private String fnTestingTriples;
	private String fnTrainingRules;
	private String fnNodeGloveVectors;
	private String fnEdgeGloveVectors;
	
	private String fnOptimumNodes;
	private String fnOptimumEdges;
	
	/**
	 * 
	 * @param graph
	 * @param config
	 */
	public KaleRunner(final InMemoryRdfGraph graph,
					final Configuration config) throws Exception {
		
        logger.info("Starting KaleRunner.");
		
		this.graph = graph;
		this.config = config;
		this.fileExtension = ".tsv";
		
		this.iDim = config.getDim();
		this.m_NumFactor = config.getDim();
		final int[] verts = graph.getVertices().toIntArray();
		final int[] edges = graph.getEdges().toIntArray();
		this.iNumVerts = verts.length;
		this.iNumEdges = edges.length;
        this.iNumTotal = this.iNumVerts + this.iNumEdges;
		this.uniqueRelationTypes = new HashMap<String, Integer>();

		int e = -1;
        logger.info("Determining unique predicates.");
        try {
        	for (e = 0; e < edges.length; e++) {
        		String predicate;
        		if (graph.getEdgeTypeProperty().getValueAsInt(edges[e]) != 0) 
        			predicate = graph.getEdgeLabelProperty().getValueAsString(edges[e]).toLowerCase();
        		else predicate = "generated_predicate";
				
				if (!this.uniqueRelationTypes.containsKey(predicate)) {
					this.uniqueRelationTypes.put(predicate, graph.getEdgeTypeProperty().getValueAsInt(edges[e]));
				}
			}
			int newType = this.uniqueRelationTypes.size();
			while (this.uniqueRelationTypes.containsValue(newType))
				newType++;
			this.uniqueRelationTypes.put("is_same_or_before", newType);
			
        } catch (Exception ex) { /*System.out.println("\nweird edge = " + edges[e] + "\n");*/ ex.printStackTrace(); }
        logger.info("Finished with " +uniqueRelationTypes.size()+ " unique predicates.");
		
		this.iNumUniqueRelations = uniqueRelationTypes.size();
		System.out.println("Number of unique relations found: " +uniqueRelationTypes.size());
		for (Entry<String, Integer> entry : this.uniqueRelationTypes.entrySet()) {
			System.out.println("typeID: " +entry.getValue()+ ", relation: "+entry.getKey());
		}

        logger.info("Generating kale BookmarkColoring.");
        try { 
        	/*boolean noOptimumFile = false;
        	fnOptimumNodes = DataGenerator.checkFileExists(this.FILEPATH, this.fileExtension, "OptimumNodes", 0);
        	fnOptimumEdges = DataGenerator.checkFileExists(this.FILEPATH, this.fileExtension, "OptimumEdges", 0);
        	if (fnOptimumNodes == "") {
        		fnOptimumNodes = DataGenerator.generateFileDir(this.FILEPATH, this.fileExtension, "OptimumNodes");
        		noOptimumFile = true;
        	}
        	if (fnOptimumEdges == "") {
        		fnOptimumEdges = DataGenerator.generateFileDir(this.FILEPATH, this.fileExtension, "OptimumEdges");
        		noOptimumFile = true;
        	}
        	/*
        	 * TEMP
        	 *
        	noOptimumFile = true;
    		fnOptimumNodes = DataGenerator.generateFileDir(this.FILEPATH, this.fileExtension, "OptimumNodes");
    		fnOptimumEdges = DataGenerator.generateFileDir(this.FILEPATH, this.fileExtension, "OptimumEdges");
        	/*
        	 * TEMP
        	 */
            this.bcaNodes = new BookmarkColoringNodes(graph, config);
            this.bcaEdges = new BookmarkColoringEdges(graph, config,
            											this.bcaNodes,
            											this.uniqueRelationTypes);

        	if (true) {
        	//if (noOptimumFile) {

        		fnOptimumNodes = DataGenerator.generateFileDir(this.FILEPATH, this.fileExtension, "OptimumNodes");
        		fnOptimumEdges = DataGenerator.generateFileDir(this.FILEPATH, this.fileExtension, "OptimumEdges");
	            final IOptimizer optimizerNodes = createOptimizer(config, bcaNodes);
	            final IOptimizer optimizerEdges = createOptimizer(config, bcaEdges);
	            final Optimum optimumNodes = optimizerNodes.optimize();
	            final Optimum optimumEdges = optimizerEdges.optimize();
	            this.gloveArrayNodes = optimumNodes.getResult();
	            this.gloveArrayEdges = optimumEdges.getResult();
	            final KaleEmbeddingTextWriter writerNodes = new KaleEmbeddingTextWriter(fnOptimumNodes, config, this.fileExtension);
	            final KaleEmbeddingTextWriter writerEdges = new KaleEmbeddingTextWriter(fnOptimumEdges, config, this.fileExtension);
	            writerNodes.write(this.gloveArrayNodes, bcaNodes.getOrderedIDs(), this.iDim);
	            writerEdges.write(this.gloveArrayEdges, bcaEdges.getOrderedIDs(), this.iDim);
	
	            
        	} else {
        		EmbeddingTextReader readerNodes = new EmbeddingTextReader(fnOptimumNodes);
        		EmbeddingTextReader readerEdges = new EmbeddingTextReader(fnOptimumEdges);
        		this.gloveArrayNodes = readerNodes.getFloatEmbeddings();
        		this.gloveArrayEdges = readerEdges.getFloatEmbeddings();
        		this.orderedNodes = readerNodes.getOrderedIDs();
        		this.orderedEdges = readerEdges.getOrderedIDs();
        		this.iDim = readerNodes.getDimension();
        		
        	}
            
	        logger.info("Finished generating kale BookmarkColoring.");
	        //this.edgeTypeMap = this.BCA.generateEdgeTypeMap();

	        logger.info("Starting dataGenerator.");
			boolean undirected = true;
			this.fileExtension = ".tsv";
			//DataGenerator dataGenerator = new DataGenerator(graph, config, undirected, this.bcaNodes, this.uniqueRelationTypes);
			//dataGenerator.Initialize(this.FILEPATH, this.fileExtension, this.bcaNodes, this.bcaEdges);
			DataGenerator dataGenerator = new DataGenerator(graph, config, undirected,
					this.uniqueRelationTypes,
					this.FILEPATH, this.fileExtension,
					this.bcaNodes, this.bcaEdges);
	        logger.info("Finished dataGenerator.");
			
			this.fnSaveFile = generateResultsFileDir();
			
			this.fnTrainingTriples = dataGenerator.getFileTrainingTriples();
			this.fnValidateTriples = dataGenerator.getFileValidateTriples();
			this.fnTestingTriples = dataGenerator.getFileTestingTriples();
			this.fnTrainingRules = dataGenerator.getFileTrainingRules();
			this.fnNodeGloveVectors = dataGenerator.getFileNodeBCVs();
			this.fnEdgeGloveVectors = dataGenerator.getFileEdgeBCVs();
	
	        logger.info("Creating Kale Model and initializing it.");
			//this.kale = new KaleModel();
			//this.kale.Initialization(this.iNumVerts,
	        this.kale = new KaleModel(this.iNumVerts,
					this.iNumTotal,
					this.gloveArrayNodes,
					this.gloveArrayEdges,
					this.fnTrainingTriples, 
					this.fnValidateTriples, 
					this.fnTestingTriples, 
					//this.fnTrainingRules,
					this.fileExtension,
					this.uniqueRelationTypes,
					this.graph, this.config,
					dataGenerator,
					bcaNodes, bcaEdges);
			
	        logger.info("Start training the "
	        		+ "Kale Model using Cochez method.");
			this.kale.CochezLearn(true);
        } catch (Exception ex) { ex.printStackTrace(); }
	}
	
	public CoOccurrenceMatrix getKaleVectors() {
		return this.kale.kaleVectorMatrix;
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	public void CochezLearn() throws Exception {
		this.kale.CochezLearn();
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	public void TransELearn() throws Exception {
		this.kale.TransE_Learn();
	}
	

	/*
	 * Hyper-parameter getters and setters.
	 *
	 */
	
	public void setNumFactors(final int numFactors) {
		this.m_NumFactor = numFactors;
		this.kale.m_NumFactor = numFactors;
	}
	
	public void setNumMiniBatch(final int numMiniBatch) {
		this.m_NumMiniBatch = numMiniBatch;
		this.kale.m_NumMiniBatch = numMiniBatch;
	}
	
	public void setDelta(final double delta) {
		this.m_Delta = delta;
		this.kale.m_Delta = delta;
	}
	
	public void setGammaE(final double gammaE) {
		this.m_GammaE = gammaE;
		this.kale.m_GammaE = gammaE;
	}
	
	public void setGammaR(final double gammaR) {
		this.m_GammaR = gammaR;
		this.kale.m_GammaR = gammaR;
	}
	
	public void setNumIteration(final int numIterations) {
		this.m_NumIteration = numIterations;
		this.kale.m_NumIteration = numIterations;
	}
	
	public void setOutputIterSkip(final int outputIterSkip) {
		this.m_OutputIterSkip = outputIterSkip;
		this.kale.m_OutputIterSkip = outputIterSkip;
	}
	
	public void setWeight(final double weight) {
		this.m_Weight = weight;
		this.kale.m_Weight = weight;
	}
	
	public void setfileExtension(final String fileExtension) throws Exception {
		// Simple check
		if (fileExtension.charAt(0) != '.') { 
			throw new Exception("Provided file extension in KaleRunner has invalid format.");
		}
		this.fileExtension = fileExtension;
		this.kale.fileExtension = fileExtension;

		this.fnSaveFile = generateResultsFileDir();
	}
	
	public CoOccurrenceMatrix getBcaNodes() {
		return this.bcaNodes;
	}
	
	public CoOccurrenceMatrix getBcaEdges() {
		return this.bcaEdges;
	}

	
	private static IOptimizer createOptimizer(final Configuration config, final CoOccurrenceMatrix coMatrix) {

        CostFunction cf;
        switch (config.getMethodEnum()) {
            default:
                throw new IllegalArgumentException("Invalid cost function");
            case GLOVE:
                cf = new GloveCost();
                break;
            case PGLOVE:
                cf = new PGloveCost();
                break;
        }

        switch(config.getOpt().getMethodEnum()) {
            default:
                throw new IllegalArgumentException("Invalid optimization method");
            case ADAGRAD:
            	if (coMatrix instanceof BookmarkColoringEdges) return new AdagradKale((BookmarkColoringEdges)coMatrix, config, cf);
                return new Adagrad(coMatrix, config, cf);
            case ADAM:
                return new Adam(coMatrix, config, cf);
            case AMSGRAD:
            	if (coMatrix instanceof BookmarkColoringEdges) return new AMSGradKale((BookmarkColoringEdges)coMatrix, config, cf);
                return new AMSGrad(coMatrix, config, cf);
        }
    }
	
	/**
	 * Generates file directory without overwriting existing
	 * files by iterating through numbers at start of file.
	 * 
	 * @param fnDirName
	 * @return
	 */
	public String generateResultsFileDir() {
		int num = 0;
		String fnTail = "_result-k" + this.m_NumFactor 
				+ "-d" + this.decimalFormat.format(this.m_Delta)
				+ "-ge" + this.decimalFormat.format(this.m_GammaE) 
				+ "-gr" + this.decimalFormat.format(this.m_GammaR)
				+ "-w" +  this.decimalFormat.format(this.m_Weight) + this.fileExtension;
		String fnDir = this.FILEPATH + num + fnTail;
		
		File f = new File(fnDir);
		while (f.exists()) {
			num++;
			fnDir = this.FILEPATH + num + fnTail;
			f = new File(fnDir);
		}
		return fnDir;
	}
	
	public int getNumFactors() {
		return this.m_NumFactor;
	}
	
	public int getNumMiniBatch() {
		return this.m_NumMiniBatch;
	}
	
	public double getDelta() {
		return this.m_Delta;
	}
	
	public double getGammaE() {
		return this.m_GammaE;
	}
	
	public double getGammaR() {
		return this.m_GammaR;
	}
	
	public int getNumIteration() {
		return this.m_NumIteration;
	}
	
	public int getOutputIterSkip() {
		return this.m_OutputIterSkip;
	}
	
	public double getWeight() {
		return this.m_Weight;
	}
	
	public String getfileExtension() {
		return this.fileExtension;
	}
}
