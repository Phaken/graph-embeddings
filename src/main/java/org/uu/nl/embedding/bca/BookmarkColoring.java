package org.uu.nl.embedding.bca;

import grph.Grph;
import me.tongfei.progressbar.ProgressBar;
import org.uu.nl.embedding.CooccurenceMatrix;
import org.uu.nl.embedding.Settings;
import org.uu.nl.embedding.bca.util.GraphStatistics;
import org.uu.nl.embedding.bca.util.BCAOptions;
import org.uu.nl.embedding.bca.util.BCV;
import org.uu.nl.embedding.bca.util.OrderedIntegerPair;
import org.uu.nl.embedding.convert.util.EdgeNeighborhoodAlgorithm;
import org.uu.nl.embedding.convert.util.VertexNeighborHoodAlgorithm;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;

/**
 * @author Jurian Baas
 */
public class BookmarkColoring implements CooccurenceMatrix {

	private final String[] dict;
	private final GraphStatistics stats;
	private final byte[] types;
	private final int[] cooccurrenceIdx_I;
	private final int[] cooccurrenceIdx_J;
	private final double[] cooccurence;
	private final double alpha, epsilon;
	private double max;
	private final int vocabSize;
	private final int cooccurenceCount;
	private final boolean includeReverse;

	private static final Settings settings = Settings.getInstance();

	public BookmarkColoring(Grph graph, BCAOptions options) {

		this.includeReverse = options.isReverse();
		this.alpha = options.getAlpha();
		this.epsilon = options.getEpsilon();
		
		this.stats = new GraphStatistics(graph);
		this.types = stats.types;
		this.dict = stats.dict;
		this.vocabSize = stats.dict.length;

		final int numThreads = 1;//Runtime.getRuntime().availableProcessors() - 1;
		
		final Map<OrderedIntegerPair, Double> cooccurrence_map = new ConcurrentHashMap<>(vocabSize);
		final ExecutorService es = Executors.newWorkStealingPool(numThreads);

		final int[][] inEdge = new EdgeNeighborhoodAlgorithm.In().compute(graph);
		final int[][] outEdge = new EdgeNeighborhoodAlgorithm.Out().compute(graph);
		final int[][] in = new VertexNeighborHoodAlgorithm.In().compute(graph);
		final int[][] out = new VertexNeighborHoodAlgorithm.Out().compute(graph);

		try(ProgressBar pb = settings.progressBar("BCA", stats.jobs.length, "nodes")) {

			CompletionService<BCV> completionService = new ExecutorCompletionService<>(es);
			//System.out.println("Submitting " + stats.jobs.length + " jobs");
			for(int bookmark : stats.jobs) {
				switch(options.getType()) {
				default:
				case VANILLA:
					completionService.submit(new VanillaBCAJob(
							graph, bookmark,
							includeReverse, alpha, epsilon,
							in, out, inEdge, outEdge));
					break;
				case SEMANTIC:
					completionService.submit(new SemanticBCAJob(
							graph, bookmark,
							includeReverse, alpha, epsilon,
							in, out));
					break;
				}
			}
			
			//now retrieve the futures after computation (auto wait for it)
			int received = 0;

			while(received < stats.jobs.length) {

				try {
					BCV bcv = completionService.take().get();
					// We have to collect all the BCV's first before we can store them
					// in a more efficient lookup friendly way below
					bcv.normalize();

					bcv.addTo(cooccurrence_map);

					//computedBCV.put(bcv.getRootNode(), bcv);

					// It is possible to use this maximum value in GloVe, although in the
					// literature they set this value to 100 and leave it at that
					setMax(bcv.max());

				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();

				} finally {

					received ++;
					pb.step();
				}
			}

			pb.setExtraMessage("Processed " + stats.jobs.length + " jobs");

		} finally {
			es.shutdown();
		}
		
		this.cooccurenceCount = cooccurrence_map.size();
		this.cooccurence = new double[cooccurenceCount];
		this.cooccurrenceIdx_I = new int[cooccurenceCount];
		this.cooccurrenceIdx_J = new int[cooccurenceCount];
		
		int i = 0;
		for(Entry<OrderedIntegerPair, Double> entry : cooccurrence_map.entrySet()) {
			this.cooccurrenceIdx_I[i] = entry.getKey().getIndex1();
			this.cooccurrenceIdx_J[i] = entry.getKey().getIndex2();
			this.cooccurence[i] = entry.getValue();
			i++;
		}
	}
	
	public int cIdx_I(int i) {
		return this.cooccurrenceIdx_I[i];
	}
	
	public int cIdx_J(int j) {
		return this.cooccurrenceIdx_J[j];
	}
	
	public double cIdx_C(int i) {
		return this.cooccurence[i];
	}
	
	public byte getType(int index) {
		return this.types[index];
	}
	
	public int cooccurrenceCount() {
		return this.cooccurenceCount;
	}

	/**
	 * Retention coefficient
	 */
	public double getAlpha() {
		return alpha;
	}

	/**
	 * Tolerance threshold
	 */
	public double getEpsilon() {
		return epsilon;
	}
	
	@Override
	public int vocabSize() {
		return this.vocabSize;
	}
	
	@Override
	public double max() {
		return this.max;
	}
	
	@Override
	public String getKey(int index) {
		return this.dict[index];
	}
	
	@Override
	public String[] getKeys() {
		return this.dict;
	}
	
	@Override
	public byte[] getTypes() {
		return this.types;
	}
	
	private void setMax(double newMax) {
		if(newMax > max) max = newMax;
	}
	
	@Override
	public int uriNodeCount() {
		return this.stats.getUriNodeCount();
	}

	@Override
	public int predicateNodeCount() {
		return this.stats.getPredicateNodeCount();
	}

	@Override
	public int blankNodeCount() {
		return this.stats.getBlankNodeCount();
	}

	@Override
	public int literalNodeCount() {
		return this.stats.getLiteralNodeCount();
	}

	public boolean isIncludeReverse() {
		return includeReverse;
	}

}