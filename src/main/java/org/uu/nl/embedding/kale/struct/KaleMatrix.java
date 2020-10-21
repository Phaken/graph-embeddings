package org.uu.nl.embedding.kale.struct;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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

	private double[][] pData = null;
	private double[][] pSumData = null;
	private boolean arrayBased = false;
	

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

	/**
	 *
	 * @param iRows
	 * @param iColumns
	 */
	public KaleMatrix(int iRows, int iColumns) {

		this.iNumberOfRows = iRows;
		this.iNumberOfColumns = iColumns;
		this.iDim = iColumns;
		
		this.rowMap = new HashMap<Integer, HashMap<Integer, Double>>(iRows);
		this.rowSumMap = new HashMap<Integer, HashMap<Integer, Double>>(iRows);
		this.rowPositions = new ArrayList<Integer>();
		this.isRelationMatrix = false;
	}
	
	public KaleMatrix(int iRows, int iColumns, final boolean isRelationMatrix) {
		this(iRows, iColumns);
		if (isRelationMatrix) logger = Logger.getLogger("KaleMatrix-Edges");
		else logger = Logger.getLogger("KaleMatrix-Nodes");
		
		this.isRelationMatrix = isRelationMatrix;
	}

	public KaleMatrix(final float[] gloveArray, final int[] orderedIDs,
					  final int dim, final boolean isRelationMatrix, final boolean asArrays) throws Exception  {

		if (isRelationMatrix) logger = Logger.getLogger("KaleMatrix-Edges");
		else logger = Logger.getLogger("KaleMatrix-Nodes");

		logger.info("Initializing KaleMatrix, array based.");
		if (gloveArray.length <= 0) throw new Exception("Received invalid GloVe array: "+gloveArray.length);
		if (orderedIDs.length <= 0) throw new Exception("Received invalid GloVe array: "+orderedIDs.length);
		if (dim <= 0) throw new Exception("Received invalid GloVe array: "+dim);

		this.isRelationMatrix = isRelationMatrix;
		this.arrayBased = asArrays;
		this.gloveArray = gloveArray;
		this.orderedIDs = orderedIDs;
		this.iDim = dim;
		logger.info("Arguments declarated:\n"
				+ "\t- number of GloVe values is: "+this.gloveArray.length+"\n"
				+ "\t- dimension is set at: "+this.iDim);

		pData = new double[orderedIDs.length][];
		pSumData = new double[orderedIDs.length][];
		int iRows = orderedIDs.length;
		int iColumns = dim;

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
		logger.info("Arguments declared:\n"
				+ "\t- number of GloVe values is: "+this.gloveArray.length+"\n"
				+ "\t- dimension is set at: "+this.iDim);
		
		this.rowMap = new HashMap<Integer, HashMap<Integer, Double>>();
		this.rowSumMap = new HashMap<Integer, HashMap<Integer, Double>>();
		this.rowPositions = new ArrayList<Integer>();
		
		fillGloveInMatrix();
	}

	private void fillGloveInMatrix() throws Exception {
		logger.info("Start filling matrix with GloVe values.");

		int id, index;
		float value;
			for (int i = 0; i < this.orderedIDs.length; i++) {
				id = this.orderedIDs[i];
				//if (id % 5000 == 0) System.out.println("KaleMatrix.fillGloveInMatrix() - Adding row for id: "+id);
				for (int j = 0; j < this.iDim; j++) {
					index = ((id * this.iDim) + j);
					//System.out.println("KaleMatrix.fillGloveInMatrix() - Adding at index: "+index);
					value = this.gloveArray[index];
					setNeighbor(i, j, (double) value);
				}
				rowPositions.add(id);
			}

		for (int i = 0; i < this.orderedIDs.length; i++) {
			for (int j = 0; j < this.iDim; j++) {
				accumulatedByGradNeighbor(i, j);
			}
		}

		this.iNumberOfRows = rowMap.size();
		this.iNumberOfColumns = this.iDim;
		logger.info("Matrix initialized with following dimensions: " + this.iNumberOfRows + "x" +this.iNumberOfColumns);
	}
	
	/*private void fillGloveInMatrix(boolean withProgression) throws Exception {
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
					rowPositions.add(id);
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
	}*/
	
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

				//System.out.println("KaleMatrix.setNeighbor() - New neighbor set.");
				
			} else {
				rowMap.get(i).put(j, dValue);
				//System.out.println("KaleMatrix.setNeighbor() - Neighbor set.");
			}
			
			if (!rowSumMap.containsKey(i)) {
				HashMap<Integer, Double> map = new HashMap<Integer, Double>();
				map.put(j, 0d);
				rowSumMap.put(i, map);
			} else if (!rowSumMap.get(i).containsKey(j)) {
				rowSumMap.get(i).put(j, 0d);
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
		
		if (!rowSumMap.containsKey(i)) {
			HashMap<Integer, Double> map = new HashMap<Integer, Double>();
			double val = rowMap.get(i).get(j);
			map.put(j, val*val);
			rowSumMap.put(i, map);
			
		} else if (!rowSumMap.get(i).containsKey(j)) {
			double val = rowMap.get(i).get(j);
			rowSumMap.get(i).put(j, val*val);
			
		} else {
			double curSum = rowSumMap.get(i).get(j);
			double val = rowMap.get(i).get(j);
			rowSumMap.get(i).put(j, (curSum + (val*val)) );
		}
		
	}
	
	/**
	 * Makes sure that the vertex or predicate ID is the right one based on the row.
	 * @param rowPos
	 * @return
	 */
	public int getIdByRow(final int rowPos) {
		//return rowPositions.get(rowPos);
		return this.orderedIDs[rowPos];
	}
	
	public KaleMatrix generateGradientMatrix() throws Exception {
		float[] zeroArray = new float[this.gloveArray.length];
		KaleMatrix gradMat = new KaleMatrix(zeroArray, this.orderedIDs,
						this.iDim, this.isRelationMatrix);
		gradMat.resetMatrix();
		
		return gradMat;
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

	public void output(String fnOutput, boolean useToString) throws Exception {
		Writer writer = new BufferedWriter(new FileWriter(fnOutput));

		writer.write("iNumberOfRows: " + iNumberOfRows + "; iNumberOfColumns: " + iNumberOfColumns + "\n");
		String lines = toString();

		writer.write(lines);

		writer.close();
	}
	
	public void output(String fnOutput) throws Exception {
		Writer writer = new BufferedWriter(new FileWriter(fnOutput));
		writer.write("iNumberOfRows: " + iNumberOfRows + "; iNumberOfColumns: " + iNumberOfColumns + "\n");

		String line = "";
		int row;

		if (this.arrayBased) {
			System.out.println("KaleMatrix.output(String) - is Array based");
			for (int i = 0; i < this.pData.length; i++) {
				line = "Row: " + i + "\t";
				for (int j = 0; j < this.iDim; j++) {
					line += "" + this.pData[i][j] + "\t";
				}
				line += "\n";
				writer.write(line);
				if (i % 1000 == 0) System.out.println("KaleMatrix.output(String) - row "+i+" written to file.");
			}

		} else {
			System.out.println("KaleMatrix.output(String) - is Map based");
			int cntr = -1;
			for (Map.Entry entry : this.rowMap.entrySet()) {
				row = (int) entry.getKey();
				line = "ID: " + row + "\t";
				cntr++;

				for (Map.Entry entryCol : this.rowMap.get(row).entrySet()) {
					/*line += String.valueOf((int) entryCol.getKey()) + ": ";
					line += String.valueOf((double) entryCol.getValue()) + "\t";*/
					line += (int) entryCol.getKey() + ": ";
					line += (double) entryCol.getValue() + "\t";
				}
				line += "\n";
				writer.write(line);
				if (cntr % 1000 == 0) System.out.println("KaleMatrix.output(String) - row "+row+" written to file.");
			}
		}

		writer.close();
	}
	
	public void releaseMemory() {
		rowMap = new HashMap<Integer, HashMap<Integer, Double>>(this.iNumberOfRows);
		rowSumMap = new HashMap<Integer, HashMap<Integer, Double>>(this.iNumberOfRows);
	}
	
	private void resetMatrix() {
		for (Entry<Integer, HashMap<Integer, Double>> entry : this.rowMap.entrySet()) {
			int row = entry.getKey();
			for (Entry<Integer, Double> entry2 : entry.getValue().entrySet()) {
				int col = entry2.getKey();
				this.rowMap.get(row).put(col, 0d);
				this.rowSumMap.get(row).put(col, 0d);
			}
		}
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

	public ArrayList<String> toLines() {
		ArrayList<String> lines = new ArrayList<String>();
		String line = "";
		int row;

		if (this.arrayBased) {
			System.out.println("KaleMatrix.toLines() - is Array based");
			for (int i = 0; i < this.pData.length; i++) {
				line = "Row: " + i + "\t";
				for (int j = 0; j < this.iDim; j++) {
					line += "" + this.pData[i][j] + "\t";
				}
				line += "\n";
				lines.add(line);
			}

		} else {
			System.out.println("KaleMatrix.toLines() - is Map based");
			for (Map.Entry entry : this.rowMap.entrySet()) {
				row = (int) entry.getKey();
				line = "ID: " + row + "\t";

				for (Map.Entry entryCol : this.rowMap.get(row).entrySet()) {
					/*line += String.valueOf((int) entryCol.getKey()) + ": ";
					line += String.valueOf((double) entryCol.getValue()) + "\t";*/
					line += (int) entryCol.getKey() + ": ";
					line += (double) entryCol.getValue() + "\t";
				}
				line += "\n";
				lines.add(line);
			}
		}

		return lines;
	}

	@Override
	public String toString() {
		String res = "";
		int row;
		
		for (Map.Entry entry : this.rowMap.entrySet()) {
			row = (int) entry.getKey();
			res = "ID: " + row + "\t";

			for (Map.Entry entryCol : this.rowMap.get(row).entrySet()) {
				res += String.valueOf((int) entryCol.getKey()) + ": ";
				res += String.valueOf((double) entryCol.getValue()) + "\t";
			}
			res += "\n";		
		}
		return res;
	}


	/*
	 * All array-based methods.
	 */
	public double arrayGet(int i, int j) throws Exception {
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("get error in DenseMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("get error in DenseMatrix: ColumnID out of range");
		}
		return pData[i][j];
	}

	public void arraySet(int i, int j, double dValue) throws Exception {
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("set error in DenseMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("set error in DenseMatrix: ColumnID out of range");
		}
		pData[i][j] = dValue;
	}

	public void setToValueArray(double dValue) {
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				pData[i][j] = dValue;
			}
		}
	}

	public void setToRandomArray() {
		Random rd = new Random(123);
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				double dValue = rd.nextDouble();
				pData[i][j] = 2.0 * dValue - 1.0;
			}
		}
	}

	public double getSumArray(int i, int j) throws Exception {
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("get error in DenseMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("get error in DenseMatrix: ColumnID out of range");
		}
		return pSumData[i][j];
	}

	public void arrayAdd(int i, int j, double dValue) throws Exception {
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("add error in DenseMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("add error in DenseMatrix: ColumnID out of range");
		}
		pData[i][j] += dValue;
	}

	public void normalizeArray() {
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

	public void normalizeByRowArray() {
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

	public void rescaleByRowArray() {
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

	public void normalizeByColumnArray() {
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

	public void accumulatedByGradArray(int i, int j) throws Exception {
		if (i < 0 || i >= iNumberOfRows) {
			throw new Exception("add error in DenseMatrix: RowID out of range");
		}
		if (j < 0 || j >= iNumberOfColumns) {
			throw new Exception("add error in DenseMatrix: ColumnID out of range");
		}
		pSumData[i][j] += pData[i][j] * pData[i][j];
	}

	public boolean loadArray(String fnInput) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(fnInput), "UTF-8"));

		String line = "";
		line = reader.readLine();
		String[] first_line = StringSplitter.RemoveEmptyEntries(StringSplitter
				.split(":; ", line));
		if (iNumberOfRows != Integer.parseInt(first_line[1]) ||
				iNumberOfColumns != Integer.parseInt(first_line[3])) {
			throw new Exception("load error in DenseMatrix: row/column number incorrect");
		}

		int iRowID = 0;
		while ((line = reader.readLine()) != null) {
			String[] tokens = StringSplitter.RemoveEmptyEntries(StringSplitter
					.split("\t ", line));
			if (iRowID < 0 || iRowID >= iNumberOfRows) {
				throw new Exception("load error in DenseMatrix: RowID out of range");
			}
			if (tokens.length != iNumberOfColumns) {
				throw new Exception("load error in DenseMatrix: ColumnID out of range");
			}
			for (int iColumnID = 0; iColumnID < tokens.length; iColumnID++) {
				pData[iRowID][iColumnID] = Double.parseDouble(tokens[iColumnID]);
			}
			iRowID++;
		}

		reader.close();
		return true;
	}

	public void outputArray(String fnOutput) throws Exception {
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

	public void releaseMemoryArray() {
		for (int i = 0; i < iNumberOfRows; i++) {
			pData[i] = null;
		}
		pData = null;
		iNumberOfRows = 0;
		iNumberOfColumns = 0;
	}

	public void resetToZeroArray() {
		for (int i = 0; i < iNumberOfRows; i++) {
			for (int j = 0; j < iNumberOfColumns; j++) {
				pSumData[i][j] = 0.0;
			}
		}
	}
}