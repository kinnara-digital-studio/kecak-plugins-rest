package com.kinnarastudio.kecakplugins.rest.commons;

import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;

import javax.annotation.Nonnull;

/**
 * Combination of form and formData
 */
public class StuffedForm {

    final private Form form;

    final private FormData formData;

    public StuffedForm(@Nonnull Form form, @Nonnull FormData formData) {
        this.form = form;
        this.formData = formData;
    }


    @Nonnull
    public FormData getFormData() {
        return formData;
    }

    @Nonnull
    public Form getForm() {
        return form;
    }
}
