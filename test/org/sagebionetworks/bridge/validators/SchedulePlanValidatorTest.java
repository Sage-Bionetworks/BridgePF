package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;

public class SchedulePlanValidatorTest {

	private SchedulePlanValidator validator;

	@Before
	public void before() throws Exception {
		validator = new SchedulePlanValidator();
	}

	@Test
	public void validSchedulePlan() {
		SchedulePlan plan = getValidSimpleStrategy();
		Validate.entityThrowingException(validator, plan);

		plan = getValidABTestStrategy();
		Validate.entityThrowingException(validator, plan);
	}

	@Test
	public void studyKeyRequired() {
		SchedulePlan plan = getValidSimpleStrategy();
		plan.setStudyKey(null);
		assertMessage(plan, "studyKey cannot be missing, null, or blank", "studyKey");
	}

	@Test
	public void labelRequired() {
		SchedulePlan plan = getValidSimpleStrategy();
		plan.setLabel(null);
		assertMessage(plan, "label cannot be missing, null, or blank", "label");
	}

	@Test
	public void strategyRequired() {
		SchedulePlan plan = getValidSimpleStrategy();
		plan.setStrategy(null);
		assertMessage(plan, "strategy is required", "strategy");
	}

	@Test
	public void simpleStrategyScheduleRequired() {
		SchedulePlan plan = getValidSimpleStrategy();
		((SimpleScheduleStrategy)plan.getStrategy()).setSchedule(null);
		assertMessage(plan, "strategy.schedule is required", "strategy.schedule");
	}
	
	@Test
	public void abTestStrategyScheduleAddsUpTo100() {
		SchedulePlan plan = getValidABTestStrategy();
		((ABTestScheduleStrategy)plan.getStrategy()).getScheduleGroups().get(0).setPercentage(10);
		
		assertMessage(plan, "strategy.scheduleGroups groups must add up to 100% (not 40%; give 20% as 20, for example)", "strategy.scheduleGroups");
	}
	
	@Test
	public void abTestStrategyScheduleRequiresSchedule() {
		SchedulePlan plan = getValidABTestStrategy();
		((ABTestScheduleStrategy)plan.getStrategy()).getScheduleGroups().get(0).setSchedule(null);
		
		assertMessage(plan, "strategy.scheduleGroups0.schedule is required", "strategy.scheduleGroups0.schedule");
	}

	private void assertMessage(SchedulePlan plan, String message, String fieldName) {
		try {
			Validate.entityThrowingException(validator, plan);
			fail("Should have thrown exception");
		} catch (InvalidEntityException e) {
			assertEquals(message, e.getErrors().get(fieldName).get(0));
		}
	}

	private Schedule getSchedule() {
		Schedule schedule = new Schedule();
		schedule.setScheduleType(ScheduleType.ONCE);
		schedule.setLabel("a label");
		schedule.addActivity(TestConstants.TEST_3_ACTIVITY);
		return schedule;
	}

	private SchedulePlan getValidSimpleStrategy() {
		SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
		strategy.setSchedule(getSchedule());

		// This is valid, then you can manipulate it to invalidate it.
		SchedulePlan plan = new DynamoSchedulePlan();
		plan.setLabel("a label");
		plan.setStudyKey("study-key");
		plan.setStrategy(strategy);
		return plan;
	}

	private SchedulePlan getValidABTestStrategy() {
		ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
		strategy.addGroup(70, getSchedule());
		strategy.addGroup(30, getSchedule());

		// This is valid, then you can manipulate it to invalidate it.
		SchedulePlan plan = new DynamoSchedulePlan();
		plan.setLabel("a label");
		plan.setStudyKey("study-key");
		plan.setStrategy(strategy);
		return plan;
	}
}
