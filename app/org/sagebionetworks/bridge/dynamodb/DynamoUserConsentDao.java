package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

public class DynamoUserConsentDao implements UserConsentDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig(
                SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES,
                ConsistentReads.CONSISTENT,
                TableNameOverrideFactory.getTableNameOverride(DynamoUserConsent.class));
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void giveConsent(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent consent = new DynamoUserConsent();
        consent.setHealthCodeStudy(healthCode + ":" + studyConsent.getStudyKey());
        consent.setConsentTimestamp(studyConsent.getTimestamp());
        consent.setGive(DateTime.now(DateTimeZone.UTC).getMillis());
        mapper.save(consent);
    }

    @Override
    public void withdrawConsent(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent consent = new DynamoUserConsent();
        consent.setHealthCodeStudy(healthCode + ":" + studyConsent.getStudyKey());
        consent.setConsentTimestamp(studyConsent.getTimestamp());
        consent.setWithdraw(DateTime.now(DateTimeZone.UTC).getMillis());
        mapper.save(consent);
    }

    @Override
    public boolean hasConsented(String healthCode, StudyConsent studyConsent) {
        QueryResultPage<DynamoUserConsent> page = queryForConsent(healthCode, studyConsent.getStudyKey());
        return (page != null && page.getResults().size() > 0);
    }

    @Override
    public long getConsentTimestamp(String healthCode, String studyKey) {
        QueryResultPage<DynamoUserConsent> page = queryForConsent(healthCode, studyKey);
        if (page == null) {
            throw new RuntimeException("User hasn't consented yet.");
        }
        if (page.getResults().size() == 0) {
            throw new RuntimeException("User hasn't consented yet.");
        }
        return page.getResults().get(0).getConsentTimestamp();
    }

    private QueryResultPage<DynamoUserConsent> queryForConsent(String healthCode, String studyKey) {
        DynamoUserConsent consentWithHashKey = new DynamoUserConsent();
        consentWithHashKey.setHealthCodeStudy(healthCode + ":" + studyKey);
        DynamoDBQueryExpression<DynamoUserConsent> queryExpression =
                new DynamoDBQueryExpression<DynamoUserConsent>()
                .withHashKeyValues(consentWithHashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("withdraw", new Condition().withComparisonOperator(ComparisonOperator.NULL));
        return mapper.queryPage(DynamoUserConsent.class, queryExpression);
    }
}
