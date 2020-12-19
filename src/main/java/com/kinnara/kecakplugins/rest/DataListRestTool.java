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
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public Object execute(Map properties) {
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

            rows.stream()
                    .peek(m -> LogUtil.info(getClassName(), "rows id ["+m.get("id")+"] export_id ["+m.get("export_id")+"]"))
                    .map(m -> formatRow(dataList, m))
                    .forEach(m -> {
                        try {
                            String primaryKeyField = dataList.getBinder().getPrimaryKeyColumnName();
                            String primaryKeyValue = m.getOrDefault(primaryKeyField, "").toString();

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

                                // Form Binding
                                String formDefId = getPropertyString("formDefId");
                                if(!formDefId.isEmpty()) {
                                    Form form = generateForm(formDefId);

                                    String recordPath = getPropertyString("jsonRecordPath");
                                    Object[] fieldMapping = (Object[]) getProperty("fieldMapping");

                                    Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
                                    Map<String, Pattern> fieldPattern = new HashMap<>();
                                    for (Object o : fieldMapping) {
                                        Map<String, String> mapping = (Map<String, String>) o;
                                        Pattern pattern = Pattern.compile(mapping.get("jsonPath").replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
                                        fieldPattern.put(mapping.get("formField"), pattern);
                                    }

                                    FormRowSet result = new FormRowSet();
                                    parseJson("", completeElement, recordPattern, fieldPattern, true, result, null, primaryKeyField, primaryKeyValue);

                                    if (isDebug()) {
                                        result.stream()
                                                .peek(r -> LogUtil.info(getClassName(), "-------Row Set-------"))
                                                .flatMap(r -> r.entrySet().stream())
                                                .forEach(e -> LogUtil.info(getClassName(), "key [" + e.getKey() + "] value [" + e.getValue() + "]"));
                                    }

                                    // save data to form
                                    result.stream()
                                            .findFirst()
                                            .ifPresent(row -> {
                                                FormData formData = new FormData();
                                                formData.setPrimaryKeyValue(row.getId());
                                                formData.setActivityId(workflowAssignment.getActivityId());
                                                formData.setProcessId(workflowAssignment.getProcessId());

                                                form.getStoreBinder().store(form, result, formData);
                                            });
                                }
                            }
                        } catch (JsonSyntaxException | RestClientException | IOException e) {
                            if(isDebug()) {
                                LogUtil.error(getClassName(), e, e.getMessage());
                            }
                        }
                    });

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
}
