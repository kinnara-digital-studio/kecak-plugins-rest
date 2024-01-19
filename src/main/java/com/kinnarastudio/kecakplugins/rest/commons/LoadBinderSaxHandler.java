package com.kinnarastudio.kecakplugins.rest.commons;

import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import java.util.regex.Pattern;

public class LoadBinderSaxHandler extends DefaultXmlSaxHandler {
    private final FormRowSet rowSet;
    private FormRow row;

    /**
     * @param recordPattern
     * @param rowSet : output parameter, the record set being built
     */
    public LoadBinderSaxHandler(final Pattern recordPattern, final FormRowSet rowSet) {
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
