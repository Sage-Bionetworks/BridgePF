package org.sagebionetworks.bridge.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class ScheduleChangeListener implements ApplicationListener<ApplicationEvent>, BeanFactoryAware {
    
    private ExecutorService executor = Executors.newSingleThreadExecutor(); //.newFixedThreadPool(3);
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
        ScheduleChangeWorker worker = beanFactory.getBean("scheduleChangeWorker", ScheduleChangeWorker.class);
        worker.setApplicationEvent(event);
        
        executor.submit(new FutureTask<Boolean>(worker));
    }
}
