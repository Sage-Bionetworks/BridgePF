package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Task;

import com.google.common.collect.Lists;

public class TaskServiceTest {

    // Mostly testing validation here as this just passes through to the DAO.
    private TaskService service;
    
    private User user;
    
    private TaskDao taskDao;
    
    private DateTime endsOn;
    
    @Before
    public void before() {
        endsOn = DateTime.now().plusDays(2);
        
        service = new TaskService();
        user = mock(User.class);
        
        List<Task> tasks = Lists.newArrayList(getTask(), getTask());
        
        taskDao = mock(DynamoTaskDao.class);
        when(taskDao.getTasks(user, endsOn)).thenReturn(tasks);
        
        service.setTaskDao(taskDao);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getTasks(user, DateTime.now().minusSeconds(1));
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getTasks(user, DateTime.now().plusDays(TaskService.MAX_EXPIRES_ON_DAYS).plusSeconds(1));
    }

    @Test(expected = BadRequestException.class)
    public void rejectsListOfTasksWithNullElement() {
        List<Task> tasks = Lists.newArrayList(getTask(), null, getTask());
        
        service.updateTasks("AAA", tasks);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsListOfTasksWithTaskThatLacksGUID() {
        Task task = getTask();
        task.setGuid(null);
        List<Task> tasks = Lists.newArrayList(task);
        
        service.updateTasks("AAA", tasks);
    }
    @Test
    public void gettingTasksWorks() {
        List<Task> tasks = service.getTasks(user, endsOn);
        
        assertEquals(2, tasks.size());
        verify(taskDao).getTasks(user, endsOn);
        verifyNoMoreInteractions(taskDao);
    }
    
    @Test
    public void updateTasksWorks() {
        List<Task> tasks = Lists.newArrayList(getTask());
        
        service.updateTasks("BBB", tasks);
        verify(taskDao).updateTasks("BBB", tasks);
        verifyNoMoreInteractions(taskDao);
    }
    
    @Test
    public void deleteTasksDeletes() {
        service.deleteTasks("BBB");
        
        verify(taskDao).deleteTasks("BBB");
        verifyNoMoreInteractions(taskDao);
    }
    
    private Task getTask() {
        DynamoTask task = new DynamoTask();
        task.setGuid(BridgeUtils.generateGuid());
        return task;
    }
    
}
