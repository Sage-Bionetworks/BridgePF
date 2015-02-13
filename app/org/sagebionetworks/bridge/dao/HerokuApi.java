package org.sagebionetworks.bridge.dao;

// REMOVEME
public interface HerokuApi {

    public String registerDomainForStudy(String identifier);
    
    public String getDomainRegistrationForStudy(String identifier);
    
    public void unregisterDomainForStudy(String identifier);
    
}
