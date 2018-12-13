package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 
 * @author aristo
 *
 */
public class RestDatalistBinder extends DataListBinderDefault{

	public DataListColumn[] getColumns() {
		FormRowSet rowSet = executeRequest(1);
		if(rowSet != null && rowSet.size() > 0) {
			FormRow row = rowSet.get(0);
			DataListColumn[] columns = new DataListColumn[row.size()];
			int i = 0;
			for(Object key : row.keySet()) { 
				columns[i++] = new DataListColumn(key.toString(), key.toString(), true);
			}
			return columns;
		}
		
		return null;
	}

	public String getPrimaryKeyColumnName() {
		return getPropertyString("primaryKey");
	}

	public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
		return executeRequest(0).stream().skip(start).limit(rows)
				.collect(DataListCollection::new, DataListCollection::add, DataListCollection::addAll);
	}

	public int getDataTotalRowCount(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects) {
		// TODO : stop this dumb ways
		return executeRequest(0).size();
	}

	public String getLabel() {
		return getName();
	}

	public String getClassName() {
		return getClass().getName();
	}

	public String getPropertyOptions() {
		return AppUtil.readPluginResource(getClassName(), "/properties/RestDataListBinder.json", null, true, "message/RestDataListBinder");
	}

	public String getName() {
        return "REST DataList Binder";
    }

    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    public String getDescription() {
    	return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    } 

    @Nonnull
    private FormRowSet executeRequest(int limit) {
        try {            
            String url = AppUtil.processHashVariable(getPropertyString("url"), null, null, null);
            
            // persiapkan parameter
            // mengkombine parameter ke url
            Object[] parameters = (Object[]) getProperty("parameters");
            if(parameters != null) {
	            for(Object rowParameter : parameters){
	            	Map<String, String> row = (Map<String, String>) rowParameter;
	                url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.*") ? "&" : "?" ,row.get("key"), row.get("value"));
	            }
            }

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

            HttpRequestBase request = new HttpGet(url);
            
            // persiapkan HTTP header
            Object[] headers = (Object[]) getProperty("headers");
            if(headers != null) {
	            for(Object rowHeader : headers){
	            	Map<String, String> row = (Map<String, String>) rowHeader;
	                request.addHeader(row.get("key"), AppUtil.processHashVariable(row.get("value"), null, null, null));
	            }
            }
            
            // kirim request ke server
            HttpResponse response = client.execute(request);
            String responseContentType = response.getEntity().getContentType().getValue();
            
            // get properties
			String recordPath = getPropertyString("recordPath");
			
			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			
            if(responseContentType.contains("application/json")) {
				JsonParser parser = new JsonParser();
				try(JsonReader reader = new JsonReader(new InputStreamReader(response.getEntity().getContent()))) {
					JsonElement element = parser.parse(reader);
					JsonHandler handler = new JsonHandler(element, recordPattern);
					
					return handler.parse(limit);
				} catch (JsonSyntaxException ex) {
					LogUtil.error(getClassName(), ex, ex.getMessage());
				}
            } else if(responseContentType.contains("application/xml") || responseContentType.contains("text/xml")) {
				LogUtil.warn(getClassName(), "Content Type [" + responseContentType.toString() + "] is not supported");				
            } else {
            	BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = br.readLine()) != null) {
                	sb.append(line);
                }
                LogUtil.warn(getClassName(), "Not supported yet");
                LogUtil.warn(getClassName(), sb.toString());
            }
            
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            Logger.getLogger(RestOptionsBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new FormRowSet();
    }
}
