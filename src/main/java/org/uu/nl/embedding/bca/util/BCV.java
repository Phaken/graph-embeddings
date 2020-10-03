package org.uu.nl.embedding.bca.util;

import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.ExtendedRandom;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * This class represents a bookmark coloring vector
 * @author Jurian Baas
 *
 */
public class BCV extends HashMap<Integer, Float> {

	private static final ExtendedRandom random = Configuration.getThreadLocalRandom();
	private static final long serialVersionUID = 1L;

	private final int rootNode;
	public boolean notEmpty;
	public int iNumNeighbors;

	public int getRootNode() {
		return this.rootNode;
	}

	public BCV(int rootNode) {
		this.rootNode = rootNode;
	}
	
	public int getMaxNeighbors() {
		return this.iNumNeighbors;
	}

	/*@Override
	public String toString() {
		//Float[] values = entrySet().stream().sorted(Entry.comparingByKey()).map(Entry::getValue).toArray(Float[]::new);
		//int maxKey = keySet().stream().max(Integer::compareTo).orElse(0);
		StringBuilder s = new StringBuilder();
		for(int i = 0; i < size(); i++) {
			Float f = get(i);
			s.append(f == null ? "\t\t\t\t": 	"\t" + i + ": " + f);
		}
		return rootNode + ":\t" + s.toString();
	}*/
	
	@Override
	public String toString() {
		//Float[] values = entrySet().stream().sorted(Entry.comparingByKey()).map(Entry::getValue).toArray(Float[]::new);
		//int maxKey = keySet().stream().max(Integer::compareTo).orElse(0);
		StringBuilder s = new StringBuilder();
		for(int i = 0; i < size(); i++) {
			Float f = get(i);
			s.append(f == null ? "": 	"\t" + i + ": " + f);
			
			if(f != null) this.notEmpty = true;
		}
		return rootNode + ":\t" + s.toString();
	}

	/**
	 * Add the value to a key, or create a new 
	 * record if the key was not present before
	 * @param key key with which the specified value is to be associated
	 * @param value value to be associated with the specified key
	 */
	public void add(int key, float value) {
		super.put(key, getOrDefault(key, 0f) + value);
	}

	/**
	 * Add the value to a key, or create a new
	 * record if the key was not present before
	 * @param key key with which the specified value is to be associated
	 * @param value value to be associated with the specified key
	 */
	public void add(int key, double value) {
		this.add(key, (float)value);
	}

	/**
	 * Changes the values to positive values between 1 and 100
	 */
	public void toCounts() {
		final float aMax = max();
		final float aMin = min();
		for(Entry<Integer, Float> entry : entrySet()) {
			entry.setValue(scale(entry.getValue(), aMax, aMin, 1000, 1));
		}
		remove(rootNode);
	}

	/**
	 * Changes the values to sum to 1
	 */
	public void toUnity() {
		remove(rootNode);
		final float sum = sum();
		for(Entry<Integer, Float> entry : entrySet()) {
			entry.setValue(entry.getValue() / sum - 1e-6f);
		}
	}

	/**
	 * Changes the values to sum to 1
	 */
	public void smooth() {
		remove(rootNode);
		
		final float sum = sum();
		float value;
		ArrayList<Integer> deleteLst = new ArrayList<Integer>();
		for(Entry<Integer, Float> entry : entrySet()) {
			value = entry.getValue() / sum;
			
			if (value < 1e-6f) deleteLst.add(entry.getKey());
			entry.setValue(value);
		}
		for (int key : deleteLst) this.remove(key);
	}

	public void toUnity(boolean other) {
		remove(rootNode);

		float sum = 0f;
		for(Entry<Integer, Float> entry : entrySet()) {
			//if (entry.getValue() < 0) System.out.println("BCV.toUnity() - negavtive entry.getValue(): "+entry.getValue());
			sum += (float) entry.getValue();
		}
		for(Entry<Integer, Float> entry : entrySet()) {
			entry.setValue(entry.getValue() / sum);
			//if (entry.getValue() < 0) System.out.println("BCV.toUnity() - negavtive entry.getValue(): "+entry.getValue());
		}
	}

	/**
	 * @return The minimum value for this BCV
	 */
	private float min() {
		return values().stream().min(Float::compareTo).orElse(0f);
	}

	/**
	 * @return The maximum value for this BCV
	 */
	public float max() {
		return values().stream().max(Float::compareTo).orElse(1f);
	}

	/**
	 * Used to scale the probabilities to some positive range
	 */
	private float scale(float a, float aMax, float aMin, float max, float min) {
		return (a / ((aMax - aMin) / (max - min))) + min;
	}

	/**
	 * @return The total sum of all values in this BCV
	 */
    private float sum() {
    	return values().stream().reduce(Float::sum).orElse(0f);
	}
	
	/**
	 * Merge this BCV with another BCV (usually using the same root-node
	 * but in reverse order)
	 * @param other The other BCV
	 */
	public BCV merge(BCV other) {
		other.forEach((key, value2) -> this.merge(key, value2, Float::sum));
		return this;
	}
	

	public BCV merge(BCV other, boolean bool) {
		float value;
		for (Entry<Integer, Float> entry : other.entrySet()) {
			value = 0;
			if (this.containsKey(entry.getKey())) value = this.get(entry.getKey());
			value += entry.getValue();
			if (value < 0) System.out.println("BCV.merge() - Xij negative: "+value);
			this.put(entry.getKey(), value);
		}
		return this;
	}
	

	public BCV copy() {
		BCV bcvNew = new BCV(this.getRootNode());
		this.forEach((key, value2) -> bcvNew.add(key, value2));
		return bcvNew;
	}
	
}