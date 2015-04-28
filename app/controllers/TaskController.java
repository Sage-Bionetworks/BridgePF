package controllers;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.services.TaskService;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;

import play.mvc.Result;

public class TaskController extends BaseController {

    private final TypeReference<ArrayList<DynamoTask>> taskTypeRef = new TypeReference<ArrayList<DynamoTask>>() {};
    
    private final BridgeObjectMapper mapper = BridgeObjectMapper.get();
    
    private TaskService taskService;
    
    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }
    
    public Result getTasks(String endsOnString) throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        DateTime endsOn = (endsOnString == null) ? 
            DateTime.now().plusDays(TaskService.DEFAULT_EXPIRES_ON_DAYS) :
            DateTime.parse(endsOnString);
        
        List<Task> tasks = taskService.getTasks(session.getUser(), endsOn);
        return okResult(tasks);
    }
    
    public Result updateTasks() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<Task> tasks = mapper.convertValue(requestToJSON(request()), taskTypeRef);
        taskService.updateTasks(session.getUser().getHealthCode(), tasks);
        
        return okResult("Tasks updated.");
    }
    
}
