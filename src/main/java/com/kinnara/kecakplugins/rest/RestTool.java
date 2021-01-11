package com.kinnara.kecakplugins.rest;

import com.google.gson.*;
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
		String json = AppUtil.readPluginResource((String)this.getClass().getName(), (String)"/properties/restTool.json", (Object[])arguments, (boolean)true, (String)"message/restTool");
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
			ApplicationContext appContext = AppUtil.getApplicationContext();
			WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
			WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

			String statusCodeworkflowVariable = String.valueOf(properties.get("statusCodeworkflowVariable"));

			try {
				final String url = getPropertyUrl(workflowAssignment);
				final HttpClient client = getHttpClient(isIgnoreCertificateError());
				final HttpEntity httpEntity = getRequestEntity(workflowAssignment, null);
				final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, null);
				final HttpResponse response = client.execute(request);

				HttpEntity entity = response.getEntity();
				if(entity == null) {
					throw new RestClientException("Empty response");
				}

				final int statusCode = getResponseStatus(response);
				if(getStatusGroupCode(statusCode) != 200) {
					throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success)");
				}

				final String responseContentType = getResponseContentType(response);

				try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String responseBody = br.lines().collect(Collectors.joining());

					if (isDebug()) {
						LogUtil.info(getClassName(), "Response Content-Type [" + responseContentType + "] body [" + responseBody + "]");
					}

					if (!Optional.ofNullable(statusCodeworkflowVariable).orElse("").isEmpty()) {
						workflowManager.processVariable(workflowAssignment.getProcessId(), statusCodeworkflowVariable, String.valueOf(statusCode));
					}

					if (!isJsonResponse(response)) {
						throw new RestClientException("Content-Type : [" + responseContentType + "] not supported");
					}

					final JsonElement completeElement;
					try {
						JsonParser parser = new JsonParser();
						completeElement = parser.parse(responseBody);
					} catch (JsonSyntaxException ex) {
						throw new RestClientException(ex);
					}

					Optional.ofNullable(properties.get("mapresponsetovariable"))
							.map(o -> (Object[]) o)
							.map(Arrays::stream)
							.orElse(Stream.empty())
							.map(o -> (Map<String, String>) o)
							.forEach(row -> {
								String[] responseVariables = row.get("responseValue").split("\\.");

//								JsonElement currentElement = completeElement;
//								for (String responseVariable : responseVariables) {
//									if (currentElement == null)
//										break;
//
//									currentElement = getJsonResultVariable(responseVariable, currentElement);
//								}
//
//								if (currentElement != null && currentElement.isJsonPrimitive()) {
//									if (isDebug())
//										LogUtil.info(getClassName(), "Setting workflow variable [" + row.get("workflowVariable") + "] with [" + currentElement.getAsString() + "]");
//									workflowManager.processVariable(workflowAssignment.getProcessId(), row.get("workflowVariable"), currentElement.getAsString());
//								}

								getJsonResultVariableValue(row.get("responseValue"), completeElement)
										.ifPresent(s -> {
											if (isDebug()) {
												LogUtil.info(getClassName(), "Setting workflow variable [" + row.get("workflowVariable") + "] with [" + s + "]");
											}

											workflowManager.processVariable(workflowAssignment.getProcessId(), row.get("workflowVariable"), s);
										});
							});

					// Form Binding
					String formDefId = getPropertyString("formDefId");
					if (!formDefId.isEmpty()) {
						Form form = generateForm(formDefId);

						try {
							String recordPath = getPropertyString("jsonRecordPath");
							Object[] fieldMapping = (Object[]) getProperty("fieldMapping");

							Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
							Map<String, Pattern> fieldPattern = new HashMap<String, Pattern>();
							for (Object o : fieldMapping) {
								Map<String, String> mapping = (Map<String, String>) o;
								Pattern pattern = Pattern.compile(mapping.get("jsonPath").replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
								fieldPattern.put(mapping.get("formField"), pattern);
							}

							AppService appService = (AppService) appContext.getBean("appService");
							FormRowSet result = new FormRowSet();
							String primaryKey = appService.getOriginProcessId(workflowAssignment.getProcessId());
							parseJson("", completeElement, recordPattern, fieldPattern, true, result, null, getPropertyString("foreignKey"), primaryKey);

							if (isDebug()) {
								result.stream()
										.peek(r -> LogUtil.info(getClassName(), "-------Row Set-------"))
										.flatMap(r -> r.entrySet().stream())
										.forEach(e -> LogUtil.info(getClassName(), "key [" + e.getKey() + "] value [" + e.getValue() + "]"));
							}

							// save data to form
							form.getStoreBinder().store(form, result, new FormData());
						} catch (JsonSyntaxException ex) {
							LogUtil.error(getClassName(), ex, ex.getMessage());
						}
					}
				}
			} catch (IOException e) {
				LogUtil.error(getClassName(), e, e.getMessage());
			}
		} catch (RestClientException e) {
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
