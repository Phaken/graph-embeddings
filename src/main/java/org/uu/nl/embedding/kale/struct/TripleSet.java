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
	public ArrayList<KaleTriple> pTriple = null;
	public HashMap<String, Boolean> pTripleStr = null;
	/*
	 * STARTTEMP
	 */
	private int[][] outverts;
	/*
	 * ENDTEMP
	 */
	
	public TripleSet() {
		pTripleStr = new HashMap<String, Boolean>();
		pTriple = new ArrayList<KaleTriple>();
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
		pTriple = new ArrayList<KaleTriple>();
		addArrayTriples(arTripleSet, true);
	}
	
	public TripleSet(int iEntities, int iRelations) throws Exception {
		this.iNumberOfEntities = iEntities;
		this.iNumberOfRelations = iRelations;
		pTriple = new ArrayList<KaleTriple>();
	}

	public TripleSet(final String fnLoadFile, int iEntities, int iRelations) throws Exception {
		pTriple = new ArrayList<KaleTriple>();
		load(fnLoadFile);

		this.iNumberOfEntities = iEntities;
		this.iNumberOfRelations = iRelations;
	}

	/*
	 * STARTTEMP
	 */
	public void setOutverts(final int[][] outverts) {
		this.outverts = outverts;
	}
	/*
	 * ENDTEMP
	 */
	
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
	
	public KaleTriple get(int iID) throws Exception {
		if (iID < 0) {
			throw new Exception("getTriple error in TripleSet: ID out of range. Received: " + iID);
		}
		return pTriple.get(iID);
	}
	
	/**
	 * 
	 * @param triples
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void addTriples(final KaleTriple[] triples) throws Exception {
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
	public void addTriples(final KaleTriple triple) throws Exception {
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
	 * @param triples
	 * @throws Exception
	 * @author Euan Westenbroek
	 */
	public void addTriple(final KaleTriple triple) {
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
		if (isInit) { pTriple = new ArrayList<KaleTriple>(); }
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
			int iRelation = iTriple[1];
			int iTail = iTriple[2];
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
			this.pTriple.add(new KaleTriple(iHead, iRelation, iTail));
		}
	}
	
	public void load(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));
		pTriple = new ArrayList<KaleTriple>();
		
		String line = "";
		try {
			
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
				int iRelation = Integer.parseInt(tokens[1]);
				int iTail = Integer.parseInt(tokens[2]);
				if (iHead < 0) {
					throw new Exception("Loading error in TripleSet: head entity ID out of range.");
				}
				if (iTail < 0) {
					throw new Exception("Loading error in TripleSet: tail entity ID out of range.");
				}
				if (iRelation < 0) {
					throw new Exception("Loading error in TripleSet: relation ID out of range.");
				}
				pTriple.add(new KaleTriple(iHead, iRelation, iTail));
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
		pTriple = new ArrayList<KaleTriple>();
		
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
				int iRelation = Integer.parseInt(tokens[1]);
				int iTail = Integer.parseInt(tokens[2]);
				if (iHead < 0) {
					throw new Exception("Loading error in TripleSet: head entity ID out of range");
				}
				if (iTail < 0) {
					throw new Exception("Loading error in TripleSet: tail entity ID out of range");
				}
				if (iRelation < 0) {
					throw new Exception("Loading error in TripleSet: relation ID out of range");
				}
				pTriple.add(new KaleTriple(iHead, iRelation, iTail));
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
		TreeMap<Double, KaleTriple> tmpMap = new TreeMap<Double, KaleTriple>();
		for (int iID = 0; iID < iNumberOfTriples; iID++) {
			int i = pTriple.get(iID).head();
			int k = pTriple.get(iID).relation();
			int j = pTriple.get(iID).tail();
			tmpMap.put(Math.random(), new KaleTriple(i, k, j));
		}
		
		pTriple = new ArrayList<KaleTriple>();
		Iterator<Double> iterValues = tmpMap.keySet().iterator();
		while (iterValues.hasNext()) {
			double dRand = iterValues.next();
			KaleTriple trip = tmpMap.get(dRand);
			pTriple.add(new KaleTriple(trip.head(), trip.relation(), trip.tail()));
		}
		iNumberOfTriples = pTriple.size();
		tmpMap.clear();
	}
	
	@Override
	public String toString() {
		String res = "";
		for (KaleTriple kt : this.pTriple) {
			res += kt.toString() + "\n";
		}
		return res;
	}
}