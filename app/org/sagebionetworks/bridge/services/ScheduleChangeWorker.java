package org.sagebionetworks.bridge.services;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.ScheduleDao;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.events.SchedulePlanCreatedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanDeletedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanUpdatedEvent;
import org.sagebionetworks.bridge.events.UserEnrolledEvent;
import org.sagebionetworks.bridge.events.UserUnenrolledEvent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;

import com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

public class ScheduleChangeWorker implements Callable<Boolean> {
    
    private static Logger logger = LoggerFactory.getLogger(ScheduleChangeWorker.class);

    private ApplicationEvent event;
    private Client stormpathClient;
    private DistributedLockDao lockDao;
    private ScheduleDao scheduleDao;
    private SchedulePlanDao schedulePlanDao;
    private StudyService studyService;
   
    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }
    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    public void setScheduleDao(ScheduleDao scheduleDao) {
        this.scheduleDao = scheduleDao;
    }
    public void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    public void setApplicationEvent(ApplicationEvent event) {
        this.event = event;
    }
    
    @Override
    public Boolean call() throws Exception {
        try {
            if (event instanceof SchedulePlanCreatedEvent) {
                schedulePlanCreated((SchedulePlanCreatedEvent)event);
            } 
            else if (event instanceof SchedulePlanUpdatedEvent) {
                schedulePlanUpdated((SchedulePlanUpdatedEvent)event);
            } 
            else if (event instanceof SchedulePlanDeletedEvent) {
                schedulePlanDeleted((SchedulePlanDeletedEvent)event);
            } 
            else if (event instanceof UserEnrolledEvent) {
                userEnrolled((UserEnrolledEvent)event);
            } 
            else if (event instanceof UserUnenrolledEvent) {
                userUnenrolled((UserUnenrolledEvent)event);
            }        
        } catch(Throwable throwable) {
            logger.error(throwable.getMessage(), throwable);
            return false;
        }
        return true;
    }

    private void schedulePlanCreated(SchedulePlanCreatedEvent event) {
        logger.info("EVENT: Schedule plan "+event.getSchedulePlan().getGuid()+" created");
        
        SchedulePlan plan = event.getSchedulePlan();
        Study study = studyService.getStudyByKey(plan.getStudyKey());
        ArrayList<User> users = getStudyUsers();
        List<Schedule> schedules = plan.getStrategy().scheduleExistingUsers(study, users);
        setSchedulePlan(schedules, plan);
        
        String lockId = null;
        try {
            // Find all users, create schedules for them as a group
            lockId = lockDao.createLock(Study.class, study.getKey());
            scheduleDao.createSchedules(schedules);
        } finally {
            lockDao.releaseLock(Study.class, study.getKey(), lockId);
        }
    }
    private void schedulePlanDeleted(SchedulePlanDeletedEvent event) {
        logger.info("EVENT: Schedule plan "+event.getSchedulePlan().getGuid()+" deleted");
        
        SchedulePlan plan = event.getSchedulePlan();
        Study study = studyService.getStudyByKey(plan.getStudyKey());
        
        String lockId = null;
        try {
            // Find all schedules for this plan, delete them
            lockId = lockDao.createLock(Study.class, study.getKey());
            scheduleDao.deleteSchedules(plan);
        } finally {
            lockDao.releaseLock(Study.class, study.getKey(), lockId);
        }
    }
    private void schedulePlanUpdated(SchedulePlanUpdatedEvent event) {
        logger.info("EVENT: Schedule plan "+event.getSchedulePlan().getGuid()+" updated");
        
        SchedulePlan plan = event.getSchedulePlan();
        Study study = studyService.getStudyByKey(plan.getStudyKey());
        ArrayList<User> users = getStudyUsers();
        List<Schedule> schedules = plan.getStrategy().scheduleExistingUsers(study, users);
        setSchedulePlan(schedules, plan);
        
        String lockId = null;
        try {
            // Find all schedules for this plan, delete them
            // Find all users, create schedules for them as a group
            lockId = lockDao.createLock(Study.class, study.getKey());
            scheduleDao.deleteSchedules(plan);
            scheduleDao.createSchedules(schedules);
        } finally {
            lockDao.releaseLock(Study.class, study.getKey(), lockId);
        }
    }
    private void userEnrolled(UserEnrolledEvent event) {
        logger.info("EVENT: User " + event.getUser().getId() + " enrolled in study " + event.getStudy().getKey());
        
        Study study = event.getStudy();
        User user = event.getUser();
        List<SchedulePlan> plans = schedulePlanDao.getSchedulePlans(event.getStudy());
        List<Schedule> schedules = Lists.newArrayListWithCapacity(plans.size()); 
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().scheduleNewUser(study, user);
            schedule.setSchedulePlanGuid(plan.getGuid());
            schedules.add(schedule);
        }
        
        String lockId = null;
        try {
            // Find all the plans, assemble a list of schedules for this user, save
            lockId = lockDao.createLock(Study.class, study.getKey());
            scheduleDao.createSchedules(schedules);
        } finally {
            lockDao.releaseLock(Study.class, study.getKey(), lockId);
        }
    }
    private void userUnenrolled(UserUnenrolledEvent event) {
        logger.info("EVENT: User " + event.getUser().getId() + " withdrawn from study " + event.getStudy().getKey());
        
        Study study = event.getStudy();
        User user = event.getUser();
        
        String lockId = null;
        try {
            // Find all schedules for this user, delete them
            lockId = lockDao.createLock(Study.class, study.getKey());
            scheduleDao.deleteSchedules(study, user);
        } finally {
            lockDao.releaseLock(Study.class, study.getKey(), lockId);
        }
        
    }
    private void setSchedulePlan(List<Schedule> schedules, SchedulePlan plan) {
        for (Schedule schedule : schedules) {
            schedule.setSchedulePlanGuid(plan.getGuid());
        }
    }
    // TEMPORARY. This is all users who signed up for a study, not all who are, at this moment, consented to 
    // participate. They'll certainly be very close in the alpha, so this is fine for now.
    // Also, I would just return IDs here, but it seems that requirements will eventually require something
    // more complex that this... not necessarily a user though. E.g. cohort codes.
    private ArrayList<User> getStudyUsers() {
        ArrayList<User> users = Lists.newArrayList();
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        // TODO: Don't see a way to only retrieve the ID, is this paginated?!?
        AccountList accounts = application.getAccounts(); 
        for (Account account : accounts) {
            users.add(new User(account));
        }
        return users;
    }
    
}
