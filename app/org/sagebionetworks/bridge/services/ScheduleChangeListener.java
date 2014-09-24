package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.events.SchedulePlanCreatedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanDeletedEvent;
import org.sagebionetworks.bridge.events.SchedulePlanUpdatedEvent;
import org.sagebionetworks.bridge.events.UserCreatedEvent;
import org.sagebionetworks.bridge.events.UserDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * Note that this is synchronous: these changes occur as part of the requests to create 
 * schedule plans or manipulate users. This can be made asynchronous, but ultimately 
 * these events could be queued and a worker thread could periodically process them, 
 * this would be best I think. That would allow for error recovery (retrying queue 
 * items), right now there is none. 
 */
public class ScheduleChangeListener implements ApplicationListener<ApplicationEvent> {
    
    private static Logger logger = LoggerFactory.getLogger(ScheduleChangeListener.class);

    // scheduleDao
    // userConsentDao
    // userLockDao
    // schedulePlanLockDao

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // It's annoying, but not as annoying as created 5 listeners for each specific event type
        if (event instanceof SchedulePlanCreatedEvent) {
            schedulePlanCreated((SchedulePlanCreatedEvent)event);
        } 
        else if (event instanceof SchedulePlanUpdatedEvent) {
            schedulePlanUpdated((SchedulePlanUpdatedEvent)event);
        } 
        else if (event instanceof SchedulePlanDeletedEvent) {
            schedulePlanDeleted((SchedulePlanDeletedEvent)event);
        } 
        else if (event instanceof UserCreatedEvent) {
            userCreated((UserCreatedEvent)event);
        } 
        else if (event instanceof UserDeletedEvent) {
            userDeleted((UserDeletedEvent)event);
        }
    }

    private void schedulePlanCreated(SchedulePlanCreatedEvent event) {
        logger.info("EVENT: Schedule plan created: " + event.getSchedulePlan().getGuid());
        // Find all users, create schedules for them as a group
    }
    private void schedulePlanDeleted(SchedulePlanDeletedEvent event) {
        logger.info("EVENT: Schedule plan deleted: " + event.getSchedulePlan().getGuid());
        // Find all schedules for this plan, delete them
    }
    private void schedulePlanUpdated(SchedulePlanUpdatedEvent event) {
        logger.info("EVENT: Schedule plan updated: " + event.getSchedulePlan().getGuid());
        // Find all schedules for this plan, delete them
        // Find all users, create schedules for them as a group
        // schedulePlanDeleted(event);
        // schedulePlanCreated(event);
    }
    private void userDeleted(UserDeletedEvent event) {
        logger.info("EVENT: User deleted: " + event.getUser().getId());
        // Find all schedules for this user, delete them
    }
    private void userCreated(UserCreatedEvent event) {
        logger.info("EVENT: User created: " + event.getUser().getId());
        // Find all the plans, assemble a list of schedules for this user, save
    }

}
