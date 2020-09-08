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
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.Permutation;

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
	private Map<String, Double> bcvMaxVals;
	private final ArrayList<BCV> BCVs;
	public int maxNeighbors;
	private int neighborCntr;
	
	private double max;
	private final int focusVectors, contextVectors;
	private int coOccurrenceCount;
	private Permutation permutation;
	private final InMemoryRdfGraph graph;
	

	private final int[][] inVertex;
	private final int[][] outVertex;
	private final int[][] inEdge;
	private final int[][] outEdge;
	private final Map<Integer, Integer> context2focus;
	private final Map<Integer, Integer> focus2context;
	
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
		
		BCVs = new ArrayList<BCV>();

		final Configuration.Output output = config.getOutput();

		this.context2focus = new HashMap<>();
		this.focus2context = new HashMap<>();

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
	public BookmarkColoring(final InMemoryRdfGraph graph, final Configuration config, final boolean nonDefault) {
		
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
		
		this.bcvMaxVals = new HashMap<String, Double>();
		nonEmptyBcvs = new ArrayList<BCV>();
		BCVs = new ArrayList<BCV>();
		
        logger.info("Reading configuration...");
		final Configuration.Output output = config.getOutput();

		this.inVertex = graph.getInNeighborhoods();
		this.outVertex = graph.getOutNeighborhoods();
		this.inEdge = new InEdgeNeighborhoodAlgorithm(config).compute(graph);
		this.outEdge = new OutEdgeNeighborhoodAlgorithm(config).compute(graph);
        logger.info("Edges read ==> in-edges are: " +this.inEdge.length+ " & out-edges are: " +this.outEdge.length);

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
		logger.info("Finished selecting vertices to compute BCVs.");
		
		TreeMap<Integer, Integer> edgeIdMap = generateEdgeIdMap(graph);
		// Initialization standard co-occurrence matrix
		this.focusVectors = notSkipped;
		final int nVectors = notSkipped + edgeIdMap.size();
		this.coOccurrenceIdx_I = new ArrayList<>(nVectors);
		this.coOccurrenceIdx_J = new ArrayList<>(nVectors);
		this.coOccurrenceValues = new ArrayList<>(nVectors);

		final int numThreads = config.getThreads();

		final ExecutorService es = Executors.newWorkStealingPool(numThreads);

		CompletionService<BCV> completionService = new ExecutorCompletionService<>(es);

		this.context2focus = new HashMap<>();
		this.focus2context = new HashMap<>();
		
		// Create subgraphs according to config-file.
		boolean loggerInfoProvided = false;
		logger.info("Start performing generating BCVs.");
		for(int i = 0, j = 0; i < this.contextVectors; i++) {
			
			// Skip unnecessary nodes.
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
							this.inVertex, this.outVertex, this.inEdge, this.outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a DIRECTED BCV.");
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
				case KALEUNDIRECTED:
					completionService.submit(new KaleUndirectedWeighted(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a KALEUNDIRECTED BCV.");
						loggerInfoProvided = true;
					}
					break;
				case KALESEPERATED:
					completionService.submit(new KaleUndirectedWeightedSeperated(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a KALESEPERATED BCV.");
						loggerInfoProvided = true;
					}
					break;
				case KALENODEBASED:
					completionService.submit(new KaleUndirectedWeightedNodeBased(
							graph, bookmark,
							alpha, epsilon,
							inVertex, outVertex, inEdge, outEdge));
					if(!loggerInfoProvided) { 
						logger.info("Generated a KALENODEBASED BCV.");
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
		progressThroughMatrix(config, notSkipped, es, completionService);
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
		
		try(ProgressBar pb = Configuration.progressBar("BCA", notSkipped, "nodes")) {
			logger.info("Progression bar initialized.");
			
			//now retrieve the futures after computation (auto wait for it)
			int received = 0;
			
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
					
					//System.out.println("\n"+bcv);
				
					// It is possible to use this maximum value in GloVe, although in the
					// literature they set this value to 100 and leave it at that
					setMax(bcv.getRootNode(), bcv.max());
					
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
					for (Entry<Integer, Float> bcr : bcv.entrySet()) {
						coOccurrenceIdx_I.add(bcv.getRootNode());
						coOccurrenceIdx_J.add(bcr.getKey());
						coOccurrenceValues.add(bcr.getValue());
						if (bcr.getValue() != 0f) neighborCntr++;
					}
					coOccurrenceCount += bcv.size();
					
					if (this.maxNeighbors < neighborCntr) { this.maxNeighbors = neighborCntr; System.out.println("this.maxNeighbors= " + this.maxNeighbors); }

					/*
					 * START TEMP
					 */
					//if (received % 1000 == 0) System.out.println("BookmarkColoring(Kale)--------------BCV: " +bcv);
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
		
		} finally {
			logger.info("ExecutorService shutting down.");
			es.shutdown();
		}
		
		logger.info("Max BCV history: " + this.maxHistory);
		logger.info("The co-occurrence count is: " +coOccurrenceCount);
		this.permutation = new Permutation(coOccurrenceCount);
		logger.info("Class permutation variable finalized.");
	}

	/**
	 * 
	 * @param graph
	 * @return
	 */
    public TreeMap<Integer, Integer> generateEdgeIdMap(final InMemoryRdfGraph graph) {
    	
    	logger.info("Start generating the edgeIdMap.");

        final int numVerts = graph.getVertices().toIntArray().length;
        
        final TreeMap<Integer, Integer> edgeNodeID = new TreeMap<>();
        
        int[] edges;
        int edge, edgeID;
        for (int v = 0; v < this.outEdge.length; v++) {
        	edges = this.outEdge[v];
        	for (int e = 0; e < edges.length; e++) {
        		edge = edges[e];
	        	edgeID = numVerts + edge;
	        	
	        	if (!edgeNodeID.containsKey(edge)) edgeNodeID.put(edge, edgeID);
        }}
        
        logger.info("Finished generating the edgeIdMap.");
        return edgeNodeID;
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
