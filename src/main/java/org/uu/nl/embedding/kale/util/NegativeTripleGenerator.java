package org.uu.nl.embedding.kale.util;

import java.util.Random;

import org.uu.nl.embedding.kale.struct.KaleMatrix;
import org.uu.nl.embedding.kale.struct.KaleTriple;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class NegativeTripleGenerator {
	public KaleTriple PositiveTriple;
	public int iNumberOfEntities;
	public int iNumberOfRelation;
	private int[] nonEmptyVerts;
	private KaleMatrix matrixE;
	
	public NegativeTripleGenerator(KaleTriple inPositiveTriple,
			int inNumberOfEntities, int inNumberOfRelation) {
		PositiveTriple = inPositiveTriple;
		iNumberOfEntities = inNumberOfEntities;
		iNumberOfRelation = inNumberOfRelation;
	}
	
	/*public NegativeTripleGenerator(Triple inPositiveTriple,
			int inNumberOfEntities, int inNumberOfRelation,
			int[] nonEmptyVerts) {
		
		PositiveTriple = inPositiveTriple;
		iNumberOfEntities = inNumberOfEntities;
		iNumberOfRelation = inNumberOfRelation;
		this.nonEmptyVerts = nonEmptyVerts;
	}*/
	
	public NegativeTripleGenerator(KaleTriple inPositiveTriple,
			int inNumberOfEntities, int inNumberOfRelation,
			KaleMatrix matrixE) {
		
		PositiveTriple = inPositiveTriple;
		iNumberOfEntities = inNumberOfEntities;
		iNumberOfRelation = inNumberOfRelation;
		this.matrixE = matrixE;
	}
	
	public KaleTriple generateHeadNegTriple() throws Exception {
		int iPosHead = PositiveTriple.head();
		int iPosTail = PositiveTriple.tail();
		int iPosRelation = PositiveTriple.relation();
		Random random = new Random();

		//int interval = this.nonEmptyVerts.length;
		int interval = this.matrixE.rows();
		int iNegHead = iPosHead;
		
		KaleTriple NegativeTriple = new KaleTriple(iNegHead, iPosRelation, iPosTail);
		while (iNegHead == iPosHead) {
			iNegHead = this.matrixE.getIdByRow(random.nextInt(interval));
			NegativeTriple = new KaleTriple(iNegHead, iPosRelation, iPosTail);
		}
		return NegativeTriple;
	}
	
	public KaleTriple generateTailNegTriple() throws Exception {
		Random random = new Random();
		
		int iPosHead = PositiveTriple.head();
		int iPosTail = PositiveTriple.tail();
		int iPosRelation = PositiveTriple.relation();

		//int interval = this.nonEmptyVerts.length;
		int interval = this.matrixE.rows();
		int iNegTail = iPosTail;
		
		KaleTriple NegativeTriple = new KaleTriple(iPosHead, iPosRelation, iNegTail);
		while (iNegTail == iPosTail) {
			iNegTail = this.matrixE.getIdByRow(random.nextInt(interval));
			NegativeTriple = new KaleTriple(iPosHead, iPosRelation, iNegTail);
		}
		return NegativeTriple;
	}
	
	public KaleTriple generateRelNegTriple(KaleMatrix matrixR) throws Exception {
		Random random = new Random();
		
		int iPosHead = PositiveTriple.head();
		int iPosTail = PositiveTriple.tail();
		int iPosRelation = PositiveTriple.relation();
		
		int interval = matrixR.rows();
		
		int iNegRelation = iPosRelation;
		KaleTriple NegativeTriple = new KaleTriple(iPosHead, iNegRelation, iPosTail);
		while (iNegRelation == iPosRelation) {
			iNegRelation = this.matrixE.getIdByRow(random.nextInt(interval));
			//iNegRelation = (int)(random.nextInt(iNumberOfRelation));
			NegativeTriple = new KaleTriple(iPosHead, iNegRelation, iPosTail);
		}
		return NegativeTriple;
	}
}