package org.koagex.object.lending.sparkjava;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.shiro.authc.credential.DefaultPasswordService;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.password.PasswordEncoder;
import org.pac4j.core.credentials.password.ShiroPasswordEncoder;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.definition.CommonProfileDefinition;
import org.pac4j.sql.profile.DbProfile;
import org.pac4j.sql.profile.service.DbProfileService;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.GeneratedKeys;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Database {
	/*
	 * SQL Styleguide
	 * - keywords uppercase
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

	private final DbProfileService service;

	private final DBI dbi;

	public Database(File databaseFile) {
		/* h2 could be run in memory with ":memory:"
		 * but it will be discarded once the connection
		 * is closed. Thus it can not be used with Flyway
		 * which uses a DataSource.
		 * Therefore the constructor requires a file.
		 */
		String path = databaseFile.getAbsolutePath();

		// http://h2database.com/html/features.html#other_logging
		// avoid creation of <db_file>.trace.db
		String activateSlf4j = ";TRACE_LEVEL_FILE=4";

		// h2 always appends .mv.db to the file name.
		// If you deactivate mvStore it will append .h2.db
		// https://stackoverflow.com/questions/23806471/why-is-my-embedded-h2-program-writing-to-a-mv-db-file

		String prefix = "jdbc:h2:";
		String dbUrl = prefix + path + activateSlf4j;
		LOGGER.info("dbUrl= " + dbUrl);

		//		show all tables
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setUrl(dbUrl);

		try {
			List<Map<String, java.lang.Object>> result = DBI.open(dataSource.getConnection()).select(
					"SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE table_schema != 'INFORMATION_SCHEMA'");
			LOGGER.info(result.toString());
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Flyway flyway = new Flyway();
		LOGGER.info(Arrays.asList(flyway.getLocations()).toString());
		flyway.setDataSource(dataSource);

		boolean startedWithEmptyDatabase = flyway.info().applied().length == 0;

		flyway.migrate();
		LOGGER.info("applied migration count: " + flyway.info().applied().length);
		LOGGER.info("pending migration count: " + flyway.info().pending().length);

		service = createService(dataSource);
		dbi = service.getDbi();

		// for development: after db refresh(delete) insert dummy objects with images
		if (startedWithEmptyDatabase) {
			importDummyObjects();
		}

	}

	private DbProfileService createService(DataSource dataSource) {

		// https://github.com/pac4j/pac4j/blob/master/pac4j-sql/pom.xml
		// https://github.com/pac4j/pac4j/blob/master/pac4j-sql/src/test/java/org/pac4j/sql/profile/service/DbProfileServiceTests.java
		// https://github.com/pac4j/pac4j/blob/master/pac4j-sql/src/test/java/org/pac4j/sql/test/tools/DbServer.java

		DbProfileService service = new DbProfileService(dataSource);
		/*
		PasswordEncoder passwordEncoder = new SpringSecurityPasswordEncoder(new BCryptPasswordEncoder());
		I rejected spring-security-crypto because this:
		   java.lang.NoClassDefFoundError: org/apache/commons/logging/LogFactory
		   at org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.<init>(BCryptPasswordEncoder.java:37)
		spring-security-crypto did not declare its dependency on apache commons logging.
		*/
		PasswordEncoder passwordEncoder = new ShiroPasswordEncoder(new DefaultPasswordService());
		service.setPasswordEncoder(passwordEncoder);
		service.init(null);

		return service;
	}

	List<Object> getUserObjects(String userName) {
		return dbi.withHandle(handle -> {
			return handle.createQuery(//
					"  select o.id, name, description  " //
							+ " from object o " //
							+ "join place p on o.place = p.id" //
							+ " where p.user = :userName")//
					.bind("userName", userName)//
					.map(getObjectRowMapper())//
					.list();
		});

	}

	private ResultSetMapper<Object> getObjectRowMapper() {

		return (i, rs, ctx) -> {
			int id = Integer.parseInt(rs.getString("id"));
			String name = rs.getString("name");
			String description = rs.getString("description");
			return new Object(name, description, id);
		};
	}

	List<Object> getAllObjects() {
		return dbi.withHandle(//
				handle -> handle.createQuery("select id, name, description from object")//
						.map(getObjectRowMapper())//
						.list()//
		);
	}

	ResultPage<Object> getObjectsPage(int page) {

		int pageSize = 18;

		int count = queryInt("SELECT count(*) FROM object");
		// https://stackoverflow.com/questions/787700/how-to-make-the-division-of-2-ints-produce-a-float-instead-of-another-int
		int pageCount = (int) Math.ceil(Double.valueOf(count) / pageSize);

		// page numbers start with 1
		int offset = (page - 1) * pageSize;
		List<Object> list = dbi.withHandle(//
				handle -> handle.createQuery("SELECT id, name, description FROM object LIMIT :limit OFFSET :offset")//
						.bind("limit", pageSize)//
						.bind("offset", offset)//
						.map(getObjectRowMapper())//
						.list()//
		);

		return new ResultPage<>(page, pageCount, list);
	}

	public void register(String loginName, String password, String email, String location, String contact) {

		DbProfile profile = new DbProfile();
		// use id = username, because then we can use service.findById(username)
		// Also the username/loginname should never be changed.
		profile.setId(loginName);
		profile.addAttribute(Pac4jConstants.USERNAME, loginName);
		profile.addAttribute(CommonProfileDefinition.EMAIL, email);

		service.create(profile, password);

		insert("insert into place (USER,address)  values (?,?)", loginName, location);
		insert("insert into contact (USER,contact)  values (?,?)", loginName, contact);
	}

	public void deleteUser(String loginName) {
		service.removeById(loginName);
	}

	public boolean userExists(String loginName) {
		return service.findById(loginName) != null;
	}

	public DbProfile getUser(String loginName) {
		return service.findById(loginName);
	}

	public boolean validate(String loginName, String password) {

		try {
			service.validate(new UsernamePasswordCredentials(loginName, password, null), null);
			return true;
		} catch (HttpAction e) {
			throw new RuntimeException(e);
		} catch (CredentialsException e) {
			e.printStackTrace();
			return false;
		}
	}

	public DbProfileService getService() {
		return service;
	}

	public int getPlaceId(String loginName) {

		return querySingle(//
				handle -> handle.createQuery("select id from place where user = :userName")//
						.bind("userName", loginName)//
				, Integer.class//
		);
	}

	public void setPlaceCoordinates(String loginName, double latitude, double longitude) {
		int placeId = getPlaceId(loginName);

		dbi.useHandle(handle -> {
			handle.update("UPDATE place  " + "SET LATITUDE = ? , LONGITUDE = ? " + "WHERE ID = ?", //
					latitude, longitude, //
					placeId);
		});
	}

	public List<SearchResult> searchByLocation(double latitude, double longitude) {
		return search(new Location(latitude, longitude), null, null);
	}

	public List<Integer> getTagIds(List<String> tags) {
		return getTags().stream()//
				.filter(t -> tags.contains(t.name))//
				.map(Tag::getId)//
				.collect(toList());
	}

	public List<Integer> getObjectIdsByTags(List<String> tags) {

		// https://stackoverflow.com/questions/3107044/preparedstatement-with-list-of-parameters-in-a-in-clause
		// https://stackoverflow.com/questions/32526233/jdbi-how-to-bind-a-list-parameter-in-java

		List<Integer> tagIds = getTagIds(tags);

		String inList = tags.stream().map(t -> "?").collect(joining(","));

		String query = "SELECT object_id " + //
				"         FROM object_tag " + //
				"        WHERE tag_id IN (" + inList + ") " + //
				"     GROUP BY OBJECT_ID " + //
				"       HAVING count(*) = ?";

		return dbi.withHandle(//
				handle -> {
					PreparedStatement statement = handle.getConnection().prepareStatement(query);
					int tagCount = tags.size();
					for (int i = 0; i < tagCount; i++) {
						statement.setInt(i + 1, tagIds.get(i));
					}
					statement.setInt(tagCount + 1, tagCount);
					ResultSet rs = statement.executeQuery();
					List<Integer> result = new ArrayList<>();
					while (rs.next()) {
						result.add(rs.getInt(1));
					}
					return result;
				});
	}

	public List<SearchResult> search(//
			Location location, //
			String pName, //
			String pDescription, //
			List<String> tags, //
			List<String> tagsExclude//
	) {

		boolean hasLocation = location != null;

		List<SearchResult> result = hasLocation ? //
				searchWithLocation(location, pName, pDescription) : //
				searchWithoutLocation(pName, pDescription);

		List<SearchResult> filteredResult = filterWithTags(result, tags, tagsExclude);

		return filteredResult;
	}

	private List<SearchResult> searchWithoutLocation(String pName, String pDescription) {
		String name = pName == null ? "" : pName;
		String description = pDescription == null ? "" : pDescription;

		String query = Resource.getStringResource("/db/query/search_without_location.sql");

		List<SearchResult> result = dbi.withHandle(handle -> {
			Query<Map<String, java.lang.Object>> q = handle.createQuery(query);
			q = q.bind("name", name);//
			q = q.bind("description", description);//

			return q.map((i, rs, ctx) -> {
				int id = rs.getInt("object_id");
				String resultName = rs.getString("name");
				String resultDescription = rs.getString("description");
				Object object = new Object(resultName, resultDescription, id);
				Double distance = null;
				return new SearchResult(object, distance);
			})//
					.list();
		});
		return result;
	}

	private List<SearchResult> searchWithLocation(Location location, String pName, String pDescription) {

		String name = pName == null ? "" : pName;
		String description = pDescription == null ? "" : pDescription;

		String query = Resource.getStringResource("/db/query/search_with_location.sql");

		return dbi.withHandle(handle -> {
			Query<Map<String, java.lang.Object>> q = handle.createQuery(query)//
					.bind("latitude", location.latitude)//
					.bind("longitude", location.longitude)//
					.bind("name", name)//
					.bind("description", description);//

			return q.map((i, rs, ctx) -> {
				String resultName = rs.getString("name");
				String resultDescription = rs.getString("description");
				Object object = new Object(resultName, resultDescription);
				Double distance = null;
				double latitude = rs.getDouble("LATITUDE");
				double longitude = rs.getDouble("LONGITUDE");
				distance = distance(latitude, location.latitude, longitude, location.longitude);
				return new SearchResult(object, distance);
			})//
					.list();
		});

	}

	@Deprecated
	public List<SearchResult> search(Location location, String pName, String pDescription) {

		String name = nullOrEmpty(pName) ? null : pName;
		String description = nullOrEmpty(pDescription) ? null : pDescription;

		String joinPlace = location == null ? //
				"" : //
				" JOIN (    SELECT ID  " //
						+ "         , POWER(LATITUDE  - :latitude, 2)  "
						+ "         + POWER(LONGITUDE - :longitude, 2  ) distance " //
						+ "         , \"USER\" " //
						+ "         ,LATITUDE    " //
						+ "         ,LONGITUDE    " //
						+ "          FROM place  " //
						+ ") p " //
						+ "ON o.PLACE = p.ID  ";

		String order = location == null ? //
				"" : //
				"ORDER BY nvl  (distance, 1)  ";

		String nameCondition = name == null ? "" : "AND   lower(o.name) LIKE '%' || lower(:name) || '%'   ";
		String descriptionCondition = description == null ? ""
				: "AND  lower(o.description) LIKE '%' || lower(:description) || '%'  ";

		String selectLocation = location == null ? "" : " , p.LATITUDE , p.LONGITUDE ";

		String query = "SELECT o.ID object_id,o.NAME, o.DESCRIPTION   " //
				+ selectLocation //
				+ "FROM OBJECT o " //
				+ joinPlace //
				+ "WHERE 1=1 " //
				+ nameCondition //
				+ descriptionCondition //
				+ order;

		return dbi.withHandle(handle -> {
			Query<Map<String, java.lang.Object>> q = handle.createQuery(query);

			if (location != null) {
				q = q.bind("latitude", location.latitude);//
				q = q.bind("longitude", location.longitude);//
			}

			if (name != null) {
				q = q.bind("name", name);//
			}

			if (description != null) {
				q = q.bind("description", description);//
			}

			return q.map((i, rs, ctx) -> {
				String resultName = rs.getString("name");
				String resultDescription = rs.getString("description");
				Object object = new Object(resultName, resultDescription);
				Double distance = null;
				if (location != null) {
					double latitude = rs.getDouble("LATITUDE");
					double longitude = rs.getDouble("LONGITUDE");
					distance = distance(latitude, location.latitude, longitude, location.longitude);
				}
				return new SearchResult(object, distance);
			})//
					.list();
		});

	}

	private List<SearchResult> filterWithTags(List<SearchResult> result, List<String> tags, List<String> tagsExclude) {
		List<SearchResult> filteredResult = new ArrayList<>(result);

		boolean hasTags = !tags.isEmpty();
		if (hasTags) {
			List<Integer> includeObjectIds = getObjectIdsByTags(tags);
			filteredResult.removeIf(r -> {
				Integer id = r.object.id;
				boolean contains = includeObjectIds.contains(id);
				return !contains;
			});
		}

		boolean hasExcludes = !tagsExclude.isEmpty();
		if (hasExcludes) {
			Set<Integer> excludeObjectIds = tagsExclude.stream()//
					.map(t -> getObjectIdsByTags(Arrays.asList(t)))//
					.flatMap(List::stream)//
					.collect(toSet());
			filteredResult.removeIf(r -> excludeObjectIds.contains(r.object.id));
		}
		return filteredResult;
	}

	public int addObject(String loginName, Object object) {
		return addObject(loginName, object, null, null);
	}

	public int addObject(String loginName, Object object, byte[] image, List<String> tags) {
		int placeId = getPlaceId(loginName);

		Integer id = dbi.withHandle(handle -> {

			// https://stackoverflow.com/questions/1915166/how-to-get-the-insert-id-in-jdbc#1915197
			GeneratedKeys<Map<String, java.lang.Object>> generatedKeys = handle.createStatement(//
					"insert into object (place, name, description) values (:place,:name,:description)"//
			)//
					.bind("place", placeId)//
					.bind("name", object.name)//
					.bind("description", object.description)//
					.executeAndReturnGeneratedKeys();
			Map<String, java.lang.Object> first = generatedKeys.first();

			return (Integer) first.get("id");

		});

		if (image != null) {
			this.setObjectImage(id, image);
		}

		if (tags != null) {
			this.saveTags(id, tags);
		}

		return id;

	}

	public byte[] getObjectImage(int objectId) {
		return dbi.withHandle(handle -> {
			return handle.createQuery("select image from object where id = :id")//
					.bind("id", objectId)//
					.map((i, rs, ctx) -> blobToImageBytes(rs.getBlob("image")))//
					.first();
		});
	}

	public void setObjectImage(int id, byte[] image) {
		LOGGER.debug("image size: {}", image.length);
		dbi.useHandle(handle -> handle.update("update object set image = ? where id = ? ", image, id));

		int maxSize = 300;
		byte[] smallBytes = ImageResizer.resizeImage(image, maxSize);
		setObjectThumbnail(id, smallBytes);
	}

	public byte[] getObjectThumbnail(int objectId) {
		return dbi.withHandle(handle -> {
			return handle.createQuery("select thumbnail from object where id = :id")//
					.bind("id", objectId)//
					.map((i, rs, ctx) -> blobToImageBytes(rs.getBlob("thumbnail")))//
					.first();
		});
	}

	private void setObjectThumbnail(int id, byte[] image) {
		LOGGER.debug("image size: {}", image.length);
		dbi.useHandle(handle -> handle.update("update object set thumbnail = ? where id = ? ", image, id));
	}

	private void insert(String statement, java.lang.Object... args) {
		dbi.useHandle(handle -> handle.insert(statement, args));
	}

	private void update(String statement, java.lang.Object... args) {
		dbi.useHandle(handle -> handle.update(statement, args));
	}

	public String getUserAddress(String loginName) {
		return querySingle(//
				handle -> handle.createQuery("select address from place where user = :userName")//
						.bind("userName", loginName)//
				, String.class//
		);
	}

	private byte[] blobToImageBytes(Blob imageBlob) throws SQLException {
		byte[] image;
		if (imageBlob == null) {
			image = null;
		} else {
			image = imageBlob.getBytes(0, (int) imageBlob.length());
		}
		return image;
	}

	public void deleteObject(int objectId) {
		dbi.useHandle(handle -> handle.execute("delete from object where id = ?", objectId));
	}

	public Object getObject(int id) {
		return querySingle(handle -> handle.createQuery("select id, name, description from object where id = :id")//
				.bind("id", id), getObjectRowMapper());
	}

	public ObjectDetail getObjectDetail(int id) {

		ResultSetMapper<ObjectDetail> mapper = new ResultSetMapper<ObjectDetail>() {

			@Override
			public ObjectDetail map(int index, ResultSet r, StatementContext ctx) throws SQLException {
				String name = r.getString("name");
				String description = r.getString("description");
				double latitude = r.getDouble("latitude");
				double longitude = r.getDouble("longitude");
				String contact = r.getString("contact");
				return new ObjectDetail(id, name, description, latitude, longitude, contact);
			}
		};

		return querySingle(handle -> handle.createQuery("SELECT name, description, latitude, longitude, contact " //
				+ " FROM object_detail " //
				+ " WHERE id = :id")//
				.bind("id", id), mapper);
	}

	public String getContact(String loginName) {
		return querySingle(handle -> handle.createQuery("select contact from contact where user = :user")//
				.bind("user", loginName), String.class);
	}

	public Location getLocation(String loginName) {
		ResultSetMapper<Location> mapper = (i, rs, ctx) -> new Location(//
				rs.getDouble("latitude"), //
				rs.getDouble("longitude")//
		);

		return querySingle(handle -> handle.createQuery("select latitude, longitude from place where user = :user")//
				.bind("user", loginName), mapper);
	}

	private int queryInt(String query) {
		return querySingle(handle -> handle.createQuery(query), Integer.class);
	}

	private <T> T querySingle(Function<Handle, Query<?>> query, Class<T> clazz) {
		ResultSetMapper<T> mapper = (index, r, ctx) -> r.getObject(1, clazz);
		return querySingle(query, mapper);
	}

	private <T> T querySingle(Function<Handle, Query<?>> query, ResultSetMapper<T> mapper) {
		return dbi.withHandle(//
				handle -> query.apply(handle)//
						.map(mapper)//
						.first()//
		);
	}

	private <T> List<T> queryList(String query, ResultSetMapper<T> mapper) {
		return dbi.withHandle(//
				handle -> handle.createQuery(query)//
						.map(mapper)//
						.list()//
		);
	}

	private List<String> queryStringList(String query) {
		return dbi.withHandle(//
				handle -> handle.createQuery(query)//
						.map((i, rs, ctx) -> rs.getString(1))//
						.list()//
		);
	}

	public void updateContact(String username, String contact) {
		update("update contact set contact = ? where user = ?", contact, username);
	}

	public void updateObject(int id, String name, String description) {
		update("update object set name = ?, description = ?  where id = ?", name, description, id);
	}

	public List<String> getTagNames() {
		return queryStringList("SELECT name FROM tag");
	}

	public List<Tag> getTags() {
		ResultSetMapper<Tag> mapper = (i, rs, ctx) -> new Tag(//
				rs.getInt("id"), //
				rs.getString("name")//
		);
		return queryList("SELECT id,name FROM tag", mapper);
	}

	public List<Tag> getObjectTags(int objectId) {

		ResultSetMapper<Tag> mapper = (i, rs, ctx) -> new Tag(//
				rs.getInt("tag_id"), //
				rs.getString("tag_name")//
		);

		return dbi.withHandle(//
				handle -> handle
						.createQuery("select TAG_ID, tag_name from object_tag_view where object_id = :object_id")//
						.bind("object_id", objectId)//
						.map(mapper)//
						.list()//
		);
	}

	public void saveTags(int objectId, List<String> tags) {

		// create missing tags
		List<String> existingTags = getTagNames();

		List<String> missingTags = new ArrayList<>(tags);
		missingTags.removeAll(existingTags);

		for (String tag : missingTags) {
			insert("INSERT INTO TAG (name) VALUES (?)", tag);
		}

		List<Tag> assignedTags = getObjectTags(objectId);
		List<String> assignedNames = assignedTags.stream()//
				.map(t -> t.name)//
				.collect(Collectors.toList());

		// assign new tags
		List<String> newTags = new ArrayList<>(tags);
		List<Tag> allTags = getTags();
		newTags.removeAll(assignedNames);
		for (String tagName : newTags) {
			int id = allTags.stream()//
					.filter(t -> tagName.equals(t.name))//
					.findFirst()//
					.map(t -> t.id)//
					.get();
			insert("INSERT INTO object_tag (object_id, tag_id) VALUES (?, ?) ", objectId, id);
		}

		// remove deleted tags
		assignedTags.stream()//
				.filter(t -> !tags.contains(t.name))//
				.map(t -> t.id)//
				.forEach(//
						tagId -> {
							update("DELETE FROM object_tag WHERE object_id = ? AND tag_id = ? ", objectId, tagId)//
							;
						});
	}

	public List<Pair<String, Integer>> getTagUsage() {
		String query = Resource.getStringResource("/db/query/tag_usage.sql");

		ResultSetMapper<Pair<String, Integer>> mapper = //
				(i, rs, ctx) -> new Pair<>(//
						rs.getString("name"), //
						rs.getInt("count")//
				);

		return queryList(query, mapper);
	}

	private boolean nullOrEmpty(String string) {
		return string == null || string.trim().isEmpty();
	}

	/**
	 * Haversine method
	https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude-what-am-i-doi
	 */
	public static double distance(double lat1, double lat2, double lon1, double lon2) {

		final int R = 6371; // Radius of the earth

		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) //
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2)
						* Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c;

		// round to kilometers with one digit after comma
		double rounded = Math.round(distance * 10) / 10.0;

		return rounded;
	}

	private void importDummyObjects() {
		String loginName = "a";

		String password = loginName;
		this.register(loginName, password, "fake@mail.x", "location", "tel:  1234");

		int count = 1;
		File rootDir = new File("D:\\tmp\\OBLE\\frents_pages");
		for (File dir : rootDir.listFiles()) {
			File[] files = dir.listFiles();
			for (File file : files) {
				if (count == 20) {
					return;
				}
				if (file.getName().startsWith("getImage")) {
					String name = String.valueOf(count);
					Object object = new Object(name, "");
					int id = this.addObject(loginName, object);
					byte[] imageBytes;
					try {
						imageBytes = Files.readAllBytes(file.toPath());
						this.setObjectImage(id, imageBytes);
					} catch (IOException e) {
						e.printStackTrace();
					}
					count++;
				}
			}

		}
	}

	/*
	 * TODO use @UseStringTemplate3StatementLocator and @SqlUpdate ??
	 */

}
