package org.uu.nl.embedding.kale.util;

import java.util.HashMap;
import java.util.Random;

import org.uu.nl.embedding.kale.struct.KaleTriple;
import org.uu.nl.embedding.kale.struct.TripleRule;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class NegativeRuleGenerator {
	public TripleRule PositiveRule;
	public int iNumberOfRelations;
	private HashMap<String, Integer> relationTypes = null;
	
	public NegativeRuleGenerator(TripleRule inPositiveRule,
			int inNumberOfRelations) {
		PositiveRule = inPositiveRule;
		iNumberOfRelations = inNumberOfRelations;
	}
	
	public NegativeRuleGenerator(TripleRule inPositiveRule,
			int inNumberOfRelations, HashMap<String, Integer> relationTypes) {
		
		this.PositiveRule = inPositiveRule;
		this.iNumberOfRelations = relationTypes.size() -1; // -1 as correction for index.
		this.relationTypes = relationTypes;
		
		System.out.println("NegativeRuleGenerator.constrcutor - relationTypes.size() = "+relationTypes.size());
	}
	
	public TripleRule generateSndNegRule() throws Exception {
		Random random = new Random();
		
		if(PositiveRule.getThirdTriple() == null){
			String[] operators = new String[1];
			operators[0] = this.PositiveRule.operatorFrstScnd();
			
			KaleTriple fstTriple = PositiveRule.getFirstTriple();
			int iSndHead = PositiveRule.getSecondTriple().head();
			int iSndTail = PositiveRule.getSecondTriple().tail();
			int iSndRelation = PositiveRule.getSecondTriple().relation();
			int iFstRelation = PositiveRule.getFirstTriple().relation();
			
			int iNegRelation = iSndRelation;
			KaleTriple sndTriple = new KaleTriple(iSndHead, iNegRelation, iSndTail);
			
			while (iNegRelation == iSndRelation || iNegRelation == iFstRelation) {
				System.out.println("NegativeRuleGenerator.generateSndNegRule() - Finding new iNegRelation (2)");
				
				iNegRelation = (int)(random.nextInt(iNumberOfRelations));
				//iNegRelation = (int)(Math.random() * iNumberOfRelations);
				sndTriple = new KaleTriple(iSndHead, iNegRelation, iSndTail);
			}
			System.out.println("NegativeRuleGenerator.generateSndNegRule() - New iNegRelation found (2): "+String.valueOf(iNegRelation));
			
			TripleRule NegativeRule;
			if (this.relationTypes != null) {
				NegativeRule = new TripleRule(fstTriple, sndTriple, operators[0], this.relationTypes);
			} else {
				NegativeRule = new TripleRule(fstTriple, sndTriple, operators[0]);
			}
			return NegativeRule;
		}
		else{
			String[] operators = new String[2];
			operators[0] = this.PositiveRule.operatorFrstScnd();
			operators[1] = this.PositiveRule.operatorFrstScnd();
			
			
			KaleTriple fstTriple = PositiveRule.getFirstTriple();
			KaleTriple sndTriple = PositiveRule.getSecondTriple();
			int iTrdHead = PositiveRule.getThirdTriple().head();
			int iTrdTail = PositiveRule.getThirdTriple().tail();
			int iTrdRelation = PositiveRule.getThirdTriple().relation();
			int iFstRelation = PositiveRule.getFirstTriple().relation();
			int iSndRelation = PositiveRule.getSecondTriple().relation();
			
			int iNegRelation = iTrdRelation;
			KaleTriple trdTriple = new KaleTriple(iTrdHead, iTrdTail, iNegRelation);
			
			while (iNegRelation == iTrdRelation || iNegRelation == iSndRelation || iNegRelation == iFstRelation) {
				System.out.println("NegativeRuleGenerator.generateSndNegRule() - Finding new iNegRelation (3)");

				iNegRelation = (int)(random.nextInt(iNumberOfRelations));
				//iNegRelation = (int)(Math.random() * iNumberOfRelations);
				trdTriple = new KaleTriple(iTrdHead, iTrdTail, iNegRelation);
			}
			System.out.println("NegativeRuleGenerator.generateSndNegRule() - New iNegRelation found (3): "+String.valueOf(iNegRelation));
			
			TripleRule NegativeRule;
			if (this.relationTypes != null) {
				NegativeRule = new TripleRule(fstTriple, sndTriple, trdTriple, operators[0], operators[1], this.relationTypes);
			} else {
				NegativeRule = new TripleRule(fstTriple, sndTriple, trdTriple, operators[0], operators[1]);
			}
			return NegativeRule;
		}
		
	}
	
	public TripleRule generateFstNegRule() throws Exception {
		Random random = new Random();
		String[] operators = new String[1];
		operators[0] = this.PositiveRule.operatorFrstScnd();
		
		KaleTriple sndTriple = PositiveRule.getSecondTriple();
		int ifstHead = PositiveRule.getFirstTriple().head();
		int ifstTail = PositiveRule.getFirstTriple().tail();
		int iFstRelation = PositiveRule.getFirstTriple().relation();
		int iSndRelation = PositiveRule.getSecondTriple().relation();
		
		int iNegRelation = iFstRelation;
		KaleTriple fstTriple = new KaleTriple(ifstHead, iNegRelation, ifstTail);
		while (iNegRelation == iSndRelation || iNegRelation == iFstRelation) {
			System.out.println("NegativeRuleGenerator.generateFstNegRule() - Finding new iNegRelation");
			
			iNegRelation = (int)(random.nextInt(iNumberOfRelations));
			//iNegRelation = (int)(Math.random() * iNumberOfRelations);
			fstTriple = new KaleTriple(ifstHead, iNegRelation, ifstTail);
		}
		
		TripleRule NegativeRule;
		if (this.relationTypes != null) {
			NegativeRule = new TripleRule(fstTriple, sndTriple, operators[0], this.relationTypes);
		} else {
			NegativeRule = new TripleRule(fstTriple, sndTriple, operators[0]);
		}
		return NegativeRule;
	}
	
}