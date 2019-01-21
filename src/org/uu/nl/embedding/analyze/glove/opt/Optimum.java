package org.uu.nl.embedding.analyze.glove.opt;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;


public class Optimum {
	
	private final List<Double> costOverTime = new ArrayList<>();
	private double[] result;
	public final int dimension;
	
	public Optimum(int dimension) {
		this.dimension = dimension;
	}
	
	public double[] getResult() {
		return result;
	}
	
	public double finalResult() {
		return costOverTime.get(costOverTime.size()-1);
	}
	
	public void setResult(double[] result) {
		this.result = result;
	}
	
	public void addCost(double cost) {
		costOverTime.add(cost);
	}
	
	public double[] getIterationSequence() {
		double[] seq = new double[costOverTime.size()];
		for(int i = 0; i < seq.length; i++) seq[i] = i;
		return seq;
	}
	
	public double[] getCostOverTime() {
		return ArrayUtils.toPrimitive(costOverTime.toArray(new Double[costOverTime.size()]));
	}
	
	public double[] getVector(int index) {
		double[] vec = new double[dimension];
		System.arraycopy(result, index * dimension, vec, 0, dimension);
		return vec;
	}
	
}