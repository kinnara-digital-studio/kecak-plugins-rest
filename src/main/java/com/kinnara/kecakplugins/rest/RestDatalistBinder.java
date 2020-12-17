package com.kinnara.kecakplugins.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.kinnara.kecakplugins.rest.commons.JsonHandler;
import com.kinnara.kecakplugins.rest.commons.RestMixin;
import com.kinnara.kecakplugins.rest.exceptions.RestClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 
 * @author aristo
 *
 */
public class RestDatalistBinder extends DataListBinderDefault implements RestMixin {

	private String getDefaultPropertyValues(String json) {
		try {
			JSONArray pages = new JSONArray(json);
			JSONObject values = new JSONObject();

			for(int i = 0; i < pages.length(); ++i) {
				JSONObject page = (JSONObject)pages.get(i);
				if (page.has("properties")) {
					JSONArray properties = (JSONArray)page.get("properties");

					for(int j = 0; j < properties.length(); ++j) {
						JSONObject property = (JSONObject)properties.get(j);
						if (property.has("value")) {
							values.put(property.getString("name"), property.get("value"));
						}
					}
				}
			}

			return values.toString();
		} catch (Exception var8) {
			LogUtil.error(getClassName(), var8, json);
			return "{}";
		}
	}

	@Override
	public DataListColumn[] getColumns() {
		Object[] headers = getUnformattedGridProperty("headers");

		FormRowSet rowSet = executeRequest(1, headers);
		if(rowSet.size() > 0) {
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

	private Object[] getUnformattedGridProperty(String propertyName) {
		List<Map<String, Object>> result = new ArrayList<>();
		int i = 0;
		Object key;
		Object value;
		do {
			Map<String, Object> gridRow = new HashMap<>();

			key = getProperties().get(propertyName + "[" + i + "][key]");
			value = getProperties().get(propertyName + "[" + i + "][value]");

			if(key != null && value != null) {
				gridRow.put("key", String.valueOf(key));
				gridRow.put("value", value);
				result.add(gridRow);
			}
			i++;
		} while(key != null);

		return result.toArray();
	}

	@Override
	public String getPrimaryKeyColumnName() {
		return getPropertyString("primaryKey");
	}

	@Override
	public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
		return executeRequest(0, (Object[]) getProperty("headers")).stream().skip(start).limit(rows)
				.collect(DataListCollection::new, DataListCollection::add, DataListCollection::addAll);
	}

	@Override
	public int getDataTotalRowCount(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects) {
		// TODO : stop this dumb ways
		return executeRequest(0, (Object[]) getProperty("headers")).size();
	}

	@Override
	public String getLabel() {
		return getName();
	}

	@Override
	public String getClassName() {
		return getClass().getName();
	}

	@Override
	public String getPropertyOptions() {
		return AppUtil.readPluginResource(getClassName(), "/properties/RestDataListBinder.json", null, true, "message/RestDataListBinder");
	}

	@Override
	public String getName() {
        return "REST DataList Binder";
    }

	@Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

	@Override
    public String getDescription() {
    	return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
    } 

    @Nonnull
    private FormRowSet executeRequest(int limit, Object[] headersProperty) {
        try {            
            final String url = getPropertyUrl();
			final HttpClient client = getHttpClient(isIgnoreCertificateError());
            final HttpUriRequest request = getHttpRequest(url, getPropertyMethod(), getPropertyHeaders(), null);

            // kirim request ke server
            HttpResponse response = client.execute(request);
            String responseContentType = getResponseContentType(response);

            LogUtil.info(getClassName(), "Response status code ["+ getResponseStatus(response) + "]");
            
            // get properties
			String recordPath = getPropertyRecordPath();
			
			Pattern recordPattern = Pattern.compile(recordPath.replaceAll("\\.", "\\.") + "$", Pattern.CASE_INSENSITIVE);
			
            if(responseContentType.contains("json")) {
				JsonParser parser = new JsonParser();
				try(JsonReader reader = new JsonReader(new InputStreamReader(response.getEntity().getContent()))) {
					JsonElement element = parser.parse(reader);
					JsonHandler handler = new JsonHandler(element, recordPattern);
					
					return handler.parse(limit);
				} catch (JsonSyntaxException ex) {
					LogUtil.error(getClassName(), ex, ex.getMessage());
				}
            } else if(responseContentType.contains("xml")) {
				LogUtil.warn(getClassName(), "Content Type [" + responseContentType + "] is not supported");
            } else {
				LogUtil.warn(getClassName(), "Unsupported content type [" + responseContentType + "]");
				try(BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
					String lines = br.lines().collect(Collectors.joining());
					LogUtil.info(getClassName(), "Response ["+lines+"]");
				}
            }

        } catch (IOException | RestClientException ex) {
            Logger.getLogger(RestOptionsBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new FormRowSet();
    }

	private Map<String, Object> getGridProperty(Object[] property) {
		return Optional.ofNullable(property)
				.map(Arrays::stream)
				.orElse(Stream.empty())
				.map(o -> (Map<String, String>)o)
				.filter(r -> !String.valueOf(r.get("key")).trim().isEmpty())
				.collect(HashMap::new, (hashMap, stringStringMap) -> hashMap.put(stringStringMap.get("key"), stringStringMap.get("value")), Map::putAll);
	}
}
