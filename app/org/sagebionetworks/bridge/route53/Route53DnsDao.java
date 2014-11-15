package org.sagebionetworks.bridge.route53;

import java.util.EnumSet;

import java.util.List;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.DnsDao;

import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
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
    public void addCnameRecordsForStudy(String identifier) {
        updateRecords(identifier, ChangeAction.CREATE);
    }

    @Override
    public void removeCnameRecordsForStudy(String identifier) {
        updateRecords(identifier, ChangeAction.DELETE);
    }
    
    private void updateRecords(String identifier, ChangeAction action) {
        for (Environment env : EnumSet.of(Environment.DEV, Environment.UAT, Environment.PROD)) {

            List<ResourceRecord> records = Lists.newArrayList();
            ResourceRecord record = new ResourceRecord();
            record.setValue( getHerokuHostname(env) );
            records.add(record);
            
            ResourceRecordSet recordSet = new ResourceRecordSet();
            recordSet.setName( getStudyHostname(identifier, env) );
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
    
    private String getHerokuHostname(Environment env) {
        return config.getProperty("heroku.ssl.hostname."+env.name().toLowerCase());
    }
    
    private String getStudyHostname(String identifier, Environment env) {
        return identifier + config.getProperty("study.hostname."+env.name().toLowerCase());
    }
}
