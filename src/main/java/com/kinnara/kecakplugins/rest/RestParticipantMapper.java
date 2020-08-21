package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.workflow.model.DefaultParticipantPlugin;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestParticipantMapper extends DefaultParticipantPlugin{
	private final static String LABEL = "REST Participant Mapping";

	@Override
	public Collection<String> getActivityAssignments(Map props) {
		Collection<String> approver = new ArrayList<String>();
		try { 
			LogUtil.info(getClassName(), "[REST-PARTICIPANT MAPPER]");
			DirectoryManager directoryManager = (DirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
			String url = AppUtil.processHashVariable(getPropertyString("url"), null, null, null);

			// persiapkan parameter
			// mengkombine parameter ke url
			Object[] parameters = (Object[]) getProperty("parameters");
			if(parameters != null) {
				for(Object rowParameter : parameters){
					Map<String, String> row = (Map<String, String>) rowParameter;
					url += String.format("%s%s=%s", url.trim().matches("https?://.+\\?.*") ? "&" : "?" ,row.get("key"), row.get("value"));
				}
			}

			HttpClient client;
			if(isIgnoreCertificateError()) {
				SSLContext sslContext = new SSLContextBuilder()
						.loadTrustMaterial(null, (certificate, authType) -> true).build();
				client = HttpClients.custom().setSSLContext(sslContext)
						.setSSLHostnameVerifier(new NoopHostnameVerifier())
						.build();
			} else {
				client = HttpClientBuilder.create().build();
			}

			HttpRequestBase request = new HttpGet(url);

			// persiapkan HTTP header
			Optional.ofNullable(getProperty("headers"))
					.map(o -> (Object[])o)
					.map(Arrays::stream)
					.orElse(Stream.empty())
					.map(o -> (Map<String, String>)o)
					.filter(r -> !String.valueOf(r.get("key")).trim().isEmpty())
					.forEach(row -> request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), null, null, null)));

			// kirim request ke server
			HttpResponse response = client.execute(request);
			String responseContentType = response.getEntity().getContentType().getValue();

			// get properties
			String recordPath = getPropertyString("recordPath");

			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			if(responseContentType.contains("json")) {
				JsonParser parser = new JsonParser();
				JsonElement element = parser.parse(new JsonReader(new InputStreamReader(response.getEntity().getContent())));
				JsonHandler handler = new JsonHandler(element, recordPattern);
				FormRowSet fRS = handler.parse();
				if(element.isJsonArray()) {
					JsonArray jArray = element.getAsJsonArray();
				}else {
					JsonObject jObj = element.getAsJsonObject();
					if(jObj.get(getPropertyString("recordPath")).isJsonArray()) {
						JsonArray arrData = (JsonArray) jObj.get(getPropertyString("recordPath"));
						for(JsonElement elm: arrData) {
							JsonObject objAppr = elm.getAsJsonObject();
							approver.add(objAppr.get(getPropertyString("sfieldId")).getAsString());
						}
					}
				}
			}else {
				LogUtil.warn(getClassName(), "Unsupported content type [" + responseContentType + "]");
				try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String lines = br.lines().collect(Collectors.joining());
					LogUtil.info(getClassName(), "Response ["+lines+"]");
				}
			}
		} catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
			LogUtil.error(this.getClass().getName(), ex,ex.getMessage());
		}
		return approver;
	}

	@Override
	public String getLabel() {
		return LABEL;
	}

	@Override
	public String getClassName() {
		return this.getClass().getName();
	}

	@Override
	public String getPropertyOptions() {
		return AppUtil.readPluginResource(getClassName(), "/properties/restParticipantMapper.json", null, true, "message/restParticipantMapper");
	}

	@Override
	public String getName() {
		return LABEL;
	}

	@Override
	public String getVersion() {
		return getClass().getPackage().getImplementationVersion();
	}

	@Override
	public String getDescription() {
		return getClass().getPackage().getImplementationTitle();
	}

	/**
	 * Property "ignoreCertificateError"
	 *
	 * @return
	 */
	private boolean isIgnoreCertificateError() {
		return "true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"));
	}
}
