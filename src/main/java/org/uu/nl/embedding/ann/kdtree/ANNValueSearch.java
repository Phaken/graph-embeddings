package org.uu.nl.embedding.ann.kdtree;

import org.uu.nl.embedding.ann.kdtree.util.ValueDistanceTuple;

import java.util.List;

interface ANNValueSearch<T> {
	  /**
	   * @return the k nearest neighbors to the given vector.
	   */
      List<ValueDistanceTuple<T>> getValueNearestNeighbours(double[] vec, int k);

	  /**
	   * @return nearest neighbors to the given vector within the given radius.
	   */
      List<ValueDistanceTuple<T>> getValueNearestNeighbours(double[] vec, double radius);

	  /**
	   * @return the k nearest neighbors to the given vector within the given
	   *         radius.
	   */
      List<ValueDistanceTuple<T>> getValueNearestNeighbours(double[] vec, int k, double radius);

	  /**
	   * @return the payloads within the range of the lower and upper
	   *         bounded vectors.
	   */
      List<ValueDistanceTuple<T>> rangeValueQuery(double[] lower, double[] upper);
	  
}
