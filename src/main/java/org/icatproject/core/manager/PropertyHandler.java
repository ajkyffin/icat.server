package org.icatproject.core.manager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;
import javax.naming.InitialContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.icatproject.authentication.Authentication;
import org.icatproject.authentication.Authenticator;
import org.icatproject.core.IcatException;
import org.icatproject.core.IcatException.IcatExceptionType;
import org.icatproject.utils.CheckedProperties;
import org.icatproject.utils.CheckedProperties.CheckedPropertyException;
import org.icatproject.utils.ContainerGetter;
import org.icatproject.utils.ContainerGetter.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@Singleton
public class PropertyHandler {

	public class RestAuthenticator implements Authenticator {

		private String mnemonic;
		private List<String> urls;

		public RestAuthenticator(String mnemonic, String urls) throws IcatException {
			this.mnemonic = mnemonic;
			this.urls = Arrays.asList(urls.split("\\s+"));
			String desc = null;
			for (String url : this.urls) {
				try {
					URI uri = new URIBuilder(url).setPath("/authn." + mnemonic + "/" + "description").build();

					logger.trace("Calling " + uri);
					try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
						HttpGet httpGet = new HttpGet(uri);
						try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
							String resp = getString(response);
							if (desc == null) {
								desc = resp;
							} else if (!desc.equals(resp)) {
								throw new IcatException(IcatExceptionType.INTERNAL,
										"authenticators have mismatched descriptions");
							}
						}
					}
				} catch (URISyntaxException | IOException | IcatException e) {
					logger.error(e.getClass() + " " + e.getMessage());
				}
			}
			if (desc == null) {
				throw new IcatException(IcatExceptionType.INTERNAL,
						"No authenticator of type " + mnemonic + " is working");
			}
		}

		@Override
		public Authentication authenticate(Map<String, String> credentials, String ip) throws IcatException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (JsonGenerator gen = Json.createGenerator(baos)) {
				gen.writeStartObject();
				gen.writeStartArray("credentials");
				for (Entry<String, String> entry : credentials.entrySet()) {
					gen.writeStartObject().write(entry.getKey(), entry.getValue()).writeEnd();
				}
				gen.writeEnd();
				gen.write("ip", ip);
				gen.writeEnd().close();
			}
			for (String url : this.urls) {
				try {
					URI uri = new URIBuilder(url).setPath("/authn." + mnemonic + "/" + "authenticate").build();

					List<NameValuePair> formparams = new ArrayList<>();
					formparams.add(new BasicNameValuePair("json", baos.toString()));
					try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
						HttpPost httpPost = new HttpPost(uri);
						httpPost.setEntity(new UrlEncodedFormEntity(formparams));
						try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
							try (JsonReader r = Json
									.createReader(new ByteArrayInputStream(getString(response).getBytes()))) {
								JsonObject o = r.readObject();
								String username = o.getString("username");
								String mechanism = null;
								if (o.containsKey("mechanism")) {
									mechanism = o.getString("mechanism");
								}
								return new Authentication(username, mechanism);
							}
						}
					}
				} catch (URISyntaxException | IOException | IcatException e) {
					throw new IcatException(IcatExceptionType.INTERNAL,
							"No authenticator of type " + mnemonic + " is working");
				}
			}
			throw new IcatException(IcatExceptionType.INTERNAL, "No authenticator of type " + mnemonic + " is working");
		}

		@Override
		public String getDescription() throws IcatException {
			for (String url : this.urls) {
				try {
					URI uri = new URIBuilder(url).setPath("/authn." + mnemonic + "/" + "description").build();
					try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
						HttpGet httpGet = new HttpGet(uri);
						try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
							return getString(response);
						}
					}
				} catch (URISyntaxException | IOException | IcatException e) {
					logger.error(e.getClass() + " " + e.getMessage());
				}
			}
			throw new IcatException(IcatExceptionType.INTERNAL, "No authenticator of type " + mnemonic + " is working");
		}

		private String getString(CloseableHttpResponse response) throws IcatException {
			checkStatus(response);
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				throw new IcatException(IcatExceptionType.INTERNAL, "No http entity returned in response");
			}
			try {
				return EntityUtils.toString(entity);
			} catch (ParseException | IOException e) {
				throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
			}
		}

		private void checkStatus(HttpResponse response) throws IcatException {
			StatusLine status = response.getStatusLine();
			if (status == null) {
				throw new IcatException(IcatExceptionType.INTERNAL, "Status line returned is empty");
			}
			int rc = status.getStatusCode();
			if (rc / 100 != 2) {
				HttpEntity entity = response.getEntity();
				String error;
				if (entity == null) {
					throw new IcatException(IcatExceptionType.INTERNAL, "No explanation provided");
				} else {
					try {
						error = EntityUtils.toString(entity);
					} catch (ParseException | IOException e) {
						throw new IcatException(IcatExceptionType.INTERNAL, e.getClass() + " " + e.getMessage());
					}
				}
				try (JsonParser parser = Json.createParser(new ByteArrayInputStream(error.getBytes()))) {
					String code = null;
					String message = null;
					String key = "";
					while (parser.hasNext()) {
						JsonParser.Event event = parser.next();
						if (event == Event.KEY_NAME) {
							key = parser.getString();
						} else if (event == Event.VALUE_STRING) {
							if (key.equals("code")) {
								code = parser.getString();
							} else if (key.equals("message")) {
								message = parser.getString();
							}
						}
					}

					if (code == null || message == null) {
						throw new IcatException(IcatExceptionType.INTERNAL, error);
					}
					throw new IcatException(IcatExceptionType.INTERNAL, message);
				} catch (JsonParsingException e) {
					throw new IcatException(IcatExceptionType.INTERNAL, error);
				}
			}

		}

	}

	public enum CallType {
		READ, WRITE, SESSION, INFO
	}

	public class ExtendedAuthenticator {

		private Authenticator authenticator;
		private String friendly;
		private boolean admin;

		public ExtendedAuthenticator(Authenticator authenticator, String friendly, boolean admin) {
			this.authenticator = authenticator;
			this.friendly = friendly;
			this.admin = admin;
		}

		public Authenticator getAuthenticator() {
			return authenticator;
		}

		public String getFriendly() {
			return friendly;
		}

		public boolean isAdmin() {
			return admin;
		}

	}

	private class HostPort {

		private String host;
		private Integer port;

		public HostPort(CheckedProperties props, String key) throws CheckedPropertyException {
			if (props.has(key)) {
				String hostPortString = props.getString(key);
				String[] bits = hostPortString.split(":");
				host = bits[0];
				try {
					port = Integer.parseInt(bits[1]);
				} catch (NumberFormatException e) {
					abend(e.getClass() + e.getMessage());
				}
				try {
					String hostName = InetAddress.getLocalHost().getHostName();
					if (hostName.equalsIgnoreCase(bits[0])) {
						host = null;
						port = null;
						logger.debug(key + " is local machine so is ignored");
					}
				} catch (UnknownHostException e) {
					abend(e.getClass() + e.getMessage());
				}
				formattedProps.add(key + " " + hostPortString);
			}

		}

		public String getHost() {
			return host;
		}

		public Integer getPort() {
			return port;
		}

	}

	public enum Operation {
		C, U
	}

	private final static Logger logger = LoggerFactory.getLogger(PropertyHandler.class);
	private final static Marker fatal = MarkerFactory.getMarker("FATAL");
	private final static Pattern cuPattern = Pattern.compile("[CU]*");

	private Map<String, ExtendedAuthenticator> authPlugins = new LinkedHashMap<>();

	public Map<String, ExtendedAuthenticator> getAuthPlugins() {
		return authPlugins;
	}

	private Set<String> rootUserNames = new HashSet<String>();

	private Map<String, NotificationRequest> notificationRequests = new HashMap<String, NotificationRequest>();

	public Set<String> getRootUserNames() {
		return rootUserNames;
	}

	public int getLifetimeMinutes() {
		return lifetimeMinutes;
	}

	private int lifetimeMinutes;

	private Set<CallType> logSet = new HashSet<>();
	private String luceneDirectory;
	private int luceneCommitSeconds;

	private List<String> formattedProps = new ArrayList<String>();
	private int luceneCommitCount;

	private String luceneHost;
	private Integer lucenePort;
	private int maxEntities;
	private int maxIdsInQuery;
	private long importCacheSize;
	private long exportCacheSize;
	private ContainerType containerType;
	private String jmsTopicConnectionFactory;
	private String digestKey;

	@PostConstruct
	private void init() {
		CheckedProperties props = new CheckedProperties();
		try {
			props.loadFromFile("icat.properties");
			logger.info("Property file icat.properties loaded");
			/* log4j.properties */
			String key = "log4j.properties";
			if (props.has(key)) {
				String path = props.getString("log4j.properties");
				formattedProps.add("log4j.properties " + path);
			}

			/* The authn.list */
			String authnList = props.getString("authn.list");
			formattedProps.add("authn.list " + authnList);

			for (String mnemonic : authnList.split("\\s+")) {
				Authenticator authen = null;
				key = "authn." + mnemonic + ".jndi";
				if (props.has(key)) {
					String jndi = props.getString(key);
					formattedProps.add(key + " " + jndi);
					key = "authn." + mnemonic + ".hostPort";
					if (props.has(key)) {
						abend("Key  '" + key + " specified in icat.properties is no longer permitted");
					}
					try {
						authen = (Authenticator) new InitialContext().lookup(jndi);
					} catch (Throwable e) {
						abend(e.getClass() + " reports " + e.getMessage());
					}
					logger.debug("Found Authenticator: " + mnemonic + " with jndi " + jndi);
				} else {
					key = "authn." + mnemonic + ".url";
					String urls = props.getString(key);
					try {
						authen = new RestAuthenticator(mnemonic, urls);
					} catch (IcatException e) {
						abend(e.getClass() + " " + e.getMessage());
					}
					logger.error(key + " " + urls);
					formattedProps.add(key + " " + urls);
				}

				key = "authn." + mnemonic + ".friendly";
				String friendly = null;
				if (props.has(key)) {
					friendly = props.getString(key);
					formattedProps.add(key + " " + friendly);
				}

				key = "authn." + mnemonic + ".admin";
				boolean admin = props.getBoolean(key, false);
				if (props.has(key)) {
					formattedProps.add(key + " " + admin);
				}

				ExtendedAuthenticator authenticator = new ExtendedAuthenticator(authen, friendly, admin);
				authPlugins.put(mnemonic, authenticator);

			}

			/* lifetimeMinutes */
			lifetimeMinutes = props.getPositiveInt("lifetimeMinutes");
			formattedProps.add("lifetimeMinutes " + lifetimeMinutes);

			/* rootUserNames */
			String names = props.getString("rootUserNames");
			for (String name : names.split("\\s+")) {
				rootUserNames.add(name);
			}
			formattedProps.add("rootUserNames " + names);

			/* notification.list */
			key = "notification.list";
			if (props.has(key)) {
				String notificationList = props.getString(key);
				formattedProps.add(key + " " + notificationList);

				EntityInfoHandler ei = EntityInfoHandler.getInstance();
				for (String entity : notificationList.split("\\s+")) {
					try {
						ei.getEntityInfo(entity);
					} catch (IcatException e) {
						String msg = "Value '" + entity + "' specified in 'notification.list' is not an ICAT entity";
						logger.error(fatal, msg);
						throw new IllegalStateException(msg);
					}
					key = "notification." + entity;
					String notificationOps = props.getString(key);

					formattedProps.add(key + " " + notificationOps);

					Matcher m = cuPattern.matcher(notificationOps);
					if (!m.matches()) {
						String msg = "Property  '" + key + "' must only contain the letters C and U";
						logger.error(fatal, msg);
						throw new IllegalStateException(msg);
					}
					for (String c : new String[] { "C", "U" }) {
						if (notificationOps.indexOf(c) >= 0) {
							notificationRequests.put(entity + ":" + c,
									new NotificationRequest(Operation.valueOf(Operation.class, c), entity));
						}
					}
				}
				logger.info("notification.list: {}", notificationList);
			} else {
				logger.info("'notification.list' entry not present so no notifications will be sent");
			}

			/* Call logging categories */
			key = "log.list";
			if (props.has(key)) {
				String callLogs = props.getString(key);
				formattedProps.add(key + " " + callLogs);
				for (String callTypeString : callLogs.split("\\s+")) {
					try {
						logSet.add(CallType.valueOf(callTypeString.toUpperCase()));
					} catch (IllegalArgumentException e) {
						String msg = "Value " + callTypeString + " in log.list must be chosen from "
								+ Arrays.asList(CallType.values());
						logger.error(fatal, msg);
						throw new IllegalStateException(msg);
					}
				}
				logger.info("log.list: {}", logSet);
			} else {
				logger.info("'log.list' entry not present so no JMS call logging will be performed");
			}

			/* Lucene Host */
			HostPort hostPort = new HostPort(props, "lucene.hostPort");
			luceneHost = hostPort.getHost();
			lucenePort = hostPort.getPort();

			/* Lucene Directory */
			key = "lucene.directory";
			if (props.has(key)) {
				luceneDirectory = props.getString(key);
				formattedProps.add(key + " " + luceneDirectory);
				luceneCommitSeconds = props.getPositiveInt("lucene.commitSeconds");
				formattedProps.add("lucene.commitSeconds " + luceneCommitSeconds);
				luceneCommitCount = props.getPositiveInt("lucene.commitCount");
				formattedProps.add("lucene.commitCount " + luceneCommitCount);
			}

			/*
			 * maxEntities, importCacheSize, exportCacheSize, maxIdsInQuery, key
			 */
			maxEntities = props.getPositiveInt("maxEntities");
			formattedProps.add("maxEntities " + maxEntities);

			importCacheSize = props.getPositiveLong("importCacheSize");
			formattedProps.add("importCacheSize " + importCacheSize);

			exportCacheSize = props.getPositiveLong("exportCacheSize");
			formattedProps.add("exportCacheSize " + exportCacheSize);

			maxIdsInQuery = props.getPositiveInt("maxIdsInQuery");
			formattedProps.add("maxIdsInQuery " + maxIdsInQuery);

			if (props.has("key")) {
				digestKey = props.getString("key");
				formattedProps.add("key " + digestKey);
				logger.info("Key is " + (digestKey == null ? "not set" : "set"));
			}

			/* JMS stuff */
			jmsTopicConnectionFactory = props.getString("jms.topicConnectionFactory",
					"java:comp/DefaultJMSConnectionFactory");
			formattedProps.add("jms.topicConnectionFactory " + jmsTopicConnectionFactory);

			/* find type of container and set flags */
			containerType = ContainerGetter.getContainer();
			logger.info("ICAT has been deployed in a " + containerType + " container");
			if (containerType == ContainerType.UNKNOWN) {
				abend("Container type " + containerType + " is not recognised");
			}
		} catch (CheckedPropertyException e) {
			abend(e.getMessage());
		}

	}

	private void abend(String msg) {
		logger.error(fatal, msg);
		throw new IllegalStateException(msg);
	}

	public Map<String, NotificationRequest> getNotificationRequests() {
		return notificationRequests;
	}

	public Set<CallType> getLogSet() {
		return logSet;
	}

	public String getLuceneDirectory() {
		return luceneDirectory;
	}

	public int getLuceneRefreshSeconds() {
		return luceneCommitSeconds;
	}

	public List<String> props() {
		return formattedProps;
	}

	public int getLuceneCommitCount() {
		return luceneCommitCount;
	}

	public String getLuceneHost() {
		return luceneHost;
	}

	public Integer getLucenePort() {
		return lucenePort;
	}

	public int getMaxEntities() {
		return maxEntities;
	}

	public int getMaxIdsInQuery() {
		return maxIdsInQuery;
	}

	public long getImportCacheSize() {
		return importCacheSize;
	}

	public long getExportCacheSize() {
		return exportCacheSize;
	}

	public ContainerType getContainerType() {
		return containerType;
	}

	public String getJmsTopicConnectionFactory() {
		return jmsTopicConnectionFactory;
	}

	public String getKey() {
		return digestKey;
	}

}
