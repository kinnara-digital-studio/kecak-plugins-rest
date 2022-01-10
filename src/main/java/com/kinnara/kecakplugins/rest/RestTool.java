package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.commons.Unclutter;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author aristo
 *
 */
public class RestTool extends DefaultApplicationPlugin implements RestMixin, Unclutter {
	private final static String LABEL = "REST Tool";

	public String getLabel() {
		return LABEL;
	}

	public String getClassName() {
		return getClass().getName();
	}

	public String getPropertyOptions() {
		AppDefinition appDef = AppUtil.getCurrentAppDefinition();
		String appId = appDef.getId();
		String appVersion = appDef.getVersion().toString();
		Object[] arguments = new Object[]{appId, appVersion, appId, appVersion, appId, appVersion};
		String json = AppUtil.readPluginResource(this.getClass().getName(), "/properties/restTool.json", arguments, true, "message/restTool");
		return json;
	}

	public String getName() {
		return getLabel() + getVersion();
	}

	public String getVersion() {
		return getClass().getPackage().getImplementationVersion();
	}

	public String getDescription() {
		return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
	}

	@Override
	public Object execute(Map properties) {
		try {
			final ApplicationContext appContext = AppUtil.getApplicationContext();
			final WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
			final WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

			final String statusCodeworkflowVariable = String.valueOf(properties.get("statusCodeworkflowVariable"));

			final String url = getPropertyUrl(workflowAssignment);
			final HttpClient client = getHttpClient(isIgnoreCertificateError());
			final HttpEntity httpEntity = getRequestEntity(workflowAssignment, null);
			final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, null);
			final HttpResponse response = client.execute(request);

			final HttpEntity entity = response.getEntity();
			if(entity == null) {
				throw new RestClientException("NULL response");
			}

			final String responseBody;
			try(final BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()))) {
				responseBody = br.lines().collect(Collectors.joining());
			} catch (IOException e) {
				throw new RestClientException(e);
			}

			final int statusCode = getResponseStatus(response);
			final String responseContentType = getResponseContentType(response);

			if (isDebug()) {
				LogUtil.info(getClassName(), "Response Status [" + statusCode + "] Content-Type [" + responseContentType + "] body [" + responseBody + "]");
			}

			if(getStatusGroupCode(statusCode) != 200) {
				throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success)");
			}

			if (!Optional.ofNullable(statusCodeworkflowVariable).orElse("").isEmpty()) {
				workflowManager.processVariable(workflowAssignment.getProcessId(), statusCodeworkflowVariable, String.valueOf(statusCode));
			}

			if (!isJsonResponse(response)) {
				throw new RestClientException("Content-Type : [" + responseContentType + "] not supported");
			}

			final JsonElement completeElement;
			try {
				completeElement = new JsonParser().parse(responseBody);
			} catch (JsonSyntaxException ex) {
				throw new RestClientException(ex);
			}

			Optional.ofNullable(properties.get("mapresponsetovariable"))
					.map(o -> (Object[]) o)
					.map(Arrays::stream)
					.orElseGet(Stream::empty)
					.map(o -> (Map<String, String>) o)
					.forEach(row -> {
						boolean doNotOverwriteIfValueEmpty = "true".equalsIgnoreCase(getPropertyString("doNotOverwriteIfValueEmpty"));
						getJsonResultVariableValue(row.get("responseValue"), completeElement)
								.filter(s -> !(s.isEmpty() && doNotOverwriteIfValueEmpty)) // do not write empty value
								.ifPresent(s -> {
									if (isDebug()) {
										LogUtil.info(getClassName(), "Setting workflow variable [" + row.get("workflowVariable") + "] with [" + s + "]");
									}

									workflowManager.activityVariable(workflowAssignment.getActivityId(), row.get("workflowVariable"), s);
								});
					});

			// Form Binding
			final String formDefId = getPropertyString("formDefId");
			if (formDefId.isEmpty()) {
				LogUtil.warn(getClassName(), "Error loading property [formDefId]");
				return null;
			}

			final Form form = generateForm(formDefId);
			final String recordPath = getPropertyString("jsonRecordPath");
			final Object[] fieldMapping = (Object[]) getProperty("fieldMapping");

			final Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			final Map<String, Pattern> fieldPattern = new HashMap<>();
			for (Object o : fieldMapping) {
				Map<String, String> mapping = (Map<String, String>) o;
				Pattern pattern = Pattern.compile(mapping.get("jsonPath").replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
				fieldPattern.put(mapping.get("formField"), pattern);
			}

			final AppService appService = (AppService) appContext.getBean("appService");
			final FormRowSet result = new FormRowSet();
			final String primaryKey = appService.getOriginProcessId(workflowAssignment.getProcessId());
			parseJson("", completeElement, recordPattern, fieldPattern, true, result, null, getPropertyString("foreignKey"), primaryKey);

			if (isDebug()) {
				result.stream()
						.peek(r -> LogUtil.info(getClassName(), "-------Row Set-------"))
						.flatMap(r -> r.entrySet().stream())
						.forEach(e -> LogUtil.info(getClassName(), "key [" + e.getKey() + "] value [" + e.getValue() + "]"));
			}

			// save data to form
			form.getStoreBinder().store(form, result, new FormData());
		} catch (IOException | RestClientException | JsonSyntaxException e) {
			LogUtil.error(getClassName(), e, e.getMessage());
		}

		return null;
	}

	/**
	 * Get property "parameters"
	 *
	 * @param properties
	 * @return
	 */
	private List<Map<String, String>> getParameters(Map<String, Object> properties) {
		return getGridProperties(properties, "parameters");
	}
}
