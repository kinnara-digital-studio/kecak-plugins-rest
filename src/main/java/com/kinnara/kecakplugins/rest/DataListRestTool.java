package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.commons.Unclutter;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author aristo
 */
public class DataListRestTool extends DefaultApplicationPlugin implements RestMixin, Unclutter {
    @Override
    public String getName() {
        return getLabel() + getVersion();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("build.number");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map properties) {
        PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
        WorkflowManager workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");

        try {
            DataList dataList = generateDataList(getPropertyString("dataListId"), workflowAssignment);
            Map<String, List<String>> filters = getPropertyDataListFilter(this, workflowAssignment);
            getCollectFilters(dataList, filters);
            DataListCollection<Map<String, Object>> rows = Optional.of(dataList)
                    .map(DataList::getRows)
                    .orElseGet(DataListCollection::new);

            final String url = getPropertyUrl(workflowAssignment);
            final HttpClient client = getHttpClient(isIgnoreCertificateError());

            long processingRows = rows.size();
            if(isDebug()) {
                LogUtil.info(getClassName(), "Processing [" + processingRows + "] rows");
            }

            long processedRows = rows.stream()
                    .map(m -> formatRow(dataList, m))
                    .map(throwableFunction(m -> {
                        boolean fails = false;

                        String primaryKeyField = dataList.getBinder().getPrimaryKeyColumnName();
                        String primaryKeyValue = m.getOrDefault(primaryKeyField, "");

                        LogUtil.info(getClassName(), "Executing rest API for primary key [" + primaryKeyValue + "]");

                        final HttpEntity httpEntity = getRequestEntity(workflowAssignment, m);
                        final HttpUriRequest request = getHttpRequest(workflowAssignment, url, getPropertyMethod(), getPropertyHeaders(workflowAssignment), httpEntity, m);
                        final HttpResponse response = client.execute(request);

                        HttpEntity entity = response.getEntity();
                        if (entity == null) {
                            throw new RestClientException("Empty response");
                        }

                        final int statusCode = getResponseStatus(response);
                        if (getStatusGroupCode(statusCode) != 200) {
                            throw new RestClientException("Response code [" + statusCode + "] is not 200 (Success)");
                        } else if(statusCode != 200) {
                            LogUtil.warn(getClassName(), "Response code [" + statusCode + "] is considered as success");
                        }

                        final String responseContentType = getResponseContentType(response);

                        try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                            String responseBody = br.lines().collect(Collectors.joining());

                            if (isDebug()) {
                                LogUtil.info(getClassName(), "Response Content-Type [" + responseContentType + "] body [" + responseBody + "]");
                            }

                            if (!isJsonResponse(response)) {
                                throw new RestClientException("Content-Type : [" + responseContentType + "] not supported");
                            }

                            final JsonElement completeElement;
                            try {
                                JsonParser parser = new JsonParser();
                                completeElement = parser.parse(responseBody);
                            } catch (JsonSyntaxException ex) {
                                throw new RestClientException(ex);
                            }

                            // Handle success status
                            String successStatusPath = getSuccessStatusPath();
                            if(!successStatusPath.isEmpty()) {
                                String successStatusValue = getSuccessStatusValue(workflowAssignment);
                                String responseSuccessStatusValue = getJsonResultVariableValue(successStatusPath, completeElement).orElse("");
                                if(!responseSuccessStatusValue.equals(successStatusValue)) {
                                    fails = true;
                                    LogUtil.warn(getClassName(), "Response path [" + successStatusPath + "] with value [" + responseSuccessStatusValue + "] is not indicated as success ["+successStatusValue+"]");
                                }
                            }

                            // Handle failed status
                            String failedStatusPath = getFailedStatusPath();
                            if(!failedStatusPath.isEmpty()) {
                                String failedStatusValue = getFailedStatusValue(workflowAssignment);
                                String responseFailedStatusValue = getJsonResultVariableValue(failedStatusPath, completeElement).orElse("");
                                if(responseFailedStatusValue.equals(failedStatusValue)) {
                                    fails = true;
                                    LogUtil.warn(getClassName(), "Response path [" + successStatusPath + "] with value [" + responseFailedStatusValue + "] is indicated as failed [" + failedStatusValue + "]");
                                }
                            }

                            // Form Binding
                            String formDefId = getPropertyString("formDefId");
                            if (!formDefId.isEmpty()) {
                                Form form = generateForm(formDefId);

                                String recordPath = getPropertyString("jsonRecordPath");
                                Object[] fieldMapping = (Object[]) getProperty("fieldMapping");

                                Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
                                final Map<String, Pattern> fieldPattern = new HashMap<>();
                                for (Object o : fieldMapping) {
                                    Map<String, String> mapping = (Map<String, String>) o;
                                    String regexPattern = mapping.get("jsonPath").replaceAll("\\.", "\\.") + "$";
                                    Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
                                    fieldPattern.put(mapping.get("formField"), pattern);
                                }

                                FormRowSet result = new FormRowSet();
                                parseJson("", completeElement, recordPattern, fieldPattern, true, result, null, primaryKeyField, primaryKeyValue);

                                // save data to form
                                result.stream()
                                        .findFirst()
                                        .ifPresent(row -> {
                                            FormData formData = new FormData();
                                            formData.setPrimaryKeyValue(row.getId());

                                            if(workflowAssignment != null) {
                                                formData.setActivityId(workflowAssignment.getActivityId());
                                                formData.setProcessId(workflowAssignment.getProcessId());
                                            }

                                            form.getStoreBinder().store(form, result, formData);
                                        });
                            } // if
                        } // try

                        return !fails; // success

                    }))
                    .filter(success -> success != null && success) // handle only success
                    .count();

            if (processedRows == 0) {
                LogUtil.warn(getClassName(), "From [" + processingRows + "] records, no data is successfully processed");
            } else if (processingRows != processedRows) {
                LogUtil.warn(getClassName(), "Processing [" + processingRows + "] records but only [" + processedRows + "] successfully processed");
            }

            String statusVariable = getStatusVariable();
            if(!statusVariable.isEmpty() && workflowAssignment != null) {
                String statusValue;
                if(processingRows == 0) {
                    statusValue = getValueNoData(workflowAssignment);
                } else if(processedRows == 0) {
                    statusValue = getValueNoneSuccess(workflowAssignment);
                } else if(processingRows == processedRows) {
                    statusValue = getValueFullSuccess(workflowAssignment);
                } else {
                    statusValue = getValuePartialSuccess(workflowAssignment);
                }

                if(isDebug()) {
                    LogUtil.info(getClassName(), "Setting status variable ["+statusVariable+"] with value ["+statusValue+"]");
                }

                workflowManager.processVariable(workflowAssignment.getProcessId(), statusVariable, statusValue);
            }

        } catch (RestClientException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }
        return null;
    }

    @Override
    public String getLabel() {
        return "DataList Rest Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DataListRestTool.json", null, true, "/message/Rest");
    }

    protected String getStatusVariable() {
        return getPropertyString("statusVariable");
    }

    protected String getValueNoData(WorkflowAssignment assignment) {
        return getPropertyString("valueNoData");
    }

    protected String getValueFullSuccess(WorkflowAssignment assignment) {
        return getPropertyString("valueFullSuccess");
    }

    protected String getValuePartialSuccess(WorkflowAssignment assignment) {
        return getPropertyString("valuePartialSuccess");
    }

    protected String getValueNoneSuccess(WorkflowAssignment assignment) {
        return getPropertyString("valueNoneSuccess");
    }

    protected String getSuccessStatusPath() {
        return getPropertyString("successStatusPath");
    }

    protected String getSuccessStatusValue(WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(getPropertyString("successStatusValue"), assignment, null, null);
    }

    protected String getFailedStatusPath() {
        return getPropertyString("failedStatusPath");
    }

    protected String getFailedStatusValue(WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(getPropertyString("failedStatusValue"), assignment, null, null);
    }
}
