package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@Component
public class DynamoSubpopulationDao implements SubpopulationDao {
    
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the HealthDataRecord table. This is configured by Spring. */
    @Resource(name = "subpopulationDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public Subpopulation createSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getGuid());
        checkNotNull(subpop.getStudyIdentifier());

        // guid should always be set in service, so it's okay to check with a checkNotNull. But 
        // if version is present, that's a bad submission from the service user
        if (subpop.getVersion() != null) { 
            throw new BadRequestException("Subpopulation does not appear to be new (includes version number).");
        }
        // these are completely ignored, if submitted
        subpop.setDeleted(false); 
        subpop.setDefaultGroup(false);
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public Subpopulation updateSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getStudyIdentifier());
        
        // These have to be supplied by the user so if they don't exist, we want a 400-level exception,
        // not a checkNotNull which translates to a 500 level response
        if (subpop.getVersion() == null || subpop.getGuid() == null) {
            throw new BadRequestException("Subpopulation appears to be a new object (no guid or version).");
        }
        StudyIdentifier studyId = new StudyIdentifierImpl(subpop.getStudyIdentifier());
        Subpopulation existing = getSubpopulation(studyId, subpop.getGuid());
        if (existing == null || existing.isDeleted()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        // these are ignored if submitted. delete remains what it was
        subpop.setDefaultGroup(existing.isDefaultGroup()); 
        subpop.setDeleted(false);
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean createDefault, boolean includeDeleted) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSubpopulation> query = 
                new DynamoDBQueryExpression<DynamoSubpopulation>().withHashKeyValues(hashKey);
        
        // Get all the records because we only create a default if there are no physical records, 
        // regardless of the deletion status.
        List<DynamoSubpopulation> subpops = mapper.query(DynamoSubpopulation.class, query);
        if (createDefault && subpops.isEmpty()) {
            Subpopulation subpop = createDefaultSubpopulation(studyId);
            return ImmutableList.of(subpop);
        }
        // Now filter out deleted subpopulations, if requested
        return subpops.stream()
            .filter(subpop -> includeDeleted || !subpop.isDeleted())
            .collect(toImmutableList());
    }
    
    @Override
    public Subpopulation createDefaultSubpopulation(StudyIdentifier studyId) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setGuid(studyId.getIdentifier());
        subpop.setName("Default Consent Group");
        subpop.setMinAppVersion(0);
        subpop.setDefaultGroup(true);
        mapper.save(subpop);
        return subpop;
    }
    
    @Override
    public Subpopulation getSubpopulation(StudyIdentifier studyId, String guid) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        Subpopulation subpop = mapper.load(hashKey);
        if (subpop == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        return subpop;
    }

    @Override
    public List<Subpopulation> getSubpopulationsForUser(ScheduleContext context) {
        List<Subpopulation> subpops = getSubpopulations(context.getStudyIdentifier(), true, false);
        
        return subpops.stream().filter(subpop -> {
            return CriteriaUtils.matchCriteria(context, subpop);
        }).collect(toImmutableList());
    }
    
    @Override
    public void deleteSubpopulation(StudyIdentifier studyId, String guid) {
        Subpopulation subpop = getSubpopulation(studyId, guid);
        if (subpop == null || subpop.isDeleted()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        if (subpop.isDefaultGroup()) {
            throw new BadRequestException("Cannot delete the default subpopulation for a study.");
        }
        subpop.setDeleted(true);
        mapper.save(subpop);
    }

    @Override
    public void deleteAllSubpopulations(StudyIdentifier studyId) {
        List<Subpopulation> subpops = getSubpopulations(studyId, false, true);
        
        if (!subpops.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(subpops);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
}
