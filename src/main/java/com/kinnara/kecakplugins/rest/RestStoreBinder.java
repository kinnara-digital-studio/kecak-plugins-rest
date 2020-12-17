package com.kinnara.kecakplugins.rest;

import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Map;

/**
 * @author aristo
 *
 * @deprecated use {@link RestFormElementBinder}
 *
 */
@Deprecated
public class RestStoreBinder extends FormBinder implements FormStoreElementBinder, RestMixin {
    private final static String LABEL = "(Deprecated) REST Store Binder";

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
        return AppUtil.readPluginResource(this.getClass().getName(), "/properties/restStoreBinder.json", arguments, true, "message/Rest");
    }

    public String getName() {
        return getLabel() + getVersion();
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
        return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    }

    /**
     * Store to REST API
     *
     * @param element Element
     * @param rowSet FormRowSet
     * @param formData FormData
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
            final Map<String, String> variables = generateVariables(rowSet);
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
}
