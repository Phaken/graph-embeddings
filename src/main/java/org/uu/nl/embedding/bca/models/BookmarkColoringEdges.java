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
import org.uu.nl.embedding.kale.util.DataGenerator;
import org.uu.nl.embedding.util.ArrayUtils;
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.Permutation;

import grph.properties.NumericalProperty;
import me.tongfei.progressbar.ProgressBar;

public class BookmarkColoringEdges implements CoOccurrenceMatrix {
	
    private static Logger logger = Logger.getLogger("BookmarkColoringEdges");

	private final InMemoryRdfGraph graph;
    
	private final ArrayList<Integer> coOccurrenceIdx_I_edgeTypes;
	private final ArrayList<Integer> coOccurrenceIdx_I_edges;
	private final ArrayList<Integer> coOccurrenceIdx_J_edges;
	private final ArrayList<Float> coOccurrenceValues_edges;
	
	private final ArrayList<Integer> coOccurrenceIdx_I_nodes;
	private final ArrayList<Integer> coOccurrenceIdx_J_nodes;
	private final ArrayList<Float> coOccurrenceValues_nodes;
	private final int coOccurrenceCount_nodes;
	
	private final Map<Integer, BCV> typeBcaMap;
	//private final Map<Integer, BCV> typeIdBcaMap;
	private final Map<Integer, ArrayList<Integer>> focusContextNodes;
	private int coOccurrenceCount_edges;
	
	private final ArrayList<BCV> typeIdBCVs;
	
	private double max;
	private Permutation permutation;
	
	private  int[][] inVerts;
	private  int[][] outVerts;
	private  int[][] inEdges;
	private  int[][] outEdges;
	private  int iNumVerts;
	private  int iNumEdges;
	private int iNumEdgeTypes;
	private  int iNumTotal;
	
	private final Map<Integer, Integer> context2focus;
	private final Map<Integer, Integer> focus2context;
	private final int contextVectors;
	private final int focusVectors;
	private final int[] orderedTypes;
	private final ArrayList<Integer> lstOrderedTypes;
	private final HashMap<String, Integer> relationTypeMap;
	
	
	/**
	 * 
	 * @param graph
	 * @param config
	 * @param typeBcaMap
	 * @throws Exception
	 */
	public BookmarkColoringEdges(final InMemoryRdfGraph graph,
									final Configuration config,
									final BookmarkColoringNodes bcaNodes,
									final HashMap<String, Integer> relationTypeMap) throws Exception {
	    logger.info("Starting Kale Bookmark Coloring for edges.");

		final int[] verts = graph.getVertices().toIntArray();
		final int[] edges = graph.getEdges().toIntArray();
		this.iNumVerts = verts.length;
		this.iNumEdges = edges.length;
		this.iNumTotal = this.iNumVerts + this.iNumEdges;
	    this.graph = graph;
		//this.typeIdBcaMap = bcaNodes.getEdgeBCVs();
		this.typeBcaMap = bcaNodes.getEdgeTypeBCVs();
		this.typeIdBCVs = new ArrayList<BCV>(typeBcaMap.size());
		this.iNumEdgeTypes = this.typeBcaMap.size();
		this.relationTypeMap = relationTypeMap;

		this.lstOrderedTypes = new ArrayList<Integer>();

		this.focus2context = computeFocus2Context();
		this.context2focus = new HashMap<Integer, Integer>();
		this.focusContextNodes = new HashMap<Integer, ArrayList<Integer>>();
		
		// Get node co-occurrence matrix.
		this.coOccurrenceIdx_I_nodes = bcaNodes.getCoOccurrenceI();
		this.coOccurrenceIdx_J_nodes = bcaNodes.getCoOccurrenceJ();
		this.coOccurrenceValues_nodes = bcaNodes.getCoOccurrenceValues();
		this.coOccurrenceCount_nodes = bcaNodes.coOccurrenceCount();
		
		// Initialize co-occurrence matrix.
		this.coOccurrenceIdx_I_edgeTypes = new ArrayList<Integer>();
		this.coOccurrenceIdx_I_edges = new ArrayList<Integer>();
		this.coOccurrenceIdx_J_edges = new ArrayList<Integer>();
		this.coOccurrenceValues_edges = new ArrayList<Float>();
		this.coOccurrenceCount_edges = 0;
		
		Initialize();
		
		//this.contextVectors = this.typeBCVs.size();
		this.contextVectors = bcaNodes.nrOfContextVectors();
		this.focusVectors = this.typeIdBCVs.size();
		this.orderedTypes = ArrayUtils.toArray(this.lstOrderedTypes, 0);
		
	}
	
	private void Initialize() {
		
		/*for (int i = 0; i < this.typeBcaMap.size(); i++) {
			this.typeBCVs.add(new BCV(-1));
		}*/
		
		int cntr = 0;
		int type, typeID;
		BCV bcv;
		for (Map.Entry<Integer, BCV> entry : this.typeBcaMap.entrySet()) {
			typeID = entry.getKey();
			bcv = entry.getValue();
			
			type = typeID - this.iNumTotal;
			
			bcv.remove(typeID);
			bcv.smooth();
			//if (type == 0) { System.out.println("BookmarkColoringEdges.Initialize() - Found type == 0, and skipped it."); continue; }
			//this.typeBCVs.set(type, bcv);
			this.typeIdBCVs.add(bcv);
			
			// It is possible to use this maximum value in GloVe, although in the
			// literature they set this value to 100 and leave it at that
			setMax(bcv.max());
			ArrayList<Integer> contextNodes = new ArrayList<Integer>();
			
			// Create co-occurrence matrix for standard bcv
			for (Entry<Integer, Float> bcr : bcv.entrySet()) {
				this.coOccurrenceIdx_I_edgeTypes.add(type);
				this.coOccurrenceIdx_I_edges.add(bcv.getRootNode());
				this.coOccurrenceIdx_J_edges.add(bcr.getKey());
				this.coOccurrenceValues_edges.add(bcr.getValue());
				
				/*if (cntr % 5000 == 0) 
					System.out.println("Any Xij value negative???");
				if (bcr.getValue() < 0)
					System.out.println("WARNING: BookmarkColoringEdges.Initialize() - Xij value is negative: "+bcr.getValue());*/
			
				
				contextNodes.add(bcr.getKey());
			}
			this.coOccurrenceCount_edges += bcv.size();
			//this.lstOrderedIDs.add(bcv.getRootNode());
			this.lstOrderedTypes.add(type);
			this.focusContextNodes.put(type, contextNodes);
			cntr++;
			//System.out.println("BookmarkColoringEdges.Initialize() - focusContextNodes.get("+type+").size() = " +focusContextNodes.get(type).size());
		}
		for (Entry<String, Integer> entry : this.relationTypeMap.entrySet()) {
			if (!this.lstOrderedTypes.contains(entry.getValue())) {
				this.lstOrderedTypes.add(entry.getValue());
				this.typeBcaMap.put((entry.getValue() + this.iNumTotal), new BCV((entry.getValue() + this.iNumTotal)));
			}
		}
		//System.out.println("BookmarkColoringEdges.Initialize() - focusContextNodes.size() (SO ALL TYPES?)= " +focusContextNodes.size());

		logger.info("The co-occurrence count is: " +this.coOccurrenceCount_edges);
		this.permutation = new Permutation(this.coOccurrenceCount_edges);
		logger.info("Class permutations variable finalized.");
	}
	
	private Map<Integer, Integer> computeFocus2Context() {
		Map<Integer, Integer> map = new HashMap<>();
		
		for (int type : this.typeBcaMap.keySet()) {
			//if (type == 0) continue;
			map.put(type, type);
		}
		return map;
	}

	/*private Map<Integer, BCV> idToTypeBcvMap() {
		Map<Integer, BCV> map = new HashMap<>();
		
		for (Entry<Integer, BCV> entry : this.typeIdBcaMap.entrySet()) {
			int type = entry.getKey() - this.iNumTotal;
			map.put(type, entry.getValue());
		}
		return map;
	}*/
	
	public Map<Integer, ArrayList<Integer>> getContextNodes() {
		return this.focusContextNodes;
	}
	
	public ArrayList<Integer> getContextNodesOf(final int focusNode) {
		return this.focusContextNodes.get(focusNode);
	}
    
    public Map<Integer, Integer> getContext2Focus() {
    	return this.context2focus;
    }
    
    public Map<Integer, Integer> getFocus2Context() {
    	return this.focus2context;
    }
    
    public int getNumEdges() {
    	return this.iNumEdges;
    }
    
    public int getNumVerts() {
    	return this.iNumVerts;
    }
    
    public int getNumEdgeTypes() {
    	return this.iNumEdgeTypes;
    }
    
    public int[] getOrderedIDs() {
    	return this.orderedTypes;
    }

	@Override
	public void shuffle() {
		permutation.shuffle();
	}
	
	public int cIdx_I(int i) {
		//return contextIndex2Focus(coOccurrenceIdx_I_edges.get(permutation.randomAccess(i)));
		//return contextIndex2Focus(coOccurrenceIdx_I_edgeTypes.get(permutation.randomAccess(i)));
		return this.coOccurrenceIdx_I_edgeTypes.get(permutation.randomAccess(i));
	}
	
	public int cIdx_J(int j) {
		return this.coOccurrenceIdx_J_edges.get(permutation.randomAccess(j));
	}
	
	public float cIdx_C(int i) {
		return this.coOccurrenceValues_edges.get(permutation.randomAccess(i));
	}
	
	public int cIdx_I_node(int i) {
		return this.coOccurrenceIdx_I_nodes.get(i);
	}
	
	public int cIdx_J_node(int j) {
		return this.coOccurrenceIdx_J_nodes.get(j);
	}
	
	public float cIdx_C_node(int i) {
		return this.coOccurrenceValues_nodes.get(i);
	}
	
	public int coOccurrenceCount() {
		return this.coOccurrenceCount_edges;
	}
	
	public int coOccurrenceCountNodes() {
		return this.coOccurrenceCount_nodes;
	}
	
	public byte getType(int index) {
		return (byte) this.graph.getVertexTypeProperty().getValueAsInt(focusIndex2Context(index));
	}

	public ArrayList<BCV> getBCVs() {
		return this.typeIdBCVs;
	}
	
	public ArrayList<BCV> getTypeBCVs() {
		return this.typeIdBCVs;
	}

	@Override
	public InMemoryRdfGraph getGraph() {
		return graph;
	}
	
	@Override
	public int contextIndex2Focus(int i) {
		System.out.println("WARNING: Wrong method used in BookmarkColoringEdges.");
		return this.coOccurrenceIdx_I_nodes.get(i);
	}

	@Override
	public int focusIndex2Context(int i) {
		System.out.println("WARNING: Wrong method used in BookmarkColoringEdges.");
		return focus2context.get(i);
	}

	@Override
	public int nrOfContextVectors() {
		return contextVectors;
	}

	@Override
	public int nrOfFocusVectors() {
		if (focusVectors <= 0) logger.info("WARNING: BookmarkColoringEdges.nrOfFocusVectors() returns invalid value: "+focusVectors);
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

