package org.icatproject.core.manager;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import javax.naming.InitialContext;

import org.icatproject.authentication.Authenticator;
import org.icatproject.core.IcatException;
import org.icatproject.core.manager.search.SearchManager;
import org.icatproject.utils.CheckedProperties;
import org.icatproject.utils.CheckedProperties.CheckedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@ApplicationScoped
public class PropertyHandler {

	public enum CallType {
		READ, WRITE, SESSION, INFO
	}

	public enum SearchEngine {
		LUCENE, ELASTICSEARCH, OPENSEARCH
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

	/**
	 * Configure which entities will be indexed on ingest
	 */
	private Set<String> entitiesToIndex = new HashSet<String>();

	public Set<String> getEntitiesToIndex() {
		return entitiesToIndex;
	}

	public int getLifetimeMinutes() {
		return lifetimeMinutes;
	}

	private int lifetimeMinutes;

	private Set<CallType> logSet = new HashSet<>();

	private List<String> formattedProps = new ArrayList<String>();

	private int maxEntities;
	private int maxIdsInQuery;
	private long importCacheSize;
	private long exportCacheSize;
	private String jmsTopicConnectionFactory;
	private String digestKey;
	private SearchEngine searchEngine;
	private List<URL> searchUrls = new ArrayList<>();
	private int searchPopulateBlockSize;
	private int searchSearchBlockSize;
	private Path searchDirectory;
	private long searchBacklogHandlerIntervalMillis;
	private long searchAggregateFilesIntervalMillis;
	private long searchMaxSearchTimeMillis;
	private String unitAliasOptions;
	private Map<String, String> cluster = new HashMap<>();
	private long searchEnqueuedRequestIntervalMillis;
	private int searchIndexBatchSize;
	private int searchIndexBatchesPerTimer;
	private int searchBacklogLinesPerTimer;
	private long searchQueueFileMaxSize;

	@PostConstruct
	void init() {
		CheckedProperties props = new CheckedProperties();
		try {
			props.loadFromResource("run.properties");
			logger.info("Property file run.properties loaded");
			String key;

			/* The authn.list */
			String authnList = props.getString("authn.list");
			formattedProps.add("authn.list " + authnList);

			for (String mnemonic : authnList.split("\\s+")) {
				Authenticator authen = null;
				String keyJndi = "authn." + mnemonic + ".jndi";
				String keyUrl = "authn." + mnemonic + ".url";
				if (props.has(keyJndi) && props.has(keyUrl)) {
					abend("Both " + keyJndi + " and " + keyUrl + " have been specified in run.properties");
				}
				if (props.has(keyJndi)) {
					String jndi = props.getString(keyJndi);
					formattedProps.add(keyJndi + " " + jndi);
					String hpKey = "authn." + mnemonic + ".hostPort";
					if (props.has(hpKey)) {
						abend("Key  '" + hpKey + " specified in run.properties is no longer permitted");
					}
					try {
						authen = (Authenticator) new InitialContext().lookup(jndi);
					} catch (Throwable e) {
						abend(e.getClass() + " reports " + e.getMessage());
					}
					logger.debug("Found Authenticator: " + mnemonic + " with jndi " + jndi);
				} else {
					String urls = props.getString(keyUrl);
					try {
						authen = new RestAuthenticator(mnemonic, urls);
					} catch (IcatException e) {
						abend(e.getClass() + " " + e.getMessage());
					}
					formattedProps.add(keyUrl + " = " + urls);
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

			/* entitiesToIndex */
			key = "search.entitiesToIndex";
			if (props.has(key)) {
				String indexableEntities = props.getString(key);
				for (String indexableEntity : indexableEntities.split("\\s+")) {
					entitiesToIndex.add(indexableEntity);
				}
				logger.info("search.entitiesToIndex: {}", entitiesToIndex.toString());
			} else {
				/*
				 * If the property is not specified, we default to all the entities which
				 * currently override the EntityBaseBean.getDoc() method. This should
				 * result in no change to behaviour if the property is not specified.
				 */
				entitiesToIndex.addAll(Arrays.asList("Datafile", "DatafileFormat", "DatafileParameter",
						"Dataset", "DatasetParameter", "DatasetType", "DatasetTechnique", "Facility", "Instrument",
						"InstrumentScientist", "Investigation", "InvestigationInstrument", "InvestigationParameter",
						"InvestigationType", "InvestigationUser", "ParameterType", "Sample", "SampleType",
						"SampleParameter", "User"));
				logger.info("search.entitiesToIndex not set. Defaulting to: {}", entitiesToIndex.toString());
			}
			formattedProps.add("search.entitiesToIndex " + entitiesToIndex.toString());

			/* notification.list */
			key = "notification.list";
			if (props.has(key)) {
				String notificationList = props.getString(key);
				formattedProps.add(key + " " + notificationList);

				for (String entity : notificationList.split("\\s+")) {
					try {
						EntityInfoHandler.getEntityInfo(entity);
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

			/* Search Host */
			if (props.has("search.engine")) {
				try {
					searchEngine = SearchEngine.valueOf(props.getString("search.engine").toUpperCase());
				} catch (IllegalArgumentException e) {
					String msg = "Value " + props.getString("search.engine") + " of search.engine must be chosen from "
							+ Arrays.asList(SearchEngine.values());
					throw new IllegalStateException(msg);
				}

				for (String urlString : props.getString("search.urls").split("\\s+")) {
					try {
						searchUrls.add(new URL(urlString));
					} catch (MalformedURLException e) {
						abend("Url in search.urls " + urlString + " is not a valid URL");
					}
				}

				// In principle, clustered engines like OPENSEARCH or ELASTICSEARCH should
				// support multiple urls for the nodes in the cluster, however this is not yet
				// implemented
				if (searchUrls.size() != 1) {
					String msg = "Exactly one value for search.urls must be provided when using " + searchEngine;
					throw new IllegalStateException(msg);
				}
				formattedProps.add("search.urls" + " " + searchUrls.toString());
				logger.info("Using {} as search engine with url(s) {}", searchEngine, searchUrls);

				searchPopulateBlockSize = props.getPositiveInt("search.populateBlockSize");
				formattedProps.add("search.populateBlockSize" + " " + searchPopulateBlockSize);

				searchSearchBlockSize = props.getPositiveInt("search.searchBlockSize");
				formattedProps.add("search.searchBlockSize" + " " + searchSearchBlockSize);

				searchDirectory = props.getPath("search.directory");
				if (!searchDirectory.toFile().isDirectory()) {
					String msg = searchDirectory + " is not a directory";
					logger.error(fatal, msg);
					throw new IllegalStateException(msg);
				}
				formattedProps.add("search.directory" + " " + searchDirectory);

				searchBacklogHandlerIntervalMillis = props.getPositiveLong("search.backlogHandlerIntervalSeconds");
				formattedProps.add("search.backlogHandlerIntervalSeconds" + " " + searchBacklogHandlerIntervalMillis);
				searchBacklogHandlerIntervalMillis *= 1000;

				searchEnqueuedRequestIntervalMillis = props.getPositiveLong("search.enqueuedRequestIntervalSeconds");
				formattedProps.add("search.enqueuedRequestIntervalSeconds" + " " + searchEnqueuedRequestIntervalMillis);
				searchEnqueuedRequestIntervalMillis *= 1000;

				searchAggregateFilesIntervalMillis = props.getNonNegativeLong("search.aggregateFilesIntervalSeconds");
				searchAggregateFilesIntervalMillis *= 1000;

				searchMaxSearchTimeMillis = props.getPositiveLong("search.maxSearchTimeSeconds");
				formattedProps.add("search.maxSearchTimeSeconds" + " " + searchMaxSearchTimeMillis);
				searchMaxSearchTimeMillis *= 1000;
			} else {
				logger.info("'search.engine' entry not present so no free text search available");
			}

			unitAliasOptions = props.getString("units", "");

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

			key = "cluster";
			if (props.has(key)) {
				String clusterString = props.getString(key);
				formattedProps.add(key + " " + clusterString);
				cluster = new HashMap<>();
				for (String urlString : clusterString.split("\\s+")) {
					URL url = null;
					try {
						url = new URL(urlString);
					} catch (MalformedURLException e) {
						abend("Url in cluster " + urlString + " is not a valid URL");
					}
					String host = url.getHost();
					InetAddress address = null;
					try {
						address = InetAddress.getByName(host);
					} catch (UnknownHostException e) {
						abend("Host " + host + " in cluster specification is not known");
					}
					String hostAddress = address.getHostAddress();
					try {
						if (hostAddress.equals(InetAddress.getLocalHost().getHostAddress())) {
							continue;
						}
					} catch (UnknownHostException e) {
						// Ignore
					}

					if (Arrays.asList("localhost.localdomain", "localhost", "127.0.0.1").contains(host)) {
						continue;
					}

					cluster.put(address.getHostAddress(), url.toExternalForm());
					logger.info("Cluster includes " + url.toExternalForm() + " " + hostAddress);
				}
			}

			/* JMS stuff */
			jmsTopicConnectionFactory = props.getString("jms.topicConnectionFactory",
					"java:comp/DefaultJMSConnectionFactory");
			formattedProps.add("jms.topicConnectionFactory " + jmsTopicConnectionFactory);

			key = "search.indexBatchSize";
			if (props.has(key)) {
				searchIndexBatchSize = props.getPositiveInt(key);
				formattedProps.add("search.indexBatchSize " + searchIndexBatchSize);
			} else {
				searchIndexBatchSize = SearchManager.DEFAULT_INDEX_BATCH_SIZE;
			}

			key = "search.indexBatchesPerTimer";
			if (props.has(key)) {
				searchIndexBatchesPerTimer = props.getPositiveInt(key);
				formattedProps.add("search.indexBatchesPerTimer " + searchIndexBatchesPerTimer);
			} else {
				searchIndexBatchesPerTimer = SearchManager.DEFAULT_INDEX_BATCHES_PER_TIMER;
			}

			key = "search.backlogLinesPerTimer";
			if (props.has(key)) {
				searchBacklogLinesPerTimer = props.getPositiveInt(key);
				formattedProps.add("search.backlogLinesPerTimer " + searchBacklogLinesPerTimer);
			} else {
				searchBacklogLinesPerTimer = SearchManager.DEFAULT_BACKLOG_LINES_PER_TIMER;
			}

			key = "search.queueFileMaxSize";
			if (props.has(key)) {
				searchQueueFileMaxSize = props.getPositiveLong(key);
				formattedProps.add("search.queueFileMaxSize " + searchQueueFileMaxSize);
			} else {
				searchQueueFileMaxSize = SearchManager.DEFAULT_QUEUE_FILE_MAX_SIZE;
			}
		} catch (CheckedPropertyException e) {
			abend(e.getMessage());
		}

	}

	public Map<String, String> getCluster() {
		return cluster;
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

	public List<String> props() {
		return formattedProps;
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

	public String getJmsTopicConnectionFactory() {
		return jmsTopicConnectionFactory;
	}

	public String getKey() {
		return digestKey;
	}

	public SearchEngine getSearchEngine() {
		return searchEngine;
	}

	public List<URL> getSearchUrls() {
		return searchUrls;
	}

	public int getSearchPopulateBlockSize() {
		return searchPopulateBlockSize;
	}

	public int getSearchSearchBlockSize() {
		return searchSearchBlockSize;
	}

	public long getSearchBacklogHandlerIntervalMillis() {
		return searchBacklogHandlerIntervalMillis;
	}

	public long getSearchEnqueuedRequestIntervalMillis() {
		return searchEnqueuedRequestIntervalMillis;
	}

	public long getSearchAggregateFilesIntervalMillis() {
		return searchAggregateFilesIntervalMillis;
	}

	public long getSearchMaxSearchTimeMillis() {
		return searchMaxSearchTimeMillis;
	}

	public Path getSearchDirectory() {
		return searchDirectory;
	}

	public String getUnitAliasOptions() {
		return unitAliasOptions;
	}

	public int getSearchIndexBatchSize() {
		return searchIndexBatchSize;
	}

	public int getSearchIndexBatchesPerTimer() {
		return searchIndexBatchesPerTimer;
	}

	public int getSearchBacklogLinesPerTimer() {
		return searchBacklogLinesPerTimer;
	}

	public long getSearchQueueFileMaxSize() {
		return searchQueueFileMaxSize;
	}
}
