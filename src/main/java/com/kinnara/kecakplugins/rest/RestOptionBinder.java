package com.kinnara.kecakplugins.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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
import org.joget.apps.form.model.FormLoadOptionsBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

/**
 * 
 * @author aristo
 *
 */
public class RestOptionBinder extends FormBinder implements FormLoadOptionsBinder{
	private String LABEL = "Kecak Rest Option Binder";
	
    public String getName() {
        return LABEL;
    }

    public String getVersion() {
        return "1.0.0";
    }

    public String getDescription() {
        return "Artifact ID : kecak-plugins-rest";
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
        json = AppUtil.readPluginResource((String)this.getClass().getName(), (String)"/properties/restOptionBinder.json", (Object[])arguments, (boolean)true, (String)"message/restOptionBinder");
        return json;
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
            for(Object rowParameter : parameters){
            	Map<String, String> row = (Map<String, String>) rowParameter;
                url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.+=,*") ? "&" : "?" ,row.get("key"), row.get("value"));
            }
            
            HttpClient client = HttpClientBuilder.create().build();
            HttpRequestBase request = new HttpGet(url);
            
            // persiapkan HTTP header
            Object[] headers = (Object[]) getProperty("headers");
            for(Object rowHeader : headers){
            	Map<String, String> row = (Map<String, String>) rowHeader;
                request.addHeader(row.get("key"), AppUtil.processHashVariable((String) row.get("value"), wfAssignment, null, null));
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
					FormRowSet result = new FormRowSet();
					JsonParser parser = new JsonParser();
					JsonElement element = parser.parse(new JsonReader(new InputStreamReader(response.getEntity().getContent())));
					parseJson("", element, recordPattern, valuePattern, labelPattern, groupPattern, true, result, null);					
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
							new XmlSaxHandler(
									Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									Pattern.compile(valuePath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									Pattern.compile(labelPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
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
                System.out.println("Not supported yet");
                System.out.println(sb.toString());
            }
            
        } catch (IOException ex) {
            Logger.getLogger(RestOptionBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private void setRow(Matcher matcher, String key, String value, FormRow row) {
    	if(matcher.find() && row != null && row.getProperty(key) == null) {
			row.setProperty(key, value);
    	}
    }
    
    private void parseJson(String path, JsonElement element, Pattern recordPattern, Pattern valuePattern, Pattern labelPattern, Pattern groupPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {    	
    	Matcher matcher = recordPattern.matcher(path);    	
    	boolean isRecordPath = matcher.find() && isLookingForRecordPattern && element.isJsonObject();
    	
    	if(isRecordPath) {
    		// start looking for value and label pattern
    		row = new FormRow();
    	}
    	
    	if(element.isJsonObject()) {
    		parseJsonObject(path, (JsonObject)element, recordPattern, valuePattern, labelPattern, groupPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row);
    		if(isRecordPath && row != null && row.getProperty(FormUtil.PROPERTY_VALUE) != null && row.getProperty(FormUtil.PROPERTY_LABEL) != null)
    			rowSet.add(row);
    	} else if(element.isJsonArray()) {
    		parseJsonArray(path, (JsonArray)element, recordPattern, valuePattern, labelPattern, groupPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row);
    		if(isRecordPath && row != null && row.getProperty(FormUtil.PROPERTY_VALUE) != null && row.getProperty(FormUtil.PROPERTY_LABEL) != null)
    			rowSet.add(row);
    	} else if(element.isJsonPrimitive() && !isLookingForRecordPattern) {
    		setRow(valuePattern.matcher(path), FormUtil.PROPERTY_VALUE, element.getAsString(), row);
    		setRow(labelPattern.matcher(path), FormUtil.PROPERTY_LABEL, element.getAsString(), row);
    		setRow(groupPattern.matcher(path), FormUtil.PROPERTY_GROUPING, element.getAsString(), row);
    	}
    }
    
    private void parseJsonObject(String path, JsonObject json, Pattern recordPattern, Pattern valuePattern, Pattern labelPattern, Pattern groupPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			parseJson(path + "." + entry.getKey(), entry.getValue(), recordPattern, valuePattern, labelPattern, groupPattern, isLookingForRecordPattern, rowSet, row);
		}
    }
    
    private void parseJsonArray(String path, JsonArray json, Pattern recordPattern, Pattern valuePattern, Pattern labelPattern, Pattern groupPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {    	
    	for(int i = 0, size = json.size(); i < size; i++) {
			parseJson(path, json.get(i), recordPattern, valuePattern, labelPattern, groupPattern, isLookingForRecordPattern, rowSet, row);
		}
    }
     
    private static class XmlSaxHandler extends DefaultHandler {
    	private String currentPath = "";
    	private FormRowSet rowSet;
    	private FormRow row;
    	private Pattern recordPattern;
    	private Pattern valuePattern;
    	private Pattern labelPattern;
    	
    	/**
    	 * @param recordPattern
    	 * @param valuePattern
    	 * @param labelPattern
    	 * @param rowSet : output parameter, the record set being built
    	 */
    	public XmlSaxHandler(Pattern recordPattern, Pattern valuePattern, Pattern labelPattern, FormRowSet rowSet) {
    		this.recordPattern = recordPattern;
    		this.valuePattern = valuePattern;
    		this.labelPattern = labelPattern;
    		this.rowSet = rowSet;
    		row = null;
    	}
    	
    	@Override
    	public void startElement(String uri, String localName, String qName, Attributes attributes)
    			throws SAXException {
    		currentPath += "." + qName;
    		Matcher m = recordPattern.matcher(currentPath);
    		if(m.find()) {
    			row = new FormRow();
    		}
    	}
    	
    	@Override
    	public void characters(char[] ch, int start, int length) throws SAXException {
    		String content = new String(ch, start, length).trim();			
			if(row != null) {
				Matcher valueMatcher = valuePattern.matcher(currentPath);
				Matcher labelMatcher = labelPattern.matcher(currentPath);
				if(valueMatcher.find() && row.getProperty(FormUtil.PROPERTY_VALUE) == null) {
					row.setProperty(FormUtil.PROPERTY_VALUE, content);
				} else if(labelMatcher.find() && row.getProperty(FormUtil.PROPERTY_LABEL) == null) {
					row.setProperty(FormUtil.PROPERTY_LABEL, content);
				}
			}
    	}
    	
    	@Override
    	public void endElement(String uri, String localName, String qName) throws SAXException {
    		Matcher m = recordPattern.matcher(currentPath);
    		if(m.find() && row != null) {
    			if(row.getProperty(FormUtil.PROPERTY_VALUE) != null && row.getProperty(FormUtil.PROPERTY_LABEL) != null ) {
    				rowSet.add(row);
    			}
    			row = null;
    		}
    		currentPath = currentPath.replaceAll("(\\." + qName + "$)|(^" + qName + "$)", "");
    	}
    }
}