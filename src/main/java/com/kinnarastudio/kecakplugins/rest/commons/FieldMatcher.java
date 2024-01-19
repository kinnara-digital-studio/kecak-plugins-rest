package com.kinnarastudio.kecakplugins.rest.commons;

import java.util.regex.Pattern;

public class FieldMatcher {
	private Pattern pattern;
	private String field;
	
	private FieldMatcher(Pattern pattern, String fieldName) {
		this.pattern = pattern;
		this.field = fieldName;
	}
	
	public static FieldMatcher build(Pattern pattern, String field) {
		return new FieldMatcher(pattern, field);
	}
	
	public final Pattern getPattern() {
		return pattern;
	}
	
	public final String getField() {
		return field;
	}
}
