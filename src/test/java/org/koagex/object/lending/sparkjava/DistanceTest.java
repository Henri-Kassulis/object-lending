package org.koagex.object.lending.sparkjava;

import org.junit.jupiter.api.Test;

class DistanceTest {

	@Test
	void test() {
		double lat1 = 54.12058;
		double lon1 = 12.16836;

		double lat2 = 54.1056;
		double lon2 = 12.1609;
		double result = Database.distance(lat1, lat2, lon1, lon2);
		System.out.println(result);

		System.out.println(Database.distance(0, 0, 0, 90));
	}

}
