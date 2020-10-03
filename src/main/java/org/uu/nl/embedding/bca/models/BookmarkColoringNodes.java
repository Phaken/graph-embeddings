package org.uu.nl.embedding.bca.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import grph.properties.NumericalProperty;
import me.tongfei.progressbar.ProgressBar;

public class BookmarkColoringNodes implements CoOccurrenceMatrix {

    private static Logger logger = Logger.getLogger("BookmarkColoringNodes");

	private final ArrayList<Integer> coOccurrenceIdx_I;
	private final ArrayList<Integer> coOccurrenceIdx_J;
	private final ArrayList<Float> coOccurrenceValues;
	private final ArrayList<BCV> nodeBCVs;
	private final Map<Integer, BCV> edgeTypeIdBCVs;
	private int[] orderedIDs;
	
	private double max;
	private final int focusVectors, contextVectors;
	private int coOccurrenceCount;
	private Permutation permutation;
	private final InMemoryRdfGraph graph;
	
	private final int[][] inVerts;
	private final int[][] outVerts;
	private final int[][] inEdges;
	private final int[][] outEdges;
	private final int iNumVerts;
	private final int iNumEdges;
	private final int iNumTotal;
	
	private final ArrayList<Integer> allVecs;
	private final Map<Integer, Integer> context2focus;
	private final Map<Integer, Integer> focus2context;
	
	
	public BookmarkColoringNodes(final InMemoryRdfGraph graph, final Configuration config) throws Exception {
	    logger.info("Starting Kale Bookmark Coloring.");
		
	    final double alpha = config.getBca().getAlpha();
		final double epsilon = config.getBca().getEpsilon();
		final int[] verts = graph.getVertices().toIntArray();
		final int[] edges = graph.getEdges().toIntArray();
		this.iNumVerts = verts.length;
		this.iNumEdges = edges.length;
		this.iNumTotal = this.iNumVerts + this.iNumEdges;
		final boolean[] performBCA = new boolean[verts.length];

		this.allVecs = new ArrayList<Integer>();
		this.nodeBCVs = new ArrayList<BCV>();
		this.edgeTypeIdBCVs = new TreeMap<Integer, BCV>();

		final Configuration.Output output = config.getOutput();

		this.context2focus = new HashMap<>();
		this.focus2context = new HashMap<>();
		this.orderedIDs = new int[this.iNumVerts];
		
	    logger.info("Reading configuration...");
	
		int notSkipped = 0;
		logger.info("Start selecting vertices to compute BCVs.");
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
		logger.info("Finished selecting vertices to compute BCVs: " + notSkipped + " vertices selected.");
		
		// Initialization co-occurrence matrix.
		this.graph = graph;
		this.contextVectors = verts.length;
		this.focusVectors = notSkipped;
		this.orderedIDs = new int[notSkipped];
		this.coOccurrenceIdx_I = new ArrayList<>(notSkipped);
		this.coOccurrenceIdx_J = new ArrayList<>(notSkipped);
		this.coOccurrenceValues = new ArrayList<>(notSkipped);

		// Initialize vertices and edges.
		this.inVerts = graph.getInNeighborhoods();
		this.outVerts = graph.getOutNeighborhoods();
		this.inEdges = new InEdgeNeighborhoodAlgorithm(config).compute(graph);
		this.outEdges = new OutEdgeNeighborhoodAlgorithm(config).compute(graph);

		// Initialize concurrency.
		final int numThreads = config.getThreads();
		final ExecutorService es = Executors.newWorkStealingPool(numThreads);
		CompletionService<BCV> completionService = new ExecutorCompletionService<>(es);
		
		// Create subgraphs according to config-file.
		boolean loggerInfoProvided = false;
		logger.info("Start performing generating BCVs.");

		try(ProgressBar pb2 = Configuration.progressBar("BCA-Nodes", notSkipped, "nodes")) {
			for(int i = 0, j = 0; i < notSkipped ; i++) {
				
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
								inVerts, outVerts, inEdges, outEdges));
						if(!loggerInfoProvided) { 
							logger.info("Generated DIRECTED BCV.");
							loggerInfoProvided = true;
						}
						break;
					case UNDIRECTED:
						completionService.submit(new UndirectedWeighted(
								graph, bookmark,
								alpha, epsilon,
								inVerts, outVerts, inEdges, outEdges));
						if(!loggerInfoProvided) { 
							logger.info("Generated UNDIRECTED BCV.");
							loggerInfoProvided = true;
						}
						break;
					case HYBRID:
						completionService.submit(new HybridWeighted(
								graph, bookmark,
								alpha, epsilon,
								inVerts, outVerts, inEdges, outEdges));
						if(!loggerInfoProvided) { 
							logger.info("Generated HYRBID BCV.");
							loggerInfoProvided = true;
						}
						break;
					case KALEUNDIRECTED:
						completionService.submit(new UndirectedWeighted(
								graph, bookmark,
								alpha, epsilon,
								inVerts, outVerts, inEdges, outEdges));
						break;
					case KALESEPERATED:
						completionService.submit(new UndirectedWeighted(
								graph, bookmark,
								alpha, epsilon,
								inVerts, outVerts, inEdges, outEdges));
						break;
					case KALENODEBASED:
						completionService.submit(new UndirectedWeighted(
								graph, bookmark,
								alpha, epsilon,
								inVerts, outVerts, inEdges, outEdges));
						break;
					case CONTEXTWINNOWED:
						/*
						 * Days difference fiksen vanuit config
						 */
						int daysDiff = 0;
						completionService.submit(new ContextWinnowedUndirectedWeighted(
								graph, bookmark, daysDiff,
								alpha, epsilon,
								inVerts, outVerts, inEdges, outEdges));
						if(!loggerInfoProvided) { 
							logger.info("Generated a CONTEXTWINNOWED BCV.");
							loggerInfoProvided = true;
						}
						break;
				}
			}
			pb2.step();
		} catch (Exception e) {
			logger.info("Failed to take BCV and add it to the matrix.");
			e.printStackTrace();
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

		int received = 0;
		try(ProgressBar pb = Configuration.progressBar("BCA", notSkipped, "nodes")) {
			logger.info("Progression bar initialized.");
			
			// Now retrieve the futures after computation (auto wait for it)
			while(received < notSkipped) {
				try {

					final BCV bcv = completionService.take().get();
					this.nodeBCVs.add(bcv);
					if (received == 0) logger.info("First CompletionService finished: BCV received.");
					
					if (received == notSkipped-1) logger.info("Last BCV was added to the matrix.");
				
				} catch (InterruptedException | ExecutionException e) {
					logger.info("Failed to take BCV and add it to the matrix.");
					e.printStackTrace();
				} finally {
					received++;
					pb.step();
				}
			}
			
			this.edgeTypeIdBCVs.put((0 + this.iNumTotal), new BCV(0 + this.iNumTotal));

			received = 0;
			for(final BCV bcv : this.nodeBCVs) {
				int root = bcv.getRootNode();
				
				int[] in = this.inEdges[root];
				int[] out = this.outEdges[root];
				
				for(int i = 0; i < in.length + out.length; i++) {
				
					int edge = (i < in.length) ? in[i] : out[i - in.length];
					int type = graph.getEdgeTypeProperty().getValueAsInt(edge);
					int typeID = type + this.iNumTotal;
					
					//if(type == 0) continue;

					this.edgeTypeIdBCVs.compute(typeID, (k, v) -> (v == null) ? new BCV(typeID).merge(bcv/*, true*/) : v.merge(bcv/*, true*/));
				}
				
				bcv.remove(root);
				bcv.smooth();
				// It is possible to use this maximum value in GloVe, although in the
				// literature they set this value to 100 and leave it at that
				setMax(bcv.max());
				// Add root node to all vectors ArrayList.
				this.allVecs.add(bcv.getRootNode());
				
				// Create co-occurrence matrix for standard bcv
				for (Entry<Integer, Float> bcr : bcv.entrySet()) {
					this.coOccurrenceIdx_I.add(bcv.getRootNode());
					this.coOccurrenceIdx_J.add(bcr.getKey());
					this.coOccurrenceValues.add(bcr.getValue());
					/*if (received % 5000 == 0) 
						System.out.println("Any Xij value negative???");
					if (bcr.getValue() < 0)
						System.out.println("WARNING: BookmarkColoringNodes.progressThroughMatrix() - Xij value is negative: "+bcr.getValue());*/
				}
				this.coOccurrenceCount += bcv.size();
				this.orderedIDs[received++] = bcv.getRootNode();
			}
			for (BCV edgeBCV : this.edgeTypeIdBCVs.values()) {
				for (Entry<Integer, Float> entry : edgeBCV.entrySet()) {
					if (entry.getValue() < 0) System.out.println("WARNING: BookmarkColoringNodes.progressThroughMatrix() Before unity() - Xij value is negative: "+ entry.getValue());
				}
			}
			for (BCV edgeBCV : this.edgeTypeIdBCVs.values()) edgeBCV.smooth();/*edgeBCV.toUnity(true);*/
			int cntr = 0;
			for (BCV edgeBCV : this.edgeTypeIdBCVs.values()) {
				for (Entry<Integer, Float> entry : edgeBCV.entrySet()) {
					cntr++;
					if (entry.getValue() < 0 && cntr % 5000 == 0)
						System.out.println("WARNING: BookmarkColoringNodes.progressThroughMatrix() - Xij value is negative: "+ entry.getValue());
				}
			}
			
		} finally {
			logger.info("'Received' final value = " + received + ". ExecutorService shutting down.");
			es.shutdown();
		}
		
		logger.info("The co-occurrence count is: " +this.coOccurrenceCount);
		this.permutation = new Permutation(this.coOccurrenceCount);
		logger.info("Class permutations variable finalized.");
	}
    
    public Map<Integer, Integer> getContext2Focus() {
    	return this.context2focus;
    }
    
    public Map<Integer, Integer> getFocus2Context() {
    	return this.focus2context;
    }
    
    public int[] getOrderedIDs() {
    	return this.orderedIDs;
    }
    
    public Map<Integer, BCV> getEdgeTypeBCVs() {
    	return this.edgeTypeIdBCVs;
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
		return this.nodeBCVs;
	}
	
	public ArrayList<Integer> getCoOccurrenceI() {
		return this.coOccurrenceIdx_I;
	}
	
	public ArrayList<Integer> getCoOccurrenceJ() {
		return this.coOccurrenceIdx_J;
	}
	
	public ArrayList<Float> getCoOccurrenceValues() {
		return this.coOccurrenceValues;
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
	

	/**
	 * 
	 * @return
	 */
	public int[][] getInVertices() {
		return this.inVerts;
	}
	
	/**
	 * 
	 * @return
	 */
	public int[][] getOutVertices() {
		return this.outVerts;
	}

	/**
	 * 
	 * @return
	 */
	public int[][] getInEdges() {
		return this.inEdges;
	}

	/**
	 * 
	 * @return
	 */
	public int[][] getOutEdges() {
		return this.outEdges;
	}
}
