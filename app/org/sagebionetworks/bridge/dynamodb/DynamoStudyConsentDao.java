package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

@Component
public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoStudyConsent1.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public StudyConsent addConsent(StudyIdentifier studyIdentifier, String path, String storagePath, DateTime createdOn) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(createdOn.getMillis());
        consent.setPath(path);
        consent.setStoragePath(storagePath);
        mapper.save(consent);
        return consent;
    }

    @Override
    public StudyConsent activateConsent(StudyConsent studyConsent) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyConsent.getStudyKey());
        consent.setCreatedOn(studyConsent.getCreatedOn());
        consent = mapper.load(consent);
        consent.setActive(true);
        
        List<DynamoStudyConsent1> consentsToSave = Lists.newArrayList(consent);
        
        // Get all active consents. All of them except the current one, set to inactive and add to consentsToSave
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyConsent.getStudyKey());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));
        
        QueryResultPage<DynamoStudyConsent1> page = mapper.queryPage(DynamoStudyConsent1.class, queryExpression);
        for (DynamoStudyConsent1 otherConsent : page.getResults()) {
            if (otherConsent.getCreatedOn() != consent.getCreatedOn()) {
                otherConsent.setActive(false);
                consentsToSave.add(otherConsent);
            }
        }
        List<FailedBatch> failures = mapper.batchSave(consentsToSave);
        BridgeUtils.ifFailuresThrowException(failures);

        return consent;
    }
    
    /**
     * Note that if there is no other active consent, the study will not be able to return a 
     * consent and will be broken.
     * @param studyConsent
     * @return
     */
    @Override
    public StudyConsent deactivateConsent(StudyConsent studyConsent) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyConsent.getStudyKey());
        consent.setCreatedOn(studyConsent.getCreatedOn());
        consent = mapper.load(consent);
        consent.setActive(false);
        mapper.save(consent);
        return consent;
    }
    
    /*
    @Override
    public StudyConsent setActive(StudyConsent studyConsent, boolean active) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyConsent.getStudyKey());
        consent.setCreatedOn(studyConsent.getCreatedOn());
        consent = mapper.load(consent);
        consent.setActive(active);
        mapper.save(consent);
        return consent;
    }
*/
    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));
        QueryResultPage<DynamoStudyConsent1> page = mapper.queryPage(DynamoStudyConsent1.class, queryExpression);
        if (page == null || page.getResults().size() == 0) {
            return null;
        }
        return page.getResults().get(0);
    }

    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(timestamp);
        return mapper.load(consent);
    }

    @Override
    public List<StudyConsent> getConsents(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
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
    public void deleteConsent(StudyIdentifier studyIdentifier, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(timestamp);
        consent = mapper.load(consent);
        if (consent != null) {
            mapper.delete(consent);
        }
    }
}
