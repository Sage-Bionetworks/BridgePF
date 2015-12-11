package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;

import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

@Component
public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    @Resource(name = "studyConsentDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public StudyConsent addConsent(String subpopGuid, String storagePath, DateTime createdOn) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setSubpopulationGuid(subpopGuid);
        consent.setCreatedOn(createdOn.getMillis());
        consent.setStoragePath(storagePath);
        mapper.save(consent);
        return consent;
    }

    @Override
    public StudyConsent publish(StudyConsent studyConsent) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(studyConsent.getSubpopulationGuid());
        hashKey.setCreatedOn(studyConsent.getCreatedOn());
        DynamoStudyConsent1 consent = mapper.load(hashKey);
        
        StudyConsent activeConsent = getActiveConsent(studyConsent.getSubpopulationGuid());
        
        consent.setActive(true);
        mapper.save(consent);
        
        if (activeConsent != null && activeConsent.getCreatedOn() != consent.getCreatedOn()) {
            ((DynamoStudyConsent1)activeConsent).setActive(false);
            mapper.save(activeConsent);
        }
        return consent;
    }

    @Override
    public StudyConsent getActiveConsent(String subpopGuid) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(subpopGuid);
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));
        PaginatedQueryList<DynamoStudyConsent1> page = mapper.query(DynamoStudyConsent1.class, queryExpression);
        if (page.isEmpty()) {
            return null;
        }
        return page.iterator().next();
    }

    @Override
    public StudyConsent getMostRecentConsent(String subpopGuid) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(subpopGuid);
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withLimit(1);
        PaginatedQueryList<DynamoStudyConsent1> page = mapper.query(DynamoStudyConsent1.class, queryExpression);
        if (page.isEmpty()) {
            return null;
        }
        return page.iterator().next();
    }
    
    @Override
    public StudyConsent getConsent(String subpopGuid, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setSubpopulationGuid(subpopGuid);
        consent.setCreatedOn(timestamp);
        return mapper.load(consent);
    }

    @Override
    public List<StudyConsent> getConsents(String subpopGuid) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(subpopGuid);
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false);
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        List<StudyConsent> results = new ArrayList<StudyConsent>();
        for (DynamoStudyConsent1 consent : consents) {
            results.add(consent);
        }
        return results;
    }
    
    @Override
    public void deleteAllConsents(String subpopGuid) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setSubpopulationGuid(subpopGuid);
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false);
        
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        List<StudyConsent> consentsToDelete = new ArrayList<StudyConsent>();
        for (DynamoStudyConsent1 consent : consents) {
            consentsToDelete.add(consent);
        }
        if (!consentsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(consentsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}
