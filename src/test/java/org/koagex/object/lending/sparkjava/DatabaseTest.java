package org.koagex.object.lending.sparkjava;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pac4j.sql.profile.DbProfile;

class DatabaseTest {

	private Database database;

	@BeforeEach
	void setUp() throws Exception {
		File dbFile = File.createTempFile("object_lending_test", ".h2_db");
		dbFile.deleteOnExit();

		database = new Database(dbFile);
	}

	@Test
	void validate() {
		String loginName = "user_1234";
		String password = "password_1234";
		register(loginName, password);
		boolean valid = database.validate(loginName, password);
		Assertions.assertTrue(valid);
	}

	private void register(String loginName, String password) {
		database.register(loginName, password, "email", "location", "contact");
	}

	@Test
	void userExists() {
		String loginName = "user_1234";
		String password = "password_1234";
		register(loginName, password);

		boolean userExists = database.userExists(loginName);
		Assertions.assertTrue(userExists);
	}

	@Test
	void registerEmailAndLocation() {
		String loginName = "user_1234";
		String password = "password_1234";
		String email = "hans@wurst.de";
		String location = "Rostock Kurt-Schumacher-Ring 3";
		database.register(loginName, password, email, location, "contact");

		DbProfile profile = database.getUser(loginName);
		Assertions.assertAll(//
				() -> assertEquals("Email", email, profile.getEmail()), //
				() -> assertEquals("Location", location, database.getUserAddress(loginName))//
		);
	}

	@Test
	void searchByLocation() {
		String user1 = "user1";
		register(user1, "password_1234");
		database.setPlaceCoordinates(user1, 50, 11);
		Object object1 = new Object("ob1", "desc1");
		database.addObject(user1, object1);

		String user2 = "user2";
		register(user2, "password_1234");
		database.setPlaceCoordinates(user2, 50, 12);
		Object object2 = new Object("ob2", "desc2");
		database.addObject(user2, object2);

		List<SearchResult> result = database.searchByLocation(50, 10);
		Object firstResult = result.get(0).getObject();
		assertEquals("ob1", firstResult.name);
	}

	@Test
	void searchByName() {
		String user1 = "user1";
		register(user1, "password_1234");
		database.setPlaceCoordinates(user1, 50, 11);
		Object object1 = new Object("ob1je", "desc1");
		database.addObject(user1, object1);

		Object object2 = new Object("ob2", "desc2");
		database.addObject(user1, object2);

		List<SearchResult> result = database.search(null, "b1", null);
		assertEquals("desc1", result.get(0).getObject().description);
	}
}
