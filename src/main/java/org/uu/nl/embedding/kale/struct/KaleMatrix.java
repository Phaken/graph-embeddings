package org.uu.nl.embedding.kale.struct;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.kale.util.StringSplitter;
import org.uu.nl.embedding.util.config.Configuration;

import me.tongfei.progressbar.ProgressBar;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 * @author Euan Westenbroek
 */
public class KaleMatrix {

    private static Logger logger = Logger.getLogger(KaleMatrix.class);
	
	private int[][] pNodeID = null;
	private double[][] pData = null;
	private double[][] pSumData = null;
	

	private float[] gloveArray;
	private int[] orderedIDs;
	private int iDim;
	
	private int iNumberOfRows;
	private int iNumberOfColumns;
	private int dictSize;
	private HashMap<Integer, HashMap<Integer, Double>> rowMap;
	private HashMap<Integer, HashMap<Integer, Double>> rowSumMap;
	private ArrayList<Integer> rowPositions;
	
	private boolean isRelationMatrix;
	
	public KaleMatrix(int iRows, int iColumns, int dictSize) {

		this.iNumberOfRows = iRows;
		this.iNumberOfColumns = iColumns;
		this.iDim = iColumns;
		this.dictSize = dictSize;
		
		this.rowMap = new HashMap<Integer, HashMap<Integer, Double>>(iRows);
		this.rowSumMap = new HashMap<Integer, HashMap<Integer, Double>>(iRows);
		this.rowPositions = new ArrayList<Integer>();
		this.isRelationMatrix = false;
	}
	
	public KaleMatrix(int iRows, int iColumns, int dictSize, final boolean isRelationMatrix) {
		this(iRows, iColumns, dictSize);
		if (isRelationMatrix) logger = Logger.getLogger("KaleMatrix-Edges");
		else logger = Logger.getLogger("KaleMatrix-Nodes");
		
		this.isRelationMatrix = isRelationMatrix;
	}
	
	public KaleMatrix(final float[] gloveArray, final int[] orderedIDs,
						final int dim, final boolean isRelationMatrix) throws Exception  {
		
		if (isRelationMatrix) logger = Logger.getLogger("KaleMatrix-Edges");
		else logger = Logger.getLogger("KaleMatrix-Nodes");
		
		
		logger.info("Initializing KaleMatrix.");
		if (gloveArray.length <= 0) throw new Exception("Received invalid GloVe array: "+gloveArray.length);
		if (orderedIDs.length <= 0) throw new Exception("Received invalid GloVe array: "+orderedIDs.length);
		if (dim <= 0) throw new Exception("Received invalid GloVe array: "+dim);

		this.isRelationMatrix = isRelationMatrix;
		this.gloveArray = gloveArray;
		this.orderedIDs = orderedIDs;
		this.iDim = dim;
		logger.info("Arguments declarated:\n"
				+ "\t- number of GloVe values is: "+this.gloveArray.length+"\n"
				+ "\t- dimension is set at: "+this.iDim);
		
		this.rowMap = new HashMap<Integer, HashMap<Integer, Double>>();
		this.rowSumMap = new HashMap<Integer, HashMap<Integer, Double>>();
		this.rowPositions = new ArrayList<Integer>();
		
		fillGloveInMatrix();
	}
	
	private void fillGloveInMatrix() throws Exception {
		logger.info("Start filling matrix with GloVe values.");
		
		try (ProgressBar pb = Configuration.progressBar("KaleMatrix", this.orderedIDs.length, "rows.")) {
			int id, index;
			float value;
			try {
				for (int i = 0; i < this.orderedIDs.length; i++) {
					id = this.orderedIDs[i];
					//if (id % 5000 == 0) System.out.println("KaleMatrix.fillGloveInMatrix() - Adding row for id: "+id);
					for (int j = 0; j < this.iDim; j++) {
						index = ((id * this.iDim) + j);
						//System.out.println("KaleMatrix.fillGloveInMatrix() - Adding at index: "+index);
						value = this.gloveArray[index];
						setNeighbor(i, j, (double) value);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				pb.step();
			}
			
			for (int i = 0; i < this.orderedIDs.length; i++) {
				for (int j = 0; j < this.iDim; j++) {
						accumulatedByGradNeighbor(i, j);
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
		
		this.iNumberOfRows = rowMap.size();
		this.iNumberOfColumns = this.iDim;
		logger.info("Matrix initialized with following dimensions: " + this.iNumberOfRows + "x" +this.iNumberOfColumns);
	}
	
	public int rows() {
		return this.iNumberOfRows;
	}
	
	public int columns() {
		return this.iNumberOfColumns;
	}

	public int dictSize() {
		return this.dictSize;
	}
	
	public void setNeighbor(int i, int j, double dValue) throws Exception {
		if (i < 0) {
			throw new Exception("set error in KaleMatrix: RowID out of range");
		}
		if (j < 0 /*|| ( (!rowMap.get(i).containsKey(j)) && rowMap.get(i).size() >= iNumberOfColumns)*/ ) {
			throw new Exception("set error in KaleMatrix: ColumnID out of range");
		}
		
		try {			
			if (!rowMap.containsKey(i)) {
				HashMap<Integer, Double> map = new HashMap<Integer, Double>();
				map.put(j, dValue);
				rowMap.put(i, map);
				rowPositions.add(i);

				//System.out.println("KaleMatrix.setNeighbor() - New neighbor set.");
				
			} else {
				rowMap.get(i).put(j, dValue);
				//System.out.println("KaleMatrix.setNeighbor() - Neighbor set.");
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public double getNeighbor(int i, int j) throws Exception {
		
		if (i < 0 || !rowMap.containsKey(i)) {
			throw new Exception("get error in KaleMatrix: RowID out of range. Received: " + i);
		}
		if (j < 0 /*|| (!neighborIdxMap.get(i).containsKey(j))*/) {
			throw new Exception("get error in KaleMatrix: ColumnID out of range. Received: " + j);
		}

		if (rowMap.containsKey(i) && rowMap.get(i).containsKey(j)) return rowMap.get(i).get(j);
		else return 0d;
	}
	
	public void addValue(int i, int j, double dValue) throws Exception {
		if (i < 0) {
			throw new Exception("add error in KaleMatrix: RowID out of range");
		}
		if (j < 0 /*|| ( (!rowMap.get(i).containsKey(j)) && rowMap.get(i).size() >= iNumberOfColumns)*/ ) {
			throw new Exception("add error in KaleMatrix: ColumnID out of range");
		}
		
		try {			
			if (!rowMap.containsKey(i)) {
				HashMap<Integer, Double> map = new HashMap<Integer, Double>();
				map.put(j, dValue);
				rowMap.put(i, map);
				rowPositions.add(i);
				
			} else if (!rowMap.get(i).containsKey(j)) {
				rowMap.get(i).put(j, dValue);
				
			} else {
				rowMap.get(i).put(j, rowMap.get(i).get(j)+dValue);
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public void accumulatedByGradNeighbor(int i, int j) throws Exception {
		
		if (i < 0 || !rowMap.containsKey(i)) {
			throw new Exception("get error in KaleMatrix: RowID out of range. Received: " + i);
		}
		if (j < 0 /*|| (!neighborIdxMap.get(i).containsKey(j))*/) {
			throw new Exception("get error in KaleMatrix: ColumnID out of range. Received: " + j);
		}
		
		if (!rowSumMap.containsKey(i) || !rowSumMap.get(i).containsKey(j)) {
			HashMap<Integer, Double> map = new HashMap<Integer, Double>();
			double val = rowMap.get(i).get(j);
			map.put(j, val*val);
			rowMap.put(i, map);
			rowPositions.add(i);
		
		} else {
			double curSum = rowSumMap.get(i).get(j);
			double val = rowMap.get(i).get(j);
			rowSumMap.get(i).put(j, (curSum + (val*val)) );
		}
		
	}
	
	/**
	 * 
	 * @param rowPos
	 * @return
	 */
	public int getIdByRow(final int rowPos) {
		return rowPositions.get(rowPos);
	}
	
	
	public void setToValue(double dValue) {
		HashMap<Integer, Double> map;
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				rowMap.get((int) e1.getKey()).put((int) e2.getKey(), dValue);
			}
		}
		/*
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				rowMap.get(i).put(j, dValue);
			}
		}*/
	}
	
	public void setToRandom() {
		Random rd = new Random(123);
		double dValue, newVal;
		HashMap<Integer, Double> map;
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				dValue = rd.nextDouble();
				newVal = 2.0 * dValue - 1.0;
				rowMap.get((int) e1.getKey()).put((int) e2.getKey(), newVal);
			}
		}
		/*
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				double dValue = rd.nextDouble();
				double newVal = 2.0 * dValue - 1.0;
				rowMap.get(i).put(j,newVal);
			}
		}*/
	}
	
	public double getSum(int i, int j) throws Exception {
		accumulatedByGradNeighbor(i, j);
		
		if (i < 0 || !rowSumMap.containsKey(i)) {
			throw new Exception("add error in KaleMatrix: RowID out of range. Received: " + i);
		}
		if (j < 0 || (!rowSumMap.get(i).containsKey(j))) {
			throw new Exception("get error in KaleMatrix: ColumnID out of range. Received: " + j);
		}
		return rowSumMap.get(i).get(j);
	}
	
	public void normalize() {
		double dNorm = 0.0, val;

		HashMap<Integer, Double> map;
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				val = (double) e2.getValue();
				dNorm += val * val;
			}
		}
		dNorm = Math.sqrt(dNorm);
		if (dNorm != 0.0) {

			for (Map.Entry e1 : this.rowMap.entrySet()) {
				map = (HashMap<Integer, Double>) e1.getValue();
				for (Map.Entry e2 : map.entrySet()) {
					val = (double) e2.getValue();
					rowMap.get((int) e1.getKey()).put((int) e2.getKey(), (val/dNorm));
				}
			}
		}
		/*
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				dNorm += rowMap.get(i).get(j) * rowMap.get(i).get(j);
			}
		}
		dNorm = Math.sqrt(dNorm);
		if (dNorm != 0.0) {
			for (int i = 0; i < iNumberOfRows; i++) {
				for (int j = 0; j < iNumberOfColumns; j++) {
					rowMap.get(i).put(j, (rowMap.get(i).get(j)/dNorm));
				}
			}
		}*/
	}
	
	public void normalizeByRow() {
		HashMap<Integer, Double> map;
		double dNorm, val;
		
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			dNorm = 0.0;
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				val = (double) e2.getValue();
				dNorm += val * val;
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {

				for (Map.Entry e2 : map.entrySet()) {
					val = rowMap.get((int) e1.getKey()).get((int) e2.getKey());
					rowMap.get((int) e1.getKey()).put((int) e2.getKey(), (val/dNorm));
				}
			}
		}
		/*
		for (int i = 0; i < iNumberOfRows; i++) {
			double dNorm = 0.0;
			for (int j = 0; j < iNumberOfColumns; j++) {
				dNorm += rowMap.get(i).get(j) * rowMap.get(i).get(j);
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {
				for (int j = 0; j < iNumberOfColumns; j++) {
					rowMap.get(i).put(j, (rowMap.get(i).get(j)/dNorm));
				}
			}
		}*/
	}
	
	public void rescaleByRow() {
		HashMap<Integer, Double> map;
		double dNorm, val;
		
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			dNorm = 0.0;
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				val = (double) e2.getValue();
				dNorm += val * val;
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {

				for (Map.Entry e2 : map.entrySet()) {
					val = rowMap.get((int) e1.getKey()).get((int) e2.getKey());
					rowMap.get((int) e1.getKey()).put((int) e2.getKey(), (val*Math.min(1.0, 1.0/dNorm)));
				}
			}
		}
		
		/*
		for (int i = 0; i < iNumberOfRows; i++) {
			double dNorm = 0.0;
			for (int j = 0; j < iNumberOfColumns; j++) {
				dNorm += rowMap.get(i).get(j) * rowMap.get(i).get(j);
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {
				for (int j = 0; j < iNumberOfColumns; j++) {
					rowMap.get(i).put(j, (rowMap.get(i).get(j)*Math.min(1.0, 1.0/dNorm)) );
				}
			}
		}*/
	}
	
	public void normalizeByColumn() {
		HashMap<Integer, Double> map;
		double dNorm, val;
		
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			dNorm = 0.0;
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				val = (double) e2.getValue();
				dNorm += val * val;
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {

				for (Map.Entry e2 : map.entrySet()) {
					val = rowMap.get((int) e1.getKey()).get((int) e2.getKey());
					rowMap.get((int) e1.getKey()).put((int) e2.getKey(), (val/dNorm));
				}
			}
		}
		/*
		for (int j = 0; j < iNumberOfColumns; j++) {
			double dNorm = 0.0;
			for (int i = 0; i < iNumberOfRows; i++) {
				dNorm += rowMap.get(i).get(j) * rowMap.get(i).get(j);
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {
				for (int i = 0; i < iNumberOfRows; i++) {
					rowMap.get(i).put(j, (rowMap.get(i).get(j)/dNorm));
				}
			}
		}*/
	}
	
	public boolean load(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));
		
		String line = "";
		line = reader.readLine();
		String[] first_line = StringSplitter.RemoveEmptyEntries(StringSplitter
				.split(":; ", line));
		if (iNumberOfRows != Integer.parseInt(first_line[1]) || 
				iNumberOfColumns != Integer.parseInt(first_line[3])) {
			throw new Exception("load error in KaleMatrix: row/column number incorrect");
		}
		
		int iRowID = 0;
		while ((line = reader.readLine()) != null) {
			String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
					.split("\t ", line));
			if (iRowID < 0 || iRowID >= iNumberOfRows) {
				throw new Exception("load error in KaleMatrix: RowID out of range");
			}
			if (tokens.length != iNumberOfColumns) {
				throw new Exception("load error in KaleMatrix: ColumnID out of range");
			}
			for (int iColumnID = 0; iColumnID < tokens.length; iColumnID++) {
				pData[iRowID][iColumnID] = Double.parseDouble(tokens[iColumnID]);
			}
			iRowID++;
		}
		
		reader.close();
		return true;
	}
	
	public void output(String fnOutput) throws Exception {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(fnOutput), "UTF-8"));
		
		writer.write("iNumberOfRows: " + iNumberOfRows + "; iNumberOfColumns: " + iNumberOfColumns + "\n");
		for (int i = 0; i < iNumberOfRows; i++) {
			writer.write((pData[i][0] + " ").trim());
			for (int j = 1; j < iNumberOfColumns; j++) {
				writer.write("\t" + (pData[i][j] + " ").trim());
			}
			writer.write("\n");
		}
		
		writer.close();
	}
	
	public void releaseMemory() {
		rowMap = new HashMap<Integer, HashMap<Integer, Double>>(this.iNumberOfRows);
		rowSumMap = new HashMap<Integer, HashMap<Integer, Double>>(this.iNumberOfRows);
	}
	
	public void resetToZero() {
		double dValue, newVal;
		HashMap<Integer, Double> map;
		for (Map.Entry e1 : this.rowMap.entrySet()) {
			map = (HashMap<Integer, Double>) e1.getValue();
			for (Map.Entry e2 : map.entrySet()) {
				rowSumMap.get((int) e1.getKey()).put((int) e2.getKey(), 0d);
			}
		}
	}
	
	public boolean containsKey(final int key) {
		return this.rowMap.containsKey(key);
	}
	
	@Override
	public String toString() {
		String res = "";
		int row, col;
		
		for (Map.Entry entry : this.rowMap.entrySet()) {
			row = (int) entry.getKey();
			res += "Row: " + row + "| ";

			for (Map.Entry entryCol : this.rowMap.get(row).entrySet()) {
				res += "n:" + (int) entryCol.getKey();
				res += "=" + (double) entryCol.getValue() + ", ";
			}
			res += "\n";		
		}
		return res;
	}
}