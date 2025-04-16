package org.icatproject.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

public class RunPropertiesConfigSource implements ConfigSource {

	private static final String FILENAME = "run.properties";
	private static final int ORDINAL = 200;
	private static final String PREFIX = "icat.";

	private Map<String,String> config = new HashMap<>();

	public RunPropertiesConfigSource() throws IOException {
		Properties properties = new Properties();

		try (InputStream inStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(FILENAME)) {
			if (inStream == null) {
				return;
			}

			properties.load(inStream);
		}

		properties.forEach((k, v) -> config.put(PREFIX + k.toString(), v.toString()));
	}

	@Override
	public String getName() {
		return FILENAME;
	}

	@Override
	public int getOrdinal() {
		return ORDINAL;
	}

	@Override
	public Map<String,String> getProperties() {
		return config;
	}

	@Override
	public Set<String> getPropertyNames() {
		return config.keySet();
	}

	@Override
	public String getValue(String propertyName) {
		return config.get(propertyName);
	}
}
