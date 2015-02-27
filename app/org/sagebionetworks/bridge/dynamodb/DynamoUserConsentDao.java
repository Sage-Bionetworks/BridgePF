package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.Sets;

@Component
public class DynamoUserConsentDao implements UserConsentDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUserConsent2.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public void giveConsent(String healthCode, StudyConsent studyConsent) {
        checkArgument(isNotBlank(healthCode), "Health code is blank or null");
        checkNotNull(studyConsent);
        DynamoUserConsent2 consent = null;
        try {
            consent = getUserConsent(healthCode, studyConsent);
            if (consent == null) {
                consent = new DynamoUserConsent2(healthCode, studyConsent);
            }
            consent.setSignedOn(DateTime.now(DateTimeZone.UTC).getMillis());
            mapper.save(consent);
        } catch (ConditionalCheckFailedException e) {
            throw new EntityAlreadyExistsException(consent);
        }
    }

    @Override
    public boolean withdrawConsent(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent2 consent = (DynamoUserConsent2) getUserConsent(healthCode, studyIdentifier);
        if (consent != null) {
            mapper.delete(consent);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasConsented(String healthCode, StudyIdentifier studyIdentifier) {
        return getUserConsent(healthCode, studyIdentifier) != null;
    }

    @Override
    public UserConsent getUserConsent(String healthCode, StudyIdentifier studyIdentifier) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyIdentifier.getIdentifier());
        consent = mapper.load(consent);
        return consent;
    }

    @Override
    public long getNumberOfParticipants(StudyIdentifier studyIdentifier) {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        Condition condition = new Condition();
        condition.withComparisonOperator(ComparisonOperator.EQ);
        condition.withAttributeValueList(new AttributeValue().withS(studyIdentifier.getIdentifier()));
        scan.addFilterCondition("studyKey", condition);

        Set<String> healthCodes = Sets.newHashSet();
        List<DynamoUserConsent2> mappings = mapper.scan(DynamoUserConsent2.class, scan);
        for (DynamoUserConsent2 consent : mappings) {
            healthCodes.add(consent.getHealthCode());
        }
        return healthCodes.size();
    }

    private DynamoUserConsent2 getUserConsent(String healthCode, StudyConsent studyConsent) {
        DynamoUserConsent2 consent = new DynamoUserConsent2(healthCode, studyConsent);
        consent = mapper.load(consent);
        return consent;
    }
}
