package org.icatproject.core.manager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestAuthenticator implements Authenticator {

	private static final Logger logger = LoggerFactory.getLogger(RestAuthenticator.class);

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
						Rest.checkStatus(response, IcatExceptionType.SESSION);
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
			} catch (URISyntaxException | IOException e) {
				logger.error("Authenticator of type", mnemonic, "reports", e.getClass().getName(), e.getMessage());
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
