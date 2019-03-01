package org.koagex.object.lending.sparkjava;

public class ObjectDetail {
	public final Integer id;
	public final String name;
	public final String description;
	public final double latitude;
	public final double longitude;
	public final String contact;

	public ObjectDetail(Integer id, String name, String description, double latitude, double longitude,
			String contact) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.latitude = latitude;
		this.longitude = longitude;
		this.contact = contact;
	}

}
