package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study2;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;

public class DynamoStudyDao implements StudyDao {

    private DynamoDBMapper mapper;

    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoStudy.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public boolean isStudyIdentifierUnique(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(identifier);
        study = mapper.load(study);
        return (study == null);
    }
    
    @Override
    public Study2 getStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(identifier);
        study = mapper.load(study);
        if (study == null) {
            throw new EntityNotFoundException(Study2.class);
        }
        return study;
    }
    
    @Override
    public List<Study2> getStudies() {
        DynamoDBScanExpression scan = new DynamoDBScanExpression();
        
        List<DynamoStudy> mappings = mapper.scan(DynamoStudy.class, scan);
        return new ArrayList<Study2>(mappings);
    }

    @Override
    public Study2 createStudy(Study2 study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkArgument(study.getVersion() == null, "%s has a version; may not be new", "study");
        if (!isStudyIdentifierUnique(study.getIdentifier())) {
            throw new EntityAlreadyExistsException(study);
        }
        mapper.save(study);
        return study;
    }

    @Override
    public Study2 updateStudy(Study2 study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(study.getVersion(), Validate.CANNOT_BE_NULL, "study version");
        
        mapper.save(study);
        return study;
    }

    @Override
    public void deleteStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        Study2 study = getStudy(identifier);
        mapper.delete(study);
    }

}
