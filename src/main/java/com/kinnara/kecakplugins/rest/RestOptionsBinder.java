package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.DefaultXmlSaxHandler;
import com.kinnara.kecakplugins.rest.commons.FieldMatcher;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;
import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 
 * @author aristo
 *
 */
public class RestOptionsBinder extends FormBinder implements FormLoadOptionsBinder, RestMixin {
	private String LABEL = "REST Option Binder";
	
    public String getName() {
        return getLabel() + getVersion();
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
        return AppUtil.readPluginResource(getClassName(), "/properties/RestOptionBinder.json", null, false, "message/Rest");
    }

    @Override
    public FormRowSet load(Element elmnt, String string, FormData fd) {
        try {
            ApplicationContext appContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
            WorkflowAssignment wfAssignment = workflowManager.getAssignment(fd.getActivityId());
            
            final String url = getPropertyUrl(wfAssignment);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpUriRequest request = getHttpRequest(wfAssignment, url, getPropertyMethod(), getPropertyHeaders(wfAssignment));
            final HttpResponse response = client.execute(request);

            if(response.getStatusLine().getStatusCode() != HttpServletResponse.SC_OK) {
                LogUtil.warn(getClassName(), "Response status ["+response.getStatusLine().getStatusCode()+"]");
                return new FormRowSet();
            }

            String responseContentType = getResponseContentType(response);

            // get properties
			String recordPath = getPropertyString("recordPath");
			String valuePath = getPropertyString("valuePath");
			String labelPath = getPropertyString("labelPath");
			String groupPath = getPropertyString("groupPath");
			
			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Pattern valuePattern = Pattern.compile(valuePath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Pattern labelPattern = Pattern.compile(labelPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			Pattern groupPattern = Pattern.compile(groupPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			
            if(responseContentType.contains("json")) {
				JsonParser parser = new JsonParser();
				try(JsonReader reader = new JsonReader(new InputStreamReader(response.getEntity().getContent()))) {
					JsonElement element = parser.parse(reader);
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
            } else if(responseContentType.contains("xml")) {
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
				LogUtil.warn(getClassName(), "Unsupported content type [" + responseContentType + "]");
				try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String lines = br.lines().collect(Collectors.joining());
					LogUtil.info(getClassName(), "Response ["+lines+"]");
				}
            }
            
        } catch (IOException | RestClientException ex) {
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