package org.uu.nl.embedding.opt;

import org.apache.commons.math.util.FastMath;

public class GloveCost implements CostFunction {

    public float innerCost(Optimizer opt, float Xij, int u, int v) {

        float innerCost = 0;
        for (int d = 0; d < opt.dimension; d++)
            innerCost += opt.focus[u][d] * opt.context[v][d]; // dot product of node and context node vector
        // Add separate bias for each node
        innerCost += opt.fBias[u] + opt.cBias[v] - FastMath.log(Xij);
        return innerCost;
    }

    @Override
    public float weightedCost(Optimizer opt, float innerCost, float Xij) {
        return (Xij > opt.coMatrix.max()) ? innerCost : (float) FastMath.pow(Xij /  opt.coMatrix.max(), 0.75) * innerCost;
    }
    
    public float innerCost(OptimizerKale opt, float Xij, int u, int v) {

        float innerCost = 0, dimensionalCost;
        for (int d = 0; d < opt.dimension; d++) {
        	//System.out.println("GloveCost.innerCost() - focus["+u+"]["+d+"] = "+ opt.focus[u][d]+ ", context["+u+"]["+d+"] = "+opt.context[v][d]);
        	//System.out.println("GloveCost.innerCost() - focus["+u+"]["+d+"] * context["+u+"]["+d+"] =  "+ opt.focus[u][d] * opt.context[v][d]);
        	if (opt.focus[u][d] < 0 && opt.context[v][d] > 0)
        		dimensionalCost = (((opt.focus[u][d] * -1) * opt.context[v][d]) * -1);
        	else if (opt.focus[u][d] > 0 && opt.context[v][d] < 0)
        		dimensionalCost = ((opt.focus[u][d] * (opt.context[v][d] * -1)) * -1);
            else if ((opt.focus[u][d] * opt.context[v][d]) < 1e-7) dimensionalCost = 0f;
            else dimensionalCost = (opt.focus[u][d] * opt.context[v][d]);
        	
        	//System.out.println("GloveCost.innerCost() - dimensionalCost = "+ dimensionalCost);
        	
            innerCost += dimensionalCost; // dot product of node and context node vector
        	//System.out.println("GloveCost.innerCost() - innerCost += focus["+u+"]["+d+"] * context["+u+"]["+d+"] = "+innerCost);
        	//if (Float.isNaN(innerCost) || Double.isNaN(innerCost)) innerCost = 0;
        }
        // Add separate bias for each node
    	//System.out.println("GloveCost.innerCost() - FastMath.log(Xij="+Xij+") = "+FastMath.log(Xij));
    	//System.out.println("GloveCost.innerCost() - Math.log(Xij="+Xij+") = "+Math.log(Xij));
        innerCost += opt.fBias[u] + opt.cBias[v] - FastMath.log(Xij);
    	//if (Float.isNaN(innerCost) || Double.isNaN(innerCost)) innerCost = 0;
        return innerCost;
    }

    @Override
    public float weightedCost(OptimizerKale opt, float innerCost, float Xij) {
        return (Xij > opt.coMatrix.max()) ? innerCost : (float) Math.pow(Xij /  opt.coMatrix.max(), 0.75) * innerCost;
    }
}