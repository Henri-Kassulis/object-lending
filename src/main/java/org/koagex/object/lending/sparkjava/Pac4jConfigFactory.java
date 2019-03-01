package org.koagex.object.lending.sparkjava;

import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.matching.PathMatcher;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.sparkjava.DefaultHttpActionAdapter;

public class Pac4jConfigFactory implements ConfigFactory {

	private final Authenticator<?> authenticator;

	public Pac4jConfigFactory(Authenticator<?> authenticator) {
		this.authenticator = authenticator;
	}

	@Override
	public Config build(java.lang.Object... parameters) {

		//		int port = Spark.port();
		//		String host = "http://localhost:" + port;
		String loginUrl = "/login";
		final FormClient formClient = new FormClient(loginUrl, authenticator);
		Clients clients = new Clients("/callback", formClient);
		Config config = new Config(clients);
		config.addMatcher("user_profile", new PathMatcher());
		config.setHttpActionAdapter(new DefaultHttpActionAdapter());
		return config;
	}

}
