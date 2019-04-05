package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.ScheduleValidator;

import com.google.common.collect.Lists;

public final class CriteriaScheduleStrategy implements ScheduleStrategy {
    
    private final List<ScheduleCriteria> scheduleCriteria = Lists.newArrayList();

    public void addCriteria(ScheduleCriteria criteria) {
        this.scheduleCriteria.add(criteria);
    }
    
    public List<ScheduleCriteria> getScheduleCriteria() {
        return scheduleCriteria;
    }
    
    public void setScheduleCriteria(List<ScheduleCriteria> criteria) {
        this.scheduleCriteria.clear();
        if (criteria != null) {
            this.scheduleCriteria.addAll(criteria);    
        }
    }    
    
    /**
     * Iterate through the list of schedules and return the first schedule that matches 
     * the criteria submitted for this user and this request (so order matters). Can 
     * return null, and this is accounted for now elsewehere in the code.
     * @param plan
     * @param context
     * @return schedule that matches users's criteria, or null if no schedules match the 
     *      criteria.
     */
    @Override
    public Schedule getScheduleForUser(SchedulePlan plan, ScheduleContext context) {
        for (ScheduleCriteria oneScheduleCriteria : scheduleCriteria) {
            if (CriteriaUtils.matchCriteria(context.getCriteriaContext(), oneScheduleCriteria.getCriteria())) {
                return oneScheduleCriteria.getSchedule();
            }
        }
        return null;        
    }

    @Override
    public void validate(Set<String> dataGroups, Set<String> substudyIds, Set<String> taskIdentifiers, Errors errors) {
        for (int i=0; i < scheduleCriteria.size(); i++) {
            ScheduleCriteria schCriteria = scheduleCriteria.get(i);
            errors.pushNestedPath("scheduleCriteria["+i+"]");
            if (schCriteria.getSchedule() == null){
                errors.rejectValue("schedule", "is required");
            } else {
                errors.pushNestedPath("schedule");
                new ScheduleValidator(taskIdentifiers).validate(schCriteria.getSchedule(), errors);
                errors.popNestedPath();
            }
            if (schCriteria.getCriteria() == null) {
                errors.rejectValue("criteria", "is required");
            } else {
                errors.pushNestedPath("criteria");
                CriteriaUtils.validate(schCriteria.getCriteria(), dataGroups, substudyIds, errors);
                errors.popNestedPath();
            }
            errors.popNestedPath();
        }
    }

    @Override
    public List<Schedule> getAllPossibleSchedules() {
        return scheduleCriteria.stream().map(ScheduleCriteria::getSchedule).collect(BridgeCollectors.toImmutableList());
    }        
    
    @Override
    public int hashCode() {
        return Objects.hash(scheduleCriteria);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CriteriaScheduleStrategy other = (CriteriaScheduleStrategy) obj;
        return Objects.equals(scheduleCriteria, other.scheduleCriteria);
    }

    @Override
    public String toString() {
        return "CriteriaScheduleStrategy [scheduleCriteria=" + scheduleCriteria + "]";
    }
}
