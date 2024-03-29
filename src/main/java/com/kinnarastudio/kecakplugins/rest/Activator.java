package com.kinnarastudio.kecakplugins.rest;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(RestTool.class.getName(), new RestTool(), null));
        registrationList.add(context.registerService(DataListRestTool.class.getName(), new DataListRestTool(), null));
        registrationList.add(context.registerService(RestDatalistBinder.class.getName(), new RestDatalistBinder(), null));
        registrationList.add(context.registerService(RestDataListAction.class.getName(), new RestDataListAction(), null));
        registrationList.add(context.registerService(RestOptionsBinder.class.getName(), new RestOptionsBinder(), null));
        registrationList.add(context.registerService(RestFormElementBinder.class.getName(), new RestFormElementBinder(), null));
        registrationList.add(context.registerService(RestLoadBinder.class.getName(), new RestLoadBinder(), null));
        registrationList.add(context.registerService(RestStoreBinder.class.getName(), new RestStoreBinder(), null));
        registrationList.add(context.registerService(RestParticipantMapper.class.getName(), new RestParticipantMapper(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}