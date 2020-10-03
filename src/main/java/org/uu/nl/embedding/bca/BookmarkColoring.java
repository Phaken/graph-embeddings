package org.uu.nl.embedding.bca;

import me.tongfei.progressbar.ProgressBar;
import org.apache.log4j.Logger;
import org.uu.nl.embedding.bca.jobs.ContextWinnowedUndirectedWeighted;
import org.uu.nl.embedding.bca.jobs.DirectedWeighted;
import org.uu.nl.embedding.bca.jobs.HybridWeighted;
import org.uu.nl.embedding.bca.jobs.KaleUndirectedWeighted;
import org.uu.nl.embedding.bca.jobs.KaleUndirectedWeightedNodeBased;
import org.uu.nl.embedding.bca.jobs.KaleUndirectedWeightedSeperated;
import org.uu.nl.embedding.bca.jobs.UndirectedWeighted;
import org.uu.nl.embedding.bca.util.BCV;
import org.uu.nl.embedding.convert.util.InEdgeNeighborhoodAlgorithm;
import org.uu.nl.embedding.convert.util.NodeInfo;
import org.uu.nl.embedding.convert.util.OutEdgeNeighborhoodAlgorithm;
import org.uu.nl.embedding.kale.util.DataGenerator;
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.Permutation;

import grph.properties.NumericalProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.*;

/**
 * @author Jurian Baas
 */
public class BookmarkColoring implements CoOccurrenceMatrix {

    private static Logger logger = Logger.getLogger("BookmarkColoring");

	private final ArrayList<Integer> coOccurrenceIdx_I;
	private final ArrayList<Integer> coOccurrenceIdx_J;
	private final ArrayList<Float> coOccurrenceValues;
	private ArrayList<Integer> coOccurrenceIdx_I_edge = new ArrayList<Integer>();
	private ArrayList<Integer> coOccurrenceIdx_J_edge = new ArrayList<Integer>();
	private ArrayList<Float> coOccurrenceValues_edge = new ArrayList<Float>();
	private Map<String, Double> bcvMaxVals;
	private final ArrayList<BCV> BCVs;
	public int maxNeighbors;
	private int neighborCntr;
	private boolean nonDefault;
	
	private double max;
	private final int focusVectors, contextVectors;
	private int coOccurrenceCount;
	private int coOccurrenceCount_edge;
	private Permutation permutation;
	private Permutation permutation_edge;
	private final InMemoryRdfGraph graph;
	

	private final int[][] inVertex;
	private final int[][] outVertex;
	private final int[][] inEdge;
	private final int[][] outEdge;
	private final int dictSize;
	private final ArrayList<Integer> allVecs;
	private int[][] allOut;
	private int[][] allIn;
	private final Map<Integer, Integer> context2focus;
	private final Map<Integer, Integer> focus2context;
	

	TreeMap<Integer, int[]> neighborVertsIn;
	TreeMap<Integer, int[]> neighborVertsOut;
	TreeMap<Integer, int[]> neighborEdgesIn;
	TreeMap<Integer, int[]> neighborEdgesOut;
	
	public TreeMap<Integer, Integer> edgeIdMap;
	public TreeMap<Integer, Integer> edgeIdTypeMap;
	
	/*
	 * START TEMP
	 */
	String maxHistory = "";
	public ArrayList<BCV> nonEmptyBcvs;
	/*
	 * START TEMP
	 */

	public BookmarkColoring(final InMemoryRdfGraph graph, final Configuration config) {

		final double alpha = config.getBca().getAlpha();
		final double epsilon = config.getBca().getEpsilon();
		final int[] verts = graph.getVertices().toIntArray();
		final boolean[] performBCA = new boolean[verts.length];

		this.allVecs = new ArrayList<Integer>();
		BCVs = new ArrayList<BCV>();

		final Configuration.Output output = config.getOutput();

		this.context2focus = new HashMap<>();
		this.focus2context = new HashMap<>();
		this.nonDefault = false;

		int notSkipped = 0;

		for(int i = 0; i < verts.length; i++) {

			final int vert = verts[i];
			final byte type = (byte) graph.getVertexTypeProperty().getValueAsInt(vert);
			final String key = graph.getVertexLabelProperty().getValueAsString(vert);
			final NodeInfo nodeInfo = NodeInfo.fromByte(type);

			switch (nodeInfo) {
				case URI:
					if(output.outputUriNodes() && (output.getUri().isEmpty() || output.getUri().stream().anyMatch(key::startsWith))) {
						performBCA[i] = true;
						notSkipped++;
					}
					break;
				case BLANK:
					if(output.outputBlankNodes()) {
						performBCA[i] = true;
						notSkipped++;
					}
					break;
				case LITERAL:
					if(output.outputLiteralNodes() && (output.getLiteral().isEmpty() || output.getLiteral().stream().anyMatch(key::startsWith))) {
						performBCA[i] = true;
						notSkipped++;
					}
					break;
			}
		}

		this.graph = graph;
		this.contextVectors = verts.length;
		this.focusVectors = notSkipped;
		this.coOccurrenceIdx_I = new ArrayList<>(notSkipped);
		this.coOccurrenceIdx_J = new ArrayList<>(notSkipped);
		this.coOccurrenceValues = new ArrayList<>(notSkipped);

		final int numThreads = config.getThreads();

		final ExecutorService es = Executors.newWorkStealingPool(numThreads);

		this.inVertex = graph.getInNeighborhoods();
		this.outVertex = graph.getOutNeighborhoods();
		this.inEdge = new InEdgeNeighborhoodAlgorithm(config).compute(graph);
		this.outEdge = new OutEdgeNeighborhoodAlgorithm(config).compute(graph);
		this.dictSize = DataGenerator.calculateDictSize(this.outVertex);

		CompletionService<BCV> completionService = new ExecutorCompletionService<>(es);

		for(int i = 0, j = 0; i < verts.length; i++) {

			if(!performBCA[i]) continue;

			final int bookmark = verts[i];
			context2focus.put(bookmark, j);
			focus2context.put(j, bookmark);
			j++;

			// Choose a graph neighborhood algorithm
			switch (config.getBca().getTypeEnum()){

				case DIRECTED:
					completionService.submit(new DirectedWeighted(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					break;
				case UNDIRECTED:
					completionService.submit(new UndirectedWeighted(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					break;
				case HYBRID:
					completionService.submit(new HybridWeighted(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					break;
			}
		}

		progressThroughMatrix(config, notSkipped, es, completionService);
	}
	
	/**
	 * 
	 * @param graph
	 * @param config
	 * @param nonDefault
	 */
	public BookmarkColoring(final InMemoryRdfGraph graph, final Configuration config, final boolean nonDefault) throws Exception {
		
		logger = Logger.getLogger("KALE - BookmarkColoring");
        logger.info("Starting Kale Bookmark Coloring.");
		
		final double alpha = config.getBca().getAlpha();
		final double epsilon = config.getBca().getEpsilon();
		final int[] verts = graph.getVertices().toIntArray();
		final boolean[] performBCA = new boolean[verts.length];

		this.graph = graph;
		this.contextVectors = verts.length;
		this.maxNeighbors = 0;
		this.neighborCntr = 0;
		
		this.allVecs = new ArrayList<Integer>();
		this.bcvMaxVals = new HashMap<String, Double>();
		this.nonEmptyBcvs = new ArrayList<BCV>();
		this.BCVs = new ArrayList<BCV>();
		this.nonDefault = nonDefault;
		
        logger.info("Reading configuration...");
		final Configuration.Output output = config.getOutput();

		this.inVertex = graph.getInNeighborhoods();
		this.outVertex = graph.getOutNeighborhoods();
		this.inEdge = new InEdgeNeighborhoodAlgorithm(config).compute(graph);
		this.outEdge = new OutEdgeNeighborhoodAlgorithm(config).compute(graph);
		this.dictSize = DataGenerator.calculateDictSize(this.outVertex);
        logger.info("Edges read ==> in-edges are: " +this.inEdge.length+ " & out-edges are: " +this.outEdge.length);

        /*
         * START TEMP
         *
        System.out.println("BookmarkColoring constructor - Vertex 8 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(8)) + "\n");
        System.out.println("BookmarkColoring constructor - Vertex 70 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(70)) + "\n");
        System.out.println("BookmarkColoring constructor - Vertex 107 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(107)) + "\n");
        System.out.println("BookmarkColoring constructor - Vertex 141 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(141)) + "\n");
        System.out.println("BookmarkColoring constructor - Vertex 675 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(675)) + "\n");
        System.out.println("BookmarkColoring constructor - Vertex 16523 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(16523)) + "\n");*/
        System.out.println("BookmarkColoring constructor - Vertex 40947 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(40947)) + "\n");
        System.out.println("BookmarkColoring constructor - Vertex 2486 type: " + NodeInfo.fromByte((byte) this.graph.getVertexTypeProperty().getValueAsInt(2486)) + "\n");
        /*
         * END TEMP
         */
        
		int notSkipped = 0;

		logger.info("Start selecting vertices to compute BCVs.");
		for(int i = 0; i < this.contextVectors; i++) {

			final int vert = verts[i];
			final byte type = (byte) graph.getVertexTypeProperty().getValueAsInt(vert);
			final String key = graph.getVertexLabelProperty().getValueAsString(vert);
			final NodeInfo nodeInfo = NodeInfo.fromByte(type);

			switch (nodeInfo) {
				case URI:
					if(output.outputUriNodes() && (output.getUri().isEmpty() || output.getUri().stream().anyMatch(key::startsWith))) {
						performBCA[i] = true;
						notSkipped++;
						/*if (vert == 70 || vert == 107 || vert == 675)
							System.out.println("BookmarkColoring constructor - Vertex " + vert + " is added to performBCA.\n\n");*/
					}
					break;
				case BLANK:
					if(output.outputBlankNodes()) {
						performBCA[i] = true;
						notSkipped++;
					}
					break;
				case LITERAL:
					if(output.outputLiteralNodes() && (output.getLiteral().isEmpty() || output.getLiteral().stream().anyMatch(key::startsWith))) {
						performBCA[i] = true;
						notSkipped++;
						/*if (vert == 141 || vert == 16523)
							System.out.println("BookmarkColoring constructor - Vertex " + vert + " is added to performBCA.\n\n");*/
					}
					break;
				}
		}
		logger.info("Finished selecting vertices to compute BCVs.");
		this.edgeIdMap = generateEdgeIdMap();
		this.edgeIdTypeMap = generateEdgeIdTypeMap();
		// Initialization standard co-occurrence matrix
		this.focusVectors = notSkipped;
		final int nVectors = notSkipped + this.edgeIdTypeMap.size();
		this.coOccurrenceIdx_I = new ArrayList<>(notSkipped);
		this.coOccurrenceIdx_J = new ArrayList<>(notSkipped);
		this.coOccurrenceValues = new ArrayList<>(notSkipped);
		this.coOccurrenceIdx_I_edge = new ArrayList<>();
		this.coOccurrenceIdx_J_edge = new ArrayList<>();
		this.coOccurrenceValues_edge = new ArrayList<>();
		this.coOccurrenceCount_edge = 0;
		
		ArrayList<TreeMap<Integer, int[]>> edgeNeighbors = DataGenerator.determineEdgeNeighbors(inVertex, outVertex, inEdge, outEdge);
		neighborVertsIn = edgeNeighbors.get(0);
		neighborVertsOut = edgeNeighbors.get(1);
		neighborEdgesIn = edgeNeighbors.get(2);
		neighborEdgesOut = edgeNeighbors.get(3);

		final int numThreads = config.getThreads();

		final ExecutorService es = Executors.newWorkStealingPool(numThreads);

		CompletionService<BCV> completionService = new ExecutorCompletionService<>(es);

		this.context2focus = new HashMap<>();
		this.focus2context = new HashMap<>();
		int[] vectorIDs = generateIdArray(performBCA, this.edgeIdTypeMap);
		//System.out.println("notSkipped + this.edgeIdTypeMap.size()= " + nVectors + "\nvectorIDs.length= " + vectorIDs.length);
		
		// Create subgraphs according to config-file.
		boolean loggerInfoProvided = false;
		logger.info("Start performing generating BCVs.");
		for(int i = 0, j = 0; i < vectorIDs.length; i++) {
			
			// Skip unnecessary nodes.
			//if(!performBCA[i]) continue; /* Already filtered out. */
			
			final int bookmark = vectorIDs[i];
			/*
			 * START TEMP
			 */
			if (bookmark == 70 || bookmark == 107 || bookmark == 141) 
				System.out.println("BookmarkColoring constructor - Bookmark no.: " + bookmark + " found.");
			//if (bookmark > this.outVertex.length) System.out.println("BookmarkColoring constructor - Starting BCV for EdgeID.");
			//else System.out.println("BookmarkColoring constructor - Meh.");
			/*
			 * END TEMP
			 */
			
/*Verplaatsen naar progressThroughMatrix?*/context2focus.put(bookmark, j);
/*Verplaatsen naar progressThroughMatrix?*/focus2context.put(j, bookmark);
			j++;

			// Choose a graph neighborhood algorithm
			switch (config.getBca().getTypeEnum()){
				case DIRECTED:
					completionService.submit(new DirectedWeighted(
							graph, bookmark,
							alpha, epsilon,
							this.inVertex, this.outVertex, this.inEdge, this.outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated DIRECTED BCV.");
						loggerInfoProvided = true;
					}
					break;
				case UNDIRECTED:
					completionService.submit(new UndirectedWeighted(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a UNDIRECTED BCV.");
						loggerInfoProvided = true;
					}
					break;
				case CONTEXTWINNOWED:
					/*
					 * Days difference fiksen vanuit config
					 */
					int daysDiff = 0;
					completionService.submit(new ContextWinnowedUndirectedWeighted(
							graph, bookmark, daysDiff,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a CONTEXTWINNOWED BCV.");
						loggerInfoProvided = true;
					}
					break;
				case HYBRID:
					completionService.submit(new HybridWeighted(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a HYRBID BCV.");
						loggerInfoProvided = true;
					}
					break;
			}
		}
		progressThroughMatrix(config, vectorIDs.length, es, completionService);
	}
		
	
	/**
	 * 
	 * @param config
	 * @param notSkipped
	 * @param es
	 * @param completionService
	 */
	public void progressThroughMatrix(final Configuration config, final int notSkipped,
			final ExecutorService es, final CompletionService<BCV> completionService) {
		
		logger.info("Start trying to loop through co-occurrence matrix.");

		int received = 0;
		try(ProgressBar pb = Configuration.progressBar("BCA", notSkipped, "nodes")) {
			logger.info("Progression bar initialized.");
			
			TreeMap<Integer, BCV> edgeBcvs = new TreeMap<Integer, BCV>();
			
			//now retrieve the futures after computation (auto wait for it)
			while(received < notSkipped) {
				try {

					final BCV bcv = completionService.take().get();
					BCVs.add(bcv);
					if (received == 0) logger.info("First CompletionService finished: BCV received.");
				
					switch (config.getBca().getNormalizeEnum()) {
						case UNITY:
							bcv.toUnity();
							break;
						case COUNTS:
							bcv.toCounts();
							break;
						default:
						case NONE:
							break;
					}

					if (this.nonDefault && bcv.getRootNode() > this.outVertex.length) {
						int type = this.edgeIdTypeMap.get(bcv.getRootNode());
						BCV bcvNew = bcv.copy();
						if (edgeBcvs.containsKey(type)) {
							BCV bcvOld = edgeBcvs.get(type).copy();
							bcvNew.merge(bcvOld);
						}
						edgeBcvs.put(type, bcvNew);
					}
				
					// It is possible to use this maximum value in GloVe, although in the
					// literature they set this value to 100 and leave it at that
					setMax(bcv.getRootNode(), bcv.max());
					// Add root node to all vectors ArrayList.
					this.allVecs.add(bcv.getRootNode());
					
					/*
					 * START TEMP
					 *
					String tempStr = bcv.toString();
					if (bcv.notEmpty && bcv.getRootNode() < 30) this.nonEmptyBcvs.add(bcv);
					/*
					 * END TEMP
					 */
					
					// Create co-occurrence matrix for standard bcv
					this.neighborCntr = 0;
					if (bcv.getRootNode() < this.outVertex.length) {
						for (Entry<Integer, Float> bcr : bcv.entrySet()) {
							coOccurrenceIdx_I.add(bcv.getRootNode());
							coOccurrenceIdx_J.add(bcr.getKey());
							coOccurrenceValues.add(bcr.getValue());
							if (bcr.getValue() != 0f) neighborCntr++;
						}
						coOccurrenceCount += bcv.size();
					}
					
					if (this.maxNeighbors < neighborCntr) { this.maxNeighbors = neighborCntr; }

					/*
					 * START TEMP
					 */
					//if (bcv.getRootNode() > this.outVertex.length) System.out.println("BookmarkColoring.progressThroughMatrix() Edge node added to BCV.");
					//else System.out.println("Node node added.");
					/*
					 * END TEMP
					 */
					
					if (received == notSkipped-1) logger.info("Last BCV was added to the matrix.");
				
				} catch (InterruptedException | ExecutionException e) {
					logger.info("Failed to take BCV and add it to the matrix.");
					e.printStackTrace();
				} finally {
					received++;
					pb.step();
				}
			}
			try {
				for (Map.Entry entry : edgeBcvs.entrySet()) {
					// Create co-occurrence matrix for standard bcv
					BCV bcv = (BCV) entry.getValue();
					this.neighborCntr = 0;
					for (Entry<Integer, Float> bcr : bcv.entrySet()) {
						this.coOccurrenceIdx_I_edge.add((int) entry.getKey());
						this.coOccurrenceIdx_J_edge.add(bcr.getKey());
						this.coOccurrenceValues_edge.add(bcr.getValue());
						if (bcr.getValue() != 0f) neighborCntr++;
					}
					this.coOccurrenceCount_edge += bcv.size();
				}
			} catch (Exception e) {
				logger.info("Failed to take BCV and add it to the matrix. 'Received' was: " + received);
				e.printStackTrace();
			}
			determineAllInOut();
		
		} finally {
			logger.info("'Received' final value = " + received + ". ExecutorService shutting down.");
			es.shutdown();
		}
		
		logger.info("Max BCV history: " + this.maxHistory);
		logger.info("The co-occurrence count is: " +coOccurrenceCount);
		logger.info("The co-occurrence count for edges is: " +coOccurrenceCount_edge);
		this.permutation = new Permutation(coOccurrenceCount);
		this.permutation_edge = new Permutation(coOccurrenceCount_edge);
		logger.info("Class permutations variable finalized.");
	}

	/**
	 * 
	 * @param graph
	 * @return
	 */
    public TreeMap<Integer, Integer> generateEdgeIdMap() {
    	
    	logger.info("Start generating the edgeIdMap.");
    	try {
	        final int numVerts = this.graph.getVertices().toIntArray().length;
	        //System.out.println("numVerts="+numVerts);
	        final TreeMap<Integer, Integer> edgeNodeID = new TreeMap<Integer, Integer>();
	        
	        int[] edges;
	        int edge, edgeID; int tempCntr = 0;
	        for (int v = 0; v < this.outEdge.length; v++) {
	        	edges = this.outEdge[v];
	        	for (int e = 0; e < edges.length; e++) {
	        		edge = edges[e];
		        	
		        	if (!edgeNodeID.containsKey(edge)) {
			        	edgeID = this.outVertex.length + edge;
		        		edgeNodeID.put(edge, edgeID);
		        		tempCntr++;
		        	}
	        }}
	        System.out.println("added "+tempCntr + " edges to the generateEdgeIdMap");
	        logger.info("Finished generating the edgeIdMap.");
	        return edgeNodeID;
    	} catch (Exception ex) { ex.printStackTrace(); }
    	return new TreeMap<Integer, Integer>(); 
    }


	/**
	 * 
	 * @param graph
	 * @return
	 */
    public TreeMap<Integer, Integer> generateEdgeTypeMap() throws Exception {
    	
    	logger.info("Start generating the edgeIdMap.");

    	try {
			NumericalProperty edgeTypes = this.graph.getEdgeTypeProperty();
	        final TreeMap<Integer, Integer> edgeTypeMap = new TreeMap<>();
	        
	        int[] edges;
	        int edge; int tempCntr = 0;
	        for (int v = 0; v < this.outEdge.length; v++) {
	        	edges = this.outEdge[v];
	        	for (int e = 0; e < edges.length; e++) {
	        		edge = edges[e];	        	
		        	if (!edgeTypeMap.containsKey(edge)) {
		        		edgeTypeMap.put(edge, edgeTypes.getValueAsInt(edge) + dictSize);
		        		tempCntr++;
		        	}
	        }}

	        System.out.println("added "+tempCntr + " edges to the generateEdgeTypeMap");
	        logger.info("Finished generating the edgeIdMap.");
	        return edgeTypeMap;
    	} catch (Exception ex) { ex.printStackTrace(); }
    	return new TreeMap<Integer, Integer>(); 
    }
    
    /**
	 * 
	 * @param graph
	 * @return
	 */
    public TreeMap<Integer, Integer> generateEdgeIdTypeMap() {
    	
    	logger.info("Start generating the edgeIdMap.");
    	try {
			NumericalProperty edgeTypes = this.graph.getEdgeTypeProperty();
	        final TreeMap<Integer, Integer> edgeTypeMap = new TreeMap<Integer, Integer>();
	        
	        int[] edges;
	        int edge; int tempCntr = 0;
	        for (int v = 0; v < this.outEdge.length; v++) {
	        	edges = this.outEdge[v];
	        	for (int e = 0; e < edges.length; e++) {
	        		edge = this.edgeIdMap.get(edges[e]);	        	
		        	if (!edgeTypeMap.containsKey(edge)) {
		        		edgeTypeMap.put(edge, edgeTypes.getValueAsInt(edge) + dictSize);
		        		tempCntr++;
		        	}
	        }}

	        System.out.println("added "+tempCntr + " edges to the generateEdgeIdTypeMap");
	        logger.info("Finished generating the edgeIdMap.");
	        return edgeTypeMap;
    	} catch (Exception ex) { ex.printStackTrace(); }
    	return new TreeMap<Integer, Integer>(); 
    }
    
    private int[] generateIdArray(final boolean[] bcaNodes, final TreeMap<Integer, Integer> edgeIdTypeMap) {
    	
    	logger.info("Start generating id-array of both nodes and edges.");
    	try {
	    	int[] array = new int[edgeIdTypeMap.size()+bcaNodes.length];
	    	int j = 0;
	    	for (int i = 0; i < bcaNodes.length; i++) {
	    		if (bcaNodes[i]) { array[j] = i; j++; }
	    	}
	    	int i = 0;
	    	while (i < edgeIdTypeMap.size()) {
	    		for (Map.Entry entry : edgeIdTypeMap.entrySet()) {
	    			array[j] = (int) entry.getKey();
	    			/*
	    			 * START TEMP
	    			 */
	    			//if (array[j]!=0) System.out.println("BookmarkColoring.generateIdArray() For edge: " + (int) entry.getKey()+ " array[j]="+array[j]);
	    			//if (array[j]!=0 && (int) entry.getKey() > this.outVertex.length + this.outEdge.length) System.out.println("edgeID larger than no. verts, namely: " + (int) entry.getKey());
	    			/*
	    			 * END TEMP
	    			 */
	    			i++; j++;
	    		}
	    	}
	    	logger.info("Generated id-array of both nodes and edges.");
	    	return array;
    	} catch (Exception ex) { ex.printStackTrace(); }
    	return new int[0]; 
    }
    
    public void determineAllInOut() {
    	this.allOut = new int[this.dictSize][];
    	this.allIn = new int[this.dictSize][];
    	
    	int[] neighborsOut, neighborsIn;
    	for (int i = 0; i < this.outVertex.length; i++) {

			neighborsOut = new int[0];
			neighborsIn = new int[0];
			// Give new values if there is a BCV for this ID.
    		if (this.allVecs.contains(i)) {
    			
    			if (this.allVecs.get(i) < this.outVertex.length) {
    				neighborsOut = this.outVertex[i];
    				neighborsIn = this.inVertex[i];
    				
    			} else if (this.allVecs.get(i) >= this.outVertex.length) {
    				neighborsOut = this.neighborEdgesOut.get(i);
    				neighborsIn = this.neighborEdgesIn.get(i);
    				
    			}
    		}
        	this.allOut[i] = neighborsOut;
        	this.allIn[i] = neighborsIn;
    	}
    	int edge;
    	int[] neighbors;
		for (Map.Entry entry : this.neighborEdgesIn.entrySet()) {
			edge = (int) entry.getKey();
			neighbors = (int[]) entry.getValue();
        	this.allIn[edge] = neighbors;
		}
		for (Map.Entry entry : this.neighborEdgesOut.entrySet()) {
			edge = (int) entry.getKey();
			neighbors = (int[]) entry.getValue();
        	this.allOut[edge] = neighbors;
    	}
    }
    
    public int[][] getAllOutVecs() {
    	return this.allOut;
    }
    
    public int[][] getAllInVecs() {
    	return this.allIn;
    }
    
    public ArrayList<Integer> getCoOccurrenceIdx_I() {
    	return this.coOccurrenceIdx_I;
    }
    public ArrayList<Integer> getCoOccurrenceIdx_J() {
    	return this.coOccurrenceIdx_J;
    }
    public ArrayList<Float> getCoOccurrenceValues() {
    	return this.coOccurrenceValues;
    }

    public ArrayList<Integer> getCoOccurrenceIdx_I_edges() {
    	return this.coOccurrenceIdx_I_edge;
    }
    public ArrayList<Integer> getCoOccurrenceIdx_J_edges() {
    	return this.coOccurrenceIdx_J_edge;
    }
    public ArrayList<Float> getCoOccurrenceValues_edges() {
    	return this.coOccurrenceValues_edge;
    }

	@Override
	public void shuffle() {
		permutation.shuffle();
	}
	
	public int cIdx_I(int i) {
		return contextIndex2Focus(coOccurrenceIdx_I.get(permutation.randomAccess(i)));
	}
	
	public int cIdx_J(int j) {
		return this.coOccurrenceIdx_J.get(permutation.randomAccess(j));
	}
	
	public float cIdx_C(int i) {
		return this.coOccurrenceValues.get(permutation.randomAccess(i));
	}
	
	public byte getType(int index) {
		return (byte) this.graph.getVertexTypeProperty().getValueAsInt(focusIndex2Context(index));
	}
	
	public int coOccurrenceCount() {
		return this.coOccurrenceCount;
	}
	
	public ArrayList<BCV> getBCVs() {
		return this.BCVs;
	}


	@Override
	public InMemoryRdfGraph getGraph() {
		return graph;
	}

	@Override
	public int contextIndex2Focus(int i) {
		return context2focus.get(i);
	}

	@Override
	public int focusIndex2Context(int i) {
		return focus2context.get(i);
	}

	@Override
	public int nrOfContextVectors() {
		return contextVectors;
	}

	@Override
	public int nrOfFocusVectors() {
		return focusVectors;
	}

	@Override
	public double max() {
		return this.max;
	}
	
	@Override
	public String getKey(int index) {
		return this.graph.getVertexLabelProperty().getValueAsString(focusIndex2Context(index));
	}
	
	private void setMax(double newMax) {
		this.max = Math.max(max, newMax);
	}
	
	private void setMax(int bookmark, double newMax) {
		double oldMax = this.max;
		this.max = Math.max(max, newMax);
		if (this.max != oldMax) this.maxHistory = "|" + oldMax + " --> " + this.max + " #Bookmark: " + bookmark + "|";
	}
	

	/**
	 * 
	 * @return
	 */
	public int[][] getInVertices() {
		return this.inVertex;
	}
	
	/**
	 * 
	 * @return
	 */
	public int[][] getOutVertices() {
		return this.outVertex;
	}

	/**
	 * 
	 * @return
	 */
	public int[][] getInEdges() {
		return this.inEdge;
	}

	/**
	 * 
	 * @return
	 */
	public int[][] getOutEdges() {
		return this.outEdge;
	}
}
