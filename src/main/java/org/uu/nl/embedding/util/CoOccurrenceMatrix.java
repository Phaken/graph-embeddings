package org.uu.nl.embedding.util;

/**
 * @author Jurian Baas
 */
public interface CoOccurrenceMatrix {

	int vocabSize();
	double max();
	String getKey(int index);
	byte getType(int index);
	int cIdx_I(int i);
	int cIdx_J(int j);
	float cIdx_C(int i);
	int coOccurrenceCount();
	void shuffle();
}
