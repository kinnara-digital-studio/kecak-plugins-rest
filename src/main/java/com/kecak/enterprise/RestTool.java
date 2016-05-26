package com.kecak.enterprise;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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
		return "1.0";
	}

	public String getDescription() {
		return LABEL;
	}

	@Override
	public Object execute(Map properties) {
	    ApplicationContext appContext = AppUtil.getApplicationContext();
	    WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
		WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
		
		String url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null);
		String method = getPropertyString("method");
		String statusCodeworkflowVariable = getPropertyString("statusCodeworkflowVariable");
		String contentType = getPropertyString("contentType");
		String body = AppUtil.processHashVariable(getPropertyString("body"), wfAssignment, null, null);
		
		// Parameters
		FormRowSet parameters = (FormRowSet) getProperty("parameters");
		for(FormRow row : parameters) {
			// if url already contains parameters, use &	
			url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.+=,*") ? "&" : "?" ,row.getProperty("key"), row.getProperty("value"));
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
			} else if(responseContentType.contains("application/xml") || responseContentType.contains("text/xml")) {
				System.out.println("URL " + request.getURI().toString());
				System.out.println("Response Content-Type : " + responseContentType + " not supported" );
				
				try {
					DocumentBuilder builder = DocumentBuilderFactory.newInstance()
					        .newDocumentBuilder();
					
					// parse document from response input stream
					Document doc = builder.parse(response.getEntity().getContent());
					
					// normalize document
					doc.getDocumentElement().normalize();
					
					// get root node
					Node root = doc.getDocumentElement();
					
					// process response properties
					Object[] responseBody = (Object[])getProperty("responseBody");
					for(Object rowBody : responseBody) {
						Map<String, String> row = (Map<String, String>) rowBody;
						String xmlVariable = row.get("responseValue");
						String workflowVariable = row.get("workflowVariable");
						
						// TODO : assign xmlVariable to workflowVariable
					}
					
					
				} catch (ParserConfigurationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnsupportedOperationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SAXException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			} else {
				System.out.println("URL " + request.getURI().toString());
				System.out.println("Response Content-Type : " + responseContentType + " not supported" );
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
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
}
