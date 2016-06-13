package com.kecak.enterprise.rest;

import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormStoreElementBinder;

public class RestFormStoreBinder extends FormBinder implements FormStoreElementBinder{
	private final static String LABEL = "REST Store Binder";
	
	public String getLabel() {
		return LABEL;
	}

	public String getClassName() {
		return getClass().getName();
	}

	public String getPropertyOptions() {
		return null;
	}

	public String getName() {
		return LABEL;
	}

	public String getVersion() {
		return "1.0";
	}

	public String getDescription() {
		return "Kecak - " + LABEL; 
	}

	public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
		// TODO Auto-generated method stub
		return null;
	}

}
