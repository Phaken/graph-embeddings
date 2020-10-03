package org.uu.nl.embedding.kale.struct;

import java.util.HashMap;
import java.util.Map.Entry;

public class KaleTriple {

	private int iEntity;
	private String kStrRelation = "";
	private int kRelation;
	private int jEntity;
	
	private HashMap<String, Integer> relationTypeMap = null;
	
	public KaleTriple(final int ei, final int rk, final int ej) {
		this.iEntity = ei;
		this.kRelation = rk;
		this.jEntity = ej;
	}
	
	public KaleTriple(final int ei, final String rk, final int ej) {
		this.iEntity = ei;
		this.kStrRelation = rk;
		this.kRelation = -1;
		this.jEntity = ej;
	}
	
	public KaleTriple(final int ei, final String rk, final int ej,
						HashMap<String, Integer> relationTypeMap) throws Exception {
		
		if (relationTypeMap == null || relationTypeMap.size() == 0) 
			throw new Exception("'Relation type map' not properly initialized.");
		this.relationTypeMap = relationTypeMap;
		
		this.iEntity = ei;
		this.kStrRelation = rk;
		this.jEntity = ej;
		
		if (!this.relationTypeMap.containsKey(rk)) {
			String[] tokens = rk.split("_");
			boolean superStringFound = false;
			for (Entry<String, Integer> entry : this.relationTypeMap.entrySet()) {
				if (entry.getKey().contains(tokens[0])) {
					superStringFound = true;
					this.kRelation = entry.getValue();
				}
			}
			if (!superStringFound) throw new Exception("Provided relation type not known in current map: "+ rk);
		}
		else this.kRelation = this.relationTypeMap.get(rk);
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
	
	public String toString() {
		/*String relationStr = String.valueOf(this.kRelation);
		if (kStrRelation == "" && (this.relationTypeMap != null || this.relationTypeMap.size() != 0)) {
			for (Entry<String, Integer> entry : this.relationTypeMap.entrySet()) {
				if (entry.getValue() == this.kRelation) {relationStr = entry.getKey();
				System.out.println("KaleTriple.toString() - entry found: "+ entry.getKey()+" - "+String.valueOf(entry.getValue()));}
			}
		}*/
		
		if (this.kStrRelation != null || this.kStrRelation != "")
			return "t(" + String.valueOf(this.iEntity) + ", " + this.kStrRelation + ", " + String.valueOf(this.jEntity) + ")";
		return "t(" + String.valueOf(this.iEntity) + ", " + String.valueOf(this.kRelation) + ", " + String.valueOf(this.jEntity) + ")";
	}
}
