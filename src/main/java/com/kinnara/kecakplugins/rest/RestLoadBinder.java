package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.DefaultXmlSaxHandler;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
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
import org.xml.sax.SAXException;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RestLoadBinder extends FormBinder implements FormLoadElementBinder {
	private String LABEL = "REST Load Binder";

    public String getName() {
        return LABEL;
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

    @Override
    public FormRowSet load(Element elmnt, String primaryKey, FormData fd) {
    	if(primaryKey == null || primaryKey.isEmpty()) {
    		LogUtil.warn(getClassName(), "Primary Key is not defined or empty");
		}

        try {
            ApplicationContext appContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
            WorkflowAssignment wfAssignment = workflowManager.getAssignment(fd.getActivityId());

            String url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null);

            // combine parameter ke url
			Object[] parameters = (Object[]) getProperty("parameters");
			if(parameters != null) {
				url += (url.trim().matches("https{0,1}://.+\\?.*") ? "&" : "?") + Arrays.stream(parameters)
						.filter(Objects::nonNull)
						.map(o -> (Map<String, String>)o)
						.map(m -> String.format("%s=%s", m.get("key"), AppUtil.processHashVariable(m.get("value"), wfAssignment, null, null)))
						.collect(Collectors.joining("&"));
			}

			url = url.replaceAll(":id", primaryKey);

			HttpClient client;
			if("true".equalsIgnoreCase(getPropertyString("ignoreCertificateError"))) {
				SSLContext sslContext = new SSLContextBuilder()
						.loadTrustMaterial(null, (certificate, authType) -> true).build();
				client = HttpClients.custom().setSSLContext(sslContext)
						.setSSLHostnameVerifier(new NoopHostnameVerifier())
						.build();
			} else {
				client = HttpClientBuilder.create().build();
			}

			LogUtil.info(getClassName(), "url ["+url+"]");
            HttpRequestBase request = new HttpGet(url);

            // persiapkan HTTP Header
			Optional.ofNullable(getProperty("headers"))
					.map(o -> (Object[])o)
					.map(Arrays::stream)
					.orElse(Stream.empty())
					.map(o -> (Map<String, String>)o)
					.filter(r -> !String.valueOf(r.get("key")).trim().isEmpty())
					.forEach(row -> request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null)));

            // kirim request ke server
            HttpResponse response = client.execute(request);
			if(response.getStatusLine().getStatusCode() != HttpServletResponse.SC_OK) {
				LogUtil.warn(getClassName(), "Response status ["+response.getStatusLine().getStatusCode()+"]");
				return new FormRowSet();
			}

            String responseContentType = response.getEntity().getContentType().getValue();

            // get properties
			String recordPath = getPropertyString("recordPath");

			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);

            if(responseContentType.contains("application/json")) {
				JsonParser parser = new JsonParser();
				try(JsonReader reader = new JsonReader(new InputStreamReader(response.getEntity().getContent()))) {
					JsonElement element = parser.parse(reader);
					LogUtil.info(getClassName(), "json element ["+element.toString()+"]");
					JsonHandler handler = new JsonHandler(element, recordPattern);
					FormRowSet result = handler.parse(1);
					return result;
				} catch (JsonSyntaxException ex) {
					LogUtil.error(getClassName(), ex, ex.getMessage());
				}
            } else if(responseContentType.contains("application/xml") || responseContentType.contains("text/xml")) {
				try {
					FormRowSet result = new FormRowSet();
					SAXParserFactory factory = SAXParserFactory.newInstance();
					SAXParser saxParser = factory.newSAXParser();
					saxParser.parse(response.getEntity().getContent(),
							new LoadBinderSaxHandler(
									Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE),
									result
							));

					return result;
				} catch (UnsupportedOperationException | SAXException | ParserConfigurationException e1) {
					e1.printStackTrace();
				}

			} else {
            	try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					LogUtil.warn(getClassName(), "Response content type [" + responseContentType + "] not supported yet. ["+br.lines().collect(Collectors.joining())+"]");
				}
            }
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
			LogUtil.error(getClassName(), e, e.getMessage());
        }
		return null;
    }

    private static class LoadBinderSaxHandler extends DefaultXmlSaxHandler {
    	private FormRowSet rowSet;
    	private FormRow row;

    	/**
    	 * @param recordPattern
    	 * @param rowSet : output parameter, the record set being built
    	 */
    	public LoadBinderSaxHandler(Pattern recordPattern, FormRowSet rowSet) {
    		super(recordPattern);
    		this.rowSet = rowSet;
    		row = null;
    	}

		@Override
		protected void onOpeningTag(String recordQName) {
			row = new FormRow();
		}

		@Override
		protected void onTagContent(String recordQName, String path, String content) {
			if(row.getProperty(recordQName) == null) {
				row.setProperty(recordQName, content);
			}
		}

		@Override
		protected void onClosingTag(String recordQName) {
			rowSet.add(row);
		}
    }

}
