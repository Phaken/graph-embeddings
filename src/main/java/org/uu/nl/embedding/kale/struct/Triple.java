package org.uu.nl.embedding.kale.struct;

public class Triple {

	private int iEntity;
	private String kStrRelation;
	private int kRelation;
	private int jEntity;
	
	public Triple(final int ei, final int rk, final int ej) {
		this.iEntity = ei;
		this.kRelation = rk;
		this.jEntity = ej;
	}
	
	public Triple(final int ei, final String rk, final int ej) {
		this.iEntity = ei;
		this.kRelation = -1;
		this.kStrRelation = rk;
		this.jEntity = ej;
	}
	
	public int head() {
		return this.iEntity;
	}
	
	public int relation() {
		return this.kRelation;
	}
	
	public String relationStr() {
		return this.kStrRelation;
	}
	
	public int tail() {
		return this.jEntity;
	}
}
