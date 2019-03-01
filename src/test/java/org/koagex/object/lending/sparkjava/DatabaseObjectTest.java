package org.koagex.object.lending.sparkjava;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseObjectTest {
	private static Database database;
	private static String loginName;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		File dbFile = File.createTempFile("object_lending_test", ".h2_db");
		dbFile.deleteOnExit();

		database = new Database(dbFile);
		loginName = "user_1234";
		database.register(loginName, "password_1234", "email", "location","contact");
	}

	@Test
	void addObject() {
		Object object = new Object("buch", "roman");
		database.addObject(loginName, object);

		// visible for own user
		List<Object> userObjects = database.getUserObjects(loginName);
		Object loadedObject = userObjects.get(0);
		assertEquals(object, loadedObject);

		// public visible
		List<Object> allObjects = database.getAllObjects();
		Object loadedPublicObject = allObjects.get(0);
		assertEquals(object, loadedPublicObject);

		// not visible for other user
		List<Object> otherUserObjects = database.getUserObjects("hans");
		assertEquals(otherUserObjects.size(), 0);
	}



}
