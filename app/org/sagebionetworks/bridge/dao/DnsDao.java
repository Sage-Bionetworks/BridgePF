package org.sagebionetworks.bridge.dao;

public interface DnsDao {

    public void addCnameRecordsForStudy(String identifier);

    public void removeCnameRecordsForStudy(String identifier);
    
}
