package org.uu.nl.embedding.kale.struct;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.kale.util.StringSplitter;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 *
 */
public class TripleSet {
    private final static Logger logger = Logger.getLogger("TripleSet");
    
	private int iNumberOfEntities;
	private int iNumberOfRelations;
	private int iNumberOfTriples;
	public ArrayList<Triple> pTriple = null;
	public HashMap<String, Boolean> pTripleStr = null;
	
	public TripleSet() {
		pTripleStr = new HashMap<String, Boolean>();
	}
	
	/**
	 * 
	 * @param arTripleSet
	 * @param iNumGraphEntities
	 * @param iNumGraphRelations
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public TripleSet(final int[][] arTripleSet, final int iNumGraphEntities,
			int iNumGraphRelations) throws Exception {
		
		this.iNumberOfEntities = iNumGraphEntities;
		this.iNumberOfRelations = iNumGraphRelations;
		this.iNumberOfTriples = arTripleSet.length;
		addArrayTriples(arTripleSet, true);
	}
	
	public TripleSet(int iEntities, int iRelations) throws Exception {
		this.iNumberOfEntities = iEntities;
		this.iNumberOfRelations = iRelations;
	}
	
	public int entities() {
		return iNumberOfEntities;
	}
	
	public int relations() {
		return iNumberOfRelations;
	}
	
	public int nTriples() {
		return iNumberOfTriples;
	}
	
	public HashMap<String, Boolean> tripleSet() {
		return pTripleStr;
	}
	
	public Triple get(int iID) throws Exception {
		if (iID < 0 || iID >= iNumberOfTriples) {
			throw new Exception("getTriple error in TripleSet: ID out of range");
		}
		return pTriple.get(iID);
	}
	
	/**
	 * 
	 * @param triples
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void addTriples(final Triple[] triples) throws Exception {
		// Loop through arrays and construct Triples from them.
		for (int i = 0; i < triples.length; i++) {
			addTriples(triples[i]);
		}
	}
	
	/**
	 * 
	 * @param triples
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void addTriples(final Triple triple) throws Exception {
		// Check for valid values.
		if (triple.head() < 0) {
			throw new Exception("Loading error in TripleSet: head entity ID out of range");
		}
		if (triple.tail() < 0) {
			throw new Exception("Loading error in TripleSet: tail entity ID out of range");
		}
		if (triple.relation() < 0) {
			throw new Exception("Loading error in TripleSet: relation ID out of range");
		}
		this.pTriple.add(triple);
	}
	
	/**
	 * 
	 * @param arTripleSet
	 * @param isInit
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void addArrayTriples(final int[][] arTripleSet, final boolean isInit) throws Exception {
		if (isInit) { pTriple = new ArrayList<Triple>(); }
		int[] iTriple;
		
		// Loop through arrays and construct Triples from them.
		for (int i = 0; i < arTripleSet.length; i++) {
			iTriple = arTripleSet[i];
			
			// Check for valid format.
			String strTriple = "";
			if (iTriple.length != 3) {
				for (int token : iTriple) strTriple += token + " - ";
				throw new Exception("Loading error in TripleSet: data format incorrect. "
						+ "Expected 3, but received " + iTriple.length + ". With the following values:\n"
						+ strTriple);
			}
			int iHead = iTriple[0];
			int iTail = iTriple[1];
			int iRelation = iTriple[2];
			// Check for valid values.
			if (iHead < 0) {
				throw new Exception("Loading error in TripleSet: head entity ID out of range");
			}
			if (iTail < 0) {
				throw new Exception("Loading error in TripleSet: tail entity ID out of range");
			}
			if (iRelation < 0) {
				throw new Exception("Loading error in TripleSet: relation ID out of range");
			}
			this.pTriple.add(new Triple(iHead, iTail, iRelation));
		}
	}
	
	public void load(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));
		pTriple = new ArrayList<Triple>();
		
		String line = "";
		try {
			/*while (((line = reader.readLine()) != null) &&
					((line.contains("#")) || (line.toLowerCase().contains("method:")))) {
				/*Do nothing.
				//System.out.println("#Commented line -> Skipped.");
			}*/
			
			while ((line = reader.readLine()) != null) {
				
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				
				String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
						.split("\t ", line));
				if (tokens.length != 3) {
					String str = "";
					for (String token : tokens) str += token + " ";
					throw new Exception("Loading error in TripleSet: data format incorrect. "
							+ "Expected 3, but received " + tokens.length + ". With the following values:\n"
							+ str);
				}
				int iHead = Integer.parseInt(tokens[0]);
				int iTail = Integer.parseInt(tokens[2]);
				int iRelation = Integer.parseInt(tokens[1]);
				if (iHead < 0) {
					throw new Exception("Loading error in TripleSet: head entity ID out of range.");
				}
				if (iTail < 0) {
					throw new Exception("Loading error in TripleSet: tail entity ID out of range.");
				}
				if (iRelation < 0) {
					throw new Exception("Loading error in TripleSet: relation ID out of range.");
				}/*
				if (iHead < 0 || iHead >= iNumberOfEntities) {
					throw new Exception("Loading error in TripleSet: head entity ID out of range.\n" + 
							"Expected positive number up to: " + iNumberOfEntities + ", instead received: " + iHead);
				}
				if (iTail < 0 || iTail >= iNumberOfEntities) {
					throw new Exception("Loading error in TripleSet: tail entity ID out of range.\n" + 
							"Expected positive number up to: " + iNumberOfEntities + ", instead received: " + iTail);
				}
				if (iRelation < 0 || iRelation >= iNumberOfRelations) {
					throw new Exception("Loading error in TripleSet: relation ID out of range.\n" +
							"Expected positive number up to: " + iNumberOfRelations + ", instead received: " + iRelation);
				}*/
				pTriple.add(new Triple(iHead, iTail, iRelation));
			}
		} catch (Exception e) { e.printStackTrace(); }
		
		iNumberOfTriples = pTriple.size();
		reader.close();
	}
	
	public void loadStr(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));
		
		String line = "";
		while ((line = reader.readLine()) != null) {
			pTripleStr.put(line.trim(), true);
		}
		reader.close();
	}
	
	public void subload(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));
		pTriple = new ArrayList<Triple>();
		
		String line = "";
		//int count=0;
		try {
			while ((line = reader.readLine()) != null) {
				if ((line.contains("#")) || (line.toLowerCase().contains("method:")))
					{ /*System.out.println(line);*/ continue; }
				
				//count++;
				String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
						.split("\t ", line));
				// Check for valid format.
				if (tokens.length != 3) {
					String str = "";
					for (String token : tokens) str += token + " ";
					System.out.println(str);
					throw new Exception("Loading error in TripleSet: data format incorrect. "
							+ "Expected 3, but received " + tokens.length + ". With the following values:\n"
							+ str);
				}
				int iHead = Integer.parseInt(tokens[0]);
				int iTail = Integer.parseInt(tokens[2]);
				int iRelation = Integer.parseInt(tokens[1]);
				if (iHead < 0) {
					throw new Exception("Loading error in TripleSet: head entity ID out of range");
				}
				if (iTail < 0) {
					throw new Exception("Loading error in TripleSet: tail entity ID out of range");
				}
				if (iRelation < 0) {
					throw new Exception("Loading error in TripleSet: relation ID out of range");
				}
				pTriple.add(new Triple(iHead, iTail, iRelation));
				/*
				if(count==1000){
					break;
				}*/
			}
		} catch (Exception e) { e.printStackTrace(); }
		
		iNumberOfTriples = pTriple.size();
		reader.close();
	}
	
	
	public void randomShuffle() {
		TreeMap<Double, Triple> tmpMap = new TreeMap<Double, Triple>();
		for (int iID = 0; iID < iNumberOfTriples; iID++) {
			int i = pTriple.get(iID).head();
			int j = pTriple.get(iID).tail();
			int k = pTriple.get(iID).relation();
			tmpMap.put(Math.random(), new Triple(i, j, k));
		}
		
		pTriple = new ArrayList<Triple>();
		Iterator<Double> iterValues = tmpMap.keySet().iterator();
		while (iterValues.hasNext()) {
			double dRand = iterValues.next();
			Triple trip = tmpMap.get(dRand);
			pTriple.add(new Triple(trip.head(), trip.tail(), trip.relation()));
		}
		iNumberOfTriples = pTriple.size();
		tmpMap.clear();
	}
}