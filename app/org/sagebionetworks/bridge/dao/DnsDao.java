package org.sagebionetworks.bridge.dao;

// REMOVEME
public interface DnsDao {

    public String createDnsRecordForStudy(String identifier);

    public String getDnsRecordForStudy(String identifier);
    
    public void deleteDnsRecordForStudy(String identifier);
    
}
