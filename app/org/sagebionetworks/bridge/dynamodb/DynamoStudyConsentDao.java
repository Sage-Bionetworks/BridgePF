package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoStudyConsent.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public StudyConsent addConsent(String studyKey, String path, int minAge) {
        DynamoStudyConsent consent = new DynamoStudyConsent();
        consent.setStudyKey(studyKey);
        consent.setTimestamp(DateTime.now(DateTimeZone.UTC).getMillis());
        consent.setPath(path);
        consent.setMinAge(minAge);
        mapper.save(consent);
        return consent;
    }

    @Override
    public void setActive(StudyConsent studyConsent) {
        DynamoStudyConsent consent = new DynamoStudyConsent();
        consent.setStudyKey(studyConsent.getStudyKey());
        consent.setTimestamp(studyConsent.getTimestamp());
        consent = mapper.load(consent);
        consent.setActive(true);
        mapper.save(consent);
    }

    @Override
    public StudyConsent getConsent(String studyKey) {
        DynamoStudyConsent consentWithHashKey = new DynamoStudyConsent();
        consentWithHashKey.setStudyKey(studyKey);
        DynamoDBQueryExpression<DynamoStudyConsent> queryExpression =
                new DynamoDBQueryExpression<DynamoStudyConsent>()
                .withHashKeyValues(consentWithHashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));
        QueryResultPage<DynamoStudyConsent> page = mapper.queryPage(DynamoStudyConsent.class, queryExpression);
        if (page == null || page.getResults().size() == 0) {
            return null;
        }
        return page.getResults().get(0);
    }

    @Override
    public StudyConsent getConsent(String studyKey, long timestamp) {
        DynamoStudyConsent consent = new DynamoStudyConsent();
        consent.setStudyKey(studyKey);
        consent.setTimestamp(timestamp);
        return mapper.load(consent);
    }
}
