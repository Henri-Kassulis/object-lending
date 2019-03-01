package org.koagex.object.lending.sparkjava;

import java.util.List;

public class ResultPage<T> {
	final int pageNumber;
	final int pageCount;
	final List<T> list;

	public ResultPage(int pageNumber, int pageCount, List<T> list) {
		this.pageNumber = pageNumber;
		this.pageCount = pageCount;
		this.list = list;
	}

}
