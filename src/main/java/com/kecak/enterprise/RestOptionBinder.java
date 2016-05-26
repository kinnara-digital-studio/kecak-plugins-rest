/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kecak.enterprise;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author mrd
 */
   

public class RestOptionBinder extends org.joget.apps.form.model.FormBinder implements org.joget.apps.form.model.FormLoadOptionsBinder{

    public String getName() {
        return "Rest Tool Option Binder"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getVersion() {
        return "1.0.0"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getDescription() {
        return "Rest Tool Option Binder"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getLabel() {
        return "Rest Tool Option Binder"; //To change body of generated methods, choose Tools | Templates.
    }

    public String getClassName() {
        return getClass().getName(); //To change body of generated methods, choose Tools | Templates.
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion, appId, appVersion, appId, appVersion};
        String json;
        json = AppUtil.readPluginResource((String)this.getClass().getName(), (String)"/properties/restOptionBinder.json", (Object[])arguments, (boolean)true, (String)"message/restOptionBinder");
        return json; //To change body of generated methods, choose Tools | Templates.
    }

    public FormRowSet load(Element elmnt, String string, FormData fd) {
        try {
            ApplicationContext appContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager)appContext.getBean("workflowManager");
            WorkflowAssignment wfAssignment = (WorkflowAssignment) workflowManager.getAssignment(fd.getActivityId());
            
            String url = AppUtil.processHashVariable(getPropertyString("url"), wfAssignment, null, null);
            //String method = getPropertyString("method");
            //String statusCodeworkflowVariable = getPropertyString("statusCodeworkflowVariable");
            //String contentType = getPropertyString("contentType");
            //String body = AppUtil.processHashVariable(getPropertyString("body"), wfAssignment, null, null);
            
            // persiapkan parameter
            // mengkombine parameter ke url
            
            Object[] parameters = (Object[]) getProperty("parameters");
            for(Object rowParameter : parameters){
            	Map<String, String> row = (Map<String, String>) rowParameter;
                url += String.format("%s%s=%s", url.trim().matches("https{0,1}://.+\\?.+=,*") ? "&" : "?" ,row.get("key"), row.get("value"));
            }
            
            
            // persiapkan header
            HttpClient client = HttpClientBuilder.create().build();
            
            HttpRequestBase request = new HttpGet(url);
            
            Object[] headers = (Object[]) getProperty("headers");
            for(Object rowHeader : headers){
            	Map<String, String> row = (Map<String, String>) rowHeader;
                request.addHeader(row.get("key"), AppUtil.processHashVariable((String) row.get("value"), wfAssignment, null, null));
            }
            // kirim request ke server
            HttpResponse response = client.execute(request);
            
            BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
           
            String line;
            while((line = br.readLine()) != null) {
                // process
                System.out.println(line);
            }    
        } catch (IOException ex) {
            Logger.getLogger(RestOptionBinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }   
}