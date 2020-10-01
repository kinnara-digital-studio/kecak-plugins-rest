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

/**
 * @author aristo
 *
 * @deprecated use {@link RestFormElementBinder}
 */
@Deprecated
public class RestLoadBinder extends FormBinder implements FormLoadElementBinder, RestMixin {
	private String LABEL = "(Deprecated) REST Load Binder";

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
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion, appId, appVersion, appId, appVersion};
        String json;
        json = AppUtil.readPluginResource(this.getClass().getName(), "/properties/RestLoadBinder.json", arguments, true, "message/Rest");
        return json;
    }

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

			final HttpClient client = getHttpClient(isIgnoreCertificateError());
			final HttpEntity httpEntity = getRequestEntity(workflowAssignment);
			final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity);
			final HttpResponse response = client.execute(request);
			return handleResponse(response);
		} catch (IOException | RestClientException e) {
			LogUtil.error(getClassName(), e, e.getMessage());
		}

		return null;
	}
}
