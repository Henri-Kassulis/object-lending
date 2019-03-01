package org.koagex.object.lending.sparkjava;

import static java.util.stream.Collectors.toList;
import static spark.Spark.before;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.notFound;
import static spark.Spark.post;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.sparkjava.CallbackRoute;
import org.pac4j.sparkjava.LogoutRoute;
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.sparkjava.SparkWebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheResolver;
import com.github.mustachejava.resolver.FileSystemResolver;

import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;
import spark.utils.IOUtils;

public class SparkApplication {

	private static final String INCLUDE_GET_LOCATION_JS = "<script src='/scripts/get_location.js'></script>";

	private static final String PARAM_OBJECT_ID = "object_id";

	static final Logger LOGGER = LoggerFactory.getLogger(SparkApplication.class);

	/* Abbreviations
	 * req = Request
	 * res = Response
	 */
	private final MustacheTemplateEngine templateEngine;

	private final Database database;

	public SparkApplication() {
		LOGGER.info("start");

		boolean runInDeveloperWorkspace = isDevelopmentMode();
		templateEngine = createTemplateEngine(runInDeveloperWorkspace);

		// https://github.com/tipsy/spark-ssl/blob/master/src/main/java/SslExample.java
		// https://docs.oracle.com/cd/E19509-01/820-3503/ggfen/index.html
		// https://community.letsencrypt.org/t/tutorial-java-keystores-jks-with-lets-encrypt/34754
		// https://tutorials-raspberrypi.de/raspberry-pi-ssl-zertifikat-kostenlos-mit-lets-encrypt-erstellen/
		// https://maximilian-boehm.com/en-gb/blog/create-a-java-keystore-jks-from-lets-encrypt-certificates-1884000/
		// https://www.noip.com/support/knowledgebase/install-ip-duc-onto-raspberry-pi/
		// domain and keystore password must be the same
		String keystoreFileName = "keystore.jks";
		boolean keystoreExists = new File(keystoreFileName).exists();
		LOGGER.info("keystoreExists: " + keystoreExists);
		if (keystoreExists) {
			Properties properties = readObleProperties();
			String keystorePassword = properties.getProperty("keystore.password");
			Spark.secure(keystoreFileName, keystorePassword, null, null);
		}
		int httpsPort = 443;
		int httpPort = 80;
		int port = keystoreExists ? httpsPort : httpPort;
		Spark.port(port);

		setStaticFileLocation(runInDeveloperWorkspace);

		File databaseFile = new File("object_lending.h2");
		database = new Database(databaseFile);

		Spark.before("*", (req, res) -> {
			if (!"localhost".equals(req.host())) {
				LOGGER.info("ip: " + req.ip() + "  path: " + req.pathInfo());
			}
		});

		setupPaths();

		// Without this exceptions would be unseen and the browser only shows 500 -
		// internal server error
		exception(Exception.class, (exception, req, res) -> {
			exception.printStackTrace();
			res.body("Exception:\n" + exception.getMessage());
		});

		notFound((req, res) -> {
			LOGGER.warn(req.pathInfo());
			res.redirect("/");
			return null;
		});

	}

	private Properties readObleProperties() {
		File file = new File("oble.properties");

		if (!file.exists()) {
			LOGGER.error("you must create file " + file.getAbsolutePath());
		}

		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(file));
		} catch (IOException e) {
			LOGGER.error("failed to load " + file.getAbsolutePath(), e);
		}
		return properties;
	}

	private MustacheTemplateEngine createTemplateEngine(boolean runInDeveloperWorkspace) {
		MustacheTemplateEngine engine;
		if (runInDeveloperWorkspace) {
			// reload templates with every refresh
			MustacheResolver x = new FileSystemResolver(new File("src/main/resources/templates"));
			DefaultMustacheFactory factory = new DefaultMustacheFactory(x) {

				// Override compile to disable caching
				@Override
				public Mustache compile(String name) {
					Mustache mustache = mc.compile(name);
					mustache.init();
					return mustache;
				}

			};
			engine = new MustacheTemplateEngine(factory);
		} else {
			engine = new MustacheTemplateEngine();
		}
		return engine;
	}

	private void setupPaths() {
		/*
		 * Security mechanism tutorial https://github.com/pac4j/spark-pac4j
		 *
		 * Example project https://github.com/pac4j/spark-pac4j-demo
		 *
		 */
		get("/", this::index);

		get("/all_objects", this::allObjectsGet);

		// consistency: request and response should be the first two parameters.

		String searchPostPath = "/search";
		get("/search", searchGet(searchPostPath));
		post(searchPostPath, this::searchPost);

		String postPath = "/register";
		String myObjectsPath = "/my_objects";
		get("/register", registerGet(postPath));
		post("/register", registerPost(myObjectsPath));

		Config config = new Pac4jConfigFactory(database.getService()).build();

		get("/login", login(config));
		LogoutRoute logoutRoute = new LogoutRoute(config, "/");
		get("/logout", logoutRoute);

		CallbackRoute callback = new CallbackRoute(config, null, true);
		get("/callback", callback);
		post("/callback", callback);

		String editProfilePath = "/edit_profile";
		get(editProfilePath, editProfileGet(editProfilePath));
		post(editProfilePath, editProfilePost(myObjectsPath));
		post("/delete_profile", deleteProfile(logoutRoute));

		String editObjectPath = "/edit_object/:" + PARAM_OBJECT_ID;
		get(editObjectPath, (req, res) -> {
			int id = getObjectId(req);
			return editObjectGet(id, req, res, editObjectPath);
		});
		post(editObjectPath, editObjectPost(myObjectsPath));

		before(myObjectsPath, new SecurityFilter(config, "FormClient"));

		get(myObjectsPath, this::userObjects);
		post("/delete_object/:" + PARAM_OBJECT_ID, (req, res) -> {
			int id = getObjectId(req);
			database.deleteObject(id);

			res.redirect(myObjectsPath);
			return res;
		});

		String addObjectPath = "/add_object";
		before(addObjectPath, new SecurityFilter(config, "FormClient"));
		get(addObjectPath, addObjectGet(addObjectPath));
		post(addObjectPath, addObjectPost(myObjectsPath));

		get("/image/:" + PARAM_OBJECT_ID, (req, res) -> {
			int id = getObjectId(req);
			byte[] image = database.getObjectImage(id);
			if (image != null) {
				ServletOutputStream outputStream = res.raw().getOutputStream();
				outputStream.write(image);
			}
			return res;
		});

		get("/object/:" + PARAM_OBJECT_ID, (req, res) -> {
			int id = getObjectId(req);
			return objectDetail(id, req, res);
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String allObjectsGet(Request req, Response res) {
		int page = Integer.parseInt(req.queryParamOrDefault("page", "1"));
		String title = "Alle Objekte";

		ResultPage<Object> objectsPage = database.getObjectsPage(page);

		String url = "";
		Map paginationModel = createPaginationModel(url, objectsPage.pageNumber, objectsPage.pageCount);

		final Map map = new HashMap<>();
		map.put("objects", objectsPage.list);
		map.put("pagination", paginationModel);
		ModelAndView model = new ModelAndView(map, "object_tiles.mustache");

		String headerAddition = "<link rel='stylesheet' type='text/css' href='style/object_tiles.css'>";

		return wrapInBaseHtml(title, model, req, res, headerAddition);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map createPaginationModel(String url, int currentPage, int pageCount) {
		HashMap model = new HashMap<>();
		model.put("url", url);
		ArrayList pages = new ArrayList<>();
		for (int i = 1; i <= pageCount; i++) {
			HashMap page = new HashMap<>();
			page.put("page", i);
			page.put("active", currentPage == i);
			pages.add(page);
		}
		model.put("pages", pages);
		return model;
	}

	private void setStaticFileLocation(boolean runInDeveloperWorkspace) {

		if (runInDeveloperWorkspace) {
			// http://sparkjava.com/documentation#how-do-i-enable-automatic-refresh-of-static-files
			Spark.staticFiles.externalLocation("src/main/resources/public");
		} else {
			Spark.staticFiles.location("/public");
		}
	}

	private boolean isDevelopmentMode() {
		File currentDir = new File(new File("").getAbsolutePath());
		LOGGER.info("working directory: " + currentDir.getPath());

		boolean runInDeveloperWorkspace = false;
		String[] list = currentDir.list();
		if (list != null) {
			List<String> fileNames = Arrays.asList(list);
			List<String> devEnvironmentFiles = Arrays.asList("src", "pom.xml");
			runInDeveloperWorkspace = fileNames.containsAll(devEnvironmentFiles);
		}
		return runInDeveloperWorkspace;
	}

	private String wrapInBaseHtml(String title, ModelAndView model, Request request, Response response) {
		String headerAddition = "";
		return wrapInBaseHtml(title, model, request, response, headerAddition);
	}

	private String wrapInBaseHtml(String title, ModelAndView model, Request request, Response response,
			String headerAddition) {
		String content = templateEngine.render(model);
		String baseTemplate = getBaseHtml();
		String navigationBar = navigationBar(request, response);
		String result = baseTemplate.replace("!!NAVIGATION!!", navigationBar)//
				.replace("!!HEADER_ADDITION!!", headerAddition)//
				.replace("!!TITLE!!", title)//
				.replace("!!CONTENT!!", content);

		return result;
	}

	private String getBaseHtml() {
		return Resource.getStringResource("/templates/base.html");
	}

	private Route addObjectGet(String postPath) {
		return (request, response) -> {
			List<Pair<String, Integer>> tagUsage = database.getTagUsage();
			List<String> tags = tagUsage.stream()//
					.map(Pair::getKey)//
					.collect(toList());
			final Map<String, java.lang.Object> map = new HashMap<>();
			map.put("add_object_post", postPath);
			map.put("tags", tags);
			ModelAndView content = new ModelAndView(map, "add_object.mustache");

			String headerAddition = "<link rel='stylesheet' type='text/css' href='style/add_object.css'>";
			return wrapInBaseHtml("Objekt hinzufügen", content, request, response, headerAddition);
		};
	}

	private Route addObjectPost(String redirectPath) {

		return (request, response) -> {
			CommonProfile profile = getProfile(request, response).get();
			String username = profile.getUsername();

			// https://github.com/tipsy/spark-file-upload/blob/master/src/main/java/UploadExample.java
			request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

			HttpServletRequest httpRequest = request.raw();
			httpRequest.getParts(); // This seems to have side effects. Without this the request.queryParams methods will return null.

			String name = request.queryParams("name");
			String description = request.queryParams("description");
			String tagString = request.queryParams("tags");

			Objects.requireNonNull(name, "name was null");

			Object object = new Object(name, description);

			Part part = httpRequest.getPart("file");
			byte[] image = null;
			if (part != null) {
				long size = part.getSize();
				if (size != 0) {
					try (InputStream input = part.getInputStream()) { // getPart needs to use same "name" as input field in form
						image = IOUtils.toByteArray(input);
					}
				}
			}

			List<String> tags = stringToList(tagString);
			database.addObject(username, object, image, tags);

			response.redirect(redirectPath);

			return null;
		};
	}

	private String index(Request request, Response response) {

		final Map<String, String> map = new HashMap<>();

		ModelAndView content = new ModelAndView(map, "index.mustache");
		return wrapInBaseHtml("Index", content, request, response);
	}

	@SuppressWarnings("unchecked")
	private String navigationBar(Request request, Response response) {
		Optional<CommonProfile> optionalProfile = getProfile(request, response);

		boolean isLoggedIn = optionalProfile.isPresent();

		@SuppressWarnings("rawtypes")
		final Map map = new HashMap<>();
		map.put("not_logged_in", Boolean.valueOf(!isLoggedIn));
		map.put("logged_in", isLoggedIn);
		if (isLoggedIn) {
			map.put("name", optionalProfile.get().getUsername());
		}

		ModelAndView content = new ModelAndView(map, "navigation.mustache");
		return templateEngine.render(content);
	}

	private Route registerGet(String postPath) {
		return (request, response) -> {
			String title = "Registrierung";

			final Map<String, String> map = new HashMap<>();
			map.put("post_path", postPath);
			ModelAndView model = new ModelAndView(map, "register.mustache");

			String headerAddition = INCLUDE_GET_LOCATION_JS;
			return wrapInBaseHtml(title, model, request, response, headerAddition);
		};
	}

	private Route registerPost(String redirectPath) {
		return (req, res) -> {
			String userName = req.queryParams("username");
			String password = req.queryParams("password");
			String email = req.queryParams("email");
			String location = req.queryParams("location");
			String contact = req.queryParams("contact");
			double latitude = Double.parseDouble(req.queryParams("latitude"));
			double longitude = Double.parseDouble(req.queryParams("longitude"));

			LOGGER.info("contact:" + contact);

			database.register(userName, password, email, location, contact);

			database.setPlaceCoordinates(userName, latitude, longitude);

			res.redirect(redirectPath);

			return null;
		};
	}

	private Route login(Config config) {
		return (req, res) -> {
			String title = "Login";
			final FormClient formClient = config.getClients().findClient(FormClient.class);
			String callbackUrl = formClient.getCallbackUrl();
			LOGGER.info("form() -> callbackUrl: " + callbackUrl);

			final Map<String, String> map = new HashMap<>();
			map.put("callbackUrl", callbackUrl);
			ModelAndView content = new ModelAndView(map, "loginForm.mustache");
			return wrapInBaseHtml(title, content, req, res);
		};
	}

	private String userObjects(Request request, Response response) {
		String title = "Meine Objekte";

		Optional<CommonProfile> optional = getProfile(request, response);
		CommonProfile profile = optional.get();
		String userName = profile.getUsername();
		LOGGER.info("userName: " + userName);

		List<Object> userObjects = database.getUserObjects(userName);

		final Map<String, List<Object>> map = new HashMap<>();
		map.put("objects", userObjects);
		ModelAndView content = new ModelAndView(map, "user_objects.mustache");

		return wrapInBaseHtml(title, content, request, response);
	}

	private Route searchGet(String postPath) {
		return (request, response) -> {
			String title = "Suche";

			List<Pair<String, Integer>> tagUsage = database.getTagUsage();

			final Map<String, java.lang.Object> map = new HashMap<>();
			map.put("post_path", postPath);
			map.put("tag_usage", tagUsage);
			ModelAndView content = new ModelAndView(map, "search.mustache");
			String headerAddition = INCLUDE_GET_LOCATION_JS;
			return wrapInBaseHtml(title, content, request, response, headerAddition);
		};
	}

	@SuppressWarnings("unchecked")
	private String searchPost(Request req, Response res) {
		String title = "Suchergebnisse";

		String name = req.queryParams("name");
		String description = req.queryParams("description");

		String latitudeString = (req.queryParams("latitude"));
		String longitudeString = (req.queryParams("longitude"));

		List<String> tags = stringToList(req.queryParams("tags"));
		List<String> tagsExlude = stringToList(req.queryParams("tags_exclude"));

		Location location = null;

		if (!latitudeString.isEmpty() || !longitudeString.isEmpty()) {
			double latitude = Double.parseDouble(latitudeString);
			double longitude = Double.parseDouble(longitudeString);
			location = new Location(latitude, longitude);
		}

		List<SearchResult> searchResult = database.search(location, name, description, tags, tagsExlude);
		Boolean hasDistance = searchResult.stream()//
				.findFirst()//
				.map(r -> r.getDistance() != null)//
				.orElse(false);

		@SuppressWarnings("rawtypes")
		final Map map = new HashMap<>();
		map.put("results", searchResult);
		map.put("hasDistance", hasDistance);
		ModelAndView content = new ModelAndView(map, "search_results.mustache");
		return wrapInBaseHtml(title, content, req, res);
	}

	private String objectDetail(int id, Request req, Response res) {
		ObjectDetail object = database.getObjectDetail(id);

		Objects.requireNonNull(object, "Es gibt kein Objekt mit id " + id);

		ModelAndView content = new ModelAndView(object, "object_detail.mustache");

		String leafletHeader = " <link rel='stylesheet' href='https://unpkg.com/leaflet@1.3.4/dist/leaflet.css' " + //
				" integrity='sha512-puBpdR0798OZvTTbP4A8Ix/l+A4dHDD0DGqYW6RQ+9jxkRFclaxxQb/SJAWZfWAkuyeQUytO7+7N4QKrDh+drA==' "//
				+ " crossorigin='' /> " + //
				// add leaflet.js after leaflet.css
				" <script src='https://unpkg.com/leaflet@1.3.4/dist/leaflet.js' " + //
				" integrity='sha512-nMMmRyTVoLYqjP9hrbed9S+FzjZHW5gY1TWCHA5ckwXZBadntCNs8kEqAWdrb9O7rxbCaA4lKTIWjDXZxflOcA==' "//
				+ " crossorigin='' /></script> ";

		String objectDetailCss = " <link rel='stylesheet' type='text/css' href='/style/object_detail.css'> ";

		String headerAddition = leafletHeader + objectDetailCss;

		String title = object.name;
		return wrapInBaseHtml(title, content, req, res, headerAddition);
	}

	private Optional<CommonProfile> getProfile(Request request, Response response) {
		final SparkWebContext context = new SparkWebContext(request, response);
		final ProfileManager<CommonProfile> manager = new ProfileManager<>(context);
		Optional<CommonProfile> optional = manager.get(true);
		return optional;
	}

	private Route editProfileGet(String postPath) {
		return (request, response) -> {
			String title = "Profil bearbeiten";
			CommonProfile profile = getProfile(request, response).get();
			String username = profile.getUsername();

			String contact = database.getContact(username);
			Location location = database.getLocation(username);

			Map<String, String> map = new HashMap<>();
			map.put("post_path", postPath);
			map.put("username", username);
			map.put("contact", contact);
			map.put("latitude", String.valueOf(location.latitude));
			map.put("longitude", String.valueOf(location.longitude));

			ModelAndView content = new ModelAndView(map, "edit_profile.mustache");
			return wrapInBaseHtml(title, content, request, response);
		};
	}

	private Route editProfilePost(String redirectPath) {
		return (req, res) -> {
			CommonProfile profile = getProfile(req, res).get();
			String username = profile.getUsername();

			String contact = req.queryParams("contact");

			database.updateContact(username, contact);

			double latitude = Double.parseDouble(req.queryParams("latitude"));
			double longitude = Double.parseDouble(req.queryParams("longitude"));
			database.setPlaceCoordinates(username, latitude, longitude);

			res.redirect(redirectPath);

			return null;
		};
	}

	private Route deleteProfile(LogoutRoute logoutRoute) {
		return (req, res) -> {
			CommonProfile profile = getProfile(req, res).get();
			String username = profile.getUsername();
			database.deleteUser(username);

			logoutRoute.handle(req, res);
			return null;
		};
	}

	/**
	 * @param id
	 * @param req
	 * @param res
	 * @param postPath
	 * @return
	 */
	private String editObjectGet(int id, Request req, Response res, String postPath) {
		String title = "Objekt bearbeiten";

		Object object = database.getObject(id);

		List<Tag> objectTags = database.getObjectTags(id);
		String tagString = objectTags.stream().map(Tag::getName).collect(Collectors.joining(", "));

		Map<String, String> map = new HashMap<>();
		map.put("post_path", postPath);
		map.put("name", object.name);
		map.put("description", object.description);
		map.put("id", String.valueOf(object.id));
		map.put("tags", tagString);

		ModelAndView content = new ModelAndView(map, "edit_object.mustache");
		return wrapInBaseHtml(title, content, req, res);
	}

	private Route editObjectPost(String redirectPath) {
		return (req, res) -> {

			int id = getObjectId(req);

			// https://github.com/tipsy/spark-file-upload/blob/master/src/main/java/UploadExample.java
			req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

			HttpServletRequest httpRequest = req.raw();
			httpRequest.getParts(); // This seems to have side effects. Without this the request.queryParams methods will return null.

			Part part = httpRequest.getPart("file");
			if (part != null) {
				long size = part.getSize();
				if (size != 0) {
					try (InputStream input = part.getInputStream()) { // getPart needs to use same "name" as input field in form
						byte[] image = IOUtils.toByteArray(input);
						database.setObjectImage(id, image);
					}
				}
			}

			String name = req.queryParams("name");
			String description = req.queryParams("description");
			String tagString = req.queryParams("tags");
			List<String> tags = stringToList(tagString);

			database.updateObject(id, name, description);

			database.saveTags(id, tags);

			res.redirect(redirectPath);

			return null;
		};
	}

	private List<String> stringToList(String string) {
		return Stream.of(string.split(","))//
				.map(String::trim)//
				.filter(s -> !s.isEmpty())// because "".split does not return an empty array
				.collect(Collectors.toList());
	}

	private int getObjectId(Request req) {
		String idString = req.params(PARAM_OBJECT_ID);
		return Integer.parseInt(idString);
	}

}
