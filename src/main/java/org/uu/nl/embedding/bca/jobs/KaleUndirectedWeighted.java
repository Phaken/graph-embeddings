package org.uu.nl.embedding.bca.jobs;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.bca.util.BCAJobStable;
import org.uu.nl.embedding.bca.util.BCV;
import org.uu.nl.embedding.bca.util.PaintedNode;
import org.uu.nl.embedding.convert.util.NodeInfo;
import org.uu.nl.embedding.kale.util.DataGenerator;
import org.uu.nl.embedding.util.ArrayUtils;
import org.uu.nl.embedding.util.InMemoryRdfGraph;
import org.uu.nl.embedding.util.config.Configuration;

import grph.properties.NumericalProperty;

public class KaleUndirectedWeighted extends BCAJobStable {
	/*
	 * 15x15 version.
	 */

    private static Logger logger = Logger.getLogger("KaleUndirectedWeighted (sqrd)");
	
    int numVerts;
    final int iNumTotal;
    final byte type;
    final int bookmark;
    TreeMap<Integer, Integer> edgeIdTypeMap = new TreeMap<>();
    /*
    TreeMap<Integer, Integer> edgeNodeID = new TreeMap<>();
    
    TreeMap<Integer, Integer> edgeCntTree = new TreeMap<>();
    TreeMap<Integer, Double> edgeTotalWeights = new TreeMap<>();
    TreeMap<Integer, ArrayList<Integer>> outEdgeOf = new TreeMap<>();
    */
	TreeMap<Integer, int[]> neighborVertsIn;
	TreeMap<Integer, int[]> neighborVertsOut;
	TreeMap<Integer, int[]> neighborEdgesIn;
	TreeMap<Integer, int[]> neighborEdgesOut;

	/**
	 * Constructor method for nodes.
	 * @param graph
	 * @param config
	 * @param bookmark
	 * @param alpha
	 * @param epsilon
	 * @param vertexIn
	 * @param vertexOut
	 * @param edgeIn
	 * @param edgeOut
	 * @param dictSize
	 */
	public KaleUndirectedWeighted(InMemoryRdfGraph graph, Configuration config, int bookmark,
						double alpha, double epsilon, int dictSize,
						int[][] vertexIn, int[][] vertexOut,
						int[][] edgeIn, int[][] edgeOut,
						TreeMap<Integer, Integer> edgeIdTypeMap) {
		super(bookmark, true, alpha, epsilon, graph, vertexOut, vertexIn, edgeOut, edgeIn);
		this.bookmark = bookmark;
		//logger.info("DOWORK() WERKT NOG NIET JUIST!!!!!!!!");
		/*
		 * doWork() gaat ervan uit dat alles een normale node is en pakt daarvan dus de neighbors en edges
		 * ==> Dit werkt dus uiteraard niet, maar hoe oplossen????*/
		//System.out.println("edgeIdTypeMap.size() = "+ edgeIdTypeMap.size());
		this.edgeIdTypeMap = edgeIdTypeMap;
		this.type = (byte)0;
		this.iNumTotal = dictSize;
		//fillMaps();
	}
	
	/**
	 * Constructor method for edges.
	 * @param graph
	 * @param config
	 * @param bookmark
	 * @param alpha
	 * @param epsilon
	 * @param iNumTotal
	 * @param neighborVertsIn
	 * @param neighborVertsOut
	 * @param neighborEdgesIn
	 * @param neighborEdgesOut
	 */
	public KaleUndirectedWeighted(InMemoryRdfGraph graph, Configuration config, int bookmark,
						double alpha, double epsilon, int iNumTotal,
						int[][] vertexIn, int[][] vertexOut,
						int[][] edgeIn, int[][] edgeOut,
						TreeMap<Integer, int[]> neighborVertsIn,
						TreeMap<Integer, int[]> neighborVertsOut,
						TreeMap<Integer, int[]> neighborEdgesIn,
						TreeMap<Integer, int[]> neighborEdgesOut,
						TreeMap<Integer, Integer> edgeIdTypeMap) {
		super(bookmark, true, alpha, epsilon, graph, vertexOut, vertexIn, edgeOut, edgeIn);

		this.bookmark = bookmark;
		
		
		this.neighborVertsIn = neighborVertsIn;
		this.neighborVertsOut = neighborVertsOut;
		this.neighborEdgesIn = neighborEdgesIn;
		this.neighborEdgesOut = neighborEdgesOut;
		this.edgeIdTypeMap = edgeIdTypeMap;

		this.type = (byte)1;
		this.iNumTotal = iNumTotal;
		//fillMaps();
}

	@Override
	protected int[] getIndexes(boolean reverse, int focusNode, int[][] indexIn, int[][] indexOut) {

		int[] index = new int[indexIn[focusNode].length + indexOut[focusNode].length];
		System.arraycopy(indexIn[focusNode], 0, index, 0, indexIn[focusNode].length);
		System.arraycopy(indexOut[focusNode], 0, index, indexIn[focusNode].length, indexOut[focusNode].length);

		return index;
	}

	protected int[] getIndexes(boolean reverse, int focusNode, TreeMap<Integer, int[]> indexIn, TreeMap<Integer, int[]> indexOut) {

		int[] index;
		int j = 0;
		if (indexIn.containsKey(focusNode) && indexOut.containsKey(focusNode)) {
			index = new int[indexIn.get(focusNode).length + indexOut.get(focusNode).length];
			for (int i = 0; i < indexIn.get(focusNode).length; i++)
				index[j++] = indexIn.get(focusNode)[i];
			for (int i = 0; i < indexOut.get(focusNode).length; i++)
				index[j++] = indexOut.get(focusNode)[i];
			
		} else if (indexIn.containsKey(focusNode)) {
			index = new int[indexIn.get(focusNode).length];
			for (int i = 0; i < indexIn.get(focusNode).length; i++)
				index[j++] = indexIn.get(focusNode)[i];
			
		} else if (indexOut.containsKey(focusNode)) {
			index = new int[indexOut.get(focusNode).length];
			for (int i = 0; i < indexOut.get(focusNode).length; i++)
				index[j++] = indexOut.get(focusNode)[i];
		} else {
			index = new int[0];
		}

		return index;
	}
	

    /**
     * Expands the doWork for vertices by adding the
     * edges to the bcv's.
     * @param graph
     * @param reverse
     * @return
     * @author Euan Westenbroek
     */
	@Override
    public BCV doWork(final boolean reverse) throws Exception {
    	try {
	        if (this.type == (byte) 0) doWorkNodes(reverse);
	        else if (this.type == (byte) 1) doWorkEdges(reverse);

    	} catch (Exception ex) {ex.printStackTrace(); }
        return new BCV(-1);
    }
	
	protected BCV doWorkNodes(final boolean reverse) throws Exception {
		//System.out.println("KaleUndirectedWeighted.doWorkNodes() - Started for node: " + this.bookmark);

		final TreeMap<Integer, PaintedNode> nodeTree = new TreeMap<>();
		final TreeMap<Integer, Double> paintedEdges = new TreeMap<>();
		final BCV bcv = new BCV(this.bookmark);

		nodeTree.put(this.bookmark, new PaintedNode(this.bookmark, 1));

		int[] neighbors, edges;
		int focusNode;
		double wetPaint, partialWetPaint, totalWeight, edgePaint;
		PaintedNode node;

		int tempCntr = 0;
		while (!nodeTree.isEmpty()) {

			node = nodeTree.pollFirstEntry().getValue();
			focusNode = node.nodeID;
			wetPaint = node.getPaint();

			// Keep part of the available paint on this node, distribute the rest
			bcv.add(focusNode, (this.alpha * wetPaint));

			neighbors = getNeighbors(reverse, focusNode);
			edges = getEdges(reverse, focusNode);

			totalWeight = getTotalWeight(neighbors, edges);

			for (int i = 0; i < neighbors.length; i++) {

				float weight = this.graph.getEdgeWeightProperty().getValueAsFloat(edges[i]);
				partialWetPaint = (1 - this.alpha) * wetPaint * (weight / totalWeight);

				// Stopping early here increases stability in GloVe
				if (partialWetPaint < this.epsilon) continue;

				// Log(n) time lookup
				// Start with nodes.
				if (nodeTree.containsKey(neighbors[i])) {
					nodeTree.get(neighbors[i]).addPaint(partialWetPaint);
				} else {
					nodeTree.put(neighbors[i], new PaintedNode(neighbors[i], partialWetPaint));
					tempCntr++;
				}
				// Do same for edges.
				/*if (paintedEdges.containsKey(edges[i])) {
					edgePaint = paintedEdges.get(edges[i]);
					paintedEdges.put(edges[i], (edgePaint + partialWetPaint));
				} else {
					paintedEdges.put(edges[i], (partialWetPaint));
				}*/
				int edgeType = graph.getEdgeTypeProperty().getValueAsInt(edges[i]);
				bcv.add(edgeType, partialWetPaint);
				

			}
		}
		//if (tempCntr > 20)
		//	System.out.println("KaleUndirectedWeighted.doWorkNodes() - bookmark = "+ this.bookmark+ ", nodeTree checked: " + tempCntr + " nodes.");
		
		
		// Summation over edge types.
		/*
		final TreeMap<Integer, Double> paintedTypeEdges = new TreeMap<>();
		int edgeType;
		double paint;
		for (Map.Entry<Integer, Double> entry : paintedEdges.entrySet()) {
			//edgeType = edgeIdTypeMap.get(entry.getKey());
			edgeType = graph.getEdgeTypeProperty().getValueAsInt(entry.getKey());
			paint = entry.getValue();
			
			if (paintedTypeEdges.containsKey(edgeType)) {
				edgePaint = paintedTypeEdges.get(edgeType);
				paintedTypeEdges.put(edgeType, (edgePaint + paint));
			} else {
				paintedTypeEdges.put(edgeType, (paint));
			}
		}
		
		// Add edge types to bcv.
		for (Map.Entry<Integer, Double> entry : paintedTypeEdges.entrySet()) {
			//System.out.println("KaleUndirectedWeighted.doWorkNodes() - edgeType bcv added.");
			bcv.add(entry.getKey(), entry.getValue());
		}*/
		
		return bcv;
	}
	
	protected BCV doWorkEdges(final boolean reverse) throws Exception {
		//if (this.bookmark < 0) System.out.println("KaleUndirectedWeighted.doWorkEdges() - negative edge bookmark received: " + this.bookmark);
		//System.out.println("KaleUndirectedWeighted.doWorkEdges() - Started for edge: " + this.bookmark);

		final TreeMap<Integer, Double> nodeTree = new TreeMap<>();
		final TreeMap<Integer, Double> paintedEdges = new TreeMap<>();
		final BCV bcv = new BCV(this.bookmark);
		//if (bcv.getRootNode() < 0) System.out.println("KaleUndirectedWeighted.doWorkEdges() - negative edge bcv.getRootNode() received: " + bcv.getRootNode());
		

		// bookmark should be edgeID (not edgeType) as the bcv's of all edges
		// of same type will be summed.
		nodeTree.put(this.bookmark, (double)1);

		int[] neighbors, edges;
		int focusNode;
		double wetPaint, treePaint, partialWetPaint, totalWeight, edgePaint;
		Map.Entry<Integer, Double> node;

		int tempCntr = 0;
		while (!nodeTree.isEmpty()) {

			node = nodeTree.pollFirstEntry();
			focusNode = node.getKey();
			//if (focusNode < 0) 
			//	System.out.println("KaleUndirectedWeighted.doWorkEdges() - negative focusNode received: " + focusNode);
			
			wetPaint = node.getValue();

			// Keep part of the available paint on this node, distribute the rest
			bcv.add(focusNode, (this.alpha * wetPaint));
			
			if (focusNode != this.bookmark) {
				neighbors = getNeighbors(reverse, focusNode);
				edges = getEdges(reverse, focusNode);
			} else {
				neighbors = getEdgeNeighbors(reverse, focusNode);
				edges = getEdgeEdges(reverse, focusNode);
			}

			totalWeight = getTotalWeight(neighbors, edges);

			for (int i = 0; i < neighbors.length; i++) {

				float weight = this.graph.getEdgeWeightProperty().getValueAsFloat(edges[i]);
				partialWetPaint = (1 - this.alpha) * wetPaint * (weight / totalWeight);

				// Stopping early here increases stability in GloVe
				if(partialWetPaint < this.epsilon) continue;

				// Log(n) time lookup
				// Start with nodes.
				if (nodeTree.containsKey(neighbors[i])) {
					treePaint = nodeTree.get(neighbors[i]);
					nodeTree.put(neighbors[i], (treePaint + partialWetPaint));
				} else {
					nodeTree.put(neighbors[i], partialWetPaint);
					tempCntr++;
				}
				// Do same for edges.
				/*if (paintedEdges.containsKey(edges[i])) {
					edgePaint = paintedEdges.get(edges[i]);
					paintedEdges.put(edges[i], (edgePaint + partialWetPaint));
				} else {
					paintedEdges.put(edges[i], (partialWetPaint));
				}*/
				int edgeType = graph.getEdgeTypeProperty().getValueAsInt(edges[i]);
				bcv.add(edgeType, partialWetPaint);

			}
		}
		//if (tempCntr > 50)
		 	//System.out.println("KaleUndirectedWeighted.doWorkEdges() - bookmark = "+ this.bookmark+ ", nodeTree checked: " + tempCntr + " nodes.");
		
		/*
		// Summation over edge types.
		final TreeMap<Integer, Double> paintedTypeEdges = new TreeMap<>();
		int edgeType;
		double paint;
		for (Map.Entry<Integer, Double> entry : paintedEdges.entrySet()) {
			//edgeType = edgeIdTypeMap.get(entry.getKey());
			edgeType = graph.getEdgeTypeProperty().getValueAsInt(entry.getKey());
			paint = entry.getValue();
			
			if (paintedTypeEdges.containsKey(edgeType)) {
				edgePaint = paintedTypeEdges.get(edgeType);
				paintedTypeEdges.put(edgeType, (edgePaint + paint));
			} else {
				paintedTypeEdges.put(edgeType, (paint));
			}
		}
		
		// Add edge types to bcv.
		for (Map.Entry<Integer, Double> entry : paintedTypeEdges.entrySet()) {
			//System.out.println("KaleUndirectedWeighted.doWorkEdges() - edgeType bcv added.");
			bcv.add(entry.getKey(), entry.getValue());
		}*/
		if (bcv.getRootNode() < 0)
			System.out.println("KaleUndirectedWeighted.doWorkEdges() - bcv.getRootNode() < 0: " + bcv.getRootNode());
		return bcv;
	}


	protected int[] getEdgeNeighbors(final boolean reverse, final int focusNode) {
		return getIndexes(reverse, focusNode, this.neighborVertsIn, this.neighborVertsOut);
	}

	protected int[] getEdgeEdges(final boolean reverse, final int focusNode) {
		return getIndexes(reverse, focusNode, this.neighborEdgesIn, this.neighborEdgesOut);
	}
	
	/*
    private void fillMaps() {
    	int[] verts = graph.getVertices().toIntArray();
    	int numVerts = verts.length;
    	
	    double weight, sumOfWeights = 0d;
	    int[] edges;
	    int edge, edgeID;
	    ArrayList<Integer> vertList;

        final NumericalProperty edgeWeights = this.graph.getEdgeWeightProperty();
	    
	    // Fill all maps for the edges.
	    for (int vert = 0; vert < numVerts; vert++) {
	    	edges = this.edgeOut[vert];
	    	for (int neighbor = 0; neighbor < edges.length; neighbor++) {
	        	edge = edges[neighbor];
	        	edgeID = numVerts + edge;
	        	
	        	if (!this.edgeNodeID.containsKey(edge)) this.edgeNodeID.put(edge, edgeID);
	        	
	        	if (!this.edgeCntTree.containsKey(edgeID)) this.edgeCntTree.put(edgeID, 1);
	        	else { this.edgeCntTree.put(edgeID, this.edgeCntTree.get(edgeID)+1); }
	        	
	        	weight = (double)edgeWeights.getValueAsFloat(this.edgeOut[vert][neighbor]);
	        	sumOfWeights += weight;
	        	if (!this.edgeTotalWeights.containsKey(edgeID)) this.edgeTotalWeights.put(edgeID, weight);
	        	else { this.edgeTotalWeights.put(edgeID, this.edgeTotalWeights.get(edgeID) + weight); }
	        	
	        	if (!this.outEdgeOf.containsKey(vert)) { vertList = new ArrayList<Integer>(); }
	        	else { vertList = this.outEdgeOf.get(edgeID); }
	    		vertList.add(vert);
	    		this.outEdgeOf.put(edgeID, vertList);
	    	}
	    }
    }*/
    
		
}
