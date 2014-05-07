package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.salt.ZeroSaltGenerator;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.context.BridgeContext;
import org.sagebionetworks.bridge.dynamodb.DynamoRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.healthdata.HealthDataEntry;
import org.sagebionetworks.bridge.healthdata.HealthDataKey;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.UserSessionData;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class HealthDataServiceImpl implements HealthDataService, BeanFactoryAware, InitializingBean {

    private DynamoDBMapper createMapper;
    private DynamoDBMapper updateMapper;
    private BeanFactory beanFactory;
    private final StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
    
    public DynamoDBMapper getCreateMapper() {
        return createMapper;
    }

    public void setCreateMapper(DynamoDBMapper createMapper) {
        this.createMapper = createMapper;
    }

    public DynamoDBMapper getUpdateMapper() {
        return updateMapper;
    }

    public void setUpdateMapper(DynamoDBMapper updateMapper) {
        this.updateMapper = updateMapper;
    }
    
    public void setBeanFactory(BeanFactory factory) {
        this.beanFactory = factory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        BridgeContext context = new BridgeContext();
        encryptor.setPassword(context.get(BridgeContext.PASSWORD));
        // We need stable production of a token.
        encryptor.setSaltGenerator(new ZeroSaltGenerator());
    }
    
    private SynapseClient getSynapseClient(String sessionToken) {
        SynapseClient client = beanFactory.getBean("synapseClient", SynapseClient.class);
        client.setSessionToken(sessionToken);
        client.appendUserAgent(BridgeConstants.USER_AGENT);
        return client;
    }
    
    private static final Comparator<HealthDataEntry> START_DATE_COMPARATOR = new Comparator<HealthDataEntry>() {
        @Override
        public int compare(HealthDataEntry entry1, HealthDataEntry entry2) {
            return (int)(entry1.getStartDate() - entry2.getStartDate());
        }
    };
    
    private String healthDataKeyToAnonimizedKeyString(HealthDataKey key) throws BridgeServiceException, SynapseException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey cannot be null");
        } else if (key.getStudyId() == 0) {
            throw new BridgeServiceException("HealthDataKey does not have a study ID");
        } else if (key.getTrackerId() == 0) {
            throw new BridgeServiceException("HealthDataKey does not have a tracker ID");
        } else if (key.getSessionToken() == null) {
            throw new BridgeServiceException("HealthDataKey does not have a session token");
        }
        
        UserSessionData data = getSynapseClient(key.getSessionToken()).getUserSessionData();
        String ownerId = data.getProfile().getOwnerId();
        if (StringUtils.isBlank(ownerId)) {
            throw new BridgeServiceException("Cannot find ID for user");
        }
        String encryptedValue = encryptor.encrypt(ownerId);
        
        return String.format("%s:%s:%s", key.getStudyId(), key.getTrackerId(), encryptedValue);
    }
    
    private String generateId() {
        return UUID.randomUUID().toString();
    }
    
    private List<HealthDataEntry> toHealthDataEntries(Collection<DynamoRecord> records) {
        List<HealthDataEntry> entries = new ArrayList<HealthDataEntry>(records.size());
        for (DynamoRecord r : records) {
            entries.add(r.toEntry());
        }
        Collections.sort(entries, START_DATE_COMPARATOR);
        return entries;
    }
    
    @Override
    public String appendHealthData(HealthDataKey key, HealthDataEntry entry) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("Cannot create a new HealthDataEntry instance without specifying a HealthDataKey");
        } else if (entry == null) {
            throw new BridgeServiceException("New HealthDataEntry instance is null");
        } else if (entry.getRecordId() != null) {
            throw new BridgeServiceException("New HealthDataEntry instance has a record ID (should be blank)");
        } else if (entry.getStartDate() == 0) {
            throw new BridgeServiceException("New HealthDataEntry instance does not have a startDate set");
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();

            String recordId = generateId();
            entry.setRecordId(recordId);
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord record = new DynamoRecord(anonKey, entry);
            
            mapper.save(record);
            return recordId;
        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public List<HealthDataEntry> getAllHealthData(HealthDataKey key) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey key is null");
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();
            DynamoRecord record = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));
            DynamoDBQueryExpression<DynamoRecord> queryExpression = new DynamoDBQueryExpression<DynamoRecord>().withHashKeyValues(record);

            List<DynamoRecord> records = mapper.query(DynamoRecord.class, queryExpression);
            return toHealthDataEntries(records);
        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    // To do this we would need a secondary index on the endDate.
    // (startDate <= windowEnd && (endDate == null || endDate >= windowStart))
    
    // For any local secondary index, you can store up to 10 GB of data per distinct hash key value. 
    // I think this is okay. That's per user per tracker per study. That's 160k records.
    
    @Override
    public List<HealthDataEntry> getHealthDataByDateRange(HealthDataKey key, Date startDate, Date endDate) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey cannot be null");
        } else if (startDate == null) {
            throw new BridgeServiceException("startDate cannot be null");
        } else if (endDate == null) {
            throw new BridgeServiceException("endDate cannot be null");
        }
        try {                
            DynamoDBMapper mapper = getCreateMapper();
            DynamoRecord record = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));
            
            // (start date <= end date window) AND (start date window <= end date OR end date is null)
            // BUT, you cannot create queries that combine and/or. So we make two queries right now,
            // and take the intersection
    
            AttributeValue endDateWindow = new AttributeValue().withN(Long.toString(endDate.getTime()));

            Condition startDateCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.LE)
                .withAttributeValueList(endDateWindow);
            
            Map<String, Condition> keyConditions = new HashMap<String, Condition>();
            keyConditions.put("startDate", startDateCondition);
            
            DynamoDBQueryExpression<DynamoRecord> query = new DynamoDBQueryExpression<DynamoRecord>()
                .withHashKeyValues(record)
                .withRangeKeyConditions(keyConditions);
            
            List<DynamoRecord> records1 = mapper.query(DynamoRecord.class, query);

            AttributeValue startDateWindow = new AttributeValue().withN(Long.toString(startDate.getTime()));
            
            Condition endDateCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.GE)
                .withAttributeValueList(startDateWindow);
            
            query = new DynamoDBQueryExpression<DynamoRecord>()
                .withHashKeyValues(record)
                .withRangeKeyCondition("endDate", endDateCondition);

            /* Throws an error, how do you test that end date is null?
            query.setConditionalOperator(ConditionalOperator.OR);
            
            Condition endDateConditionNull = new Condition()
                .withComparisonOperator(ComparisonOperator.NULL);
            query.withRangeKeyCondition("endDate", endDateConditionNull);
            */
            List<DynamoRecord> records2 = mapper.query(DynamoRecord.class, query);
            
            Set<DynamoRecord> intersection = new HashSet<DynamoRecord>(records1);
            intersection.retainAll( new HashSet<DynamoRecord>(records2) );
            
            return toHealthDataEntries(intersection);
        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public HealthDataEntry getHealthDataEntry(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (recordId == null) {
            throw new BridgeServiceException("HealthDataEntry record ID cannot be null");
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();

            DynamoRecord record = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));
            
            Condition rangeKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(recordId));

            DynamoDBQueryExpression<DynamoRecord> queryExpression = new DynamoDBQueryExpression<DynamoRecord>()
                    .withHashKeyValues(record)
                    .withRangeKeyCondition("recordId", rangeKeyCondition);
            
            List<DynamoRecord> results = mapper.query(DynamoRecord.class, queryExpression);
            if (results.size() > 1) {
                throw new BridgeServiceException("getHealthDataEntry for '"+recordId+ "' matched more than one record");
            }
            return (results.isEmpty()) ? null : results.get(0).toEntry();
        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }

    @Override
    public void updateHealthDataEntry(HealthDataKey key, HealthDataEntry entry) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required on update (it's null)");
        } else if (entry == null) {
            throw new BridgeServiceException("HealthDataEntry is required on update (it's null)");
        } else if (entry.getStartDate() == 0L) {
            throw new BridgeServiceException("HealthDataEntry startDate & endDate are required on update (point-in-time events set the same time for both fields");
        } else if (entry.getRecordId() == null) {
            throw new BridgeServiceException("HealthDataEntry record ID is required on update (it's null)");
        }
        try {

            DynamoDBMapper mapper = getUpdateMapper();
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord record = new DynamoRecord(anonKey, entry);
            mapper.save(record);

        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public void deleteHealthDataEntry(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required for delete (it's null)");
        } else if (recordId == null) {
            throw new BridgeServiceException("HealthDataEntry record ID is required for delete (it's null)");
        }
        try {
            HealthDataEntry entry = getHealthDataEntry(key, recordId);
            if (entry == null) {
                throw new BridgeServiceException("Object does not exist: " + key.toString() + ", record ID #" + recordId);
            }
            DynamoDBMapper mapper = getUpdateMapper();
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord record = new DynamoRecord(anonKey, recordId, entry);
            mapper.delete(record);
            
        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }

}
