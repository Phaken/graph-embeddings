package org.uu.nl.embedding.bca;

import grph.Grph;
import org.uu.nl.embedding.bca.util.BCAJob;
import org.uu.nl.embedding.bca.util.BCV;
import org.uu.nl.embedding.bca.util.PaintRegistry;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Jurian Baas
 */
public class VanillaBCAJob extends BCAJob {

	private final int[][] vertexOut, vertexIn, edgeOut, edgeIn;
	
	public VanillaBCAJob(Grph graph,
                         int bookmark, boolean reverse, double alpha, double epsilon,
                         int[][] vertexIn, int[][] vertexOut, int[][] edgeIn, int[][] edgeOut) {
		super(bookmark, reverse, alpha, epsilon, graph);
		this.vertexOut = vertexOut;
		this.vertexIn = vertexIn;
		this.edgeOut = edgeOut;
		this.edgeIn = edgeIn;
	}

	@Override
	protected BCV doWork(Grph graph, boolean reverse) {
		
		final Queue<Integer> nodeQueue = new LinkedList<>();
		final PaintRegistry<Integer> wetPaintRegister = new PaintRegistry<>();
		final BCV bcv = new BCV(bookmark);
		
		nodeQueue.add(bookmark);
		wetPaintRegister.put(bookmark, 1d);
		
		int[] neighbors, edgeCache;
		int focusNode, edge;
		double partialWetPaint;

		
		while (!nodeQueue.isEmpty()) {

			focusNode = nodeQueue.poll();
			final double wetPaint = wetPaintRegister.get(focusNode);


            // Keep part of the available paint on this node, distribute the rest
            bcv.add(focusNode, (alpha * wetPaint));

            // If there is not enough paint we stop and don't distribute among the neighbors
            if (wetPaint < epsilon)
                continue;

            if(reverse) neighbors = vertexIn[focusNode];
            else neighbors = vertexOut[focusNode];

            if(neighbors.length == 0)
                continue;

            if(reverse) edgeCache = graph.getInOnlyEdges(focusNode).toIntArray();
            else edgeCache = graph.getOutOnlyEdges(focusNode).toIntArray();

            partialWetPaint = (1 - alpha) * wetPaint / neighbors.length;

            // We can already tell that the neighbors will not have enough paint to continue
            if(partialWetPaint < epsilon)
                continue;

            if(!reverse) {
                System.out.println(focusNode);
                System.out.println();
                for(int e : edgeOut[focusNode])
                    System.out.println(getEdgeType(e));
                System.out.println();
            }

            for (int neighbor : neighbors) {

                if (reverse)
                    edge = getEdge(neighbor, focusNode, graph.getOutOnlyEdges(neighbor).toIntArray(), edgeCache);
                else
                    edge = getEdge(focusNode, neighbor, edgeCache, graph.getInOnlyEdges(neighbor).toIntArray());

                if(!reverse) {
                    System.out.println(getEdgeType(edge));
                }

                // Add the predicate to the context
                bcv.add(graph.getVertices().size() + getEdgeType(edge), partialWetPaint);

                if (nodeQueue.contains(neighbor)) {
                    wetPaintRegister.add(neighbor, partialWetPaint);
                } else {
                    nodeQueue.add(neighbor);
                    wetPaintRegister.put(neighbor, partialWetPaint);
                }
            }
            if(!reverse) {
                System.out.println();
                System.out.println("-------------------");
                System.out.println();
            }
		}
		return bcv;
	}
}