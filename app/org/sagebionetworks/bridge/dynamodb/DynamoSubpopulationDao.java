package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.CriteriaUtils.SPECIFICITY_SORTER;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.collect.Lists;

@Component
public class DynamoSubpopulationDao implements SubpopulationDao {
    
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the HealthDataRecord table. This is configured by Spring. */
    @Resource(name = "subpopulationDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public Subpopulation createSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getStudyIdentifier());

        subpop.setVersion(null);
        subpop.setDeleted(false);
        subpop.setGuid(BridgeUtils.generateGuid());
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public Subpopulation updateSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getStudyIdentifier());
        checkNotNull(subpop.getVersion());
        checkNotNull(subpop.getGuid());
        
        // This will throw a not found exception if this is not an update. 
        // It also prevents deletion through this call.
        Subpopulation existing = getSubpopulation(new StudyIdentifierImpl(subpop.getStudyIdentifier()), subpop.getGuid());
        subpop.setDeleted(existing.isDeleted());
        
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean allExistingRecordsOnly) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSubpopulation> query = 
                new DynamoDBQueryExpression<DynamoSubpopulation>().withHashKeyValues(hashKey);
        
        // Get all the records because we only create a default if there are no physical records, 
        // regardless of the deletion status.
        List<DynamoSubpopulation> subpops = mapper.query(DynamoSubpopulation.class, query);
        if (!allExistingRecordsOnly && subpops.isEmpty()) {
            Subpopulation subpop = createDefaultSubpopulation(studyId);
            return Lists.newArrayList(subpop);
        }
        // Now filter out deleted subpopulations, if requested
        return subpops.stream()
            .filter(subpop -> allExistingRecordsOnly || !subpop.isDeleted())
            .sorted(SPECIFICITY_SORTER).collect(Collectors.toList());
    }
    
    @Override
    public Subpopulation createDefaultSubpopulation(StudyIdentifier studyId) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setGuid(studyId.getIdentifier());
        subpop.setName("Default Consent Group");
        subpop.setMinAppVersion(0);
        subpop.setRequired(true);
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
    public Subpopulation getSubpopulationForUser(ScheduleContext context) {
        List<Subpopulation> found = Lists.newArrayList();

        List<Subpopulation> subpops = getSubpopulations(context.getStudyIdentifier(), false);
        for (Subpopulation subpop : subpops) {
            boolean matches = CriteriaUtils.matchCriteria(context, subpop);
            if (matches) {
                found.add(subpop);
            }
        }
        if (found.isEmpty()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        // Return the most specific match. Eventually we may return them all
        return found.stream().sorted(SPECIFICITY_SORTER).collect(Collectors.toList()).get(0);
    }
    
    @Override
    public void deleteSubpopulation(StudyIdentifier studyId, String guid) {
        Subpopulation subpop = getSubpopulation(studyId, guid);
        subpop.setDeleted(true);
        mapper.save(subpop);
    }

    @Override
    public void deleteAllSubpopulations(StudyIdentifier studyId) {
        List<Subpopulation> subpops = getSubpopulations(studyId, true);
        
        if (!subpops.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(subpops);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
}
