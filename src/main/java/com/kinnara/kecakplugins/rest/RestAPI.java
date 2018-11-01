package com.kinnara.kecakplugins.rest;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.workflow.security.WorkflowUserDetails;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;



public class RestAPI extends Element implements PluginWebSupport{
	protected WorkflowUserManager wfUserManager;

	@Override
	public String getLabel() {
		// TODO Auto-generated method stub
		return "REST API";
	}

	@Override
	public String getClassName() {
		// TODO Auto-generated method stub
		return this.getClass().getName();
	}

	@Override
	public String getPropertyOptions() {
		return null;
	}

	@Override
	public String getName() {
		return "REST API";
	}

	@Override
	public String getVersion() {
		return getClass().getPackage().getImplementationVersion();
	}

	@Override
	public String getDescription() {
		return "Artifact ID : " + getClass().getPackage().getImplementationTitle();
	}

	@Override
	public void webService(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// TODO Auto-generated method stub

		LogUtil.info(getClassName(), "Executing API " + request.getRequestURI() + " in " + request.getMethod() + " method");

		String method = request.getMethod();
		response.setContentType("application/json");
		try {
			if ("GET".equals(method)) {
//				authenticateAndSetThreadUser(request);
				
				FormRowSet results = new FormRowSet();
		        results.setMultiRow(true);
		        FormRowSet filtered = new FormRowSet();
		        filtered.setMultiRow(true);
		        
		        String formDefId = (String) request.getParameter("formDefId");
		        String tableName = getTableName(formDefId);
		        if (tableName != null) {
		        	String labelColumn = (String) request.getParameter("labelColumn");
		        	String idColumn = (String) request.getParameter("idColumn");
                    idColumn = (idColumn == null || "".equals(idColumn)) ? FormUtil.PROPERTY_ID : idColumn;
                    String groupingColumn = (String) request.getParameter("groupingColumn");
                    
		        	FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
		        	results = formDataDao.find(formDefId, tableName, null, null, labelColumn, false, null, null);
		        	
		        	JsonArray arrResult = new JsonArray();
		        	for (FormRow row : results) {
		        		JsonArray arrRow = new JsonArray();
                        String id = row.getProperty(idColumn);
                        String label = row.getProperty(labelColumn);
                        String grouping = "";
                        if (groupingColumn != null && !groupingColumn.isEmpty() && row.containsKey(groupingColumn)) {
                            grouping = row.getProperty(groupingColumn);
                        }
                        
                        if (id != null && !id.isEmpty() && label != null && !label.isEmpty()) {
                        	JsonObject content = new JsonObject();
                        	content.addProperty(FormUtil.PROPERTY_VALUE, id);
                        	content.addProperty(FormUtil.PROPERTY_LABEL, "true".equals(getPropertyString("showIdInLabel"))
                                    ? String.format("%s (%s)", label, id)
                                    : label);
                        	content.addProperty(FormUtil.PROPERTY_GROUPING, grouping);
                        	arrRow.add(content);
                        }
                        arrResult.add(arrRow);
                    }
		        	response.setStatus(HttpServletResponse.SC_OK);
		        	response.getWriter().write(arrResult.toString());
		        }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	protected boolean authenticateAndSetThreadUser(@Nonnull HttpServletRequest request) throws RestApiException {

		User user = TokenService.getAuthentication(request);
		if (user == null) {
			throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED, "user not found");
		}

		WorkflowUserDetails userDetail = new WorkflowUserDetails(user);
		Authentication auth = new UsernamePasswordAuthenticationToken(userDetail, userDetail.getUsername(), userDetail.getAuthorities());

		//Login the user
		SecurityContextHolder.getContext().setAuthentication(auth);
		wfUserManager.setCurrentThreadUser(user.getUsername());
		return true;
	}
	
	protected String getTableName(String formDefId) {
        String tableName = null;
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (appDef != null && formDefId != null) {
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            tableName = appService.getFormTableName(appDef, formDefId);
        }
        return tableName;
    }

	@Override
	public String renderTemplate(FormData formData, Map dataModel) {
		// TODO Auto-generated method stub
		return null;
	}

}
