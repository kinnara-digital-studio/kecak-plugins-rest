/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kecak.enterprise;

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
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author mrd
 */
   

public class RestOptionBinder extends org.joget.apps.form.model.FormBinder implements org.joget.apps.form.model.FormLoadOptionsBinder{

    public String getName() {
        return "Rest Tool Option Binder"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getVersion() {
        return "1.0.0"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getDescription() {
        return "Rest Tool Option Binder"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getLabel() {
        return "Rest Tool Option Binder"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getClassName() {
        return getClass().getName(); //To change body of generated methods, choose Tools | Templates.
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion, appId, appVersion, appId, appVersion};
        String json;
        json = AppUtil.readPluginResource((String)this.getClass().getName(), (String)"/properties/restOptionBinder.json", (Object[])arguments, (boolean)true, (String)"message/restOptionBinder");
        return json; //To change body of generated methods, choose Tools | Templates.
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
            
            if(responseContentType.contains("application/xml") || responseContentType.contains("text/xml")) {
				try {
					String recordPath = getPropertyString("recordPath");
					String valuePath = getPropertyString("valuePath");
					String labelPath = getPropertyString("labelPath");
					
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
     
    private static class XmlSaxHandler extends DefaultHandler {
    	private final static String VALUE_KEY = "value";
    	private final static String LABEL_KEY = "label";
    	
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
    		currentPath += (currentPath.isEmpty() ? "" : ".") + qName;
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
				if(valueMatcher.find()) {
					row.setProperty(VALUE_KEY, content);
				} else if(labelMatcher.find()) {
					row.setProperty(LABEL_KEY, content);
				}
			}
    	}
    	
    	@Override
    	public void endElement(String uri, String localName, String qName) throws SAXException {
    		Matcher m = recordPattern.matcher(currentPath);
    		if(m.find() && row != null) {
    			if(row.getProperty(VALUE_KEY) != null && row.getProperty(LABEL_KEY) != null ) {
    				rowSet.add(row);
    			}
    			row = null;
    		}
    		currentPath = currentPath.replaceAll("(\\." + qName + "$)|(^" + qName + "$)", "");
    	}
    }
}