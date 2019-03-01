package org.koagex.object.lending.sparkjava;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Before;
import org.junit.Test;

public class NewTest {

	private Database database;

	@Before
	public void setUp() throws Exception {
		File dbFile = File.createTempFile("object_lending_test", ".h2_db");
		dbFile.deleteOnExit();

		database = new Database(dbFile);
	}

	@Test
	public void searchWithoutLocation() {
		List<Object> allObjects = database.getAllObjects();
		System.out.println(allObjects.size());
		int id = allObjects.get(3).id;
		System.out.println(id);

		List<String> tags = asList("CD", "Metal", "Musik");

		database.saveTags(id, tags);
		database.saveTags(allObjects.get(5).id, tags);

		List<Integer> objectIds = database.getObjectIdsByTags(tags);
		System.out.println(objectIds);

		String login = "a";
		Object ob = new Object("name", "descr");
		int idA = database.addObject(login, ob, null, asList("CD"));
		int idB = database.addObject(login, ob, null, asList("CD", "Metal"));
		int idC = database.addObject(login, ob, null, asList("CD", "Metal", "Musik"));

		System.out.println(idA + " " + idB + " " + idC);

		List<String> include = asList("CD", "Metal");
		List<String> exclude = asList("Musik");
		List<SearchResult> results = database.search(null, "", "", include, exclude);
		Set<Integer> resultIds = results.stream().map(r -> r.object.id).collect(toSet());

		System.out.println(resultIds);

		assertThat(resultIds, CoreMatchers.allOf(//
				IsCollectionContaining.hasItem(idB), //
				CoreMatchers.not(IsCollectionContaining.hasItem(idA)), //
				CoreMatchers.not(IsCollectionContaining.hasItem(idC))//
		));
	}

}
