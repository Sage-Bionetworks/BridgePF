package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyCohortDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.StudyCohort;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.google.common.collect.Lists;

@Component
public class DynamoStudyCohortDao implements StudyCohortDao {
    
    /**
     * We want to evaluate these from most to least specific, so we sort them that way, most
     * specific first.
     */
    private static final Comparator<StudyCohort> STUDY_COHORT_COMPARATOR = new Comparator<StudyCohort>() {
        @Override
        public int compare(StudyCohort cohort1, StudyCohort cohort2) {
            return specificity(cohort2) - specificity(cohort1);
        }
        private int specificity(StudyCohort cohort) {
            return bit(cohort.getDataGroup()) + bit(cohort.getMinAppVersion()) + bit(cohort.getMaxAppVersion());
        }
        private int bit(Object object) {
            return (object == null) ? 0 : 1;
        }
    };

    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the HealthDataRecord table. This is configured by Spring. */
    @Resource(name = "studyCohortDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public StudyCohort createStudyCohort(StudyCohort studyCohort) {
        checkNotNull(studyCohort);
        checkNotNull(studyCohort.getStudyIdentifier());

        studyCohort.setVersion(null);
        studyCohort.setGuid(BridgeUtils.generateGuid());
        mapper.save(studyCohort);
        return studyCohort;
    }

    @Override
    public StudyCohort updateStudyCohort(StudyCohort studyCohort) {
        checkNotNull(studyCohort);
        checkNotNull(studyCohort.getStudyIdentifier());
        checkNotNull(studyCohort.getVersion());
        checkNotNull(studyCohort.getGuid());
        
        mapper.save(studyCohort);
        return studyCohort;
    }

    /**
     * If no cohorts can be found by this method, it will create a default cohort using
     * a key that adopts the existing consent stream.
     */
    @Override
    public List<StudyCohort> getStudyCohorts(StudyIdentifier studyId, boolean createDefault) {
        DynamoStudyCohort hashKey = new DynamoStudyCohort();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        
        DynamoDBQueryExpression<DynamoStudyCohort> query = 
                new DynamoDBQueryExpression<DynamoStudyCohort>().withHashKeyValues(hashKey);
        
        List<DynamoStudyCohort> cohorts = mapper.query(DynamoStudyCohort.class, query);
        if (createDefault && cohorts.isEmpty()) {
            DynamoStudyCohort cohort = new DynamoStudyCohort();
            cohort.setStudyIdentifier(studyId.getIdentifier());
            // Using the studyId as the GUID ensures this first cohort is linked to the existing
            // stream of consent documents. Future cohorts will have a GUID instead.
            cohort.setGuid(studyId.getIdentifier());
            cohort.setName("Default Cohort");
            cohort.setMinAppVersion(0);
            cohort.setRequired(true);
            mapper.save(cohort);
            
            cohorts = mapper.query(DynamoStudyCohort.class, query);
        }
        return cohorts.stream().sorted(STUDY_COHORT_COMPARATOR).collect(Collectors.toList());
    }
    
    @Override
    public StudyCohort getStudyCohort(StudyIdentifier studyId, String guid) {
        DynamoStudyCohort hashKey = new DynamoStudyCohort();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        StudyCohort cohort = mapper.load(hashKey);
        if (cohort == null) {
            throw new EntityNotFoundException(StudyCohort.class);
        }
        return cohort;
    }

    @Override
    public StudyCohort getStudyCohortForUser(ScheduleContext context) {
        List<StudyCohort> found = Lists.newArrayList();

        List<StudyCohort> cohorts = getStudyCohorts(context.getStudyIdentifier(), true);
        for (StudyCohort cohort : cohorts) {
            if (isTargetedAppVersion(context, cohort) && isTargetedDataGroup(context, cohort)) {
                found.add(cohort);
            }
        }
        if (found.isEmpty()) {
            throw new EntityNotFoundException(StudyCohort.class);
        }
        // Return the most specific match. Eventually we may return them all
        return found.stream().sorted(STUDY_COHORT_COMPARATOR).collect(Collectors.toList()).get(0);
    }
    
    @Override
    public void deleteStudyCohort(StudyIdentifier studyId, String guid) {
        StudyCohort cohort = getStudyCohort(studyId, guid);
        mapper.delete(cohort);
    }

    private boolean isTargetedAppVersion(ScheduleContext context, StudyCohort cohort) {
        return context.getClientInfo().isTargetedAppVersion(cohort.getMinAppVersion(), cohort.getMaxAppVersion());
    }
    
    private boolean isTargetedDataGroup(ScheduleContext context, StudyCohort cohort) {
        return cohort.getDataGroup() == null || context.getUserDataGroups().contains(cohort.getDataGroup());
    }
    
}
