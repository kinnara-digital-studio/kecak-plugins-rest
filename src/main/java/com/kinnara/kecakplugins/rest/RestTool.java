package com.kinnara.kecakplugins.rest;

import com.google.gson.*;
import com.kinnara.kecakplugins.rest.commons.RestUtils;
import com.kinnara.kecakplugins.rest.commons.StuffedForm;
import com.kinnara.kecakplugins.rest.commons.Unclutter;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author aristo
 *
 */
public class RestTool extends DefaultApplicationPlugin implements RestUtils, Unclutter {
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
		return LABEL;
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
			PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
			FormDataDao formDataDao = (FormDataDao) pluginManager.getBean("formDataDao");
			WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

			final String url = String.valueOf(properties.get("url")).replaceAll("#", ":");
			String method = String.valueOf(properties.get("method"));
			String statusCodeworkflowVariable = String.valueOf(properties.get("statusCodeworkflowVariable"));
			String contentType = String.valueOf(properties.get("contentType"));
			String body = String.valueOf(properties.get("body"));
			final boolean debug = isDebug(properties);

			// Parameters
			final Pattern p = Pattern.compile("https?://.+\\?.+=,*");

			final StringBuilder urlBuilder = new StringBuilder(url.trim());
			List<Map<String, String>> parameters = getParameters(properties);
			parameters.forEach(throwableConsumer(row -> {
				// inflate hash variables
				String key = row.get("key");
				String value = row.get("value");

				// if url already contains parameters, use &
				Matcher m = p.matcher(urlBuilder.toString());
				urlBuilder.append(String.format("%s%s=%s", m.find() ? "&" : "?" ,key, URLEncoder.encode(value, "UTF-8")));
			}));

			HttpClient client = getHttpClient(getIgnoreCertificateError(properties));
			final HttpRequestBase request = getHttpRequest(urlBuilder.toString(), method);
			List<Map<String, String>> headers = getHeaders(properties);
			headers.forEach(throwableConsumer(row -> {
				// inflate hash variables
				String key = row.get("key");
				String value = row.get("value");

				request.addHeader(key, value);
			}));

			// Force to use content type from select box
			request.removeHeaders("Content-Type");
			request.addHeader("Content-Type", contentType);

			if(isNotEmpty(body) && ("POST".equals(method) || "PUT".equals(method))) {
				setHttpEntity((HttpEntityEnclosingRequestBase) request, body);
			}

			try {
				if(debug) {
					LogUtil.info(getClassName(), "REQUEST DETAILS : ");
					LogUtil.info(getClassName(), "REQUEST METHOD ["+request.getMethod()+"]");
					LogUtil.info(getClassName(), "REQUEST URI ["+request.getURI().toString()+"]");
					Arrays.stream(request.getAllHeaders()).forEach(h -> LogUtil.info(getClassName(), "REQUEST HEADER ["+h.getName()+"] ["+h.getValue()+"]"));
				}

				HttpResponse response = client.execute(request);
				HttpEntity entity = response.getEntity();

				if(entity == null) {
					LogUtil.warn(getClassName(), "Empty response");
					return null;
				}

				String responseContentType = entity.getContentType().getValue();

				int statusCode = response.getStatusLine().getStatusCode();
				LogUtil.info(getClassName(), "Response Status Code ["+statusCode+"]");

				try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String responseBody = br.lines().collect(Collectors.joining());

					if(debug) {
						LogUtil.info(getClassName(), "RESPONSE Content-Type [" + responseContentType + "] body [" + responseBody + "]");
					}

					if(!Optional.ofNullable(statusCodeworkflowVariable).orElse("").isEmpty()) {
						workflowManager.processVariable(wfAssignment.getProcessId(), statusCodeworkflowVariable, String.valueOf(statusCode));
					}

					if(!responseContentType.contains("application/json")) {
						LogUtil.warn(getClassName(), "URL [" + request.getURI().toString()
								+ "] Response Content-Type : [" + responseContentType + "] not supported [" + responseBody + "]");

						return null;
					}

					JsonParser parser = new JsonParser();
					JsonElement completeElement;
					try {
						completeElement = parser.parse(responseBody);
					} catch (JsonSyntaxException ex) {
						// do nothing
						LogUtil.error(getClassName(), ex, ex.getMessage());
						return null;
					}

					final StuffedForm stuffedForm = getForm(properties);
					final FormRow formRow = new FormRow();
					Optional.ofNullable(properties.get("mapresponsetovariable"))
							.map(o -> (Object[])o)
							.map(Arrays::stream)
							.orElse(Stream.empty())
							.map(o -> (Map<String, String>)o)
							.forEach(throwableConsumer(row -> {
								String[] responseVariables = row.get("responseValue").split("\\.");

								JsonElement currentElement = completeElement;
								for(String responseVariable : responseVariables) {
									if(currentElement == null)
										break;

									currentElement = getJsonResultVariable(responseVariable, currentElement);
								}

								if(currentElement != null && currentElement.isJsonPrimitive()) {
									if(debug)
										LogUtil.info(getClassName(), "Setting workflow variable ["+row.get("workflowVariable")+"] with ["+currentElement.getAsString()+"]");

									final String workflowVariable = row.get("workflowVariable");
									final String value = currentElement.getAsString();
									workflowManager.processVariable(wfAssignment.getProcessId(), workflowVariable, currentElement.getAsString());

									// Form Binding
									Optional.ofNullable(stuffedForm)
											.map(f -> elementStream(f.getForm(), f.getFormData()))
											.orElseGet(Stream::empty)
											.filter(e -> workflowVariable.equals(e.getPropertyString("workflowVariable")))
											.forEach(throwableConsumer(e -> {
												Objects.requireNonNull(stuffedForm);

												final String elementId = e.getPropertyString("id");
												formRow.put(elementId, value);
											}));
								}
							}));

					if(stuffedForm != null && isNotEmpty(formRow.entrySet())) {
						final FormRowSet rowSet = new FormRowSet();
						formRow.setId(stuffedForm.getFormData().getPrimaryKeyValue());
						rowSet.add(formRow);
						formDataDao.saveOrUpdate(stuffedForm.getForm(), rowSet);
					}

//					try {
//						String recordPath = getPropertyString("jsonRecordPath");
//						Object[] fieldMapping = (Object[])getProperty("fieldMapping");
//
//						Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
//						Map<String, Pattern> fieldPattern = new HashMap<String, Pattern>();
//						for(Object o : fieldMapping) {
//							Map<String, String> mapping = (Map<String, String>)o;
//							Pattern pattern = Pattern.compile(mapping.get("jsonPath").replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
//							fieldPattern.put(mapping.get("formField"), pattern);
//						}
//
//						AppService appService = (AppService)appContext.getBean("appService");
//						FormRowSet result = new FormRowSet();
//						String primaryKey = appService.getOriginProcessId(wfAssignment.getProcessId());
//						parseJson("", completeElement, recordPattern, fieldPattern, true, result, null, getPropertyString("foreignKey"), primaryKey);
//
//						if(debug) {
//							result.stream()
//									.peek(r -> LogUtil.info(getClassName(), "-------Row Set-------"))
//									.flatMap(r -> r.entrySet().stream())
//									.forEach(e -> LogUtil.info(getClassName(), "key ["+ e.getKey()+"] value ["+e.getValue()+"]"));
//						}
//
//						// save data to form
//						form.getStoreBinder().store(form, result, new FormData());
//					} catch (JsonSyntaxException ex) {
//						LogUtil.error(getClassName(), ex, ex.getMessage());
//					}
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
	 *
	 * @param path
	 * @param element
	 * @param recordPattern
	 * @param fieldPattern
	 * @param isLookingForRecordPattern
	 * @param rowSet
	 * @param row
	 * @param foreignKeyField
	 * @param primaryKey
	 */
	private void parseJson(String path, @Nonnull JsonElement element, @Nonnull Pattern recordPattern, @Nonnull Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, @Nonnull final FormRowSet rowSet, FormRow row, final String foreignKeyField, final String primaryKey) {
		Matcher matcher = recordPattern.matcher(path);
		boolean isRecordPath = matcher.find() && isLookingForRecordPattern && element.isJsonObject();

		if(isRecordPath) {
			// start looking for value and label pattern
			row = new FormRow();
		}

		if(element.isJsonObject()) {
			parseJsonObject(path, (JsonObject)element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
			if(isRecordPath) {
				if(foreignKeyField != null && !foreignKeyField.isEmpty())
					row.setProperty(foreignKeyField, primaryKey);
				rowSet.add(row);
			}
		} else if(element.isJsonArray()) {
			parseJsonArray(path, (JsonArray)element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
			if(isRecordPath) {
				if(foreignKeyField != null && !foreignKeyField.isEmpty())
					row.setProperty(foreignKeyField, primaryKey);
				rowSet.add(row);
			}
		} else if(element.isJsonPrimitive() && !isLookingForRecordPattern) {
			for(Map.Entry<String, Pattern> entry : fieldPattern.entrySet()) {
				setRow(entry.getValue().matcher(path), entry.getKey(), element.getAsString(), row);
			}
		}
	}

	private void setRow(Matcher matcher, String key, String value, FormRow row) {
		if(matcher.find() && row != null && row.getProperty(key) == null) {
			row.setProperty(key, value);
		}
	}

	private void parseJsonObject(String path, JsonObject json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row, String foreignKeyField, String primaryKey) {
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			parseJson(path + "." + entry.getKey(), entry.getValue(), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
		}
	}

	private void parseJsonArray(String path, JsonArray json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row, String foreignKeyField, String primaryKey) {
		for(int i = 0, size = json.size(); i < size; i++) {
			parseJson(path, json.get(i), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
		}
	}

	/**
	 *
	 * @param request : POST or PUT request
	 * @param entity : body content
	 */
	private void setHttpEntity(HttpEntityEnclosingRequestBase request, String entity) throws RestClientException {
		try {
			request.setEntity(new StringEntity(entity));
		} catch (UnsupportedEncodingException e) {
			throw new RestClientException(e);
		}
	}

	/**
	 *
	 * @param variable : variable name to search
	 * @param element : element to search for variable
	 * @return
	 */
	private JsonElement getJsonResultVariable(String variable, JsonElement element) {
		if(element.isJsonObject())
			return getJsonResultVariableFromObject(variable, element.getAsJsonObject());
		else if(element.isJsonArray())
			return getJsonResultVariableFromArray(variable, element.getAsJsonArray());
		else if(element.isJsonPrimitive())
			return element;
		return null;
	}

	private JsonElement getJsonResultVariableFromObject(String variable, JsonObject object) {
		return object.get(variable);
	}

	private JsonElement getJsonResultVariableFromArray(String variable, JsonArray array) {
		for(JsonElement item : array) {
			JsonElement result = getJsonResultVariable(variable, item);
			if(result != null) {
				return result;
			}
		}
		return null;
	}

	private Map<String, Form> formCache = new HashMap<String, Form>();

	private Form generateForm(String formDefId) {
		ApplicationContext appContext = AppUtil.getApplicationContext();
		FormService formService = (FormService) appContext.getBean("formService");
		FormDefinitionDao formDefinitionDao = (FormDefinitionDao)appContext.getBean("formDefinitionDao");

		// check in cache
		if(formCache != null && formCache.containsKey(formDefId))
			return formCache.get(formDefId);

		// proceed without cache    	
		Form form = null;
		AppDefinition appDef = AppUtil.getCurrentAppDefinition();
		if (appDef != null && formDefId != null && !formDefId.isEmpty()) {
			FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
			if (formDef != null) {
				String json = formDef.getJson();
				form = (Form)formService.createElementFromJson(json);

				// put in cache if possible
				if(formCache != null)
					formCache.put(formDefId, form);

				return form;
			}
		}
		return null;
	}

	/**
	 * Get property "ignoreCertificateError"
	 *
	 * @return
	 */
	private boolean getIgnoreCertificateError(Map<String, Object> properties) {
		return "true".equalsIgnoreCase(String.valueOf(properties.get("ignoreCertificateError")));
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

	/**
	 * Get property "headers"
	 *
	 * @param properties
	 * @return
	 */
	private List<Map<String, String>> getHeaders(Map<String, Object> properties) {
		return getGridProperties(properties, "headers");
	}

	private List<Map<String, String>> getGridProperties(Map<String, Object> properties, String propertyName) {
		return Optional.of(propertyName)
				.map(properties::get)
				.filter(o -> o instanceof Object[])
				.map(o -> (Object[])o)
				.map(Arrays::stream)
				.orElseGet(Stream::empty)
				.filter(o -> o instanceof Map)
				.map(o -> (Map<String, Object>)o)
				.map(m -> m.entrySet()
						.stream()
						.collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
				.collect(Collectors.toList());
	}


	/**
	 * Get property "formDefId"
	 *
	 * @param properties
	 * @return
	 * @throws RestClientException
	 */
	@Nullable
	private StuffedForm getForm(Map properties) {
		PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
		AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
		WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
		AppService appService = (AppService) pluginManager.getBean("appService");

		return Optional.of("formDefId")
				.map(properties::get)
				.map(String::valueOf)
				.map(s -> {
					FormData formData = new FormData();
					formData.setPrimaryKeyValue(getPrimaryKey(properties));
					if(workflowAssignment != null) {
						formData.setActivityId(workflowAssignment.getActivityId());
						formData.setProcessId(workflowAssignment.getProcessId());
					}

					Form form = appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), s, null, null, null, formData, null, null);

					return Optional.ofNullable(form)
							.map(f -> new StuffedForm(form, formData))
							.orElse(null);
				})
				.orElse(null);
	}

	/**
	 * Get property "debug"
	 *
	 * @param properties
	 * @return
	 */
	private boolean isDebug(Map<String, Object> properties) {
		return "true".equalsIgnoreCase(String.valueOf(properties.get("debug")));
	}
}
