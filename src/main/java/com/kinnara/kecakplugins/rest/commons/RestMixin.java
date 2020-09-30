package com.kinnara.kecakplugins.rest.commons;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONException;
import org.json.JSONObject;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE;

/**
 * @author aristo
 */
public interface RestMixin extends PropertyEditable, Unclutter {
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
     * @param assignment
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
     * @return
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
     * @param propertyName
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

    default String getPropertyRecordPath() {
        return getPropertyString("recordPath");
    }

    /**
     * Get property "headers"
     *
     * @param properties
     * @return
     */
    default List<Map<String, String>> getHeaders(Map<String, Object> properties) {
        return getGridProperties(properties, "headers");
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
     * @param jsonString
     * @param assignment
     * @param formRow
     * @return
     * @throws RestClientException
     */
    default HttpEntity getJsonRequestEntity(@Nullable String jsonString, @Nullable WorkflowAssignment assignment, @Nullable FormRow formRow) throws RestClientException {
        if(jsonString == null || jsonString.isEmpty()) {
            return null;
        }

        try {
            return getJsonRequestEntity(new JSONObject(jsonString), assignment, formRow);
        } catch (JSONException e) {
            throw new RestClientException(e);
        }
    }

    /**
     * Get JSON request body
     *
     * @param json
     * @param assignment
     * @param formRow
     * @return
     */
    @Nullable
    default HttpEntity getJsonRequestEntity(@Nullable JSONObject json, @Nullable WorkflowAssignment assignment, @Nullable FormRow formRow) {
        if(json == null) {
            return null;
        }

        String body = variableInterpolation(AppUtil.processHashVariable(json.toString(), assignment, null, null), formRow);

        if(isNotEmpty(body)) {
            return new StringEntity(body, ContentType.APPLICATION_JSON);
        } else {
            return null;
        }
    }

    /**
     * Get multipart request body
     *
     * @param multipart
     * @param assignment
     * @param row
     * @return
     */
    default HttpEntity getMultipartRequestEntity(Map<String, String> multipart, @Nullable WorkflowAssignment assignment, @Nullable FormRow row) {
        final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        builder.setMode(BROWSER_COMPATIBLE);

        multipart.forEach((k, v) -> {
            String value = Optional.ofNullable(row).map(r -> variableInterpolation(String.valueOf(v), r)).orElse(v);
            builder.addTextBody(k, value);
        });

        return builder.build();
    }

    /**
     * Get request entity
     *
     * @param assignment
     * @return
     * @throws RestClientException
     */
    default HttpEntity getRequestEntity(@Nullable WorkflowAssignment assignment) throws RestClientException {
        return getRequestEntity(assignment, (FormRow)null);
    }

    /**
     * Get request entity
     *
     * @param assignment
     * @param rowSet
     * @return
     * @throws RestClientException
     */
    default HttpEntity getRequestEntity(@Nullable WorkflowAssignment assignment, @Nullable FormRowSet rowSet) throws RestClientException {
        FormRow row = Optional.ofNullable(rowSet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .orElse(null);

        return getRequestEntity(assignment, row);
    }

    /**
     * Get request entity
     *
     * @param assignment
     * @param row
     * @return
     * @throws RestClientException
     */
    default HttpEntity getRequestEntity(@Nullable WorkflowAssignment assignment, @Nullable FormRow row) throws RestClientException {
        if(isJsonRequest()) {
            return getJsonRequestEntity(getPropertyBody(), assignment, row);
        } else if(isMultipartRequest()) {
            return getMultipartRequestEntity(getPropertyFormData(assignment), assignment, row);
        } else {
            return null;
        }
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

    /**
     * Get HTTP request
     *
     * @param assignment
     * @param url
     * @param method
     * @param headers
     * @return
     * @throws RestClientException
     */
    default HttpUriRequest getHttpRequest(WorkflowAssignment assignment, String url, String method, Map<String, String> headers) throws RestClientException {
        @Nullable HttpEntity httpEntity;
        if(isJsonRequest()) {
            httpEntity = getJsonRequestEntity(getPropertyBody(), assignment, null);
        } else if(isMultipartRequest()) {
            httpEntity = getMultipartRequestEntity(getPropertyFormData(assignment), assignment, null);
        } else {
            httpEntity = null;
        }
        return getHttpRequest(assignment, url, method, headers, httpEntity);
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
    default HttpUriRequest getHttpRequest(WorkflowAssignment assignment, String url, String method, Map<String, String> headers, @Nullable HttpEntity httpEntity) throws RestClientException {
        final HttpRequestBase request;
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

        headers.forEach((k, v) -> request.addHeader(k, AppUtil.processHashVariable(v, assignment, null, null)));

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
    default int getStatusCode(@Nonnull HttpResponse response) throws RestClientException {
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
        return status - (status & 100);
    }

    /**
     * Response is JSON
     *
     * @param response
     * @return
     * @throws RestClientException
     */
    default boolean isJsonResponse(@Nonnull HttpResponse response) throws RestClientException {
        return getResponseContentType(response).contains("json");
    }

    /**
     * Response is XML
     *
     * @param response
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
        int statusCode = getStatusCode(response);
        String responseContentType = getResponseContentType(response);

        if(isDebug()) {
            LogUtil.info(getClass().getName(), "handleResponse : Status [" + statusCode + "] Content-Type [" + responseContentType + "]");
        }

        if(statusCode != HttpServletResponse.SC_OK) {
            LogUtil.warn(getClassName(), "Response status [" + getStatusCode(response) + "] message ["+ getResponseBody(response) +"]");
            return null;
        }

        if(isJsonResponse(response)) {
            return handleJsonResponse(response);
        } else if(isXmlResponse(response)) {
            return handleXmlResponse(response);
        } else {
            LogUtil.warn(getClassName(), "Unsupported response content type [" + responseContentType + "]");
            if(isDebug()) {
                LogUtil.info(getClassName(), "handleResponse : response [" + getResponseBody(response) + "]");
            }
            return null;
        }
    }

    /**
     * Variable interpolation
     *
     * @param content
     * @param formRow
     * @return
     */
    default String variableInterpolation(String content, @Nullable FormRow formRow) {
        if(isDebug()) {
            LogUtil.info(getClassName(), "variableInterpolation : content ["+content+"]");
        }

        if(formRow == null) {
            return content;
        }

        for (Map.Entry<Object, Object> e : formRow.entrySet()) {
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

}
