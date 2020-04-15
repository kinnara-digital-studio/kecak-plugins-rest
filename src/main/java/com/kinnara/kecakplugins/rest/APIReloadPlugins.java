package com.kinnara.kecakplugins.rest;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.workflow.security.WorkflowUserDetails;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.joget.directory.model.User;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class APIReloadPlugins extends Element implements PluginWebSupport{
	protected WorkflowUserManager wfUserManager;
	
	@Override
	public String getLabel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getClassName() {
		return getClass().getName();
	}

	@Override
	public String getPropertyOptions() {
		return null;
	}

	@Override
	public String getName() {
		return "Reload All Plugins API";
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
		String method = request.getMethod();
		response.setContentType("application/json");
		try {
			if(method.equals("POST")) {
				PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
				SetupManager setupManager = (SetupManager) AppUtil.getApplicationContext().getBean("setupManager");
				setupManager.clearCache();
		        pluginManager.refresh();
		        response.setStatus(HttpServletResponse.SC_OK);
		        response.getWriter().write("Plugins Successfully Reloaded");
			}
		}catch (Exception e) {
			LogUtil.error(getClass().getName(), e, e.getMessage());
		}	
	}

	@Override
	public String renderTemplate(FormData formData, Map dataModel) {
		// TODO Auto-generated method stub
		return null;
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

}
