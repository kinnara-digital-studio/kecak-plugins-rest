package com.kinnara.kecakplugins.rest.commons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE;

/**
 * @author aristo
 */
public interface RestMixin extends PropertyEditable, Unclutter {
    Map<String, Form> formCache = new HashMap<>();

    /**
     * Get property "method"
     *
     * @return
     */
    default String getPropertyMethod() {
        return ifEmptyThen(getPropertyString("method"), "GET");
    }

    /**
     * Get property "headers"
     * @return
     */
    default Map<String, String> getPropertyHeaders() {
        return getPropertyHeaders(null);
    }

    /**
     * Get property "headers"
     *
     * @return
     */
    default Map<String, String> getPropertyHeaders(WorkflowAssignment assignment) {
        return getKeyValueProperty(assignment, "headers");
    }

    /**
     * Get parameter as String
     *
     * @param assignment
     * @return
     */
    @Nonnull
    default String getParameterString(WorkflowAssignment assignment) {
        return getParameters()
                .stream()
                .map(throwableFunction(m -> String.format("%s=%s", m.get("key"), URLEncoder.encode(AppUtil.processHashVariable(m.get("value"), assignment, null, null), "UTF-8"))))
                .collect(Collectors.joining("&"));
    }

    /**
     * Get property "parameters"
     *
     * @return
     */
    @Nonnull
    default List<Map<String, String>> getParameters() {
        return Optional.of("parameters")
                .map(this::getProperty)
                .map(o -> (Object[])o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, String>)o)
                .collect(Collectors.toList());
    }

    /**
     * Get property "url" and "parameters" combined
     *
     * @return
     */
    default String getPropertyUrl() {
        return getPropertyUrl(null);
    }

    /**
     * Get property "url" and "parameters" combined
     *
     * @param assignment WorkflowAssignment
     * @return
     */
    default String getPropertyUrl(WorkflowAssignment assignment) {
        String url = AppUtil.processHashVariable(getPropertyString("url"), assignment, null, null);
        String parameter = getParameterString(assignment).trim();

        if(!parameter.isEmpty()) {
            url += (url.trim().matches("https?://.+\\?.*") ? "&" : "?") + parameter;
        }

        // buat apa?
        return url.replaceAll("#", ":");
    }

    /**
     * Get property "body"
     *
     * @return String
     */
    @Nonnull
    default String getPropertyBody() {
        return getPropertyString("body");
    }

    @Nonnull
    default Map<String, String> getPropertyFormData(WorkflowAssignment assignment) {
        return getKeyValueProperty(assignment,"formData");
    }

    /**
     * Get grid property that contains "key" and "value"
     *
     * @param assignment WorkflowAssignment
     * @param propertyName String
     * @return
     */
    default Map<String, String> getKeyValueProperty(WorkflowAssignment assignment, String propertyName) {
        return Optional.of(propertyName)
                .map(this::getProperty)
                .map(o -> (Object[])o)
                .map(Arrays::stream)
                .orElse(Stream.empty())
                .filter(Objects::nonNull)
                .map(o -> (Map<String, String>)o)
                .collect(Collectors.toMap(
                        m -> processHashVariable(m.getOrDefault("key", ""), assignment),
                        m -> processHashVariable(m.getOrDefault("value", ""), assignment)));
    }

    default String processHashVariable(String content, @Nullable WorkflowAssignment assignment) {
        return AppUtil.processHashVariable(content, assignment, null, null);
    }

    /**
     * Get property "ignoreCertificateError"
     *
     * @return
     */
    default boolean isIgnoreCertificateError() {
        return "true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"));
    }

    /**
     * Get property "recordPath"
     *
     * @return
     */
    default String getPropertyRecordPath() {
        return getPropertyRecordPath(null);
    }

    default String getPropertyRecordPath(WorkflowAssignment workflowAssignment) {
        return processHashVariable(getPropertyString("recordPath"), workflowAssignment);
    }

    /**
     * Get property "debug"
     *
     * @return
     */
    default boolean isDebug() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }

    /**
     * Get JSON request body
     *
     * @param entity String
     * @param assignment WorkflowAssignment
     * @param variables Map
     * @return
     * @throws RestClientException
     */
    default HttpEntity getJsonRequestEntity(String entity, WorkflowAssignment assignment, Map<String, String> variables) throws RestClientException {
        String jsonString = verifyJsonString(entity);
        String body = AppUtil.processHashVariable(variableInterpolation(jsonString, variables), assignment, null, null);

        if(isNotEmpty(body)) {
            return new StringEntity(body, ContentType.APPLICATION_JSON);
        } else {
            return null;
        }
    }

    /**
     * Verify input string is JSON
     *
     * @param inputString
     * @return
     * @throws RestClientException
     */
    default String verifyJsonString(String inputString) throws RestClientException {
        try {
            return new JSONObject(inputString).toString();
        } catch (JSONException jsonException) {
            try {
                return new JSONArray(inputString).toString();
            } catch (JSONException jsonArrayException) {
                if(isDebug()) {
                    throw new RestClientException("Invalid json : " + inputString);
                } else {
                    throw new RestClientException("Invalid json");
                }
            }
        }
    }

    /**
     * Get multipart request body
     *
     * @param multipart
     * @param assignment
     * @param variables
     * @return
     */
    default HttpEntity getMultipartRequestEntity(Map<String, String> multipart, @Nullable WorkflowAssignment assignment, @Nullable Map<String, String> variables) {
        final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        builder.setMode(BROWSER_COMPATIBLE);

        multipart.forEach((k, v) -> {
            String value = Optional.ofNullable(variables).map(r -> variableInterpolation(String.valueOf(v), r)).orElse(v);
            builder.addTextBody(k, value);
        });

        return builder.build();
    }

    /**
     * Get request entity
     *
     * @param assignment
     * @param variables
     * @return
     * @throws RestClientException
     */
    default HttpEntity getRequestEntity(@Nullable WorkflowAssignment assignment, @Nullable Map<String, String> variables) throws RestClientException {
        if(isJsonRequest()) {
            return getJsonRequestEntity(getPropertyBody(), assignment, variables);
        } else if(isMultipartRequest()) {
            return getMultipartRequestEntity(getPropertyFormData(assignment), assignment, variables);
        } else {
            return null;
        }
    }

    default Map<String, String> generateVariables(FormRowSet rowSet) {
        return Optional.ofNullable(rowSet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(this::generateVariables)
                .orElse(Collections.emptyMap());
    }

    default Map<String, String> generateVariables(FormRow row) {
        return Optional.ofNullable(row)
                .map(Hashtable::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
    }

    /**
     * Get HTTP Client
     *
     * @param ignoreCertificate
     * @return
     * @throws RestClientException
     */
    default HttpClient getHttpClient(boolean ignoreCertificate) throws RestClientException {
        try {
            if (ignoreCertificate) {
                SSLContext sslContext = new SSLContextBuilder()
                        .loadTrustMaterial(null, (certificate, authType) -> true).build();
                return HttpClients.custom().setSSLContext(sslContext)
                        .setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .build();
            } else {
                return HttpClientBuilder.create().build();
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new RestClientException(e);
        }
    }

    default HttpUriRequest getHttpRequest(String url, String method, Map<String, String> headers, Map<String, String> variables) throws RestClientException {
        return getHttpRequest(null, url, method, headers, variables);
    }

    /**
     * Get HTTP request
     *
     * @param assignment
     * @param url
     * @param method
     * @param headers
     * @param variables
     * @return
     * @throws RestClientException
     */
    default HttpUriRequest getHttpRequest(WorkflowAssignment assignment, String url, String method, Map<String, String> headers, Map<String, String> variables) throws RestClientException {
        @Nullable HttpEntity httpEntity;
        if(isJsonRequest()) {
            httpEntity = getJsonRequestEntity(getPropertyBody(), assignment, variables);
        } else if(isMultipartRequest()) {
            httpEntity = getMultipartRequestEntity(getPropertyFormData(assignment), assignment, variables);
        } else {
            httpEntity = null;
        }
        return getHttpRequest(assignment, url, method, headers, httpEntity, variables);
    }

    /**
     * Get HTTP request
     *
     * @param assignment
     * @param url
     * @param method
     * @param headers
     * @param httpEntity
     * @return
     * @throws RestClientException
     */
    default HttpUriRequest getHttpRequest(WorkflowAssignment assignment, String url, String method, Map<String, String> headers, @Nullable HttpEntity httpEntity, Map<String, String> variables) throws RestClientException {
        final HttpRequestBase request;

        url = variableInterpolation(url, variables);

        if("GET".equals(method)) {
            request = new HttpGet(url);
        } else if("POST".equals(method)) {
            request = new HttpPost(url);
        } else if("PUT".equals(method)) {
            request = new HttpPut(url);
        } else if("DELETE".equals(method)) {
            request = new HttpDelete(url);
        } else {
            throw new RestClientException("Method [" + method + "] not supported");
        }

        headers.forEach((k, v) -> request.addHeader(k, AppUtil.processHashVariable(variableInterpolation(v, variables), assignment, null, null)));

        if(httpEntity != null && request instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) request).setEntity(httpEntity);
        }

        if(isDebug()) {
            LogUtil.info(getClassName(), "getHttpRequest : url [" + request.getURI() + "] method ["+request.getMethod()+"]");

            if(request instanceof HttpEntityEnclosingRequestBase) {
                HttpEntityEnclosingRequestBase entityEnclosingRequest = (HttpEntityEnclosingRequestBase) request;
                String requestContentType = Optional.of(entityEnclosingRequest)
                        .map(HttpEntityEnclosingRequestBase::getEntity)
                        .map(HttpEntity::getContentType)
                        .map(Header::getValue)
                        .orElse("");
                String requestMethod = Optional.of(entityEnclosingRequest)
                        .map(HttpEntityEnclosingRequestBase::getMethod)
                        .orElse("");

                Optional.of(entityEnclosingRequest)
                        .map(HttpEntityEnclosingRequestBase::getEntity)
                        .map(throwableFunction(HttpEntity::getContent)).ifPresent(inputStream -> {
                            try(BufferedReader br = new BufferedReader(new InputStreamReader(entityEnclosingRequest.getEntity().getContent()))) {
                                String bodyContent = br.lines().collect(Collectors.joining());
                                LogUtil.info(getClassName(), "getHttpRequest : Content-Type [" + requestContentType + "] method [" + requestMethod + "] bodyContent ["+bodyContent+"]");
                            } catch (IOException ignored) { }
                        });
            }
        }

        return request;
    }

    /**
     * Get primary key
     * @param properties
     * @return
     */
    default String getPrimaryKey(Map properties) {
        return Optional.of(properties)
                .map(m -> m.get("recordId"))
                .map(String::valueOf)
                .orElseGet(() -> {
                    PluginManager pluginManager = (PluginManager) properties.get("pluginManager");
                    WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
                    AppService appService = (AppService) pluginManager.getBean("appService");

                    return Optional.of(workflowAssignment)
                            .map(WorkflowAssignment::getProcessId)
                            .map(s -> appService.getOriginProcessId(s))
                            .orElse("");
                });
    }

    /**
     * Get status code
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default int getResponseStatus(@Nonnull HttpResponse response) throws RestClientException {
        return Optional.of(response)
                .map(HttpResponse::getStatusLine)
                .map(StatusLine::getStatusCode)
                .orElseThrow(() -> new RestClientException("Error getting status code"));
    }


    /**
     * Get property "contentType"
     * @return
     */
    default String getPropertyContentType() {
        return getPropertyString("contentType");
    }

    /**
     * Get content type from response
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default String getResponseContentType(@Nonnull HttpResponse response) throws RestClientException {
        return Optional.of(response)
                .map(HttpResponse::getEntity)
                .map(HttpEntity::getContentType)
                .map(Header::getValue)
                .orElseThrow(() -> new RestClientException("Error getting  content type"));
    }

    /**
     * Get body payload
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default String getResponseBody(@Nonnull HttpResponse response) throws RestClientException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
            return br.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new RestClientException(e);
        }
    }

    /**
     * Returns 200ish, 300ish, 400ish, or 500ish
     * @param status
     * @return
     */
    default int getStatusGroupCode(int status) {
        return status - (status % 100);
    }

    /**
     * Response is JSON
     *
     * @param response HttpResponse
     * @return boolean
     * @throws RestClientException
     */
    default boolean isJsonResponse(@Nonnull HttpResponse response) throws RestClientException {
        return getResponseContentType(response).contains("json");
    }

    /**
     * Response is XML
     *
     * @param response HttpResponse
     * @return
     * @throws RestClientException
     */
    default boolean isXmlResponse(@Nonnull HttpResponse response) throws RestClientException {
        return getResponseContentType(response).contains("xml");
    }

    /**
     * Handle JSON response
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default FormRowSet handleJsonResponse(@Nonnull HttpResponse response) throws RestClientException {
        Pattern recordPattern = Pattern.compile(getPropertyRecordPath().replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);

        return Optional.of(response)
                .map(HttpResponse::getEntity)
                .map(throwableFunction(HttpEntity::getContent))
                .map(throwableFunction(is -> {
                    try(InputStreamReader streamReader = new InputStreamReader(is);
                        JsonReader reader = new JsonReader(streamReader)) {

                        JsonParser parser = new JsonParser();
                        JsonElement jsonElement = parser.parse(reader);

                        if (isDebug()) {
                            LogUtil.info(getClass().getName(), "handleJsonResponse : jsonElement [" + jsonElement.toString() + "]");
                        }

                        JsonHandler handler = new JsonHandler(jsonElement, recordPattern);
                        FormRowSet result = handler.parse(1);
                        return result;
                    }
                })).orElseThrow(() -> new RestClientException("Error parsing JSON response"));

    }

    /**
     * Handle XML response
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default FormRowSet handleXmlResponse(@Nonnull HttpResponse response) throws RestClientException {
        try {
            FormRowSet result = new FormRowSet();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(response.getEntity().getContent(),
                    new LoadBinderSaxHandler(
                            Pattern.compile(getPropertyRecordPath().replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
                            result
                    ));

            return result;
        } catch (UnsupportedOperationException | SAXException | ParserConfigurationException | IOException e) {
            throw new RestClientException(e);
        }
    }

    /**
     * Handle response
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    @Nullable
    default FormRowSet handleResponse(@Nonnull HttpResponse response) throws RestClientException {
        int statusCode = getResponseStatus(response);
        String responseContentType = getResponseContentType(response);

        if(isDebug()) {
            LogUtil.info(getClass().getName(), "handleResponse : Status [" + statusCode + "] Content-Type [" + responseContentType + "]");
        }

        if(statusCode != HttpServletResponse.SC_OK) {
            LogUtil.warn(getClassName(), "Response status [" + getResponseStatus(response) + "] message ["+ getResponseBody(response) +"]");
            return null;
        }

        if(isJsonResponse(response)) {
            return handleJsonResponse(response);
        } else if(isXmlResponse(response)) {
            return handleXmlResponse(response);
        } else {
            if(isDebug()) {
                LogUtil.info(getClassName(), "handleResponse : response [" + getResponseBody(response) + "]");
            }

            LogUtil.warn(getClassName(), "Unsupported response content type [" + responseContentType + "], assume JSON response");
            return handleJsonResponse(response);
        }
    }

    /**
     * Variable interpolation
     *
     * @param content
     * @param variables
     * @return
     */
    default String variableInterpolation(String content, @Nullable Map<String, String> variables) {
        if(isDebug()) {
            LogUtil.info(getClassName(), "variableInterpolation : content ["+content+"]");
        }

        if(variables == null) {
            return content;
        }

        for (Map.Entry<String, String> e : variables.entrySet()) {
            content = content.replaceAll("\\$\\{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }

        return content;
    }

//    /**
//     * Set HTTP entity
//     *
//     * @param request
//     * @param entity
//     * @throws RestClientException
//     */
//    default void setHttpEntity(HttpEntityEnclosingRequestBase request, String entity) throws RestClientException {
//        try {
//            setHttpEntity(request, new JSONObject(entity));
//        } catch (JSONException e) {
//            throw new RestClientException(e);
//        }
//    }
//
//    /**
//     * Set http entity
//     *
//     * @param request
//     * @param entity
//     * @throws RestClientException
//     */
//    default void setHttpEntity(HttpEntityEnclosingRequestBase request, @Nonnull HttpEntity entity) {
//        String method = request.getMethod();
//
//        if(("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
//            LogUtil.warn(getClass().getName(), "Body request will be ignored for method [" + method + "]");
//            return;
//        }
//
//        request.setEntity(entity);
//    }
//
//    default void setHttpEntity(HttpEntityEnclosingRequestBase request, JSONObject jsonObject) throws RestClientException {
//        try {
//            setHttpEntity(request, new StringEntity(jsonObject.toString()));
//        } catch (UnsupportedEncodingException e) {
//            throw new RestClientException(e);
//        }
//    }

    default boolean isJsonRequest() {
        return getPropertyContentType().contains("json");
    }

    default boolean isMultipartRequest() {
        return getPropertyContentType().contains("multipart");
    }

    /**
     * Get grid properties
     *
     * @param properties
     * @param propertyName
     * @return
     */
    default List<Map<String, String>> getGridProperties(Map<String, Object> properties, String propertyName) {
        return Optional.of(propertyName)
                .map(properties::get)
                .filter(o -> o instanceof Object[])
                .map(o -> (Object[])o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(o -> o instanceof Map)
                .map(o -> (Map<String, Object>)o)
                .map(m -> m.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))))
                .collect(Collectors.toList());
    }

    /**
     * Get property "dataListFilter"
     *
     * @param obj
     * @return
     */
    default Map<String, List<String>> getPropertyDataListFilter(PropertyEditable obj, WorkflowAssignment workflowAssignment) {
        final Map<String, List<String>> filters = Optional.of("dataListFilter")
                .map(obj::getProperty)
                .map(it -> (Object[]) it)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .map(o -> (Map<String, Object>) o)
                .map(m -> {
                    Map<String, List<String>> map = new HashMap<>();
                    String name = String.valueOf(m.get("name"));
                    String value = Optional.of("value")
                            .map(m::get)
                            .map(String::valueOf)
                            .map(s -> processHashVariable(s, workflowAssignment))
                            .orElse("");

                    map.put(name, Collections.singletonList(value));
                    return map;
                })
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> {
                            List<String> result = new ArrayList<>(e1);
                            result.addAll(e2);
                            return result;
                        })
                );

        return filters;
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws RestClientException
     */
    @Nonnull
    default DataList generateDataList(String datalistId) throws RestClientException {
        return generateDataList(datalistId, null);
    }

    /**
     * Generate {@link DataList} by ID
     *
     * @param datalistId
     * @return
     * @throws RestClientException
     */
    @Nonnull
    default DataList generateDataList(String datalistId, WorkflowAssignment workflowAssignment) throws RestClientException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        DataListService dataListService = (DataListService) appContext.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) appContext.getBean("datalistDefinitionDao");
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);

        return Optional.ofNullable(datalistDefinition)
                .map(DatalistDefinition::getJson)
                .map(s -> processHashVariable(s, workflowAssignment))
                .map(dataListService::fromJson)
                .orElseThrow(() -> new RestClientException("DataList [" + datalistId + "] not found"));
    }

    /**
     * Get DataList row as JSONObject
     *
     * @param dataListId
     * @return
     */

    @Nonnull
    default JSONObject getDataListRow(String dataListId, @Nonnull final Map<String, List<String>> filters) throws RestClientException {
        DataList dataList = generateDataList(dataListId);

        getCollectFilters(dataList, filters);

        DataListCollection<Map<String, Object>> rows = dataList.getRows();
        if (rows == null) {
            throw new RestClientException("Error retrieving row from dataList [" + dataListId + "]");
        }

        JSONArray jsonArrayData = Optional.of(dataList)
                .map(DataList::getRows)
                .map(r -> (DataListCollection<Map<String, Object>>)r)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(m -> formatRow(dataList, m))
                .map(JSONObject::new)
                .collect(Collector.of(JSONArray::new, JSONArray::put, JSONArray::put));

        JSONObject jsonResult = new JSONObject();
        try {
            jsonResult.put("data", jsonArrayData);
        } catch (JSONException e) {
            throw new RestClientException(e);
        }

        return jsonResult;
    }

    /**
     * Get collect filters
     *
     * @param dataList Input/Output parameter
     */
    default void getCollectFilters(@Nonnull final DataList dataList, @Nonnull final Map<String, List<String>> filters) {
        Optional.of(dataList)
                .map(DataList::getFilters)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(f -> Optional.of(f)
                        .map(DataListFilter::getName)
                        .map(filters::get)
                        .map(l -> !l.isEmpty())
                        .orElse(false))
                .forEach(f -> f.getType().setProperty("defaultValue", String.join(";", filters.get(f.getName()))));

        dataList.getFilterQueryObjects();
        dataList.setFilters(null);
    }

    /**
     * Format Row
     *
     * @param dataList
     * @param row
     * @return
     */
    @Nonnull
    default Map<String, String> formatRow(@Nonnull DataList dataList, @Nonnull final Map<String, Object> row) {
        return Optional.of(dataList)
                .map(DataList::getColumns)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(not(DataListColumn::isHidden))
                .map(DataListColumn::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(s -> s, s -> formatValue(dataList, row, s)));
    }

    /**
     * Format
     *
     * @param dataList DataList
     * @param row      Row
     * @param field    Field
     * @return
     */
    @Nonnull
    default String formatValue(@Nonnull final DataList dataList, @Nonnull final Map<String, Object> row, String field) {
        String value = Optional.of(field)
                .map(row::get)
                .map(String::valueOf)
                .orElse("");

        return Optional.of(dataList)
                .map(DataList::getColumns)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .filter(Objects::nonNull)
                .filter(c -> field.equals(c.getName()))
                .findFirst()
                .map(column -> Optional.of(column)
                        .map(throwableFunction(DataListColumn::getFormats))
                        .map(Collection::stream)
                        .orElseGet(Stream::empty)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(f -> f.format(dataList, column, row, value))
                        .map(s -> s.replaceAll("<[^>]*>", ""))
                        .orElse(value))
                .orElse(value);
    }

    /**
     *
     * @param formDefId
     * @return
     */
    @Nonnull
    default Form generateForm(String formDefId) throws RestClientException {
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao)appContext.getBean("formDefinitionDao");

        // check in cache
        if(formCache.containsKey(formDefId))
            return formCache.get(formDefId);

        // proceed without cache
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef != null && formDefId != null && !formDefId.isEmpty()) {
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                String json = formDef.getJson();
                Form form = (Form)formService.createElementFromJson(json);

                // put in cache if possible
                formCache.put(formDefId, form);

                return form;
            }
        }

        throw new RestClientException("Error generating form [" + formDefId + "]");
    }

    default FormRow generateFormRow(DataList dataList, Map<String, Object> input) {
        String idField = Optional.of(dataList)
                .map(DataList::getBinder)
                .map(DataListBinder::getPrimaryKeyColumnName)
                .orElse("id");

        return Optional.ofNullable(input)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .collect(() -> {
                    FormRow row = new FormRow();
                    row.setId(input.getOrDefault(idField, "").toString());
                    return row;
                }, (r, e) -> r.setProperty(e.getKey(), e.getValue().toString()), FormRow::putAll);
    }

    /**
     *
     * @param path
     * @param element
     * @param recordPattern
     * @param fieldPattern
     * @param isLookingForRecordPattern
     * @param rowSet
     * @param row
     * @param foreignKeyField
     * @param primaryKey
     */
    default void parseJson(String path, @Nonnull JsonElement element, @Nonnull Pattern recordPattern, @Nonnull Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, @Nonnull final FormRowSet rowSet, @Nullable FormRow row, final String foreignKeyField, final String primaryKey) {
        Matcher matcher = recordPattern.matcher(path);
        boolean isRecordPath = matcher.find() && isLookingForRecordPattern && element.isJsonObject();

        if(isRecordPath) {
            // start looking for value and label pattern
            row = new FormRow();
        }

        if(element.isJsonObject()) {
            parseJsonObject(path, (JsonObject)element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
            if(isRecordPath) {
                if(foreignKeyField != null && !foreignKeyField.isEmpty())
                    row.setProperty(foreignKeyField, primaryKey);
                rowSet.add(row);
            }
        } else if(element.isJsonArray()) {
            parseJsonArray(path, (JsonArray)element, recordPattern, fieldPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
            if(isRecordPath) {
                if(foreignKeyField != null && !foreignKeyField.isEmpty())
                    row.setProperty(foreignKeyField, primaryKey);
                rowSet.add(row);
            }
        } else if(element.isJsonPrimitive() && !isLookingForRecordPattern) {
            for(Map.Entry<String, Pattern> entry : fieldPattern.entrySet()) {
                setRow(entry.getValue().matcher(path), entry.getKey(), element.getAsString(), row);
            }
        }
    }

    default void setRow(Matcher matcher, String key, String value, FormRow row) {
        if(matcher.find() && row != null && row.getProperty(key) == null) {
            row.setProperty(key, value);
        }
    }

    default void parseJsonObject(String path, JsonObject json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row, String foreignKeyField, String primaryKey) {
        for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
            parseJson(path + "." + entry.getKey(), entry.getValue(), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
        }
    }

    default void parseJsonArray(String path, JsonArray json, Pattern recordPattern, Map<String, Pattern> fieldPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row, String foreignKeyField, String primaryKey) {
        for(int i = 0, size = json.size(); i < size; i++) {
            parseJson(path, json.get(i), recordPattern, fieldPattern, isLookingForRecordPattern, rowSet, row, foreignKeyField, primaryKey);
        }
    }
}
