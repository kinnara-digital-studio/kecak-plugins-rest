package com.kinnara.kecakplugins.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import org.xml.sax.SAXException;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.DefaultXmlSaxHandler;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;

public class RestLoadBinder extends FormBinder implements FormLoadElementBinder {
	private String LABEL = "Kecak REST Load Binder";
	
    public String getName() {
        return LABEL;
    }

    public String getVersion() {
    	return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
    	return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

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
        String json;
        json = AppUtil.readPluginResource((String)this.getClass().getName(), (String)"/properties/RestLoadBinder.json", (Object[])arguments, (boolean)true, (String)"message/RestLoadBinder");
        return json;
    }

    public FormRowSet load(Element elmnt, String primaryKey, FormData fd) {
        try {
            ApplicationContext appContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
            WorkflowAssignment wfAssignment = (WorkflowAssignment) workflowManager.getAssignment(fd.getActivityId());
            
            String url = AppUtil.processHashVariable(getPropertyString("url").replaceAll(":id", primaryKey), wfAssignment, null, null);
            
            // combine parameter ke url
            Object[] parameters = (Object[]) getProperty("parameters");
            if(parameters != null)
	            for(Object rowParameter : parameters){
	            	Map<String, String> row = (Map<String, String>) rowParameter;
	                url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.+=,*") ? "&" : "?" ,row.get("key"), row.get("value").replaceAll(":id", primaryKey));
	            }
            
            HttpClient client = HttpClientBuilder.create().build();
            HttpRequestBase request = new HttpGet(url);
            
            // persiapkan HTTP header
            Object[] headers = (Object[]) getProperty("headers");
            if(headers != null)
	            for(Object rowHeader : headers){
	            	Map<String, String> row = (Map<String, String>) rowHeader;
	                request.addHeader(row.get("key"), AppUtil.processHashVariable((String) row.get("value"), wfAssignment, null, null));
	            }
            
            // kirim request ke server
            HttpResponse response = client.execute(request);
            String responseContentType = response.getEntity().getContentType().getValue();
            
            // get properties
			String recordPath = getPropertyString("recordPath");
			
			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			
            if(responseContentType.contains("application/json")) {
				try {
					JsonParser parser = new JsonParser();
					JsonElement element = parser.parse(new JsonReader(new InputStreamReader(response.getEntity().getContent())));
					JsonHandler handler = new JsonHandler(element, recordPattern);
					FormRowSet result = handler.parse(1);					
					return result;
				} catch (JsonSyntaxException ex) {
					LogUtil.error(getClassName(), ex, ex.getMessage());
				}
            } else if(responseContentType.contains("application/xml") || responseContentType.contains("text/xml")) {
				try {					
					FormRowSet result = new FormRowSet();
					SAXParserFactory factory = SAXParserFactory.newInstance();
					SAXParser saxParser = factory.newSAXParser();
					saxParser.parse(response.getEntity().getContent(),
							new LoadBinderSaxHandler(
									Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									result
							));
					
					return result;
				} catch (UnsupportedOperationException e1) {
					e1.printStackTrace();
				} catch (SAXException e1) {
					e1.printStackTrace();
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
				}
				
            } else {
            	BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                StringBuffer sb = new StringBuffer();
                String line;
                while((line = br.readLine()) != null) {
                	sb.append(line);
                }
                LogUtil.warn(getClassName(), "Response content type [" + responseContentType + "] not supported yet.");
            }
            
        } catch (IOException ex) {
            Logger.getLogger(RestOptionsBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
     
    private static class LoadBinderSaxHandler extends DefaultXmlSaxHandler {
    	private FormRowSet rowSet;
    	private FormRow row;
    	
    	/**
    	 * @param recordPattern
    	 * @param valuePattern
    	 * @param labelPattern
    	 * @param rowSet : output parameter, the record set being built
    	 */
    	public LoadBinderSaxHandler(Pattern recordPattern, FormRowSet rowSet) {
    		super(recordPattern);
    		this.rowSet = rowSet;
    		row = null;
    	}

		@Override
		protected void onOpeningTag(String recordQName) {
			row = new FormRow();
		}

		@Override
		protected void onTagContent(String recordQName, String path, String content) {
			if(row.getProperty(recordQName) == null) {
				row.setProperty(recordQName, content);
			}
		}

		@Override
		protected void onClosingTag(String recordQName) {
			rowSet.add(row);
		}
    }

}
