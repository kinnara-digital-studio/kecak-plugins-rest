package com.kinnara.kecakplugins.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadOptionsBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
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
import com.kinnara.kecakplugins.rest.commons.FieldMatcher;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;

/**
 * 
 * @author aristo
 *
 */
public class RestOptionsBinder extends FormBinder implements FormLoadOptionsBinder{
	private String LABEL = "REST Option Binder";
	
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
        return AppUtil.readPluginResource(getClassName(), "/properties/RestOptionBinder.json", null, true, "message/RestOptionBinder");
    }

    public FormRowSet load(Element elmnt, String string, FormData fd) {
        try {
            ApplicationContext appContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
            WorkflowAssignment wfAssignment = (WorkflowAssignment) workflowManager.getAssignment(fd.getActivityId());
            
            String url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null);
            
            // persiapkan parameter
            // mengkombine parameter ke url
            Object[] parameters = (Object[]) getProperty("parameters");
            if(parameters != null)
	            for(Object rowParameter : parameters){
	            	Map<String, String> row = (Map<String, String>) rowParameter;
	                url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.+=,*") ? "&" : "?" ,row.get("key"), row.get("value"));
	            }
            
            HttpClient client = HttpClientBuilder.create().build();
            HttpRequestBase request = new HttpGet(url);
            
            // persiapkan HTTP header
            Object[] headers = (Object[]) getProperty("headers");
            if(headers != null)
	            for(Object rowHeader : headers){
	            	Map<String, String> row = (Map<String, String>) rowHeader;
	                request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null));
	            }
            
            // kirim request ke server
            HttpResponse response = client.execute(request);
            String responseContentType = response.getEntity().getContentType().getValue();
            
            // get properties
			String recordPath = getPropertyString("recordPath");
			String valuePath = getPropertyString("valuePath");
			String labelPath = getPropertyString("labelPath");
			String groupPath = getPropertyString("groupPath");
			
			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Pattern valuePattern = Pattern.compile(valuePath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Pattern labelPattern = Pattern.compile(labelPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Pattern groupPattern = Pattern.compile(groupPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			
            if(responseContentType.contains("application/json")) {
				try {
					JsonParser parser = new JsonParser();
					JsonElement element = parser.parse(new JsonReader(new InputStreamReader(response.getEntity().getContent())));
					JsonHandler handler = new JsonHandler(element, recordPattern);
					FormRowSet result = handler
						.addFieldMatcher(FieldMatcher.build(valuePattern, FormUtil.PROPERTY_VALUE))
						.addFieldMatcher(FieldMatcher.build(labelPattern, FormUtil.PROPERTY_LABEL))
						.addFieldMatcher(FieldMatcher.build(groupPattern, FormUtil.PROPERTY_GROUPING))
						.parse();
											
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
							new OptionsBinderSaxHandler(
									Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									Pattern.compile(valuePath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									Pattern.compile(labelPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									result
							));
					
					return result;
				} catch (UnsupportedOperationException | SAXException | ParserConfigurationException e) {
					LogUtil.error(getClassName(), e, e.getMessage());
				}

			} else {
            	try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String lines = br.lines().collect(Collectors.joining());
					LogUtil.warn(getClassName(), "Lines [" + lines + "]");
				}
            }
            
        } catch (IOException ex) {
            Logger.getLogger(RestOptionsBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private class OptionsBinderSaxHandler extends DefaultXmlSaxHandler {
    	private FormRowSet rowSet;
    	private FormRow row;
    	private Pattern valuePattern;
    	private Pattern labelPattern;
    	
    	/**
    	 * @param recordPattern
    	 * @param valuePattern
    	 * @param labelPattern
    	 * @param rowSet : output parameter, the record set being built
    	 */
    	public OptionsBinderSaxHandler(Pattern recordPattern, Pattern valuePattern, Pattern labelPattern, FormRowSet rowSet) {
    		super(recordPattern);
    		this.valuePattern = valuePattern;
    		this.labelPattern = labelPattern;
    		this.rowSet = rowSet;
    		row = null;
    	}
    	
    	@Override
    	protected void onOpeningTag(String recordQname) {
    		row = new FormRow();    		
    	}

		@Override
		protected void onTagContent(String recordQname, String path, String content) {
			Matcher valueMatcher = valuePattern.matcher(path);
			Matcher labelMatcher = labelPattern.matcher(path);
			if(valueMatcher.find() && row.getProperty(FormUtil.PROPERTY_VALUE) == null) {
				row.setProperty(FormUtil.PROPERTY_VALUE, content);
			} else if(labelMatcher.find() && row.getProperty(FormUtil.PROPERTY_LABEL) == null) {
				row.setProperty(FormUtil.PROPERTY_LABEL, content);
			}
		}

		@Override
		protected void onClosingTag(String recordQname) {
			rowSet.add(row);
		}
    }
}