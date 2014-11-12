package org.sagebionetworks.bridge.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class ScheduleChangeListener implements ApplicationListener<ApplicationEvent>, BeanFactoryAware {
    
    private ExecutorService executor = Executors.newFixedThreadPool(3); //.newSingleThreadExecutor();
    private BeanFactory beanFactory;
    
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    
    public void destroy() {
        executor.shutdown();
    }
    
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        try {
            ScheduleChangeWorker worker = beanFactory.getBean("scheduleChangeWorker", ScheduleChangeWorker.class);
            worker.setApplicationEvent(event);
            executor.submit(new FutureTask<Boolean>(worker));
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
