package com.kecak.enterprise.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.logging.Logger;
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
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormStoreElementBinder;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

public class RestFormStoreBinder extends FormBinder implements FormStoreElementBinder {

    private static final Logger logger = Logger
            .getLogger(RestFormStoreBinder.class.getName());
    private final static String LABEL = "REST Store Binder";

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
        String json = AppUtil.readPluginResource((String)this.getClass().getName(), (String)"/properties/restStoreBinder.json", (Object[])arguments, (boolean)true, (String)"message/restTool");
        return json;
    }

    public String getName() {
        return LABEL;
    }

    public String getVersion() {
        return "1.0";
    }

    public String getDescription() {
        return "Kecak - " + LABEL;
    }

    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowAssignment wfAssignment = (WorkflowAssignment) workflowManager.getAssignmentByProcess(formData.getProcessId());

        String url = getPropertyString("url");
        String method = getPropertyString("method");
        String contentType = getPropertyString("contentType");
        String body = getPropertyString("body");
        
        if(wfAssignment!=null){
            url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null).replaceAll("#", ":");
            body = AppUtil.processHashVariable(getPropertyString("body"), wfAssignment, null, null);
        }
        
        // Parameters
        Pattern p = Pattern.compile("https{0,1}://.+\\?.+=,*");
        Object[] parameters = (Object[]) getProperty("parameters");
        for (Object parameter : parameters) {
            Map<String, String> row = (Map<String, String>) parameter;

            // inflate hash variables
            String value = row.get("value");
            if(wfAssignment!=null){
                value = AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null);
            }
            // if url already contains parameters, use &
            Matcher m = p.matcher(url.trim());
            try {
                url += String.format("%s%s=%s", m.find() ? "&" : "?", row.get("key"), URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LogUtil.error(RestFormStoreBinder.class.getName(), e, e.getMessage());
            }
        }

        HttpClient client = HttpClientBuilder.create().build();

        HttpRequestBase request = null;
        if ("GET".equals(method)) {
            request = new HttpGet(url);
        } else if ("POST".equals(method)) {
            request = new HttpPost(url);
        } else if ("PUT".equals(method)) {
            request = new HttpPut(url);
        } else if ("DELETE".equals(method)) {
            request = new HttpDelete(url);
        }

        Object[] headers = (Object[]) getProperty("headers");
        for (Object rowHeader : headers) {
            Map<String, String> row = (Map<String, String>) rowHeader;
            if(wfAssignment!=null){
                request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null));
            }else{
                request.addHeader(row.get("key"), row.get("value"));
            }
        }

        // Force to use content type from select box
        request.removeHeaders("Content-Type");
        request.addHeader("Content-Type", contentType);

        if ("POST".equals(method) || "PUT".equals(method)) {
            setHttpEntity((HttpEntityEnclosingRequestBase) request, body);
        }

        try {
            HttpResponse response = client.execute(request);
            logger.info("####Sending " + method + " request to : " + url);
        } catch (ClientProtocolException e) {
            LogUtil.error(RestFormStoreBinder.class.getName(), e, e.getMessage());
        } catch (IOException e) {
            LogUtil.error(RestFormStoreBinder.class.getName(), e, e.getMessage());
        }

        return rows;
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
            LogUtil.error(RestFormStoreBinder.class.getName(), e, e.getMessage());
        }
    }
}
