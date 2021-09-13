package com.kinnara.kecakplugins.rest;

import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author aristo
 */
public class RestFormElementBinder extends FormBinder implements FormLoadElementBinder, FormStoreElementBinder, RestMixin {
    /**
     * Load from REST API
     *
     * @param element Element
     * @param primaryKey String
     * @param formData FormData
     * @return FormRowSet
     */
    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = workflowManager.getAssignment(formData.getActivityId());

        if(isEmpty(primaryKey)) {
            LogUtil.warn(getClassName(), "Primary Key is not provided");
        }

        LogUtil.info(getClassName(), "load : primaryKey [" + primaryKey + "]");

        try {
            String url = getPropertyUrl(workflowAssignment)
                    .replaceAll("\\$\\{id}", ifEmptyThen(primaryKey, ""));

            LogUtil.info(getClassName(), "load : url ["+url+"]");

            Map<String, String> variables = Collections.singletonMap("id", primaryKey);

            final HttpClient client = getHttpClient(isIgnoreCertificateError());
//            final HttpEntity httpEntity = getRequestEntity(workflowAssignment, variables);
//            final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, variables);
            final Map<String, String> headers = Arrays.stream((Object[]) getProperty("headers"))
                    .map(o -> (Map<String, Object>)o)
                    .peek(m -> LogUtil.info(getClassName(), "load : map [" + m.entrySet().stream().map(e -> e.getKey() +"->" + e.getValue()).collect(Collectors.joining(";")) + "]"))
                    .collect(Collectors.toMap(m -> String.valueOf(m.getOrDefault("key", "")), m -> String.valueOf(m.getOrDefault("value", ""))));
            final HttpUriRequest request = getHttpRequest(url, getPropertyMethod(), headers, null);
            final HttpResponse response = client.execute(request);
            final int statusCode = getResponseStatus(response);
            if (getStatusGroupCode(statusCode) != 200) {
                throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success)");
            } else if(statusCode != 200) {
                LogUtil.warn(getClassName(), "Response code [" + statusCode + "] is considered as success");
            }
            Object[] mappingObj = (Object[]) getProperty("responseMapping");
            
            return handleResponse(response, mappingObj);
        } catch (IOException | RestClientException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    /**
     * Store to REST API
     *
     * @param element Element
     * @param rowSet FormRowSet
     * @param formData FormData
     * @return FormRowSet
     */
    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = workflowManager.getAssignmentByProcess(formData.getProcessId());

        String url = getPropertyUrl(workflowAssignment)
                .replaceAll("\\$\\{}", ifEmptyThen(formData.getPrimaryKeyValue(), ""));

        try {
            Map<String, String> variables = generateVariables(rowSet);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpEntity httpEntity = getRequestEntity(workflowAssignment, variables);
            final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, variables);
            final HttpResponse response = client.execute(request);
            return ifNullThen(handleResponse(response, null), rowSet);
        } catch (RestClientException | IOException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return rowSet;
    }

    @Override
    public String getName() {
        return getLabel() + getVersion();
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "REST Form Binder";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/RestFormElementBinder.json", null, true, "/message/Rest");
    }
}
