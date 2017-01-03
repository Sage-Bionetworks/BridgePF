package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableSet;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoStudyDao implements StudyDao {
    private static final Set<String> STUDY_WHITE_LIST = ImmutableSet.copyOf(
            BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist"));

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client, DynamoNamingHelper dynamoNamingHelper) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(dynamoNamingHelper.getTableNameOverride(DynamoStudy.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public boolean doesIdentifierExist(String identifier) {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(identifier);
        return (mapper.load(study) != null);
    }
    
    @Override
    public Study getStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(identifier);
        study = mapper.load(study);
        if (study == null) {
            throw new EntityNotFoundException(Study.class, "Study '"+identifier+"' not found.");
        }
        if (!study.isActive()) {
            throw new EntityNotFoundException(Study.class, "Study '"+identifier+"' is de-activated.");
        }
        return study;
    }

    /**
     * Only used for dao and testing to get a study no matter if it is deactivated or not
     * @param identifier
     * @return
     */
    Study getStudyOnlyForDao(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(identifier);
        study = mapper.load(study);
        if (study == null) {
            throw new EntityNotFoundException(Study.class, "Study '"+identifier+"' not found.");
        }

        return study;
    }
    
    @Override
    public List<Study> getStudies() {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();

        // get all active studies
        List<DynamoStudy> mappings = mapper.scan(DynamoStudy.class, scan).stream()
                .filter(dynamoStudy -> dynamoStudy.isActive())
                .collect(Collectors.toList());

        return new ArrayList<Study>(mappings);
    }

    @Override
    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(study.getVersion() == null, "%s has a version; may not be new", "study");
        try {
            mapper.save(study);
        } catch(ConditionalCheckFailedException e) { // in the create scenario, this should be a hash key clash.
            throw new EntityAlreadyExistsException(study);
        }
        return study;
    }

    @Override
    public Study updateStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(study.getVersion(), Validate.CANNOT_BE_NULL, "study version");
        try {
            mapper.save(study);
        } catch(ConditionalCheckFailedException e) {
            throw new ConcurrentModificationException(study);
        }
        return study;
    }

    @Override
    public void deleteStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_BLANK, "study");

        String studyId = study.getIdentifier();
        if (STUDY_WHITE_LIST.contains(studyId)) {
            throw new UnauthorizedException(studyId + " is protected by whitelist.");
        }

        mapper.delete(study);
    }

    @Override
    public void deactivateStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_BLANK, "study");

        String studyId = study.getIdentifier();
        if (STUDY_WHITE_LIST.contains(studyId)) {
            throw new UnauthorizedException(studyId + " is protected by whitelist.");
        }

        study.setActive(false);

        updateStudy(study);
    }
}
