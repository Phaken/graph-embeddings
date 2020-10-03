package org.uu.nl.embedding.kale.struct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class TripleRule {
	
	private KaleTriple firstTriple = null;
	private KaleTriple secondTriple = null;
	private KaleTriple thirdTriple = null;
	
	private KaleTriple convFirstTriple = null;
	private KaleTriple convSecondTriple = null;
	private KaleTriple convThirdTriple = null;
	
	private HashMap<String, Integer> relationTypes;
	private final HashMap<Integer, ArrayList<Integer>> literalPositions;
	private HashMap<Integer, ArrayList<Integer>>  checkLitPositions;
	private String operatorFrstScnd = "";
	private String operatorScndTrd = "";
	
	public TripleRule(final KaleTriple firstTriple, final KaleTriple secondTriple, final String operator) {
		this.firstTriple = firstTriple;
		this.secondTriple = secondTriple;
		
		this.operatorFrstScnd = operator;
		this.literalPositions = new HashMap<Integer, ArrayList<Integer>>();
		computeLiteralPositions();
	}
	
	public TripleRule(final KaleTriple firstTriple,
						final KaleTriple secondTriple,
						final KaleTriple thirdTriple,
						final String operatorFrstScnd,
						final String operatorScndTrd) {
		this.firstTriple = firstTriple;
		this.secondTriple = secondTriple;
		this.thirdTriple = thirdTriple;
		
		this.operatorFrstScnd = operatorFrstScnd;
		this.operatorScndTrd = operatorScndTrd;
		this.literalPositions = new HashMap<Integer, ArrayList<Integer>>();
		computeLiteralPositions();
	}
	
	public TripleRule(final KaleTriple firstTriple,
						final KaleTriple secondTriple,
						final String operator,
						final HashMap<String, Integer> relationTypes) throws Exception {
		this.relationTypes = relationTypes;
		
		this.firstTriple = firstTriple;
		this.secondTriple = secondTriple;
		
		this.operatorFrstScnd = operator;
		this.literalPositions = new HashMap<Integer, ArrayList<Integer>>();
		
		convertRules();
		computeLiteralPositions();
	}
	
	public TripleRule(final KaleTriple firstTriple,
						final KaleTriple secondTriple,
						final KaleTriple thirdTriple,
						final String operatorFrstScnd,
						final String operatorScndTrd,
						final HashMap<String, Integer> relationTypes) throws Exception {
		this.relationTypes = relationTypes;
		
		this.firstTriple = firstTriple;
		this.secondTriple = secondTriple;
		this.thirdTriple = thirdTriple;

		this.operatorFrstScnd = operatorFrstScnd;
		this.operatorScndTrd = operatorScndTrd;
		this.literalPositions = new HashMap<Integer, ArrayList<Integer>>();
		
		convertRules();
		computeLiteralPositions();
	}
	
	private void convertRules() throws Exception {
		convertRule(this.firstTriple, this.convFirstTriple);
		convertRule(this.secondTriple, this.convSecondTriple);
		if (this.thirdTriple != null) 
			convertRule(this.thirdTriple, this.convThirdTriple);
	}
	
	private void convertRule(KaleTriple triple, KaleTriple convTriple) throws Exception {
		int rk = -1;
		if (triple.relation() != -1) rk = triple.relation();
		else if (triple.relationStr() != null)
			if (this.relationTypes.containsKey(triple.relationStr())) {
				rk = this.relationTypes.get(triple.relationStr());
			} else throw new Exception("Received invalid relation string: "+ triple.relationStr());
		
		convTriple = new KaleTriple(triple.head(), rk, triple.tail());
	}
	
	private void computeLiteralPositions() {
		int position = 0;
		addLiteralPosition(this.firstTriple.head(), position++, this.literalPositions);
		addLiteralPosition(this.firstTriple.tail(), position++, this.literalPositions);
		addLiteralPosition(this.secondTriple.head(), position++, this.literalPositions);
		addLiteralPosition(this.secondTriple.tail(), position++, this.literalPositions);
		if (this.thirdTriple != null) {
			addLiteralPosition(this.thirdTriple.head(), position++, this.literalPositions);
			addLiteralPosition(this.thirdTriple.tail(), position++, this.literalPositions);
		}
	}
	
	private void addLiteralPosition(final int literal, final int position,
										final HashMap<Integer, ArrayList<Integer>> listsMap) {
		if (listsMap.containsKey(literal)) listsMap.get(literal).add(position);
		else {
			ArrayList<Integer> list = new ArrayList<Integer>();
			list.add(position);
			listsMap.put(literal, list);
		}
	}
	
	private void fillLitsToCheckPositions(final KaleTriple firstTriple,
										final KaleTriple secondTriple,
										final KaleTriple thirdTriple) {
		this.checkLitPositions = new HashMap<Integer, ArrayList<Integer>>();
		
		int position = 0;
		addLiteralPosition(firstTriple.head(), position++, this.checkLitPositions);
		addLiteralPosition(firstTriple.tail(), position++, this.checkLitPositions);
		addLiteralPosition(secondTriple.head(), position++, this.checkLitPositions);
		addLiteralPosition(secondTriple.tail(), position++, this.checkLitPositions);
		if (thirdTriple != null) {
			addLiteralPosition(thirdTriple.head(), position++, this.checkLitPositions);
			addLiteralPosition(thirdTriple.tail(), position++, this.checkLitPositions);
		}
	}
	
	public boolean computeValidityTriples(final KaleTriple firstTriple,
											final KaleTriple secondTriple,
											final String operatorFrstScnd) {
		
		if (!validOperators(operatorFrstScnd)) return false;
		
		fillLitsToCheckPositions(firstTriple, secondTriple, null);
		boolean samePositions = comparePositionLists();
		boolean sameOperators = this.operatorFrstScnd == operatorFrstScnd ? true : false;
		
		return samePositions && sameOperators;
	}
	
	public boolean computeValidityTriples(final KaleTriple firstTriple,
											final KaleTriple secondTriple,
											final KaleTriple thirdTriple,
											final String operatorFrstScnd,
											final String operatorScndTrd) {
		
		if ( !validOperators(operatorFrstScnd) || !validOperators(operatorScndTrd) ) return false;
		
		fillLitsToCheckPositions(firstTriple, secondTriple, thirdTriple);
		boolean samePositions = comparePositionLists();
		boolean sameOperators = (this.operatorFrstScnd == operatorFrstScnd &&
				this.operatorScndTrd == operatorScndTrd) ? true : false;
		
		return samePositions && sameOperators;
	}
	
	private boolean comparePositionLists() {
		
		if (this.literalPositions.size() != this.checkLitPositions.size()) return false;
				
		boolean allSame = true, sameListFound = false, checkNextList = false;
		int checkCntr = -1;
		while (allSame) {
			for (Entry<Integer, ArrayList<Integer>> entryThis : this.literalPositions.entrySet()) {
				ArrayList<Integer> listThis = entryThis.getValue();
				
				sameListFound = false;
				while (!sameListFound && checkCntr < this.checkLitPositions.size()) {
					
					for (Entry<Integer, ArrayList<Integer>> entryCheck : this.checkLitPositions.entrySet()) {
						checkCntr++;
						ArrayList<Integer> listCheck = entryCheck.getValue();
						
						if (listThis.size() != listCheck.size()) continue;
						
						checkNextList = false;
						int i = 0;
						while (!checkNextList && i < listThis.size()) {
							if (!listCheck.contains(listThis.get(i))) checkNextList = true;
							i++;
						}
						if (!checkNextList) sameListFound = true;
					}
				}
				if (!sameListFound) {
					allSame = false;
				}
			}
		}
		return allSame;
	}
	
	private boolean validOperators(final String operator) {
		if (operator == "&" || operator == "|" || operator == "==>") return true;
		return false;
	}
	
	public String operatorFrstScnd() {
		return this.operatorFrstScnd;
	}
	
	public String operatorScndThrd() {
		return this.operatorScndTrd;
	}
	
	public KaleTriple getFirstTriple() {
		return this.firstTriple;
	}
	
	public KaleTriple getSecondTriple() {
		return this.secondTriple;
	}
	
	public KaleTriple getThirdTriple() {
		return this.thirdTriple;
	}
	
	public void setRelationTypes(final HashMap<String, Integer> relationTypes) {
		this.relationTypes = relationTypes;
	}
	
	public HashMap<String, Integer> getRelationTypes() {
		return this.relationTypes;
	}
	
	@Override
	public String toString() {
		if (this.operatorScndTrd  == "") {
			return "RULE( "+this.firstTriple.toString()+" "+this.operatorFrstScnd+" "+this.secondTriple+" )";
		}
		return "RULE( "+this.firstTriple.toString()+" "+this.operatorFrstScnd
				+" "+this.secondTriple+" "+this.operatorScndTrd+" "+this.thirdTriple+" )";
	}
	

}
