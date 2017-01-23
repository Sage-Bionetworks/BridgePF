package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.CompoundActivityDefinitionDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

/** DynamoDB implementation of CompoundActivityDefinitionDao. */
@Component
public class DynamoCompoundActivityDefinitionDao implements CompoundActivityDefinitionDao {
    private DynamoDBMapper mapper;

    /** DDB mapper, configured by Spring. */
    @Resource(name = "compoundActivityDefinitionDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public CompoundActivityDefinition createCompoundActivityDefinition(
            CompoundActivityDefinition compoundActivityDefinition) {
        // Currently, all CompoundActivityDefinitions are DynamoCompoundActivityDefinitions.
        DynamoCompoundActivityDefinition ddbDef = (DynamoCompoundActivityDefinition) compoundActivityDefinition;

        // Clear the version. This allows people to copy-paste defs.
        ddbDef.setVersion(null);

        // Call DDB to create.
        try {
            mapper.save(ddbDef);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(ddbDef);
        }

        return ddbDef;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteCompoundActivityDefinition(StudyIdentifier studyId, String taskId) {
        // For whatever reason, DynamoDBMapper requires you to load the object before you delete it. It seems like you
        // can't use Delete Expressions to make this a single atomic request.
        DynamoCompoundActivityDefinition loadedDef = (DynamoCompoundActivityDefinition) getCompoundActivityDefinition(
                studyId, taskId);

        // Call DDB to delete.
        try {
            mapper.delete(loadedDef);
        } catch(ConditionalCheckFailedException e) {
            // in case of race condition
            throw new EntityNotFoundException(CompoundActivityDefinition.class);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId) {
        // First, query for all defs in a study.
        List<DynamoCompoundActivityDefinition> ddbDefList = getAllHelper(studyId);

        // Then, batch delete.
        List<DynamoDBMapper.FailedBatch> failedBatchList = mapper.batchDelete(ddbDefList);
        BridgeUtils.ifFailuresThrowException(failedBatchList);
    }

    /** {@inheritDoc} */
    @Override
    public List<CompoundActivityDefinition> getAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId) {
        List<DynamoCompoundActivityDefinition> ddbDefList = getAllHelper(studyId);

        // because of generics wonkiness, we need to convert this to a list of parent class CompoundActivityDefinition
        return ImmutableList.copyOf(ddbDefList);
    }

    // Helper method for getting all defs in a study. Returns the raw results using a list of the implementation type.
    // This enables us to bulk load and batch delete.
    private List<DynamoCompoundActivityDefinition> getAllHelper(StudyIdentifier studyId) {
        // query expression
        DynamoCompoundActivityDefinition ddbHashKey = new DynamoCompoundActivityDefinition();
        ddbHashKey.setStudyId(studyId.getIdentifier());
        DynamoDBQueryExpression<DynamoCompoundActivityDefinition> ddbQueryExpr =
                new DynamoDBQueryExpression<DynamoCompoundActivityDefinition>().withHashKeyValues(ddbHashKey);

        // execute query
        List<DynamoCompoundActivityDefinition> defList = mapper.query(DynamoCompoundActivityDefinition.class,
                ddbQueryExpr);
        return defList;
    }

    /** {@inheritDoc} */
    @Override
    public CompoundActivityDefinition getCompoundActivityDefinition(StudyIdentifier studyId, String taskId) {
        // create key object
        DynamoCompoundActivityDefinition ddbDef = new DynamoCompoundActivityDefinition();
        ddbDef.setStudyId(studyId.getIdentifier());
        ddbDef.setTaskId(taskId);

        // Call DDB mapper. Throw exception if null.
        DynamoCompoundActivityDefinition loadedDef = mapper.load(ddbDef);
        if (loadedDef == null) {
            throw new EntityNotFoundException(CompoundActivityDefinition.class);
        }

        return loadedDef;
    }

    /** {@inheritDoc} */
    @Override
    public CompoundActivityDefinition updateCompoundActivityDefinition(
            CompoundActivityDefinition compoundActivityDefinition) {
        // Currently, both a save expression and a mismatched version number will throw a
        // ConditionalCheckFailedException, so it's not possible to distinguish between an EntityNotFound and a
        // ConcurrentModificationException using a single atomic DDB request. Since ConcurrentModification is more
        // nefarious, we'll use the atomic operation for that and use a load-before-update to check that the def
        // already exists.

        // Call get() to verify the def exists. This will throw an EntityNotFoundException if it doesn't exist.
        StudyIdentifier studyId = new StudyIdentifierImpl(compoundActivityDefinition.getStudyId());
        String taskId = compoundActivityDefinition.getTaskId();
        getCompoundActivityDefinition(studyId, taskId);

        // Call DDB mapper to save.
        try {
            mapper.save(compoundActivityDefinition);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(compoundActivityDefinition);
        }

        return compoundActivityDefinition;
    }
}
