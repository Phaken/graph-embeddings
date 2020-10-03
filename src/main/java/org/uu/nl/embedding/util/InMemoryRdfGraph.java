package org.uu.nl.embedding.util;

import org.apache.log4j.Logger;
import org.uu.nl.embedding.kale.struct.TripleSet;

import grph.in_memory.InMemoryGrph;
import grph.properties.NumericalProperty;
import grph.properties.Property;
import grph.properties.StringProperty;

public class InMemoryRdfGraph extends InMemoryGrph {
	
    private final static Logger logger = Logger.getLogger("InMemoryRdfGraph");

    private final NumericalProperty edgeTypeProperty, edgeWeightProperty, vertexTypeProperty;
    private final StringProperty literalPredicateProperty;
    private TripleSet tripleSet;

    public InMemoryRdfGraph() {
        super();
        // Support up to 127 different predicate types
        edgeTypeProperty = new NumericalProperty("Edge types",32, 0);
        // Weights are stored as floats
        edgeWeightProperty = new NumericalProperty("Edge weights",32, 0);
        // We only need to support 3 vertex types
        vertexTypeProperty = new NumericalProperty("Vertex types",2, 0);

        literalPredicateProperty = new StringProperty("Literal Predicate", getVertices().size());
        
        logger.info("Graph initialized.");
    }

    public Property getLiteralPredicateProperty() { return literalPredicateProperty; }

    public NumericalProperty getEdgeTypeProperty() { return edgeTypeProperty; }

    public NumericalProperty getEdgeWeightProperty() {
        return edgeWeightProperty;
    }

    public NumericalProperty getVertexTypeProperty() {
        return vertexTypeProperty;
    }
    
    public void setTripleSet(final TripleSet tripleSet) {
    	this.tripleSet = tripleSet;
    }
    
    public TripleSet getTripleSet() {
    	return this.tripleSet;
    }
}
