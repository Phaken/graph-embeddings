package org.uu.nl.embedding.util.write;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.convert.util.NodeInfo;
import org.uu.nl.embedding.opt.Optimum;
import org.uu.nl.embedding.util.CoOccurrenceMatrix;
import org.uu.nl.embedding.util.config.Configuration;

import me.tongfei.progressbar.ProgressBar;

public class KaleEmbeddingTextWriter implements EmbeddingWriter {
    private final static Logger logger = Logger.getLogger("KaleEmbeddingTextWriter");

	protected final boolean[] writeNodeTypes;
	protected final Configuration config;
	private String FILE;
	private String FILETYPE;

	public KaleEmbeddingTextWriter(String fileName, Configuration config, final String FILETYPE) {
		this.FILE = fileName + FILETYPE;
		this.FILETYPE = FILETYPE;
		
		this.config = config;
		this.writeNodeTypes = new boolean[3];
		this.writeNodeTypes[NodeInfo.URI.id] = config.getOutput().outputUriNodes();
		this.writeNodeTypes[NodeInfo.BLANK.id]  = config.getOutput().outputBlankNodes();
		this.writeNodeTypes[NodeInfo.LITERAL.id] = config.getOutput().outputLiteralNodes();
	}
	
	public KaleEmbeddingTextWriter(String fileName, Configuration config) {
		this.FILE = fileName;
		
		this.config = config;
		this.writeNodeTypes = new boolean[3];
		this.writeNodeTypes[NodeInfo.URI.id] = config.getOutput().outputUriNodes();
		this.writeNodeTypes[NodeInfo.BLANK.id]  = config.getOutput().outputBlankNodes();
		this.writeNodeTypes[NodeInfo.LITERAL.id] = config.getOutput().outputLiteralNodes();
	}
	
	public void write(final ArrayList<String> lines) throws IOException {

		final int nrLines = lines.size();

		try (ProgressBar pb = Configuration.progressBar("Writing to file", nrLines, "vectors\n");
			 Writer writer = new BufferedWriter(new FileWriter(this.FILE))) {

			writeConfig(writer);

			for (int i = 0; i < nrLines; i++) {

				writer.write(lines.get(i));
				pb.step();
			}
			writer.close();
			logger.info("Data written to: " + this.FILE);
		}
	}
	
	private void writeConfig(Writer writer) throws IOException {

		writer.write("# Starting the embedding creation process with following settings:" + "\n");
		writer.write("# Graph File: " + config.getGraph() + "\n");
		writer.write("# Embedding dimensions: " + config.getDim() + "\n");
		writer.write("# Threads: " + config.getThreads() + "\n");
		writer.write("# BCA Alpha: " + config.getBca().getAlpha() + "\n");
		writer.write("# BCA Epsilon: " + config.getBca().getEpsilon() + "\n");
		writer.write("# BCA Type: " + config.getBca().getType() + "\n");
		writer.write("# BCA Normalize: " + config.getBca().getNormalize() + "\n");
		writer.write("# Gradient Descent Algorithm: " + config.getOpt().getMethod() + "\n");
		writer.write("# " + config.getMethod() + " Tolerance: " + config.getOpt().getTolerance() + "\n");
		writer.write("# " + config.getMethod() + " Maximum Iterations: " + config.getOpt().getMaxiter() + "\n");

		if(config.usingPca()) writer.write("# PCA Minimum Variance: " + config.getPca().getVariance() + "\n");
		else writer.write("# No PCA will be performed" + "\n");

		if(config.usingWeights()) {
			writer.write("# Using weights, predicates that are not listed are ignored:" + "\n");
			for (Map.Entry<String, Float> entry : config.getWeights().entrySet()) {
				writer.write("# " + entry.getKey() + ": " + entry.getValue() + "\n");
			}
		} else writer.write("# No weights specified, using linear weight" + "\n");

		if(config.usingSimilarity()) {
			writer.write("# Using the following similarity metrics:" + "\n");
			for (Configuration.SimilarityGroup s : config.getSimilarity()) {
				writer.write("# " + s.toString() + "\n");
			}
		} else writer.write("# No similarity matching will be performed" + "\n");
	}

	public void setFileType(final String newType) {
		this.FILETYPE = newType;
	}

	/**
	 * Don't use this one.
	 */
	@Override
	public void write(Optimum optimum, CoOccurrenceMatrix coMatrix, Path outputFolder) throws IOException {
		logger.info("WARNING: Wrong file writing method used in KaleEmbeddingTextWriter!");
		Files.createDirectories(outputFolder);

		byte type;
		final int vocabSize = coMatrix.nrOfFocusVectors();
		final int dimension = config.getDim();
		final String[] out = new String[dimension];
		final float[] result = optimum.getResult();

		// Create a tab-separated file
		final String delimiter = "\t";
		final String newLine = "\n";

		try (ProgressBar pb = Configuration.progressBar("Writing to file", vocabSize, "vectors");
			 Writer vect = new BufferedWriter(new FileWriter(outputFolder.resolve(this.FILE).toFile()))) {

			writeConfig(vect);

			Configuration.Output output = config.getOutput();

			for (int i = 0; i < vocabSize; i++) {

				type = coMatrix.getType(i);

				final String key = coMatrix.getKey(i);
				final NodeInfo nodeInfo = NodeInfo.fromByte(type);

				for (int d = 0; d < out.length; d++)
					out[d] = String.format("%11.6E", result[d + i * dimension]);

				vect.write(String.join(delimiter, out) + newLine);
				pb.step();
			}
		}
	}
}
