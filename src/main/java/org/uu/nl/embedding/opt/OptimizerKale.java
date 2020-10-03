package org.uu.nl.embedding.opt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.math.util.FastMath;
import org.apache.log4j.Logger;
import org.uu.nl.embedding.bca.models.BookmarkColoringEdges;
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.ExtendedRandom;

import me.tongfei.progressbar.ProgressBar;

public abstract class OptimizerKale implements IOptimizer {

	private final static Logger logger = Logger.getLogger(OptimizerKale.class);
	private static final ExtendedRandom random = Configuration.getThreadLocalRandom();

	protected final CoOccurrenceMatrix coMatrix;
	protected final BookmarkColoringEdges bcaEdges;
	protected final int dimension;
	protected final int iNumEdges, iNumVerts, iNumEdgeTypes;
	protected final int contextVectors, focusVectors;
	protected final int numThreads;
	protected final int coCount;
	protected final float learningRate = 0.05f;
	protected final float[][] focus, context;
	protected final float[] fBias, cBias;
	protected final int[] linesPerThread;
	protected final CostFunction costFunction;
	private final int maxIterations;
	private final double tolerance;

	protected OptimizerKale(BookmarkColoringEdges coMatrix, Configuration config, CostFunction costFunction) {

		this.costFunction = costFunction;
		this.coMatrix = coMatrix;
		this.bcaEdges = coMatrix;
		this.maxIterations = config.getOpt().getMaxiter();
		this.tolerance = config.getOpt().getTolerance();
		this.numThreads = config.getThreads();

		this.iNumEdges = coMatrix.getNumEdges();
		this.iNumVerts = coMatrix.getNumVerts();
		this.iNumEdgeTypes = coMatrix.getNumEdgeTypes();
		this.focusVectors = coMatrix.nrOfFocusVectors()+1;
		//this.contextVectors = coMatrix.nrOfContextVectors();
		this.contextVectors = coMatrix.nrOfContextVectors();
		this.coCount = coMatrix.coOccurrenceCount();
		this.dimension = config.getDim();

		this.focus = new float[focusVectors][dimension];
		this.context = new float[contextVectors][dimension];
		this.fBias = new float[focusVectors];
		this.cBias = new float[contextVectors];

		for (int i = 0; i < focusVectors; i++) {
			while(fBias[i] >= -1e-2 && fBias[i] <= 1e-2)
				fBias[i] = (float) (random.nextFloat() - 0.5);// / dimension;

			for (int d = 0; d < dimension; d++) {
				while(focus[i][d] >= -1e-2 && focus[i][d] <= 1e-2)
					focus[i][d] = (float) (random.nextFloat() - 0.5);// / dimension;
			}
		}

		for (int i = 0; i < contextVectors; i++) {
			while(cBias[i] >= -1e-2 && cBias[i] <= 1e-2)
				cBias[i] = (float) (random.nextFloat() - 0.5);// / dimension;

			for (int d = 0; d < dimension; d++) {
				while(context[i][d] >= -1e-2 && context[i][d] <= 1e-2)
					context[i][d] = (float) (random.nextFloat() - 0.5);// / dimension;
			}
		}

		this.linesPerThread = new int[numThreads];
		for (int i = 0; i < numThreads - 1; i++) {
			linesPerThread[i] = coCount / numThreads;
		}
		linesPerThread[numThreads - 1] = coCount / numThreads + coCount % numThreads;
	}

	@Override
	public Optimum optimize() throws OptimizationFailedException {

		final Optimum opt = new Optimum();
		final ExecutorService es = Executors.newWorkStealingPool(numThreads);
		final CompletionService<Float> completionService = new ExecutorCompletionService<>(es);

		try(ProgressBar pb = Configuration.progressBar(getName(), maxIterations, "epochs")) {

			double prevCost = 0;
			double iterDiff;
			for (int iteration = 0; iteration < maxIterations; iteration++) {

				this.bcaEdges.shuffle();

				for (int id = 0; id < numThreads; id++)
					completionService.submit(createJob(id, iteration));
				
				int received = 0;
				double localCost = 0;

				while(received < numThreads) {
					try {
						localCost += completionService.take().get();
						received++;
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
					}
				}

				if(Double.isNaN(localCost)) {
					throw new OptimizationFailedException("Cost NaN");
				}
				if(Double.isInfinite(localCost)) {
					throw new OptimizationFailedException("Cost infinite");
				}

				localCost = (localCost / coCount);

				opt.addIntermediaryResult(localCost);
				iterDiff= FastMath.abs(prevCost - localCost);

				pb.step();
				pb.setExtraMessage(formatMessage(iterDiff));
				prevCost = localCost;

				if(iterDiff <= tolerance) {

					opt.setResult(extractResult());
					opt.setFinalCost(localCost);

					break;
				}
			}
			
		} finally {
			es.shutdown();
		}

		return opt;
	}

	private String formatMessage(double iterDiff) {
		return new BigDecimal(iterDiff).stripTrailingZeros().toPlainString();
	}

	/**
	 * Create a new double array containing the averaged values between the focus and context vectors
	 */
	@Override
	public float[] extractResult() {
		logger.info("Extracting results with "+focusVectors+" focus vectors and each a dimension of "+dimension+".");
		
		float[] embedding = new float[focusVectors * dimension];
		for (int focusIndex = 0; focusIndex < focusVectors; focusIndex++) {
			//final int contextIndex = this.coMatrix.focusIndex2Context(focusIndex);
			//final int contextIndex = focusIndex;
			//float[] contextValues = getContextValues(focusIndex);
			for (int d = 0; d < dimension; d++) {
				//embedding[d + (focusIndex * dimension)] = (this.focus[focusIndex][d] + this.context[contextIndex][d]) / 2;
				//embedding[d + (focusIndex * dimension)] = (this.focus[focusIndex][d] + contextValues[d]) / 2;
				embedding[d + (focusIndex * dimension)] = this.focus[focusIndex][d];
			}
		}
		return embedding;
	}
	
	private float[] getContextValues(final int focusEdge) {
		//BookmarkColoringEdges matrix = (BookmarkColoringEdges) this.coMatrix;
		ArrayList<Integer> contextNodes = this.bcaEdges.getContextNodesOf(focusEdge);
		System.out.println("OptimizerKale.getContextValues() - contextNodes.size() = " + contextNodes.size());
		
		float[] contextValues = new float[this.dimension];
		float value;
		for (int d = 0; d < this.dimension; d++) {
			value = 0;
			for (int contextIndex : contextNodes) {
				value += this.context[contextIndex][d]  / contextNodes.size();
			}
			contextValues[d] = value;
			System.out.println("OptimizerKale.getContextValues() - contextValues[d] ( / contextNodes.size()) = " + contextValues[d]);
		}
		return contextValues;
	}

}