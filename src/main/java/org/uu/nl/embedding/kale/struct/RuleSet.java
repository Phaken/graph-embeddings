package org.uu.nl.embedding.kale.struct;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import org.apache.commons.lang3.ArrayUtils;
import org.uu.nl.embedding.kale.util.StringSplitter;
import org.uu.nl.embedding.logic.util.FormulaSet;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class RuleSet {
	private int iNumberOfEntities;
	private int iNumberOfRelations;
	private int iNumberOfRules;
	private ArrayList<FormulaSet> formulaSets;
	public ArrayList<TripleRule> pRule = null;
	public HashMap<String, Integer> variableMap;
	
	public RuleSet(int iEntities, int iRelations) throws Exception {
		iNumberOfEntities = iEntities;
		iNumberOfRelations = iRelations;
		this.pRule = new ArrayList<TripleRule>();
		this.formulaSets = new ArrayList<FormulaSet>();
		
		this.variableMap = new HashMap<String, Integer>();
		this.variableMap.put("x", -1);
		this.variableMap.put("y", -2);
		this.variableMap.put("z", -3);
	}
	
	public int entities() {
		return iNumberOfEntities;
	}
	
	public int relations() {
		return iNumberOfRelations;
	}
	
	public int rules() {
		return iNumberOfRules;
	}
	
	public TripleRule get(int iID) throws Exception {
		if (iID < 0 || iID >= this.iNumberOfRules) {
			throw new Exception("getRule error in RuleSet: ID out of range");
		}
		return pRule.get(iID);
	}
	
	public void loadTimeLogic(final String fnInput) throws Exception {
		//BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fnInput), "UTF-8"));
		
		/*
//???	 * HOE WORDEN && || ! INGELADEN????
		 */
		System.out.println(	  "#####-------------------------------#####\n"
							+ "##### HOE WORDEN && || ! INGELADEN? #####\n"
							+ "#####-------------------------------#####\n");
		
		String line = "";
		int counter = 0;
		try (BufferedReader reader = new BufferedReader(new FileReader(fnInput));) {
			
			while ((line = reader.readLine()) != null) {
				
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				
				this.formulaSets.add(counter, new FormulaSet(line));
				String[] tokens = formulaSets.get(counter).getTokens();

				if (tokens.length != 6 && tokens.length != 9) {
					String str = "";
					for (String token : tokens) str += token + " ";
					throw new Exception("Loading error in RuleSet: data format incorrect. "
							+ "Expected 6 or 9, but received " + tokens.length + ". With the following values:\n"
							+ str);
				}
	
				int iFstHead = this.variableMap.get(tokens[0]);
				String iFstRelation = tokens[1];
				int iFstTail = this.variableMap.get(tokens[2]);
				/*
				 * START TEMP
				 */
				//if (counter == 0) System.out.println("FIRST RULE: " + String.valueOf(tokens[0])+" "+String.valueOf(tokens[2])+" "+String.valueOf(tokens[1]));
				/*
				 * END TEMP
				 */
				/*
				if (iFstHead < 0) {
					throw new Exception("Loading error in RuleSet: 1st head entity ID out of range");
				}
				if (iFstTail < 0) {
					throw new Exception("Loading error in RuleSet: 1st tail entity ID out of range");
				}
				if (iFstRelation < 0) {
					throw new Exception("Loading error in RuleSet: 1st relation ID out of range");
				}*/
				Triple fstTriple = new Triple(iFstHead, iFstRelation, iFstTail);
				
				int iSndHead = this.variableMap.get(tokens[3]);
				String iSndRelation = tokens[4];
				int iSndTail = this.variableMap.get(tokens[5]);
				/*
				 * START TEMP
				 */
				//if (counter == 0) System.out.println(String.valueOf(tokens[3])+" "+String.valueOf(tokens[5])+" "+String.valueOf(tokens[4]));
				/*
				 * END TEMP
				 */
				/*
				if (iSndHead < 0) {
					throw new Exception("Loading error in RuleSet: 2nd head entity ID out of range");
				}
				if (iSndTail < 0) {
					throw new Exception("Loading error in RuleSet: 2nd tail entity ID out of range");
				}
				if (iSndRelation < 0) {
					throw new Exception("Loading error in RuleSet: 2nd relation ID out of range");
				}*/
				Triple sndTriple = new Triple(iSndHead, iSndRelation, iSndTail);
				
				if (tokens.length == 6) {
					pRule.add(new TripleRule(fstTriple, sndTriple));
				}
				else{
					int iTrdHead = this.variableMap.get(tokens[6]);
					String iTrdRelation = tokens[7];
					int iTrdTail = this.variableMap.get(tokens[8]);
					/*
					 * START TEMP
					 */
					//if (counter == 0) System.out.println(String.valueOf(tokens[7])+" "+String.valueOf(tokens[9])+" "+String.valueOf(tokens[8]));
					/*
					 * END TEMP
					 */	
					/*
					if (iTrdHead < 0) {
						throw new Exception("Loading error in RuleSet: 3rd head entity ID out of range");
					}
					if (iTrdTail < 0) {
						throw new Exception("Loading error in RuleSet: 3rd tail entity ID out of range");
					}
					if (iTrdRelation < 0) {
						throw new Exception("Loading error in RuleSet: 3rd relation ID out of range");
					}*/
					Triple trdTriple = new Triple(iTrdHead, iTrdRelation, iTrdTail);
					
					this.pRule.add(new TripleRule(fstTriple, sndTriple, trdTriple));
				}
				
				counter++;
			}
			reader.close();
		} catch (Exception e) { e.printStackTrace(); }
		
		this.iNumberOfRules = pRule.size();
	}

	
	public void load(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));
		pRule = new ArrayList<TripleRule>();
		
		String line = "";
		int counter = 0;
		try {
			while ((line = reader.readLine()) != null) {
				
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				
				String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
						.split("\t() ", line));
				
				if (tokens.length != 6 && tokens.length != 9) {
					String str = "";
					for (String token : tokens) str += token + " ";
					throw new Exception("Loading error in TripleSet: data format incorrect. "
							+ "Expected 6 or 9, but received " + tokens.length + ". With the following values:\n"
							+ str);
				}
				
				int iFstHead = Integer.parseInt(tokens[0]);
				int iFstRelation = Integer.parseInt(tokens[1]);
				int iFstTail = Integer.parseInt(tokens[2]);
				/*
				 * START TEMP
				 */
				if (counter == 0) System.out.println(iFstHead+" "+iFstRelation+" "+iFstTail);
				/*
				 * END TEMP
				 */
				if (iFstHead < 0) {
					throw new Exception("Loading error in RuleSet: 1st head entity ID out of range");
				}
				if (iFstTail < 0) {
					throw new Exception("Loading error in RuleSet: 1st tail entity ID out of range");
				}
				if (iFstRelation < 0) {
					throw new Exception("Loading error in RuleSet: 1st relation ID out of range");
				}
				Triple fstTriple = new Triple(iFstHead, iFstRelation, iFstTail);
				
				int iSndHead = Integer.parseInt(tokens[3]);
				int iSndRelation = Integer.parseInt(tokens[4]);
				int iSndTail = Integer.parseInt(tokens[5]);
				/*
				 * START TEMP
				 */
				if (counter == 0) System.out.println(iSndHead+" "+iSndRelation+" "+iSndTail);
				/*
				 * END TEMP
				 */
				if (iSndHead < 0) {
					throw new Exception("Loading error in RuleSet: 2nd head entity ID out of range");
				}
				if (iSndTail < 0) {
					throw new Exception("Loading error in RuleSet: 2nd tail entity ID out of range");
				}
				if (iSndRelation < 0) {
					throw new Exception("Loading error in RuleSet: 2nd relation ID out of range");
				}
				Triple sndTriple = new Triple(iSndHead, iSndRelation, iSndTail);
				
				if (tokens.length == 6){
					pRule.add(new TripleRule(fstTriple, sndTriple));
				}
				else{
					int iTrdHead = Integer.parseInt(tokens[7]);
					int iTrdRelation = Integer.parseInt(tokens[8]);
					int iTrdTail = Integer.parseInt(tokens[9]);
					/*
					 * START TEMP
					 */
					if (counter == 0) System.out.println(iTrdHead+" "+iTrdRelation+" "+iTrdTail);
					/*
					 * END TEMP
					 */
					if (iTrdHead < 0) {
						throw new Exception("Loading error in RuleSet: 3rd head entity ID out of range");
					}
					if (iTrdTail < 0) {
						throw new Exception("Loading error in RuleSet: 3rd tail entity ID out of range");
					}
					if (iTrdRelation < 0) {
						throw new Exception("Loading error in RuleSet: 3rd relation ID out of range");
					}
					Triple trdTriple = new Triple(iTrdHead, iTrdTail, iTrdRelation);
					
					pRule.add(new TripleRule(fstTriple, sndTriple, trdTriple));
				}
				counter++;
			}
		} catch (Exception e) { e.printStackTrace(); }
		
		iNumberOfRules = pRule.size();
		reader.close();
	}
	
	public void randomShuffle() {
		TreeMap<Double, TripleRule> tmpMap = new TreeMap<Double, TripleRule>();
		for (int iID = 0; iID < iNumberOfRules; iID++) {
			int m = pRule.get(iID).getFirstTriple().head();
			int s = pRule.get(iID).getFirstTriple().relation();
			int n = pRule.get(iID).getFirstTriple().tail();
			Triple fstTriple = new Triple(m, s, n);
			int p = pRule.get(iID).getSecondTriple().head();
			int t = pRule.get(iID).getSecondTriple().relation();
			int q = pRule.get(iID).getSecondTriple().tail();
			Triple sndTriple = new Triple(p, t, q);
			if(pRule.get(iID).getThirdTriple()==null) {
				tmpMap.put(Math.random(), new TripleRule(fstTriple, sndTriple));
			}
			else{
				int a = pRule.get(iID).getThirdTriple().head();
				int b = pRule.get(iID).getThirdTriple().relation();
				int c = pRule.get(iID).getThirdTriple().tail();
				Triple trdTriple = new Triple(a, b, c);
				tmpMap.put(Math.random(), new TripleRule(fstTriple, sndTriple, trdTriple));
			}
		}
		
		pRule = new ArrayList<TripleRule>();
		Iterator<Double> iterValues = tmpMap.keySet().iterator();
		while (iterValues.hasNext()) {
			double dRand = iterValues.next();
			TripleRule rule = tmpMap.get(dRand);
			int m = rule.getFirstTriple().head();
			int s = rule.getFirstTriple().relation();
			int n = rule.getFirstTriple().tail();
			Triple fstTriple = new Triple(m, s, n);
			int p = rule.getSecondTriple().head();
			int t = rule.getSecondTriple().relation();
			int q = rule.getSecondTriple().tail();
			Triple sndTriple = new Triple(p, t, q);
			if(rule.getThirdTriple()==null) {
				pRule.add(new TripleRule(fstTriple, sndTriple));
			}
			else{
				int a = rule.getThirdTriple().head();
				int b = rule.getThirdTriple().relation();
				int c = rule.getThirdTriple().tail();
				Triple trdTriple = new Triple(a, b, c);
				pRule.add(new TripleRule(fstTriple, sndTriple, trdTriple));
			}
		}
		iNumberOfRules = pRule.size();
		tmpMap.clear();
	}
	
	public ArrayList<FormulaSet> getFormulaSets() {
		return this.formulaSets;
	}
}