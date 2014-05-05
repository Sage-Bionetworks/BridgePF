package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.sagebionetworks.bridge.dynamodb.DynamoRecord;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.healthdata.HealthDataEntry;
import org.sagebionetworks.bridge.healthdata.HealthDataKey;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class HealthDataServiceImpl implements HealthDataService {

    private DynamoDBMapper createMapper;
    private DynamoDBMapper updateMapper;
    
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
    
    private String healthDataKeyToAnonimizedKeyString(HealthDataKey key) {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey cannot be null");
        } else if (key.getStudyId() == 0) {
            throw new BridgeServiceException("HealthDataKey does not have a study ID");
        } else if (key.getTrackerId() == 0) {
            throw new BridgeServiceException("HealthDataKey does not have a tracker ID");
        } else if (key.getSessionToken() == null) {
            throw new BridgeServiceException("HealthDataKey does not have a session token");
        }
        // Translation from session token to user to health data code would happen here
        return String.format("%s:%s:%s", key.getStudyId(), key.getTrackerId(), key.getSessionToken());
    }
    
    private String generateId() {
        return UUID.randomUUID().toString();
    }
    
    private List<HealthDataEntry> toHealthDataEntries(Collection<DynamoRecord> records) {
        List<HealthDataEntry> entries = new ArrayList<HealthDataEntry>(records.size());
        for (DynamoRecord r : records) {
            entries.add(r.toEntry());
        }
        return entries;
    }
    
    @Override
    public String appendHealthData(HealthDataKey key, HealthDataEntry entry) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("Cannot create a new HealthDataEntry instance without specifying a HealthDataKey");
        } else if (entry == null) {
            throw new BridgeServiceException("New HealthDataEntry instance is null");
        } else if (entry.getId() != null) {
            throw new BridgeServiceException("New HealthDataEntry instance has an ID (should be blank)");
        } else if (entry.getStartDate() == 0) {
            throw new BridgeServiceException("New HealthDataEntry instance does not have a startDate set");
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();

            String id = generateId();
            entry.setId(id);
            String anonKey = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord record = new DynamoRecord(anonKey, entry);
            
            mapper.save(record);
            return id;
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
        throw new UnsupportedOperationException("Not currently implemented");
        /*
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
                                                            
            AttributeValue start = new AttributeValue().withN(Long.toString(startDate.getTime()));
            AttributeValue end = new AttributeValue().withN(Long.toString(endDate.getTime()));

            Condition lowerBound = new Condition()
                .withComparisonOperator(ComparisonOperator.LE)
                .withAttributeValueList(end);
            DynamoDBQueryExpression<DynamoRecord> lowerBoundQuery = new DynamoDBQueryExpression<DynamoRecord>()
                    .withHashKeyValues(record)
                    .withRangeKeyCondition("startDate", lowerBound);
            List<DynamoRecord> records1 = mapper.query(DynamoRecord.class, lowerBoundQuery);
            
            // AND
            
            // The end date is after the window start date, or it's 0 (not ended). 
            Condition upperBound = new Condition()
                .withComparisonOperator(ComparisonOperator.GE)
                .withAttributeValueList(start);
            
            Condition upperBoundEmpty = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withN("0"));
            
            Map<String, Condition> conditions = new HashMap<String, Condition>();
            conditions.put("endDate", upperBound);
            conditions.put("endDate", upperBoundEmpty);
            
            DynamoDBQueryExpression<DynamoRecord> upperBoundQuery = new DynamoDBQueryExpression<DynamoRecord>()
                .withHashKeyValues(record)
                .withRangeKeyConditions(conditions);
            
            List<DynamoRecord> records2 = mapper.query(DynamoRecord.class, upperBoundQuery);
            
            Set<DynamoRecord> intersection = new HashSet<DynamoRecord>(records1);
            intersection.containsAll( new HashSet<DynamoRecord>(records2) );
            
            return toHealthDataEntries(intersection);
        } catch(BridgeServiceException e) {
            throw new BridgeServiceException(e);
        }*/
    }

    @Override
    public HealthDataEntry getHealthDataEntry(HealthDataKey key, String id) throws BridgeServiceException {
        if (id == null) {
            throw new BridgeServiceException("HealthDataEntry ID cannot be null");
        }
        try {
            DynamoDBMapper mapper = getCreateMapper();

            DynamoRecord record = new DynamoRecord(healthDataKeyToAnonimizedKeyString(key));
            
            Condition rangeKeyCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(id));

            DynamoDBQueryExpression<DynamoRecord> queryExpression = new DynamoDBQueryExpression<DynamoRecord>()
                    .withHashKeyValues(record)
                    .withRangeKeyCondition("id", rangeKeyCondition);
            
            List<DynamoRecord> results = mapper.query(DynamoRecord.class, queryExpression);
            if (results.size() > 1) {
                throw new BridgeServiceException("getHealthDataEntry for '"+id+ "' matched more than one record");
            }
            return (results.isEmpty()) ? null : results.get(0).toEntry();
        } catch(BridgeServiceException e) {
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
        } else if (entry.getId() == null) {
            throw new BridgeServiceException("HealthDataEntry id is required on update (it's null)");
        }
        try {
            DynamoDBMapper mapper = getUpdateMapper();
            
            String id = healthDataKeyToAnonimizedKeyString(key);
            DynamoRecord record = new DynamoRecord(id, entry);
            mapper.save(record);
        } catch(BridgeServiceException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public void deleteHealthDataEntry(HealthDataKey key, String id) throws BridgeServiceException {
        if (key == null) {
            throw new BridgeServiceException("HealthDataKey is required for delete (it's null)");
        } else if (id == null) {
            throw new BridgeServiceException("HealthDataEntry ID is required for delete (it's null)");
        }
        try {
            HealthDataEntry entry = getHealthDataEntry(key, id);
            if (entry == null) {
                throw new BridgeServiceException("Object does not exist: " + key.toString() + ", ID #" + id);
            }
            DynamoDBMapper mapper = getUpdateMapper();
            mapper.delete(entry);
        } catch(Exception e) {
            throw new BridgeServiceException(e);
        }
    }

}
