package com.kinnara.kecakplugins.rest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

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
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.workflow.model.DefaultParticipantPlugin;
import org.mozilla.universalchardet.prober.statemachine.ISO2022JPSMModel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;

public class RestParticipantMapper extends DefaultParticipantPlugin{

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
					url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.*") ? "&" : "?" ,row.get("key"), row.get("value"));
				}
			}

			HttpClient client;
			if("true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"))) {
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
			Object[] headers = (Object[]) getProperty("headers");
			if(headers != null) {
				for(Object rowHeader : headers){
					Map<String, String> row = (Map<String, String>) rowHeader;
					request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), null, null, null));
				}
			}

			// kirim request ke server
			HttpResponse response = client.execute(request);
			String responseContentType = response.getEntity().getContentType().getValue();

			// get properties
			String recordPath = getPropertyString("recordPath");

			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			if(responseContentType.contains("application/json")) {
				LogUtil.info(getClassName(), "[REST-PARTICIPANT MAPPER JSON]");
				JsonParser parser = new JsonParser();
				JsonElement element = parser.parse(new JsonReader(new InputStreamReader(response.getEntity().getContent())));
				JsonHandler handler = new JsonHandler(element, recordPattern);
				FormRowSet fRS = handler.parse();
				if(element.isJsonArray()) {
					JsonArray jArray = element.getAsJsonArray();
//					System.out.println("[JSON ARRAY] "+jArray);
				}else {
					JsonObject jObj = element.getAsJsonObject();
					if(jObj.get(getPropertyString("recordPath")).isJsonArray()) {
						JsonArray arrData = (JsonArray) jObj.get(getPropertyString("recordPath"));
						for(JsonElement elm: arrData) {
							JsonObject objAppr = elm.getAsJsonObject();
//							System.out.println("[APPROVER] "+objAppr.get(getPropertyString("sfieldId")));
							approver.add(objAppr.get(getPropertyString("sfieldId")).getAsString());
						}
					}
					System.out.println("[JSON OBJECT] "+jObj);
				}
			}else {
				LogUtil.info(getClassName(), "[REST-PARTICIPANT MAPPER NOT JSON]");
			}
		} catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
			LogUtil.error(this.getClass().getName(), ex,ex.getMessage());
		}
		return approver;
	}

	@Override
	public String getLabel() {
		return this.getName();
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
		return "REST-Participant Mapper";
	}

	@Override
	public String getVersion() {
		return getClass().getPackage().getImplementationVersion();
	}

	@Override
	public String getDescription() {
		return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
	}

}
