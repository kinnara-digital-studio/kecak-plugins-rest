package com.kinnara.kecakplugins.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

public class RestTool extends DefaultApplicationPlugin{
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
		return "1.1.1";
	}

	public String getDescription() {
		return "Kecak - " + LABEL;
	}

	@Override
	public Object execute(Map properties) {
	    ApplicationContext appContext = AppUtil.getApplicationContext();
	    WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
		WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
		
		String url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null).replaceAll("#", ":");
		String method = getPropertyString("method");
		String statusCodeworkflowVariable = getPropertyString("statusCodeworkflowVariable");
		String contentType = getPropertyString("contentType");
		String body = AppUtil.processHashVariable(getPropertyString("body"), wfAssignment, null, null);
		
		// Parameters
		Pattern p = Pattern.compile("https{0,1}://.+\\?.+=,*");
		Object[] parameters = (Object[]) getProperty("parameters");
		for(Object parameter : parameters) {
			Map<String, String> row = (Map<String, String>)parameter;
			
			// inflate hash variables
			String value = AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null);
			
			// if url already contains parameters, use &
			Matcher m = p.matcher(url.trim());
			try {
				url += String.format("%s%s=%s", m.find() ? "&" : "?" ,row.get("key"), URLEncoder.encode(value, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		
		HttpClient client = HttpClientBuilder.create().build();
		
		HttpRequestBase request = null;
		if("GET".equals(method)) {
			request = new HttpGet(url);
		} else if("POST".equals(method)) {
			request = new HttpPost(url);
		} else if("PUT".equals(method)) {
			request = new HttpPut(url);
		} else if("DELETE".equals(method)) {
			request = new HttpDelete(url);
		}
		
		Object[] headers = (Object[]) getProperty("headers");
		for(Object rowHeader : headers) {
			Map<String, String> row = (Map<String, String>) rowHeader;
			request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null));
		}
		
		// Force to use content type from select box
		request.removeHeaders("Content-Type");
		request.addHeader("Content-Type", contentType);
		
		if("POST".equals(method) || "PUT".equals(method)) {
			setHttpEntity((HttpEntityEnclosingRequestBase) request, body);
		}
		
		try {
			HttpResponse response = client.execute(request);
			LogUtil.info(getClassName(), "####Sending [" + method + "] request to : [" + url + "]");
			
			String responseContentType = response.getEntity().getContentType().getValue();
			
			BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			
			if(!statusCodeworkflowVariable.isEmpty())
				workflowManager.processVariable(wfAssignment.getProcessId(), statusCodeworkflowVariable, String.valueOf(response.getStatusLine().getStatusCode()));
			
			if(responseContentType.contains("application/json")) {
				JsonParser parser = new JsonParser();
				JsonElement completeElement = null;
				try {
					completeElement = parser.parse(br);
				} catch (JsonSyntaxException ex) {
					// do nothing
					LogUtil.error(getClassName(), ex, ex.getMessage());
				}
				
				Object[] responseBody = (Object[])getProperty("responseBody");
				for(Object rowResponseBody : responseBody) {
					Map<String, String> row = (Map<String, String>)rowResponseBody;
					String[] responseVariables = row.get("responseValue").split("\\.");
					JsonElement currentElement = completeElement;
					for(String responseVariable : responseVariables) {
						if(currentElement == null)
							break;
					
						currentElement = getJsonResultVariable(responseVariable, currentElement);
					}
					if(currentElement != null && currentElement.isJsonPrimitive()) {
						workflowManager.processVariable(wfAssignment.getProcessId(), row.get("workflowVariable"), currentElement.getAsString());
					}
				}	
				
				// Form Binding
				String formDefId = getPropertyString("formDefId");
				if(formDefId != null && !formDefId.isEmpty()) {
					Form form = generateForm(formDefId);
					
					if(form != null) {
						try {
							String recordPath = getPropertyString("jsonRecordPath");
							Object[] fieldMapping = (Object[])getProperty("fieldMapping");
							
							Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
							Map<String, Pattern> fieldPattern = new HashMap<String, Pattern>();
							for(Object o : fieldMapping) {
								Map<String, String> mapping = (Map<String, String>)o;
								Pattern pattern = Pattern.compile(mapping.get("jsonPath").replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
								fieldPattern.put(mapping.get("formField").toString(), pattern);
							}
							
							FormRowSet result = new FormRowSet();
							parseJson("", completeElement, recordPattern, fieldPattern, true, result, null);
							
							for(FormRow row : result) {
								System.out.println("------");
								for(Map.Entry<Object, Object> entry : row.entrySet()) {
									System.out.println(entry.getKey().toString() + "->" + entry.getValue().toString());
								}
							}
							
							// save data to form
							form.getStoreBinder().store(form, result, new FormData());
						} catch (JsonSyntaxException ex) {
							LogUtil.error(getClassName(), ex, ex.getMessage());
						}
					} else {
						LogUtil.warn(getClassName(), "Error generating form [" + formDefId + "]");
					}
				}
				
			} else {
				LogUtil.warn(getClassName(), "URL [" + request.getURI().toString()
						+ "] Response Content-Type : [" + responseContentType + "] not supported");
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private void parseJson(String path, JsonElement element, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {    	
    	Matcher matcher = recordPattern.matcher(path);    	
    	boolean isRecordPath = matcher.find() && isLookingForRecordPattern && element.isJsonObject();
    	
    	if(isRecordPath) {
    		// start looking for value and label pattern
    		row = new FormRow();
    	}
    	
    	if(element.isJsonObject()) {
    		parseJsonObject(path, (JsonObject)element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row);
    		if(isRecordPath && row != null)
    			rowSet.add(row);
    	} else if(element.isJsonArray()) {
    		parseJsonArray(path, (JsonArray)element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row);
    		if(isRecordPath && row != null)
    			rowSet.add(row);
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
	
	private void parseJsonObject(String path, JsonObject json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			parseJson(path + "." + entry.getKey(), entry.getValue(), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row);
		}
    }
    
    private void parseJsonArray(String path, JsonArray json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {    	
    	for(int i = 0, size = json.size(); i < size; i++) {
			parseJson(path, json.get(i), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row);
		}
    }
	
	/**
	 * 
	 * @param request : POST or PUT request
	 * @param entity : body content
	 */
	private void setHttpEntity(HttpEntityEnclosingRequestBase request, String entity) {
		try {
			request.setEntity(new StringEntity(entity));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
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
}