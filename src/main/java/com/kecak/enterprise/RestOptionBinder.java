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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
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
            //String method = getPropertyString("method");
            //String statusCodeworkflowVariable = getPropertyString("statusCodeworkflowVariable");
            //String contentType = getPropertyString("contentType");
            //String body = AppUtil.processHashVariable(getPropertyString("body"), wfAssignment, null, null);
            
            // persiapkan parameter
            // mengkombine parameter ke url
            
            Object[] parameters = (Object[]) getProperty("parameters");
            for(Object rowParameter : parameters){
            	Map<String, String> row = (Map<String, String>) rowParameter;
                url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.+=,*") ? "&" : "?" ,row.get("key"), row.get("value"));
            }
            
            HttpClient client = HttpClientBuilder.create().build();
            HttpRequestBase request = new HttpGet(url);
            
         // persiapkan header
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
					
					System.out.println("result");
					for(FormRow row : result) {
						System.out.println("row");
						for(Map.Entry entry : row.entrySet()) {
							System.out.println(entry.getKey().toString() + "=>" + entry.getValue().toString());
						}
					}					

					return result;
				} catch (UnsupportedOperationException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (SAXException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (ParserConfigurationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
//				try {
//					DocumentBuilder builder = DocumentBuilderFactory.newInstance()
//					        .newDocumentBuilder();
//					
//					// parse document from response input stream
//					Document doc = builder.parse(response.getEntity().getContent());
//					
//					// normalize document
//					doc.getDocumentElement().normalize();
//					
//					// get root node
//					Node rootNode = doc.getDocumentElement();
//					
//					FormRowSet result = new FormRowSet();
//					xmlTrace("", rootNode, getPropertyString("recordPath"), getPropertyString("valuePath"), getPropertyString("labelPath"), result);
//					
//					System.out.println("result");
//					for(FormRow row : result) {
//						System.out.println("row");
//						for(Map.Entry entry : row.entrySet()) {
//							System.out.println(entry.getKey().toString() + "=>" + entry.getValue().toString());
//						}
//					}					
//					return result;
//					
//				} catch (ParserConfigurationException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				} catch (UnsupportedOperationException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				} catch (SAXException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
            } else {
            	BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                
                StringBuffer sb = new StringBuffer();
                String line;
                while((line = br.readLine()) != null) {
                	sb.append(line);
                }
                System.out.println(sb.toString());
            }
            
        } catch (IOException ex) {
            Logger.getLogger(RestOptionBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }   
    
    private void xmlTrace(String currentPath, Node currentNode, String recordPath, String valuePath, String labelPath, FormRowSet rowSet) {
    	if(currentNode != null && currentNode.getNodeType() == Node.ELEMENT_NODE) {
    		org.w3c.dom.Element element = (org.w3c.dom.Element) currentNode;
    		System.out.println("element : " + element.getTagName());
    		currentPath += (currentPath.isEmpty() ? "" : ".") + currentNode.getNodeName();
    		System.out.println("++++++++++++++++++++++++++++");
			System.out.println("current record path : " + currentPath);
			System.out.println("record pattern : " + recordPath.replaceAll("\\.", "\\."));
			System.out.println("++++++++++++++++++++++++++++");
			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Matcher matcher = recordPattern.matcher(currentPath);
			if(matcher.find()) {
				// trace children for value and label
	    		for(int i = 0, size = currentNode.getChildNodes().getLength(); i < size; i++) {
	    			Node childNode = currentNode.getChildNodes().item(i);
	    			FormRow row = new FormRow();
	    			if(xmlTraceValue("", childNode, "value", valuePath, row) && xmlTraceValue("", childNode, "label", labelPath, row))
	    				rowSet.add(row);
	    		}
			} else {
				// trace children for record
	    		for(int i = 0, size = currentNode.getChildNodes().getLength(); i < size; i++) {
	    			Node childNode = currentNode.getChildNodes().item(i);
	    			System.out.println("trace child record");
	    			xmlTrace(currentPath, childNode, recordPath, valuePath, labelPath, rowSet);
	    		}
			}	
    	}
	}
    
    private boolean xmlTraceValue(String path, Node currentNode, String label, String keyPath, FormRow row) {
    	if(currentNode != null) {
	    	path += (path.isEmpty() ? "" : ".") + currentNode.getNodeName();
	    	System.out.println("========================");
	    	System.out.println("keyPath : " + keyPath);
	    	System.out.println("current " + label + " path : " + path);
	    	System.out.println(label + " pattern : " + keyPath.replaceAll("\\.", "\\."));
	    	System.out.println("========================");
	    	Pattern valuePattern = Pattern.compile(keyPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
	    	Matcher matcher = valuePattern.matcher(path);
	    	
	    	if(matcher.find() && currentNode.getNodeType() == Node.ELEMENT_NODE) {
	    		org.w3c.dom.Element element = (org.w3c.dom.Element)currentNode;
	    		System.out.println("element.getNodeValue() : " + element.getNodeValue());
	    		row.setProperty(label, currentNode.getNodeValue());
	    		return true;
	    	} else if (currentNode.getChildNodes() != null){
	    		// trace children
	    		for(int i = 0, size = currentNode.getChildNodes().getLength(); i < size; i++) {
	    			Node childNode = currentNode.getChildNodes().item(i);
	    			boolean found = xmlTraceValue(path, childNode, label, keyPath, row);
	    			if(found)
	    				return true;
	    		}
	    	}
    	}
    	
    	return false;
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
    		
    		System.out.println("IN : currentPath");
    	}
    	
    	@Override
    	public void characters(char[] ch, int start, int length) throws SAXException {
    		String content = new String(ch, start, length).trim();
    		System.out.println("currentPath : " + currentPath);
			System.out.println("content : " + content);
			
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
    		System.out.println("OUT : currentPath");
    		
    		Matcher m = recordPattern.matcher(currentPath);
    		if(m.find()) {
    			System.out.println("1");
    			if(row != null) {
    				System.out.println("2");
    				System.out.println("row.getProperty(VALUE_KEY) : " + row.getProperty(VALUE_KEY));
    				System.out.println("row.getProperty(LABEL_KEY) : " +  row.getProperty(LABEL_KEY));
    				
	    			if(row.getProperty(VALUE_KEY) != null && row.getProperty(LABEL_KEY) != null ) {
	    				System.out.println("3");
	    				rowSet.add(row);
	    			}
	    			
	    			row = null;
    			}
    		}
    		
    		currentPath = currentPath.replaceAll("(\\." + qName + "$)|(^" + qName + "$)", "");
    	}
    }
}