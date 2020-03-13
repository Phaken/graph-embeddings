package org.uu.nl.embedding.glove.opt.impl;

import org.apache.commons.math.util.FastMath;
import org.uu.nl.embedding.glove.GloveModel;
import org.uu.nl.embedding.glove.opt.GloveJob;
import org.uu.nl.embedding.glove.opt.GloveOptimizer;
import org.uu.nl.embedding.util.config.Configuration;

/**
 * <p>
 * Adaptive Moment Estimation (Adam) is another method that computes adaptive
 * learning rates for each parameter. In addition to storing an exponentially
 * decaying average of past squared gradients vt like Adadelta and RMSprop, Adam
 * also keeps an exponentially decaying average of past gradients mt, similar to
 * momentum.
 * </p>
 * <p>
 * Whereas momentum can be seen as a ball running down a slope, Adam behaves
 * like a heavy ball with friction, which thus prefers flat minima in the error
 * surface.
 * </p>
 *
 * @see <a href="https://arxiv.org/pdf/1412.6980.pdf">Adam paper</a>
 * @see <a href="http://nlp.stanford.edu/projects/glove/">Stanford GloVe page</a>
 * @author Jurian Baas
 */
@SuppressWarnings("Duplicates")
public class AdamOptimizer extends GloveOptimizer {

	/**
	 * Contains the decaying averages of the past first momentums w.r.t. to all parameters
	 */
	private final float[] M1focus, M1context;
	/**
	 * Contains the decaying averages of the past second momentums w.r.t. to all parameters
	 */
	private final float[] M2focus, M2context;
	/**
	 * Decay rate for first momentum
	 */
	private final double beta1 = 0.9;
	/**
	 * Decay rate for second momentum
	 */
	private final double beta2 = 0.999;
	/**
	 * Mainly used to prevent divisions by zero, in some cases setting this to 0.1 or 1 can help improve stability
	 */
	private final double epsilon = 1e-8;
	
	public AdamOptimizer(GloveModel glove, Configuration config) {
		super(glove, config);

		// Increase dimension to make room for bias terms
		int dimension = this.dimension + 1;
		this.M1focus = new float[vocabSize * dimension];
		this.M2focus = new float[vocabSize * dimension];
		this.M1context = new float[vocabSize * dimension];
		this.M2context = new float[vocabSize * dimension];
	}
	
	@Override
	public String getName() {
		return "GloVe-Adam";
	}

	@Override
	public GloveJob createJob(int id, int iteration) {
		return () -> {
			int a, d, l1, l2;
			double m1, m2, v1, v2, grad_u, grad_v;
			float cost = 0, innerCost, weightedCost;
			// From the paper, a slight improvement of efficiency can be obtained this way
			final double correction = learningRate * FastMath.sqrt(1 - FastMath.pow(beta2, iteration + 1)) / (1 - FastMath.pow(beta1, iteration + 1));
			final int offset = coCount / numThreads * id;

			for (a = 0; a < linesPerThread[id]; a++) {

				int node1 = coMatrix.cIdx_I(a + offset);
				int node2 = coMatrix.cIdx_J(a + offset);
				float Xij = coMatrix.cIdx_C(a + offset);

				assert Xij >= 0 && Xij <= 1 : "Co-occurrence is not between 0 and 1: " + Xij;

				l1 = node1 * (dimension + 1);
				l2 = node2 * (dimension + 1);

				/* Calculate cost, save diff for gradients */
				innerCost = 0;

				for (d = 0; d < dimension; d++)
					innerCost += focus[d + l1] * context[d + l2]; // dot product of node and context node vector
				// Add separate bias for each node
				innerCost += focus[dimension + l1] + context[dimension + l2] - FastMath.log(Xij);

				// multiply weighting function (f) with diff
				weightedCost = (Xij > xMax) ? innerCost : (float) (FastMath.pow(Xij / xMax, alpha) * innerCost);
				cost += 0.5 * weightedCost * innerCost; // weighted squared error

				/*---------------------------
				 * Adaptive gradient updates *
				 ---------------------------*/

				// Update the moments for the word vectors
				for (d = 0; d < dimension; d++) {
					// Compute gradients
					grad_u = weightedCost * context[d + l2];
					grad_v = weightedCost * focus[d + l1];
					// Update biased first and second moment estimates
					m1 = beta1 * M1focus[d + l1] + (1 - beta1) * grad_u;
					m2 = beta1 * M1context[d + l2] + (1 - beta1) * grad_v;
					v1 = beta2 * M2focus[d + l1] + (1 - beta2) * (grad_u * grad_u);
					v2 = beta2 * M2context[d + l2] + (1 - beta2) * (grad_v * grad_v);
					// Compute and apply updates
					focus[d + l1] -= correction * m1 / (FastMath.sqrt(v1) + epsilon);
					context[d + l2] -= correction * m2 / (FastMath.sqrt(v2) + epsilon);
					// Store new moments
					M1focus[d + l1] = (float) m1;
					M1context[d + l2] = (float) m2;
					M2focus[d + l1] = (float) v1;
					M2context[d + l2] = (float) v2;
				}

				/*---------------------
				 * Compute for biases *
				 ---------------------*/

				// Update the first, second moment for the biases
				m1 = beta1 * M1focus[dimension + l1] + (1 - beta1) * weightedCost;
				m2 = beta1 * M1context[dimension + l2] + (1 - beta1) * weightedCost;
				v1 = beta2 * M2focus[dimension + l1] + (1 - beta2) * (weightedCost * weightedCost);
				v2 = beta2 * M2context[dimension + l2] + (1 - beta2) * (weightedCost * weightedCost);
				// Perform updates on bias terms
				focus[dimension + l1] -= correction * m1 / (FastMath.sqrt(v1) + epsilon);
				context[dimension + l2] -= correction * m2 / (FastMath.sqrt(v2) + epsilon);
				// Store new moments
				M1focus[dimension + l1] = (float) m1;
				M1context[dimension + l2] = (float) m2;
				M2focus[dimension + l1] = (float) v1;
				M2context[dimension + l2] = (float) v2;
			}
			return cost;
		};
	}

}
