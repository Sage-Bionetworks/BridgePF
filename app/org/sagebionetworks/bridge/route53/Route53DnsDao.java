package org.sagebionetworks.bridge.route53;

import java.util.List;


import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.DnsDao;

import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsResult;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.google.common.collect.Lists;

public class Route53DnsDao implements DnsDao {

    private BridgeConfig config;
    
    private AmazonRoute53Client client;
    
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    
    public void setAmazonRoute53Client(AmazonRoute53Client client) {
        this.client = client;
    }
    
    @Override
    public String createDnsRecordForStudy(String identifier) {
        updateRecords(identifier, ChangeAction.CREATE);
        return getDnsRecordForStudy(identifier);
    }
    
    @Override
    public String getDnsRecordForStudy(String identifier) {
        // This is an odd way to do it, but we're basically verifying the host names
        // we expect to be there, are actually there.
        ListResourceRecordSetsRequest request = new ListResourceRecordSetsRequest();
        request.setHostedZoneId(config.getProperty("route53.zone"));
        
        ListResourceRecordSetsResult result = null;
        do {
            result = client.listResourceRecordSets(request);
            List<ResourceRecordSet> recordSets = result.getResourceRecordSets();
            for(ResourceRecordSet recordSet : recordSets) {
                // Yes it has a period at the end, so it's not the hostname exactly.
                String recordSetName = config.getFullStudyHostname(identifier) + ".";
                if (recordSetName.equals(recordSet.getName())) {
                    return config.getFullStudyHostname(identifier);
                }
            }
            request.setStartRecordName(result.getNextRecordName());
            request.setStartRecordType(result.getNextRecordType());
            result = client.listResourceRecordSets(request);
        } while (result.getNextRecordName() != null);
        return null;
    }
    
    @Override
    public void deleteDnsRecordForStudy(String identifier) {
        updateRecords(identifier, ChangeAction.DELETE);
    }
    
    private void updateRecords(String identifier, ChangeAction action) {
        List<ResourceRecord> records = Lists.newArrayList();
        ResourceRecord record = new ResourceRecord();
        record.setValue( config.getHerokuSslEndpoint() );
        records.add(record);
        
        ResourceRecordSet recordSet = new ResourceRecordSet();
        recordSet.setName( config.getFullStudyHostname(identifier) );
        recordSet.setType(RRType.CNAME);
        recordSet.setTTL(new Long(60));
        recordSet.setResourceRecords(records);
        
        List<Change> changes = Lists.newArrayList();
        Change change = new Change();
        change.setAction(action);
        change.setResourceRecordSet(recordSet);
        changes.add(change);
        
        ChangeBatch batch = new ChangeBatch();
        batch.setChanges(changes);
        
        ChangeResourceRecordSetsRequest request = new ChangeResourceRecordSetsRequest();
        request.setHostedZoneId(config.getProperty("route53.zone"));
        request.setChangeBatch(batch);
        
        client.changeResourceRecordSets(request);
    }
    
}
