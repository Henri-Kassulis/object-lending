package org.koagex.object.lending.sparkjava;

import java.io.IOException;
import java.io.UncheckedIOException;

import spark.utils.IOUtils;

public class Resource {
	public static String getStringResource(String name) {
		try {
			return IOUtils.toString(Resource.class.getResourceAsStream(name));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
