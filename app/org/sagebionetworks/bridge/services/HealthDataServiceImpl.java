package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.healthdata.HealthDataKey;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.UserSessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

public class HealthDataServiceImpl implements HealthDataService, BeanFactoryAware {

    final static Logger logger = LoggerFactory.getLogger(HealthDataServiceImpl.class);
    
    private DynamoDBMapper createMapper;
    private DynamoDBMapper updateMapper;
    private BeanFactory beanFactory;
    
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

    private SynapseClient getSynapseClient(String sessionToken) {
        SynapseClient client = beanFactory.getBean("synapseClient", SynapseClient.class);
        client.setSessionToken(sessionToken);
        client.appendUserAgent(BridgeConstants.USER_AGENT);
        return client;
    }
    
    private static final Comparator<HealthDataRecord> START_DATE_COMPARATOR = new Comparator<HealthDataRecord>() {
        @Override
        public int compare(HealthDataRecord record1, HealthDataRecord record2) {
            return (int)(record1.getStartDate() - record2.getStartDate());
        }
    };
    
    private String healthDataKeyToAnonimizedKeyString(HealthDataKey key) throws BridgeServiceException, SynapseException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey cannot be null", HttpStatus.SC_BAD_REQUEST);
        } else if (key.getStudyId() == 0) {
            throw new BridgeServiceException("HealthDataKey does not have a study ID", HttpStatus.SC_BAD_REQUEST);
        } else if (key.getTrackerId() == 0) {
            throw new BridgeServiceException("HealthDataKey does not have a tracker ID", HttpStatus.SC_BAD_REQUEST);
        } else if (key.getSessionToken() == null) {
            throw new BridgeServiceException("HealthDataKey does not have a session token", HttpStatus.SC_BAD_REQUEST);
        }
        
        UserSessionData data = getSynapseClient(key.getSessionToken()).getUserSessionData();
        String ownerId = data.getProfile().getOwnerId();
        
        if (StringUtils.isBlank(ownerId)) {
            throw new BridgeServiceException("Cannot find ID for user", HttpStatus.SC_NOT_FOUND);
        }
        return String.format("%s:%s:%s", key.getStudyId(), key.getTrackerId(), ownerId);
    }
    
    private String generateId() {
        return UUID.randomUUID().toString();
    }
    
    private List<HealthDataRecord> toHealthDataEntries(Collection<DynamoRecord> records) {
        List<HealthDataRecord> entries = new ArrayList<HealthDataRecord>(records.size());
        for (DynamoRecord r : records) {
            entries.add(r.toHealthDataRecord());
        }
        Collections.sort(entries, START_DATE_COMPARATOR);
        return entries;
    }
    
    @Override
    public String appendHealthData(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("Cannot create a new HealthDataRecord instance without specifying a HealthDataKey", HttpStatus.SC_BAD_REQUEST);
        } else if (record == null) {
            throw new BridgeServiceException("New HealthDataRecord instance is null", HttpStatus.SC_BAD_REQUEST);
        } else if (record.getRecordId() != null) {
            throw new BridgeServiceException("New HealthDataRecord instance has a record ID (should be blank)", HttpStatus.SC_BAD_REQUEST);
        } else if (record.getStartDate() == 0) {
            throw new BridgeServiceException("New HealthDataRecord instance does not have a startDate set", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();

            String recordId = generateId();
            record.setRecordId(recordId);
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord dynamoRecord = new DynamoRecord(anonKey, record);
            
            mapper.save(dynamoRecord);
            return recordId;
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<HealthDataRecord> getAllHealthData(HealthDataKey key) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey key is null", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();
            DynamoRecord dynamoRecord = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));
            DynamoDBQueryExpression<DynamoRecord> queryExpression = new DynamoDBQueryExpression<DynamoRecord>()
                    .withHashKeyValues(dynamoRecord);

            List<DynamoRecord> records = mapper.query(DynamoRecord.class, queryExpression);
            return toHealthDataEntries(records);
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // To do this we would need a secondary index on the endDate.
    // (startDate <= windowEnd && (endDate == null || endDate >= windowStart))
    
    // For any local secondary index, you can store up to 10 GB of data per distinct hash key value. 
    // I think this is okay. That's per user per tracker per study. That's 160k records.
    
    @Override
    public List<HealthDataRecord> getHealthDataByDateRange(HealthDataKey key, final Date startDate, final Date endDate) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey cannot be null", HttpStatus.SC_BAD_REQUEST);
        } else if (startDate == null || startDate.getTime() == 0) {
            throw new BridgeServiceException("startDate cannot be null/0", HttpStatus.SC_BAD_REQUEST);
        } else if (endDate == null || endDate.getTime() == 0) {
            throw new BridgeServiceException("endDate cannot be null/0", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            /* Works for sure, very inefficient. Code below this at least queries out records that start after 
             * the query window.
            return FluentIterable.from(getAllHealthData(key)).filter(new Predicate<HealthDataRecord>() {
                public boolean apply(HealthDataRecord record) {
                    if ((record.getEndDate() != 0 && record.getEndDate() < startDate.getTime())
                      || record.getStartDate() > endDate.getTime()) {
                        return false;
                    }
                    return true;
                }
            }).toList();
            */
            
            // Find records whose start date is before the window end date, and whose end date is after the window start date (or zero)
            DynamoRecord dynamoRecord = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));

            Condition isLessThanOrEqualToEndDateWindow = new Condition()
                .withComparisonOperator(ComparisonOperator.LE.toString())
                .withAttributeValueList(new AttributeValue().withN(Long.toString(endDate.getTime())));

            DynamoDBQueryExpression<DynamoRecord> queryExpression = new DynamoDBQueryExpression<DynamoRecord>()
                .withHashKeyValues(dynamoRecord)
                .withRangeKeyCondition("startDate", isLessThanOrEqualToEndDateWindow);
            
            /* DynamoDB cannot make compound queries like this. I'm about 99% sure of it at this point.
             * We have to pull more records than we intend, and then filter.
            Condition isGreaterThanOrEqualToStartDateWindow = new Condition()
                .withComparisonOperator(ComparisonOperator.GE.toString())
                .withAttributeValueList(new AttributeValue().withN(Long.toString(startDate.getTime())));
            
            Condition isEqualToZero = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withN("0"));

            DynamoDBQueryExpression<DynamoRecord> queryExpression2 = new DynamoDBQueryExpression<DynamoRecord>()
                .withHashKeyValues(dynamoRecord)
                .withRangeKeyCondition("endDate", isGreaterThanOrEqualToStartDateWindow)
                .withRangeKeyCondition("endDate", isEqualToZero);
            */
            
            DynamoDBMapper mapper = getCreateMapper();
            List<DynamoRecord> records = mapper.query(DynamoRecord.class, queryExpression);
            
            return toHealthDataEntries(FluentIterable.from(records).filter(new Predicate<DynamoRecord>() {
                public boolean apply(DynamoRecord record) {
                    return !(record.getEndDate() != 0 && record.getEndDate() < startDate.getTime());
                }
            }).toList());
            
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public HealthDataRecord getHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (recordId == null) {
            throw new BridgeServiceException("HealthDataRecord record ID cannot be null", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();

            DynamoRecord dynamoRecord = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));
            
            Condition rangeKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(recordId));

            DynamoDBQueryExpression<DynamoRecord> queryExpression = new DynamoDBQueryExpression<DynamoRecord>()
                    .withHashKeyValues(dynamoRecord)
                    .withRangeKeyCondition("recordId", rangeKeyCondition);
            
            List<DynamoRecord> results = mapper.query(DynamoRecord.class, queryExpression);
            if (results.size() == 0) {
                throw new BridgeServiceException("Health data record cannot be found", HttpStatus.SC_NOT_FOUND);
            } else if (results.size() > 1) {
                throw new BridgeServiceException("More than one health data record matches ID " + recordId,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            return results.get(0).toHealthDataRecord();
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void updateHealthDataRecord(HealthDataKey key, HealthDataRecord record) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required on update (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (record == null) {
            throw new BridgeServiceException("HealthDataRecord is required on update (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (record.getStartDate() == 0L) {
            throw new BridgeServiceException(
                    "HealthDataRecord startDate & endDate are required on update (point-in-time events set the same time for both fields",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (record.getRecordId() == null) {
            throw new BridgeServiceException("HealthDataRecord record ID is required on update (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        }
        try {

            DynamoDBMapper mapper = getUpdateMapper();
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord dynamoRecord = new DynamoRecord(anonKey, record);
            mapper.save(dynamoRecord);

        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    @Override
    public void deleteHealthDataRecord(HealthDataKey key, String recordId) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required for delete (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        } else if (recordId == null) {
            throw new BridgeServiceException("HealthDataRecord record ID is required for delete (it's null)",
                    HttpStatus.SC_BAD_REQUEST);
        }
        try {
            HealthDataRecord record = getHealthDataRecord(key, recordId);
            if (record == null) {
                throw new BridgeServiceException("Object does not exist: " + key.toString() + ", record ID #"
                        + recordId, HttpStatus.SC_NOT_FOUND);
            }
            DynamoDBMapper mapper = getUpdateMapper();
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord dynamoRecord = new DynamoRecord(anonKey, recordId, record);
            mapper.delete(dynamoRecord);
            
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
