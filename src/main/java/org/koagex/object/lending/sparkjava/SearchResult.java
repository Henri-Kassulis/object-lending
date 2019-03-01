package org.koagex.object.lending.sparkjava;

public class SearchResult {
	public final Object object;
	public final Double distance;

	public SearchResult(Object object, Double distance) {
		this.object = object;
		this.distance = distance;
	}

	public Object getObject() {
		return object;
	}

	public Double getDistance() {
		return distance;
	}

}
