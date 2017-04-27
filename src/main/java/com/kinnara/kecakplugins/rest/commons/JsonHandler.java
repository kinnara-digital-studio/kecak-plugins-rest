package com.kinnara.kecakplugins.rest.commons;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonHandler {
	private Collection<FieldMatcher> fieldMatchers = new HashSet<FieldMatcher>();
	private Pattern noPattern = Pattern.compile(".");
	
	private JsonElement json;
	private Pattern recordPattern;
	
	public JsonHandler(JsonElement json, Pattern recordPattern) {
		this.json = json;
		this.recordPattern = recordPattern;
	}
	
	public final FormRowSet parse() {
		return parse(0);
	}
	
	public final FormRowSet parse(int limit) {
		FormRowSet rowSet = new FormRowSet();
		parseJson("", "", json, recordPattern, true, rowSet, null);
		return rowSet;
	}
	
	public final JsonHandler addFieldMatcher(FieldMatcher m) {
		fieldMatchers.add(m);
		return this;
	}
	
    private final void parseJson(String currentKey, String path, JsonElement element, Pattern recordPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {
    	Matcher matcher = recordPattern.matcher(path);    	
    	boolean isRecordPath = matcher.find() && isLookingForRecordPattern && element.isJsonObject();
    	
    	if(isRecordPath) {
    		// start looking for value and label pattern
    		row = new FormRow();
    	}
    	
    	if(element.isJsonObject()) {
    		parseJsonObject(path, (JsonObject)element, recordPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row);
    		if(isRecordPath && row != null)
    			rowSet.add(row);
    	} else if(element.isJsonArray()) {
    		parseJsonArray(currentKey, path, (JsonArray)element, recordPattern, !isRecordPath && isLookingForRecordPattern, rowSet, row);
    		if(isRecordPath && row != null)
    			rowSet.add(row);
    	} else if(element.isJsonPrimitive() && !isLookingForRecordPattern) {
    		if(fieldMatchers.isEmpty()) {
    			setRow(noPattern.matcher(path), currentKey, element.getAsString(), row);
    		} else {
	    		for(FieldMatcher item : fieldMatchers) {
	    			setRow(item.getPattern().matcher(path), item.getField(), element.getAsString(), row);
	    		}
    		}
    	}
    }
    
    private void parseJsonObject(String path, JsonObject json, Pattern recordPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {
		for(Map.Entry<String, JsonElement> entry : json.entrySet()) {
			parseJson(entry.getKey(), path + "." + entry.getKey(), entry.getValue(), recordPattern, isLookingForRecordPattern, rowSet, row);
		}
    }
    
    private void parseJsonArray(String currentKey, String path, JsonArray json, Pattern recordPattern, boolean isLookingForRecordPattern, FormRowSet rowSet, FormRow row) {    	
    	for(int i = 0, size = json.size(); i < size; i++) {
			parseJson(currentKey, path, json.get(i), recordPattern, isLookingForRecordPattern, rowSet, row);
		}
    }
    
    private final void setRow(Matcher matcher, String key, String value, FormRow row) {
    	if(matcher.find() && row != null && row.getProperty(key) == null) {
			row.setProperty(key, value);
    	}
    }    
    
    public static class FieldMatcher {
    	private Pattern pattern;
    	private String field;
    	
    	private FieldMatcher(Pattern pattern, String fieldName) {
    		this.pattern = pattern;
    		this.field = fieldName;
    	}
    	
    	public static FieldMatcher build(Pattern pattern, String field) {
    		return new FieldMatcher(pattern, field);
    	}
    	
    	private Pattern getPattern() {
    		return pattern;
    	}
    	
    	private String getField() {
    		return field;
    	}
    }
    
}
