package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoStudyConsent1.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public StudyConsent addConsent(String studyKey, String path, int minAge) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyKey);
        consent.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        consent.setPath(path);
        consent.setMinAge(minAge);
        mapper.save(consent);
        return consent;
    }

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

    @Override
    public StudyConsent getConsent(String studyKey) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyKey);
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
    public StudyConsent getConsent(String studyKey, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyKey);
        consent.setCreatedOn(timestamp);
        return mapper.load(consent);
    }

    @Override
    public List<StudyConsent> getConsents(String studyKey) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyKey);
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
    public void deleteConsent(String studyKey, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyKey);
        consent.setCreatedOn(timestamp);
        consent = mapper.load(consent);
        if (consent != null) {
            mapper.delete(consent);
        }
    }
}
