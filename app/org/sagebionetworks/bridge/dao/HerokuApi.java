package org.sagebionetworks.bridge.dao;

public interface HerokuApi {

    public String registerDomainForStudy(String identifier);
    
    public String getDomainRegistrationForStudy(String identifier);
    
    public void unregisterDomainForStudy(String identifier);
    
}
