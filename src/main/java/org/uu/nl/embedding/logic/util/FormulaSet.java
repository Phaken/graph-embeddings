package org.uu.nl.embedding.logic.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import org.uu.nl.embedding.logic.LogicRule;

public class FormulaSet {
	
	private final TreeSet<String> ruleSet;
	private final Literal[] literals;
	private String[] operators;
	private final ArrayList<String> strLiterals;
	private final String strRule;
	
	public FormulaSet(final String line) throws Exception {
		this.strRule = line;

		this.ruleSet = new TreeSet<String>();
		
		this.strLiterals = new ArrayList<String>();
		String[] tokens = line.split("\t");
		String[] ops = new String[10]; /*Should be fixed when rules become larger*/
		ArrayList<String> nots = new ArrayList<String>();
		
		int counter = 0, iOperators = 0;
		boolean implication = false;
		for (int i = 0; i < tokens.length; i++) {
			if (tokens[i] == "==>") {
				implication = true;
				ops[iOperators++] = tokens[i];
				
			}
			if ((tokens[i].contains("!")) || (tokens[i].contains("&")) || (tokens[i].contains("|"))) {
				ops[iOperators++] = tokens[i];
				
			}
			
			if (!tokens[i].contains("!") && !implication) nots.add(tokens[i]); 
			else if (tokens[i].contains("!") && implication) nots.add(tokens[i]);
			
			if (!tokens[i].contains("!") && !tokens[i].contains("|") && !tokens[i].contains("&") && !tokens[i].contains("==>")) {
				this.strLiterals.add(counter, tokens[i]);
				counter++;
			}
		}
		
		this.operators = new String[iOperators];
		for (int i = 0; i < iOperators; i++) this.operators[i] = ops[i];
		
		if (counter > 3) throw new Exception("Loading error in FormulaSet: data format incorrect.");
		
		
		this.literals = new Literal[this.strLiterals.size()];
		for (int i = 0; i < this.strLiterals.size(); i++) {
			this.literals[i] = new Literal(extractTerms(this.strLiterals.get(i)));
			
			/*if (this.strLiterals.get(i).contains("birth") && this.literals[i].name.contains("birth")) {
				this.ruleSet.add(this.literals[i].name);
				
			} else if (this.strLiterals.get(i).contains("death") && this.literals[i].name.contains("death")) {
				this.ruleSet.add(this.literals[i].name);
				
			} else if (this.strLiterals.get(i).contains("baptism") && this.literals[i].name.contains("baptism")) {
				this.ruleSet.add(this.literals[i].name);
				
			} else if (this.strLiterals.get(i).contains("_is_same_or_before") && this.literals[i].name == "_is_same_or_before") {
				this.ruleSet.add(this.literals[i].name);
				
			}*/
			
			if (this.strLiterals.get(i).contains("birth_date_approx") && this.literals[i].name == "birth_date_approx") {
				this.ruleSet.add(this.literals[i].name);
				
			} else if (this.strLiterals.get(i).contains("death_date_approx") && this.literals[i].name == "death_date_approx") {
				this.ruleSet.add(this.literals[i].name);
				
			} else if (this.strLiterals.get(i).contains("baptism_date_approx") && this.literals[i].name == "baptism_date_approx") {
				this.ruleSet.add(this.literals[i].name);
				
			} else if (this.strLiterals.get(i).contains("_is_same_or_before") && this.literals[i].name == "_is_same_or_before") {
				this.ruleSet.add(this.literals[i].name);
				
			}
		}
		
	}
	
	public String[] extractTerms(String literal) {
		String[] resTokens = literal.split("[(,)]");
		return resTokens;
	}
	
	public String[][] getLiteralSet() {
		String[][] set = new String[this.literals.length][];
		String[] split = new String[5];
		for (int i = 0; i < this.literals.length; i++) {
			split[0] = this.literals[i].name;
			split[1] = this.literals[i].head;
			split[2] = this.literals[i].tail;
			split[3] = this.literals[i].headMeaning;
			split[4] = this.literals[i].tailMeaning;
			set[i] = split;
		}
		return set;
	}
	
	public String[] getTokens() {
		String[] tokens = new String[(this.literals.length * 3)];
		int counter = 0;
		for (Literal lit : this.literals) {
			tokens[counter++] = lit.head;
			tokens[counter++] = lit.name;
			tokens[counter++] = lit.tail;
		}
		return tokens;
	}
	
	public String[] getOperators() throws Exception {
		if (this.operators == null || this.operators.length == 0) throw new Exception("Operators not initialized.");
		return this.operators;
	}
	
	public String[] getOrigArgs() {
		String[] tokens = new String[(this.literals.length * 3)];
		int counter = 0;
		for (Literal lit : this.literals) {
			tokens[counter++] = lit.head;
			tokens[counter++] = lit.name;
			tokens[counter++] = lit.tail;
		}
		return tokens;
	}
	
	@Override
	public String toString() {
		return this.strRule;
	}
	
	/**
	 * Inner class: Literal.
	 * @author Euan Westenbroek
	 *
	 */
	private class Literal {
		
		private final String[] origArgs;
		public String name;
		public String head;
		public String tail;
		public String headMeaning = "";
		public String tailMeaning = "";
		
		public Literal(final String[] args) {
			this.origArgs = args;
			this.name = args[0];
			this.head = args[1];
			this.tail = args[2];
		}
		
	}

}
