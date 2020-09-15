package com.kinnara.kecakplugins.rest.commons;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.net.ssl.SSLContext;

import com.kinnara.kecakplugins.rest.exceptions.RestApiException;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.service.AppService;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;


public interface RestUtils {
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

    default HttpRequestBase getHttpRequest(String url, String method) throws RestClientException {
        if("GET".equals(method)) {
            return new HttpGet(url);
        } else if("POST".equals(method)) {
            return new HttpPost(url);
        } else if("PUT".equals(method)) {
            return new HttpPut(url);
        } else if("DELETE".equals(method)) {
            return new HttpDelete(url);
        } else {
            throw new RestClientException("Terminating : Method [" + method + "] not supported");
        }
    }

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
     * Returns 200ish, 300ish, 400ish, or 500ish
     * @param status
     * @return
     */
    default int getStatusGroupCode(int status) {
        return status - (status & 100);
    }
}
