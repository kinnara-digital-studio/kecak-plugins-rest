package com.kinnara.kecakplugins.rest;

import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.commons.collections.map.SingletonMap;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author aristo
 */
public class RestFormElementBinder extends FormBinder implements FormLoadElementBinder, FormStoreElementBinder, RestMixin {
    /**
     * Load from REST API
     *
     * @param element
     * @param primaryKey
     * @param formData
     * @return
     */
    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = workflowManager.getAssignment(formData.getActivityId());

        if(isEmpty(primaryKey)) {
            LogUtil.warn(getClassName(), "Primary Key is not provided");
        }

        try {
            String url = getPropertyUrl(workflowAssignment)
                    .replaceAll(":id", ifEmptyThen(primaryKey, ""));

            Map<String, String> variables = Collections.singletonMap("id", primaryKey);

            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpEntity httpEntity = getRequestEntity(workflowAssignment, variables);
            final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, variables);
            final HttpResponse response = client.execute(request);
            return handleResponse(response);
        } catch (IOException | RestClientException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return null;
    }

    /**
     * Store to REST API
     *
     * @param element
     * @param rowSet
     * @param formData
     * @return
     */
    @Override
    public FormRowSet store(Element element, FormRowSet rowSet, FormData formData) {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) appContext.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = workflowManager.getAssignmentByProcess(formData.getProcessId());

        String url = getPropertyUrl(workflowAssignment)
                .replaceAll(":id", ifEmptyThen(formData.getPrimaryKeyValue(), ""));

        try {
            Map<String, String> variables = generateVariables(rowSet);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpEntity httpEntity = getRequestEntity(workflowAssignment, variables);
            final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, variables);
            final HttpResponse response = client.execute(request);
            return ifNullThen(handleResponse(response), rowSet);
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
