package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;
import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.DefaultParticipantPlugin;
import org.joget.workflow.model.WorkflowActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RestParticipantMapper extends DefaultParticipantPlugin implements RestMixin {
	private final static String LABEL = "REST Participant Mapping";

	@Override
	public Collection<String> getActivityAssignments(Map props) {
		WorkflowActivity workflowActivity = (WorkflowActivity) props.get("workflowActivity");
		Collection<String> approver = new ArrayList<>();
		try {
			final String url = getPropertyUrl(null);
			final HttpClient client = getHttpClient(isIgnoreCertificateError());
			final HttpUriRequest request = getHttpRequest(null, url, getPropertyMethod(), getPropertyHeaders(null), null);
			HttpResponse response = client.execute(request);

			String responseContentType = getResponseContentType(response);

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
					LogUtil.warn(getClassName(), "Response [" + lines + "]");
				}
			}
		} catch (IOException | RestClientException ex) {
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
		return getLabel() + getVersion();
	}

	@Override
	public String getVersion() {
		PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
		ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
		String buildNumber = resourceBundle.getString("build.number");
		return buildNumber;
	}

	@Override
	public String getDescription() {
		return getClass().getPackage().getImplementationTitle();
	}
}
