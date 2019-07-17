package com.kinnara.kecakplugins.rest;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;
import sun.rmi.runtime.Log;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 */
public class RestStoreBinder extends FormBinder implements FormStoreElementBinder {
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
        String json = AppUtil.readPluginResource((String) this.getClass().getName(), "/properties/restStoreBinder.json", arguments, true, "message/Rest");
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

    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowAssignment wfAssignment = workflowManager.getAssignmentByProcess(formData.getProcessId());

        String url = getPropertyString("url");
        String method = getPropertyString("method");
        String contentType = getPropertyString("contentType");
        String body = getPropertyString("body");

//        LogUtil.info(getClassName(), "rows ["+rows.stream().map(Hashtable::entrySet).flatMap(Collection::stream).map(e -> e.getKey() + "->" + e.getValue()).collect(Collectors.joining("|"))+"]");
//        LogUtil.info(getClassName(), "formData ["+formData.getRequestParams().entrySet().stream().map(e->e.getKey() + "->" + String.join(";", e.getValue())).collect(Collectors.joining(";"))+"]");

        if (wfAssignment != null) {
            url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null).replaceAll("#", ":");
            body = AppUtil.processHashVariable(getPropertyString("body"), wfAssignment, null, null);
        }

        // interpolate fields
        body = variableInterpolation(body, Optional.ofNullable(rows).orElse(new FormRowSet()).stream().findFirst().orElse(new FormRow()));

        // Parameters
        Object[] parameters = (Object[]) getProperty("parameters");
        if (parameters != null) {
            url += (url.trim().matches("https{0,1}://.+\\?.*") ? "&" : "?") + Arrays.stream(parameters)
                    .filter(Objects::nonNull)
                    .map(o -> (Map<String, String>) o)
                    .map(m -> String.format("%s=%s", m.get("key"), AppUtil.processHashVariable(m.get("value"), wfAssignment, null, null)))
                    .collect(Collectors.joining("&"));
        }

        try {
            HttpClient client;
            if ("true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"))) {
                SSLContext sslContext = new SSLContextBuilder()
                        .loadTrustMaterial(null, (certificate, authType) -> true).build();
                client = HttpClients.custom().setSSLContext(sslContext)
                        .setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .build();
            } else {
                client = HttpClientBuilder.create().build();
            }

            final HttpRequestBase request;

            if ("POST".equals(method)) {
                request = new HttpPost(url);
            } else if ("PUT".equals(method)) {
                request = new HttpPut(url);
            } else if ("DELETE".equals(method)) {
                request = new HttpDelete(url);
            } else {
                request = new HttpGet(url);
            }

//            Object[] headers = (Object[]) getProperty("headers");
//            for (Object rowHeader : headers) {
//                Map<String, String> row = (Map<String, String>) rowHeader;
//                if (wfAssignment != null) {
//                    request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null));
//                } else {
//                    request.addHeader(row.get("key"), row.get("value"));
//                }
//            }

            Optional.ofNullable(getProperty("headers"))
                    .map(o -> (Object[])o)
                    .map(Arrays::stream)
                    .orElse(Stream.empty())
                    .map(o -> (Map<String, String>)o)
                    .filter(r -> !String.valueOf(r.get("key")).trim().isEmpty())
                    .forEach(row -> request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null)));

            // Force to use content type from select box
            request.removeHeaders("Content-Type");
            request.addHeader("Content-Type", contentType);

            if ("POST".equals(method) || "PUT".equals(method)) {
                setHttpEntity((HttpEntityEnclosingRequestBase) request, body);
            }

            try {
                HttpResponse response = client.execute(request);
                LogUtil.info(getClassName(), "Sending [" + method + "] request to : [" + url + "]");
            } catch (IOException e) {
                LogUtil.error(RestStoreBinder.class.getName(), e, e.getMessage());
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            LogUtil.error(RestStoreBinder.class.getName(), e, e.getMessage());
        }

        return rows;
    }

    /**
     * @param request : POST or PUT request
     * @param entity  : body content
     */
    private void setHttpEntity(HttpEntityEnclosingRequestBase request, String entity) {
        try {
            request.setEntity(new StringEntity(entity));
        } catch (UnsupportedEncodingException e) {
            LogUtil.error(RestStoreBinder.class.getName(), e, e.getMessage());
        }
    }

    private String variableInterpolation(String body, FormRow formRow) {
        LogUtil.info(getClassName(), "variableInterpolation");
        for (Map.Entry<Object, Object> e : formRow.entrySet()) {
            LogUtil.info(getClassName(), "body ["+body+"]");
            body = body.replaceAll("\\$\\{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }

        return body;
    }
}
