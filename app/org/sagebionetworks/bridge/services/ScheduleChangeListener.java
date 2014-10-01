package org.sagebionetworks.bridge.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class ScheduleChangeListener implements ApplicationListener<ApplicationEvent>, BeanFactoryAware {
    
    private ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private BeanFactory beanFactory;
    
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    
    public void destroy() {
        executor.shutdown();
    }
    
    public void onTestEvent(ApplicationEvent event) throws Exception {
        ScheduleChangeWorker worker = beanFactory.getBean("scheduleChangeWorker", ScheduleChangeWorker.class);
        worker.setApplicationEvent(event);
        // Wait for response, no retries, that's all.
        worker.call();
    }
    
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // Don't lose this bit of work, try ten times to complete it, in case the lock is taken.
        // We also throw 503 when someone tries to access schedules for a study in the middle of 
        // these changes, because they are not atomic.
        ScheduleChangeWorker worker = beanFactory.getBean("scheduleChangeWorker", ScheduleChangeWorker.class);
        worker.setApplicationEvent(event);
        executor.submit(new RetryingFutureTask(executor, worker, 10));
    }
}
