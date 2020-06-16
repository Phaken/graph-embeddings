package org.uu.nl.embedding.opt.grad;

import org.apache.commons.math.util.FastMath;
import org.uu.nl.embedding.opt.CostFunction;
import org.uu.nl.embedding.opt.OptimizeJob;
import org.uu.nl.embedding.opt.Optimizer;
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.config.Configuration;


@SuppressWarnings("DuplicatedCode")
public class Adagrad extends Optimizer {
    /**
     * Contains the sum of the squares of the past gradients w.r.t. to all parameters
     */
    private final float[][] gradSqFocus, gradSqContext;
    private final float[] gradSqFBias, gradSqCBias;

    public Adagrad(CoOccurrenceMatrix coMatrix, Configuration config, CostFunction costFunction) {
        super(coMatrix, config, costFunction);

        this.gradSqFocus = new float[focusVectors][dimension];
        this.gradSqContext = new float[contextVectors][dimension];
        this.gradSqFBias = new float[focusVectors];
        this.gradSqCBias = new float[contextVectors];

        for (int i = 0; i < contextVectors; i++) {
            gradSqCBias[i] = 1;
            for (int d = 0; d < dimension; d++) {
                // So initial value of eta is equal to initial learning rate
                 gradSqContext[i * dimension][d] = 1;
            }
        }

        for (int i = 0; i < focusVectors; i++) {
            gradSqFBias[i] = 1;
            for (int d = 0; d < dimension; d++) {
                // So initial value of eta is equal to initial learning rate
                gradSqFocus[i * dimension][d] = 1;
            }
        }
    }

    @Override
    public String getName() {
        return "Adagrad";
    }

    @Override
    public OptimizeJob createJob(int id, int iteration) {
        return () -> {

            int i, d, u, v, bu, bv, d1, d2;
            float cost = 0, Xij, innerCost, weightedCost, grad1, grad2;
            final int offset = coCount / numThreads * id;

            for (i = 0; i < linesPerThread[id]; i++) {

                bu = coMatrix.cIdx_I(i + offset); // Index of focus bias
                bv = coMatrix.cIdx_J(i + offset); // Index of context bias
                u = bu * dimension; // Index of focus vector
                v = bv * dimension; // Index of bias vector
                Xij = coMatrix.cIdx_C(i + offset); // Co-occurrence

                /* Calculate cost, save diff for gradients */
                innerCost = costFunction.innerCost(this, Xij, u, v, bu, bv);
                weightedCost = costFunction.weightedCost(this, innerCost, Xij);
                cost += 0.5 * weightedCost * innerCost; // weighted squared error

				/*---------------------------
				 * Adaptive gradient updates *
				 ---------------------------*/

                // Compute for word vectors
                for (d = 0; d < dimension; d++) {

                    //d1 = d + u; // Index of specific dimension in focus vector
                    //d2 = d + v; // Index of specific dimension in context vector

                    // Compute gradients
                    grad1 = weightedCost * context[v][d];
                    grad2 = weightedCost * focus[u][d];
                    // Compute and apply updates
                    focus[v][d] -= grad1 / FastMath.sqrt(gradSqFocus[v][d]) * learningRate;
                    context[u][d] -= grad2 / FastMath.sqrt(gradSqContext[u][d]) * learningRate;
                    // Store squared gradients
                    gradSqFocus[v][d] += grad1 * grad1;
                    gradSqContext[u][d] += grad2 * grad2;
                }

				/*---------------------
				 * Compute for biases *
				 ---------------------*/

                // Compute updates (gradient of bias is the weighted cost)
                fBias[bu] -= weightedCost / FastMath.sqrt(gradSqFBias[bu]);
                cBias[bv] -= weightedCost / FastMath.sqrt(gradSqCBias[bv]);
                weightedCost *= weightedCost;
                // Store squared gradients
                gradSqFBias[bu] += weightedCost;
                gradSqCBias[bv] += weightedCost;

            }
            return cost;
        };
    }
}