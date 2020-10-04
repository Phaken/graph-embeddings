package org.uu.nl.embedding.kale.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.kale.model.KaleModel;
import org.uu.nl.embedding.kale.struct.KaleMatrix;
import org.uu.nl.embedding.kale.struct.SparseKaleMatrix;
import org.uu.nl.embedding.kale.struct.TripleSet;
import org.uu.nl.embedding.util.config.Configuration;
import org.uu.nl.embedding.util.rnd.Permutation;

import me.tongfei.progressbar.ProgressBar;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class MetricMonitor {

    private final static Logger logger = Logger.getLogger(MetricMonitor.class);
	
	public TripleSet lstValidateTriples;
	public HashMap<String, Boolean> lstTriples;
	public KaleMatrix MatrixE;
	public KaleMatrix MatrixR;
	public double dMeanRank;
	public double dMRR;
	public double dHits;
	public boolean isGlove;
	
	private int iNumValidations;
	private int iNumManipulations;
	
	public MetricMonitor(TripleSet inLstValidateTriples,
			HashMap<String, Boolean> inlstTriples,
			KaleMatrix inMatrixE,
			KaleMatrix inMatrixR,
			final boolean isGlove) {
		lstValidateTriples = inLstValidateTriples;
		lstTriples = inlstTriples;
		MatrixE = inMatrixE;
		MatrixR = inMatrixR;
		this.isGlove = isGlove;
		//this.isGlove = true;
		this.iNumValidations = 1;
		this.iNumManipulations = 1;
		
		if (this.lstValidateTriples.nTriples() < this.iNumValidations)
			this.iNumValidations = this.lstValidateTriples.nTriples();
		if (MatrixE.rows() < this.iNumManipulations) 
			this.iNumManipulations = MatrixE.rows();
	}
	
	/**
	 * 
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void calculateMetrics() throws Exception {
		if (this.isGlove) calculateMetricsGlove();
		else calculateMetricsDefault();
	}
	
	/**
	 * 
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void calculateMetricsGlove() throws Exception {
		int iNumberOfEntities = MatrixE.rows();
		int iNumberOfFactors = MatrixE.columns();
		
		int accessID;
		Permutation triplePermutation = new Permutation(this.lstValidateTriples.nTriples());
		triplePermutation.shuffle();
		int accessVertID;
		Permutation manipulationPermutation = new Permutation(iNumberOfEntities);
		
		// Initialize variables.
		int iCnt = 0;
		int nextIdx;
		double tripleSum;
		double avgMeanRank = 0.0;
		double avgMRR = 0.0;
		double avgHits = 0d;
		this.dHits = 0d;
		//
		int totalSteps = (this.iNumValidations * (2 * this.iNumManipulations));
		try(ProgressBar pb = Configuration.progressBar("Calculating metrics", totalSteps, "nodes")) {
			logger.info("Initializing metrics calculations.");
			// Loop through triples to be validated.
// #######
			//for (int iID = 0; iID < 1; iID++) {
			for (int iID = 0; iID < this.iNumValidations; iID++) {
				try {
					// Get indices.
					accessID = triplePermutation.randomAccess(iID);
					int iRelationID = this.lstValidateTriples.get(accessID).relation();
					int iSubjectID = this.lstValidateTriples.get(accessID).head();
					int iObjectID = this.lstValidateTriples.get(accessID).tail();
					// Element-wise calculation.
					double dTargetValue = 0.0;
					for (int p = 0; p < iNumberOfFactors; p++) {
						/*
						 * From paper:
						 * ||e_i + r_k - e_j||_1
						 * 
						 * Where e_i, r_k, e_j are the GloVe vector embedding of
						 * head entity, relation, and tail entity respectively.
						 */
						tripleSum = this.MatrixE.getNeighbor(iSubjectID, p) + 
										this.MatrixR.getNeighbor(iRelationID, p) - 
										this.MatrixE.getNeighbor(iObjectID, p);
						dTargetValue -= Math.abs(tripleSum);
					}
					
					int leftID;
					int iLeftRank = 1;
					int iLeftIdentical = 0;
					// Validate left-altered triple
					if (iID % 1000 == 0) logger.info("Looping through head vertices for validation triple: "+iID);
					manipulationPermutation.shuffle();
// ##########					
					//for (int iLeftID = 0; iLeftID < 1; iLeftID++) {
					for (int iLeftID = 0; iLeftID < this.iNumManipulations; iLeftID++) {
						// Get altered entity and skip if its the same as the
						// iSubjectID.
						/*
						 * leftID = this.orderedIdxMap.get(iLeftID);
						 */
						leftID = manipulationPermutation.randomAccess(iLeftID);
						//leftID = iLeftID;
						while (leftID == iSubjectID) leftID = manipulationPermutation.randomAccess(iLeftID);
						
						String negTriple = leftID + "\t" + iRelationID + "\t" +iObjectID;
						
						// Add to triples list if it doesn't already contains
						// this triple.
						double dValue = 0.0;
						if (!this.lstTriples.containsKey(negTriple)){
							for (int p = 0; p < iNumberOfFactors; p++) {
								tripleSum = this.MatrixE.getNeighbor(leftID, p) + 
												this.MatrixR.getNeighbor(iRelationID, p) -
												this.MatrixE.getNeighbor(iObjectID, p);
								dValue -= Math.abs(tripleSum);
							}
							// Increment rank if resulting value is larger than
							// the target value.
							if (dValue > dTargetValue) {
								iLeftRank++;
							} // Increment iLeftIdentical if these values are equal.
							else if (dValue == dTargetValue) {
								iLeftIdentical++;
							}
						}
					}
		
					// Update statistics of current triple.
					double dLeftRank = iLeftRank;
					int iLeftHitsAt10 = 0;
					if (dLeftRank <= 10.0) {
						iLeftHitsAt10 = 1;
					}
					avgMeanRank += dLeftRank;
					avgMRR += 1.0/(double)dLeftRank;
					this.dHits += iLeftHitsAt10;
					iCnt++;
		
					// Repeat for object entity: Validate left-altered triple
					int rightID;
					int iRightRank = 1;
					int iRightIdentical = 0;
					if (accessID % 1000 == 0) logger.info("Looping through tail vertices for validation triple: "+accessID);
					manipulationPermutation.shuffle();
// #####
					//for (int iRightID = 0; iRightID < 1; iRightID++) {
					for (int iRightID = 0; iRightID < this.iNumManipulations; iRightID++) {
						// Get altered entity and skip if its the same as the
						// iObjectID.
						/*
						 * rightID = this.orderedIdxMap.get(iRightID);
						 */
						rightID = manipulationPermutation.randomAccess(iRightID);
						//rightID = iRightID;
						while (rightID == iObjectID) rightID = manipulationPermutation.randomAccess(iRightID);
						
						String negTiple = iSubjectID + "\t" + iRelationID + "\t" + rightID;
						
						// Add to triples list if it doesn't already contains
						// this triple.
						double dValue = 0.0;
						if(!lstTriples.containsKey(negTiple)){
							for (int p = 0; p < iNumberOfFactors; p++) {
								tripleSum = this.MatrixE.getNeighbor(iSubjectID, p) +
												this.MatrixR.getNeighbor(iRelationID, p) -
												this.MatrixE.getNeighbor(rightID, p);
								dValue -= Math.abs(tripleSum);
							}
							// Increment rank if resulting value is larger than
							// the target value.
							if (dValue > dTargetValue) {
								iRightRank++;
							} // Increment iLeftIdentical if these values are equal.
							if (dValue == dTargetValue) {
								iRightIdentical++;
							}
						}
					}
					
					// Update statistics of current triple.
					double dRightRank = iRightRank;
					int iRightHitsAt10 = 0;
					if (dRightRank <= 10.0) {
						iRightHitsAt10 = 1;
					}
					avgMeanRank += dRightRank;
					avgMRR += 1.0/(double)dRightRank;
					this.dHits += iRightHitsAt10;
					iCnt++;
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} finally {
					pb.step();
				}
			} // END this.lstValidateTriples-loop.
		}
		
		// Update object statistics and print sub-results during runtime.
		this.dMRR = avgMRR / (double)(iCnt);
		avgHits = (double)this.dHits / (double)(iCnt);
		System.out.println("avgMRR:" + avgMRR + "\t" + "avgHits:" + avgHits);
		System.out.println("MRR:" + this.dMRR + "\t" + "Hits:" + this.dHits);
	}
	
	public void calculateMetricsDefault() throws Exception {
		int iNumberOfEntities = MatrixE.rows();
		int iNumberOfFactors = MatrixE.columns();
		
		int iCnt = 0;
		double avgMeanRank = 0.0;
		double avgMRR = 0.0;
		double avgHits = 0d;
		this.dHits = 0d;
		for (int iID = 0; iID < lstValidateTriples.nTriples(); iID++) {
			int iRelationID = lstValidateTriples.get(iID).relation();
			int iSubjectID = lstValidateTriples.get(iID).head();
			int iObjectID = lstValidateTriples.get(iID).tail();
			double dTargetValue = 0.0;
			for (int p = 0; p < iNumberOfFactors; p++) {
				dTargetValue -= Math.abs(MatrixE.getNeighbor(iSubjectID, p) + MatrixR.getNeighbor(iRelationID, p) - MatrixE.getNeighbor(iObjectID, p));
			}
			
			int iLeftRank = 1;
			int iLeftIdentical = 0;
			for (int iLeftID = 0; iLeftID < iNumberOfEntities; iLeftID++) {
				double dValue = 0.0;
				String negTriple = iLeftID + "\t" + iRelationID + "\t" +iObjectID;
				if(!this.lstTriples.containsKey(negTriple)){
					for (int p = 0; p < iNumberOfFactors; p++) {
						dValue -= Math.abs(MatrixE.getNeighbor(iLeftID, p) + MatrixR.getNeighbor(iRelationID, p) - MatrixE.getNeighbor(iObjectID, p));
					}
					if (dValue > dTargetValue) {
						iLeftRank++;
					}
					if (dValue == dTargetValue) {
						iLeftIdentical++;
					}
				}

			}
			
			double dLeftRank = iLeftRank;
			int iLeftHitsAt10 = 0;
			if (dLeftRank <= 10.0) {
				iLeftHitsAt10 = 1;
			}
			avgMeanRank += dLeftRank;
			avgMRR += 1.0/(double)dLeftRank;
			this.dHits += iLeftHitsAt10;
			iCnt++;
			
			int iRightRank = 1;
			int iRightIdentical = 0;
			for (int iRightID = 0; iRightID < iNumberOfEntities; iRightID++) {
				double dValue = 0.0;
				String negTiple = iSubjectID + "\t" + iRelationID + "\t" +iRightID;
				if(!lstTriples.containsKey(negTiple)){
					for (int p = 0; p < iNumberOfFactors; p++) {
						dValue -= Math.abs(MatrixE.getNeighbor(iSubjectID, p) + MatrixR.getNeighbor(iRelationID, p) - MatrixE.getNeighbor(iRightID, p));
					}
					if (dValue > dTargetValue) {
						iRightRank++;
					}
					if (dValue == dTargetValue) {
						iRightIdentical++;
					}
				}
			}

			double dRightRank = iRightRank;
			int iRightHitsAt10 = 0;
			if (dRightRank <= 10.0) {
				iRightHitsAt10 = 1;
			}
			avgMeanRank += dRightRank;
			avgMRR += 1.0/(double)dRightRank;
			this.dHits += iRightHitsAt10;
			iCnt++;	
		}
		
		this.dMRR = avgMRR / (double)(iCnt);
		avgHits = (double)this.dHits / (double)(iCnt);
		System.out.println("avgMRR:" + avgMRR + "\t" + "avgHits:" + avgHits);
		System.out.println("MRR:" + this.dMRR + "\t" + "Hits:" + this.dHits);
	}
}
