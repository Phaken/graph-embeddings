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

import org.apache.log4j.Logger;
import org.uu.nl.embedding.kale.util.StringSplitter;


/**
 * Class imported from iieir-km / KALE on GitHub
 * (https://github.com/iieir-km/KALE/tree/817474edb0da54a76b562bed2328e96284557b87)
 * @author Euan Westenbroek
 */
public class OrigKaleMatrix {

    private static Logger logger = Logger.getLogger("KaleMatrix");
	
	private int[][] pNodeID = null;
	private double[][] pData = null;
	private double[][] pSumData = null;
	private int iNumberOfRows;
	private int iNumberOfColumns;
	//private ArrayList<HashMap<Integer, Integer>> neighborIdxMap;
	private HashMap<Integer, Integer> nodeRowIdx;
	private HashMap<Integer, HashMap<Integer, Integer>> neighborIdxMap;
	
	/*
	public KaleMatrix(int iRows, int iColumns) {
		pData = new double[iRows][];
		pSumData = new double[iRows][];
		for (int i = 0; i < iRows; i++) {
			pData[i] = new double[iColumns];
			pSumData[i] = new double[iColumns];
			for (int j = 0; j < iColumns; j++) {
				pData[i][j] = 0.0;
				pSumData[i][j] = 0.0;
			}
		}
		iNumberOfRows = iRows;
		iNumberOfColumns =  iColumns;
		//neighborIdxMap = new ArrayList<HashMap<Integer, Integer>>(iRows);
	}*/
	
	public OrigKaleMatrix(int iRows, int iColumns) {
		pNodeID = new int[iRows][];
		pData = new double[iRows][];
		pSumData = new double[iRows][];
		for (int i = 0; i < iRows; i++) {
			pNodeID[i] = new int[iColumns];
			pData[i] = new double[iColumns];
			pSumData[i] = new double[iColumns];
			for (int j = 0; j < iColumns; j++) {
				pNodeID[i][j] = 0;
				pData[i][j] = 0.0;
				pSumData[i][j] = 0.0;
			}
		}
		iNumberOfRows = iRows;
		iNumberOfColumns =  iColumns;
		//neighborIdxMap = new ArrayList<HashMap<Integer, Integer>>(iRows);
		nodeRowIdx = new HashMap<Integer, Integer>();
		neighborIdxMap = new HashMap<Integer, HashMap<Integer, Integer>>(iRows);
	}
	
	public int rows() {
		return iNumberOfRows;
	}
	
	public int columns() {
		return iNumberOfColumns;
	}
	
	public double getNeighbor(int i, int j) throws Exception {
		if (i < 0 || !nodeRowIdx.containsKey(i)) {
			throw new Exception("get error in KaleMatrix: RowID out of range. Received: " + i);
		}
		if (j < 0 /*|| (!neighborIdxMap.get(i).containsKey(j))*/) {
			throw new Exception("get error in KaleMatrix: ColumnID out of range. Received: " + j);
		}
		if (neighborIdxMap.get(i).containsKey(j)) return pData[nodeRowIdx.get(i)][neighborIdxMap.get(i).get(j)];
		else return 0d;
	}
	
	public void setNeighbor(int i, int j, double dValue) throws Exception {
		if (i < 0) {
			throw new Exception("set error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || ( (!neighborIdxMap.get(i).containsKey(j)) && neighborIdxMap.get(i).size() >= iNumberOfColumns) ) {
			throw new Exception("set error in KaleMatrix: ColumnID out of range");
		}
		
		if (!neighborIdxMap.containsKey(i)) {
			int rowIdx = nodeRowIdx.size();
			nodeRowIdx.put(i, rowIdx);
			HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
			map.put(j, 0);
			pNodeID[rowIdx][map.get(j)] = j;
			neighborIdxMap.put(i, map);
			pData[rowIdx][neighborIdxMap.get(i).get(j)] = dValue;
			
		} else if (!neighborIdxMap.get(i).containsKey(j)) {
			neighborIdxMap.get(i).put(j, neighborIdxMap.get(i).size());
			pNodeID[nodeRowIdx.get(i)][neighborIdxMap.get(i).size()] = j;
			pData[nodeRowIdx.get(i)][neighborIdxMap.get(i).get(j)] = dValue;
			
		} else {
			pData[nodeRowIdx.get(i)][neighborIdxMap.get(i).get(j)] = dValue;
		}
	}
	
	public void addValue(int i, int j, double dValue) throws Exception {
		if (i < 0) {
			throw new Exception("add error in KaleMatrix: RowID out of range");
		}
		if (j < 0) {
			throw new Exception("add error in KaleMatrix: ColumnID out of range");
		}
		
		if (!neighborIdxMap.containsKey(i)) {
			int rowIdx = nodeRowIdx.size();
			nodeRowIdx.put(i, rowIdx);
			HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
			map.put(j, 0);
			pNodeID[rowIdx][map.get(j)] = j;
			neighborIdxMap.put(i, map);
			pData[rowIdx][neighborIdxMap.get(i).get(j)] = dValue;
			
		} else if (!neighborIdxMap.get(i).containsKey(j)) {
			neighborIdxMap.get(i).put(j, neighborIdxMap.get(i).size());
			pNodeID[nodeRowIdx.get(i)][neighborIdxMap.get(i).size()] = j;
			pData[nodeRowIdx.get(i)][neighborIdxMap.get(i).get(j)] = dValue;
			
		} else {
			pData[nodeRowIdx.get(i)][neighborIdxMap.get(i).get(j)] += dValue;
		}
	}
	
	public void accumulatedByGradNeighbor(int i, int j) throws Exception {
		if (i < 0 || nodeRowIdx.get(i) >= iNumberOfRows) {
			throw new Exception("add error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || (!neighborIdxMap.get(i).containsKey(j))) {
			throw new Exception("add error in KaleMatrix: ColumnID out of range");
		}
		int idxJ = neighborIdxMap.get(i).get(j);
		pSumData[nodeRowIdx.get(i)][idxJ] += pData[nodeRowIdx.get(i)][idxJ] * pData[nodeRowIdx.get(i)][idxJ];
	}	
	
	
	
	
	
	public double get(int i, int j) throws Exception {
		logger.info("WARNING: Wrong get method used.");
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("get error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("get error in KaleMatrix: ColumnID out of range");
		}
		return pData[i][j];
	}
	
	public void set(int i, int j, double dValue) throws Exception {
		logger.info("WARNING: Wrong set method used.");
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("set error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("set error in KaleMatrix: ColumnID out of range");
		}
		pData[i][j] = dValue;
	}
	
	
	public void setToValue(double dValue) {
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				pData[i][j] = dValue;
			}
		}
	}
	
	public void setToRandom() {
		Random rd = new Random(123);
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				double dValue = rd.nextDouble();
				pData[i][j] = 2.0 * dValue - 1.0;
			}
		}
	}
	
	public double getSum(int i, int j) throws Exception {
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("get error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("get error in KaleMatrix: ColumnID out of range");
		}
		return pSumData[i][j];
	}
	
	public void add(int i, int j, double dValue) throws Exception {
		logger.info("WARNING: Wrong add method used.");
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("add error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("add error in KaleMatrix: ColumnID out of range");
		}
		pData[i][j] += dValue;
	}
	
	public void normalize() {
		double dNorm = 0.0;
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				dNorm += pData[i][j] * pData[i][j];
			}
		}
		dNorm = Math.sqrt(dNorm);
		if (dNorm != 0.0) {
			for (int i = 0; i < iNumberOfRows; i++) {
				for (int j = 0; j < iNumberOfColumns; j++) {
					pData[i][j] /= dNorm;
				}
			}
		}
	}
	
	public void normalizeByRow() {
		for (int i = 0; i < iNumberOfRows; i++) {
			double dNorm = 0.0;
			for (int j = 0; j < iNumberOfColumns; j++) {
				dNorm += pData[i][j] * pData[i][j];
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {
				for (int j = 0; j < iNumberOfColumns; j++) {
					pData[i][j] /= dNorm;
				}
			}
		}
	}
	
	public void rescaleByRow() {
		for (int i = 0; i < iNumberOfRows; i++) {
			double dNorm = 0.0;
			for (int j = 0; j < iNumberOfColumns; j++) {
				dNorm += pData[i][j] * pData[i][j];
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {
				for (int j = 0; j < iNumberOfColumns; j++) {
					pData[i][j] *= Math.min(1.0, 1.0/dNorm);
				}
			}
		}
	}
	
	public void normalizeByColumn() {
		for (int j = 0; j < iNumberOfColumns; j++) {
			double dNorm = 0.0;
			for (int i = 0; i < iNumberOfRows; i++) {
				dNorm += pData[i][j] * pData[i][j];
			}
			dNorm = Math.sqrt(dNorm);
			if (dNorm != 0.0) {
				for (int i = 0; i < iNumberOfRows; i++) {
					pData[i][j] /= dNorm;
				}
			}
		}
	}
	
	public void accumulatedByGrad(int i, int j) throws Exception {
		logger.info("WARNING: Wrong accumulatedByGrad() method used.");
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("add error in KaleMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("add error in KaleMatrix: ColumnID out of range");
		}
		pSumData[i][j] += pData[i][j] * pData[i][j];
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
		for (int i = 0; i < iNumberOfRows; i++) {
			pData[i] = null;
		}
		pData = null;
		iNumberOfRows = 0;
		iNumberOfColumns = 0;
	}
	
	public void resetToZero() {
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				pSumData[i][j] = 0.0;
			}
		}
	}
	
	@Override
	public String toString() {
		String res = "";
		int row, col;
		
		for (Map.Entry entry : this.nodeRowIdx.entrySet()) {
			row = (int) entry.getKey();
			res += "Row: " + row + "| ";

			/*for (Map.Entry entryCol : this.neighborIdxMap.get(row).entrySet()) {
				col = (int) entryCol.getKLey();*/
			for (int j = 0; j < this.pData[row].length; j++) {
				res += "n:" + this.pNodeID[row][j];
				res += "=" + this.pData[row][j] + ", ";
			}
			res += "\n";		
		}
		return res;
	}
}