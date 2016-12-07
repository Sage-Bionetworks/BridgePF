package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * This test extensively reproduces Erin's test account in Lily (1232) where there are currently duplicates, and uses that 
 * to verify de-duplication and the correct fix for any schedule that has no times in it (the time must be set to 
 * a standard time and midnight seems to work regardless of when the timestamp is).
 */
@RunWith(MockitoJUnitRunner.class)
public class ScheduledActivityServiceDuplicateTest {
    //"studyKey (S)","guid (S)","label (S)","modifiedOn (N)","strategy (S)","version (N)"
    private static final String[][] SCHEDULE_PLAN_RECORDS = new String[][] {
        new String[] {"lilly","04aaee5e-6e8e-4282-8b8a-4c041af09434","Onsite Schedule 4","1462824789953","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"once\",\"eventId\":\"activity:71c00390-19a6-4ece-a2f2-c1300daf3d63:finished\",\"activities\":[{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"d9a52be9-4c59-4c67-a0d6-236b7bf92c45\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"delay\":\"PT12H\",\"expires\":\"PT1H\",\"times\":[],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[\"onsite\"],\"noneOfGroups\":[],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","4"},
        new String[] {"lilly","406d68fb-a6b7-4cf8-bc81-5b70b8d43573","Onsite Schedule 3","1462824777119","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"once\",\"eventId\":\"activity:71c00390-19a6-4ece-a2f2-c1300daf3d63:finished\",\"activities\":[{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"6fb15e4d-4821-4932-ab12-6d2ba8713617\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"delay\":\"PT6H\",\"expires\":\"PT1H\",\"times\":[],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[\"onsite\"],\"noneOfGroups\":[],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","5"},
        new String[] {"lilly","76898415-7f3a-46e0-a43e-d7703a326c03","Onsite Schedule 1","1462824757184","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"recurring\",\"eventId\":\"enrollment\",\"activities\":[{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"},{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"interval\":\"P1D\",\"expires\":\"PT12H\",\"times\":[\"08:00:00.000\"],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[\"onsite\"],\"noneOfGroups\":[],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","7"},
        new String[] {"lilly","7745b010-0e11-407c-8503-53ef23571dae","Onsite Schedule 2","1462824767748","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"once\",\"eventId\":\"activity:71c00390-19a6-4ece-a2f2-c1300daf3d63:finished\",\"activities\":[{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"3ebaf94a-c797-4e9c-a0cf-4723bbf52102\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"delay\":\"PT2H\",\"expires\":\"PT1H\",\"times\":[],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[\"onsite\"],\"noneOfGroups\":[],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","5"},
        new String[] {"lilly","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","At-Home Schedule 4","1462824730679","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"recurring\",\"eventId\":\"enrollment\",\"activities\":[{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"interval\":\"P1D\",\"expires\":\"PT1H\",\"times\":[\"20:00:00.000\"],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[],\"noneOfGroups\":[\"onsite\"],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","4"},
        new String[] {"lilly","a599020c-73e0-42d4-95a4-7d8860fbffd3","Initial Enrollment Activities","1462903633113","{\"type\":\"SimpleScheduleStrategy\",\"schedule\":{\"scheduleType\":\"once\",\"eventId\":\"enrollment\",\"activities\":[{\"label\":\"Background Survey\",\"labelDetail\":\"5 minutes\",\"guid\":\"bea8fd5d-7622-451f-a727-f9e37f00e1be\",\"task\":{\"identifier\":\"1-MedicationTracker-20EF8ED2-E461-4C20-9024-F43FCAAAF4C3\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"},{\"label\":\"Training Session 1\",\"labelDetail\":\"5  minutes\",\"guid\":\"6966c3d7-0949-43a8-804e-efc25d0f83e2\",\"task\":{\"identifier\":\"1-Training-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"},{\"label\":\"Training Session 2\",\"labelDetail\":\"5 minutes\",\"guid\":\"79cf1788-a087-4fa3-92e4-92e43d9699a7\",\"task\":{\"identifier\":\"1-Training-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"times\":[],\"type\":\"Schedule\"}}","4"},
        new String[] {"lilly","a5ae70d5-4273-4308-b212-c570b4e10759","At-Home Schedule 3","1462824719866","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"recurring\",\"eventId\":\"enrollment\",\"activities\":[{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"interval\":\"P1D\",\"expires\":\"PT1H\",\"times\":[\"14:00:00.000\"],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[],\"noneOfGroups\":[\"onsite\"],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","6"},
        new String[] {"lilly","d0e806db-390f-464b-9f9f-fbe0c90382e5","At-Home Schedule 1","1462824691723","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"recurring\",\"eventId\":\"enrollment\",\"activities\":[{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"interval\":\"P1D\",\"expires\":\"PT1H\",\"times\":[\"08:00:00.000\"],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[],\"noneOfGroups\":[\"onsite\"],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","14"},
        new String[] {"lilly","f35e34c0-8713-42bf-aab1-086ffbe6fce5","At-Home Schedule 2","1462824708443","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"recurring\",\"eventId\":\"enrollment\",\"activities\":[{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":false,\"interval\":\"P1D\",\"expires\":\"PT1H\",\"times\":[\"10:00:00.000\"],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[],\"noneOfGroups\":[\"onsite\"],\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","5"},
        new String[] {"lilly","39badeaa-8204-4475-996d-b8d829c9b5cd","Test Persistent Schedule","1478721380678","{\"type\":\"CriteriaScheduleStrategy\",\"scheduleCriteria\":[{\"schedule\":{\"scheduleType\":\"persistent\",\"activities\":[{\"label\":\"Do Persistent Activity\",\"guid\":\"21e97935-6d64-4cd5-ae70-653caad7b2f9\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}],\"persistent\":true,\"times\":[],\"type\":\"Schedule\"},\"criteria\":{\"allOfGroups\":[],\"noneOfGroups\":[],\"minAppVersions\":{},\"maxAppVersions\":{},\"type\":\"Criteria\"},\"type\":\"ScheduleCriteria\"}]}","1"}
    };
    
    // "healthCode (S)","guid (S)","data (S)","localScheduledOn (S)","persistent (N)","schedulePlanGuid (S)",
    // "localExpiresOn (S)","finishedOn (N)","startedOn (N)"
    private static final String[][] PERSISTED_ACTIVITIES = new String[][] {
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-08-21T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-21T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-08-21T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-08-22T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-22T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-08-22T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-08-23T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-23T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-08-23T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-08-24T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-24T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-08-24T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-09-07T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-07T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-09-07T15:00:00.000","1473283964607","1473283778183"},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-09-08T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-09-08T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-09-09T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-09T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-09-09T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-09-10T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-10T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-09-10T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","278f4d75-adc1-4003-8a3c-f8e9e196b621:2016-09-11T14:00:00.000","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"278f4d75-adc1-4003-8a3c-f8e9e196b621\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-11T14:00:00.000","0","a5ae70d5-4273-4308-b212-c570b4e10759","2016-09-11T15:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","3ebaf94a-c797-4e9c-a0cf-4723bbf52102:2016-09-07T16:34:45.378","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"3ebaf94a-c797-4e9c-a0cf-4723bbf52102\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-07T16:34:45.378","0","7745b010-0e11-407c-8503-53ef23571dae","2016-09-07T17:34:45.378",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-07T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-07T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-07T20:00:00.000","1473284076544","1473284023415"},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-08T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-08T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-09T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-09T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-09T20:00:00.000","1473453198249","1473448817025"},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-10T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-10T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-10T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-11T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-11T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-11T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-12T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-12T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-12T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-09-13T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-13T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-13T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-11-07T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-07T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-07T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-11-08T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-08T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-08T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-11-09T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-09T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-09T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-11-10T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-10T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-10T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","56092328-955b-4289-a59f-415b192602d2:2016-11-11T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"56092328-955b-4289-a59f-415b192602d2\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-11T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-11T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","6966c3d7-0949-43a8-804e-efc25d0f83e2:2016-06-30T11:43:07.951","{\"activity\":{\"label\":\"Training Session 1\",\"labelDetail\":\"5  minutes\",\"guid\":\"6966c3d7-0949-43a8-804e-efc25d0f83e2\",\"task\":{\"identifier\":\"1-Training-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-06-30T11:43:07.951","0","a599020c-73e0-42d4-95a4-7d8860fbffd3",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","6966c3d7-0949-43a8-804e-efc25d0f83e2:2016-06-30T12:43:07.951","{\"activity\":{\"label\":\"Training Session 1\",\"labelDetail\":\"5  minutes\",\"guid\":\"6966c3d7-0949-43a8-804e-efc25d0f83e2\",\"task\":{\"identifier\":\"1-Training-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-06-30T12:43:07.951","0","a599020c-73e0-42d4-95a4-7d8860fbffd3",null,"1471742065356"},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","6fb15e4d-4821-4932-ab12-6d2ba8713617:2016-09-07T20:34:45.378","{\"activity\":{\"label\":\"Activity Session 3\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"6fb15e4d-4821-4932-ab12-6d2ba8713617\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-07T20:34:45.378","0","406d68fb-a6b7-4cf8-bc81-5b70b8d43573","2016-09-07T21:34:45.378",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-07T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-07T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-07T20:00:00.000","1473284085378","1473284082194"},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-08T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-08T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-09T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-09T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-09T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-10T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-10T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-10T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-11T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-11T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-11T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-12T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-12T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-12T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-09-13T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-13T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-09-13T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-11-07T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-07T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-07T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-11-08T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-08T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-08T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-11-09T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-09T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-09T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-11-10T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-10T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-10T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","71c00390-19a6-4ece-a2f2-c1300daf3d63:2016-11-11T08:00:00.000","{\"activity\":{\"label\":\"Study Drug Tracking\",\"labelDetail\":\"To be completed by site coordinator\",\"guid\":\"71c00390-19a6-4ece-a2f2-c1300daf3d63\",\"task\":{\"identifier\":\"1-StudyTracker-408C5ED4-AB61-41d3-AF37-7f44C6A16BBF\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-11-11T08:00:00.000","0","76898415-7f3a-46e0-a43e-d7703a326c03","2016-11-11T20:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","79cf1788-a087-4fa3-92e4-92e43d9699a7:2016-06-30T11:43:07.951","{\"activity\":{\"label\":\"Training Session 2\",\"labelDetail\":\"5 minutes\",\"guid\":\"79cf1788-a087-4fa3-92e4-92e43d9699a7\",\"task\":{\"identifier\":\"1-Training-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-06-30T11:43:07.951","0","a599020c-73e0-42d4-95a4-7d8860fbffd3",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","79cf1788-a087-4fa3-92e4-92e43d9699a7:2016-06-30T12:43:07.951","{\"activity\":{\"label\":\"Training Session 2\",\"labelDetail\":\"5 minutes\",\"guid\":\"79cf1788-a087-4fa3-92e4-92e43d9699a7\",\"task\":{\"identifier\":\"1-Training-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-06-30T12:43:07.951","0","a599020c-73e0-42d4-95a4-7d8860fbffd3",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-08-21T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-21T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-08-21T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-08-22T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-22T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-08-22T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-08-23T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-23T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-08-23T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-08-24T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-24T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-08-24T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-09-08T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-09-08T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-09-09T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-09T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-09-09T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-09-10T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-10T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-09-10T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","932c34d9-ca38-4ca5-bb5b-295b91b8bbed:2016-09-11T08:00:00.000","{\"activity\":{\"label\":\"Activity Session 1\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"932c34d9-ca38-4ca5-bb5b-295b91b8bbed\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-11T08:00:00.000","0","d0e806db-390f-464b-9f9f-fbe0c90382e5","2016-09-11T09:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-08-20T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-20T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-08-20T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-08-21T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-21T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-08-21T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-08-22T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-22T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-08-22T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-08-23T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-23T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-08-23T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-08-24T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-24T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-08-24T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-09-07T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-07T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-09-07T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-09-08T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-09-08T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-09-09T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-09T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-09-09T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-09-10T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-10T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-09-10T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b25c9782-5ba3-42bf-9478-e7ddd82ae350:2016-09-11T20:00:00.000","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b25c9782-5ba3-42bf-9478-e7ddd82ae350\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-11T20:00:00.000","0","85f3e00b-3df2-4c91-bda5-7cc0b705f90f","2016-09-11T21:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-08-21T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-21T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-08-21T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-08-22T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-22T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-08-22T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-08-23T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-23T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-08-23T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-08-24T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-08-24T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-08-24T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-09-08T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-09-08T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-09-09T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-09T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-09-09T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-09-10T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-10T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-09-10T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","b7f9ed2a-7378-477c-95c6-08d3731680aa:2016-09-11T10:00:00.000","{\"activity\":{\"label\":\"Activity Session 2\",\"labelDetail\":\"Do at home - 5 minutes\",\"guid\":\"b7f9ed2a-7378-477c-95c6-08d3731680aa\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-11T10:00:00.000","0","f35e34c0-8713-42bf-aab1-086ffbe6fce5","2016-09-11T11:00:00.000",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","bea8fd5d-7622-451f-a727-f9e37f00e1be:2016-06-30T11:43:07.951","{\"activity\":{\"label\":\"Background Survey\",\"labelDetail\":\"5 minutes\",\"guid\":\"bea8fd5d-7622-451f-a727-f9e37f00e1be\",\"task\":{\"identifier\":\"1-MedicationTracker-20EF8ED2-E461-4C20-9024-F43FCAAAF4C3\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-06-30T11:43:07.951","0","a599020c-73e0-42d4-95a4-7d8860fbffd3",null,null},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","bea8fd5d-7622-451f-a727-f9e37f00e1be:2016-06-30T12:43:07.951","{\"activity\":{\"label\":\"Background Survey\",\"labelDetail\":\"5 minutes\",\"guid\":\"bea8fd5d-7622-451f-a727-f9e37f00e1be\",\"task\":{\"identifier\":\"1-MedicationTracker-20EF8ED2-E461-4C20-9024-F43FCAAAF4C3\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-06-30T12:43:07.951","0","a599020c-73e0-42d4-95a4-7d8860fbffd3","1471742129501","1471742065356"},
        new String[] {"d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f","d9a52be9-4c59-4c67-a0d6-236b7bf92c45:2016-09-08T02:34:45.378","{\"activity\":{\"label\":\"Activity Session 4\",\"labelDetail\":\"Do in clinic - 5 minutes\",\"guid\":\"d9a52be9-4c59-4c67-a0d6-236b7bf92c45\",\"task\":{\"identifier\":\"1-Combo-295f81EF-13CB-4DB4-8223-10A173AA0780\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"type\":\"Activity\"}}","2016-09-08T02:34:45.378","0","04aaee5e-6e8e-4282-8b8a-4c041af09434","2016-09-08T03:34:45.378",null,null}
    };
    private static final String HEALTH_CODE = "d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f";
    private static final DateTime ENROLLMENT = DateTime.parse("2016-06-30T19:43:07.951Z");
    private static final DateTime ENROLLMENT_AFTER_DAY_ROLLS = DateTime.parse("2016-07-01T03:13:07.951Z");
    private static final DateTime ACTIVITIES_LAST_RETRIEVED_ON = DateTime.parse("2016-11-07T18:32:31.000Z");
    private static final DateTimeZone PST = DateTimeZone.forOffsetHours(-8);
    private static final DateTimeZone MSK = DateTimeZone.forOffsetHours(4);
    
    @Mock
    ScheduledActivityDao activityDao;
    
    @Mock
    ActivityEventService activityEventService;
    
    @Mock
    SchedulePlanService schedulePlanService;
    
    ScheduledActivityService service;
    
    ScheduleContext.Builder contextBuilder;
    
    @Before
    public void before() throws Exception {
        // This is the exact time that Erin contacted the server
        DateTimeUtils.setCurrentMillisFixed(ACTIVITIES_LAST_RETRIEVED_ON.getMillis());
        
        service = new ScheduledActivityService();
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        service.setSchedulePlanService(schedulePlanService);
        
        contextBuilder = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("Lilly/25 (iPhone Simulator; iPhone OS/9.3) BridgeSDK/12"))
                .withNow(ACTIVITIES_LAST_RETRIEVED_ON)
                .withStudyIdentifier("test-study")
                .withEndsOn(DateTime.now().plusDays(4))
                .withHealthCode("d8bc3e0e-51b6-4ead-9b82-33a8fde88c6f")
                .withUserId("6m7Yj31Pp41yjvoyU5y6RE");
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    // "healthCode (S)","guid (S)","data (S)","localScheduledOn (S)","persistent (N)",
    // "schedulePlanGuid (S)","localExpiresOn (S)","finishedOn (N)","startedOn (N)"
    private List<ScheduledActivity> makeResultSet(DateTimeZone zone) throws Exception {
        List<ScheduledActivity> dbActivities = Lists.newArrayList();
        for (int i=0; i < PERSISTED_ACTIVITIES.length; i++) {
            String[] record = PERSISTED_ACTIVITIES[i];
            boolean hasExpires = (record.length == 9);
            DynamoScheduledActivity activity = new DynamoScheduledActivity();
            activity.setHealthCode(record[0]);
            activity.setGuid(record[1]);
            activity.setTimeZone(zone);
            activity.setData((ObjectNode)BridgeObjectMapper.get().readTree(record[2]));
            activity.setLocalScheduledOn(LocalDateTime.parse(record[3]));
            activity.setPersistent("1".equals(record[4]));
            activity.setSchedulePlanGuid(record[5]);
            if (hasExpires) {
                if (record[6] != null) {
                    activity.setLocalExpiresOn(LocalDateTime.parse(record[6]));    
                }
                if (record[7] != null) {
                    activity.setFinishedOn(Long.parseLong(record[7]));
                }
                if (record[8] != null) {
                    activity.setStartedOn(Long.parseLong(record[8]));
                }
            } else {
                if (record[6] != null) {
                    activity.setFinishedOn(Long.parseLong(record[6]));
                }
                if (record[7] != null) {
                    activity.setStartedOn(Long.parseLong(record[7]));
                }
            }
            dbActivities.add(activity);
        }
        return dbActivities;
    }
    
    // "studyKey (S)","guid (S)","label (S)","modifiedOn (N)","strategy (S)","version (N)"
    private List<SchedulePlan> makeSchedulePlans() throws Exception {
        List<SchedulePlan> plans = Lists.newArrayList();
        for (int i=0; i < SCHEDULE_PLAN_RECORDS.length; i++) {
            String[] record = SCHEDULE_PLAN_RECORDS[i];
            
            DynamoSchedulePlan plan = new DynamoSchedulePlan();
            plan.setStudyKey(record[0]);
            plan.setGuid(record[1]);
            plan.setLabel(record[2]);
            plan.setModifiedOn(Long.parseLong(record[3]));
            plan.setStrategy(BridgeObjectMapper.get().readValue(record[4], ScheduleStrategy.class));
            plan.setVersion(Long.parseLong(record[5]));
            plans.add(plan);
        }
        return plans;
    }
    
    private ScheduleContext mockEventsAndContext(DateTime enrollment, DateTimeZone zone) {
        Map<String,DateTime> events = new ImmutableMap.Builder<String, DateTime>()
            .put("enrollment",ENROLLMENT.withZone(DateTimeZone.UTC))
            .put("activity:278f4d75-adc1-4003-8a3c-f8e9e196b621:finished", new DateTime(1473283964607L, DateTimeZone.UTC))
            .put("activity:56092328-955b-4289-a59f-415b192602d2:finished", new DateTime(1473453198249L, DateTimeZone.UTC))
            .put("activity:6966c3d7-0949-43a8-804e-efc25d0f83e2:finished", new DateTime(1471742129501L, DateTimeZone.UTC))
            .put("activity:71c00390-19a6-4ece-a2f2-c1300daf3d63:finished", new DateTime(1473284085378L, DateTimeZone.UTC))
            .put("activity:bea8fd5d-7622-451f-a727-f9e37f00e1be:finished", new DateTime(1471742129501L, DateTimeZone.UTC)).build();
        doReturn(events).when(activityEventService).getActivityEventMap(HEALTH_CODE);
        contextBuilder.withAccountCreatedOn(enrollment.minusDays(3));
        contextBuilder.withTimeZone(zone);
        return contextBuilder.build();
    }
    
    // BRIDGE-1589, using duplicates generated for Erin Mount's Lily account (user 1232)
    @Test
    public void testExistingDuplicationScenario() throws Exception {
        // Mock activityEventService
        ScheduleContext context = mockEventsAndContext(ENROLLMENT, PST);
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);

        // Duplicated persisted activities.
        List<ScheduledActivity> dbActivities = makeResultSet(PST);
        doReturn(dbActivities).when(activityDao).getActivities(any(), any());
        
        // Correctly scheduled one-time tasks coming from scheduler
        doReturn(makeSchedulePlans()).when(schedulePlanService).getSchedulePlans(any(),any());
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There's only one of these and they are set to midnight UTC.
        verify(activityDao).getActivities(any(), any());
        verify(schedulePlanService).getSchedulePlans(any(), any());
        
        log(activities, context);
        allWithinQueryWindow(activities, context);
        //assertEquals(16, activities.size());
        assertEquals(1, filterByGuid(activities, "bea8fd5d-7622-451f-a727-f9e37f00e1be:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "6966c3d7-0949-43a8-804e-efc25d0f83e2:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "79cf1788-a087-4fa3-92e4-92e43d9699a7:2016-06-30T19:43:07.951").size());
        // NOTE: this task has reset because the timestamp is different
        // assertNotNull(filterByLabel(activities, "Training Session 1").get(0).getStartedOn());
    }
    
    private void allWithinQueryWindow(List<ScheduledActivity> activities, ScheduleContext context) {
        for (ScheduledActivity act : activities) {
            DateTime windowStart = context.getNow();
            DateTime windowEnd = context.getEndsOn();
            
            assertTrue(act.getExpiresOn() == null || act.getExpiresOn().isAfter(windowStart));
            assertTrue(act.getScheduledOn().isBefore(windowEnd));
        }
    }
    
    @Test
    public void settingEnrollmetToNextDayUTCWorks() throws Exception {
        // Mock activityEventService
        ScheduleContext context = mockEventsAndContext(ENROLLMENT_AFTER_DAY_ROLLS, MSK);
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        
        // Duplicated persisted activities.
        List<ScheduledActivity> dbActivities = makeResultSet(MSK);
        doReturn(dbActivities).when(activityDao).getActivities(any(), any());
        
        // Correctly scheduled one-time tasks coming from scheduler
        doReturn(makeSchedulePlans()).when(schedulePlanService).getSchedulePlans(any(),any());
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There's only one of these and they are set to midnight UTC.
        verify(activityDao).getActivities(any(), any());
        verify(schedulePlanService).getSchedulePlans(any(), any());
        
        allWithinQueryWindow(activities, context);
        assertEquals(1, filterByGuid(activities, "bea8fd5d-7622-451f-a727-f9e37f00e1be:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "6966c3d7-0949-43a8-804e-efc25d0f83e2:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "79cf1788-a087-4fa3-92e4-92e43d9699a7:2016-06-30T19:43:07.951").size());
        // NOTE: this task has reset because the timestamp is different
        //assertNotNull(filterByLabel(activities, "Training Session 1").get(0).getStartedOn());
    }

    @Test
    public void withNoPersistedTasksItWorks() throws Exception {
        // Mock activityEventService
        ScheduleContext context = mockEventsAndContext(ENROLLMENT, PST);
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        
        // Correctly scheduled one-time tasks coming from scheduler
        doReturn(makeSchedulePlans()).when(schedulePlanService).getSchedulePlans(any(),any());
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There's only one of these and they are set to midnight UTC.
        verify(activityDao).getActivities(any(), any());
        verify(schedulePlanService).getSchedulePlans(any(), any());
        // This one is there...
        assertEquals(1, filterByGuid(activities, "bea8fd5d-7622-451f-a727-f9e37f00e1be:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "6966c3d7-0949-43a8-804e-efc25d0f83e2:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "79cf1788-a087-4fa3-92e4-92e43d9699a7:2016-06-30T19:43:07.951").size());
        // This one hasn't been started, obviously
        assertNull(filterByLabel(activities, "Training Session 1").get(0).getStartedOn());
    }
    
    private void log(List<ScheduledActivity> activities, ScheduleContext context) {
        System.out.println("activities: " + activities.size());
        System.out.println("context.getNow(): " + context.getNow() + ", endsOn: " + context.getEndsOn());
        for (ScheduledActivity act : activities) {
            System.out.println(act.getExpiresOn() + ": " + act.getActivity().getLabel());
        }
    }
    
    @Test
    public void withNoPersistedTasksItWorksEvenOnNextDayUTC() throws Exception {
        // Mock activityEventService
        ScheduleContext context = mockEventsAndContext(ENROLLMENT_AFTER_DAY_ROLLS, MSK);
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        
        // Correctly scheduled one-time tasks coming from scheduler
        doReturn(makeSchedulePlans()).when(schedulePlanService).getSchedulePlans(any(),any());
        
        List<ScheduledActivity> activities = service.getScheduledActivities(context);
        
        // There's only one of these and they are set to midnight UTC.
        verify(activityDao).getActivities(any(), any());
        verify(schedulePlanService).getSchedulePlans(any(), any());
        assertEquals(1, filterByGuid(activities, "bea8fd5d-7622-451f-a727-f9e37f00e1be:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "6966c3d7-0949-43a8-804e-efc25d0f83e2:2016-06-30T19:43:07.951").size());
        assertEquals(1, filterByGuid(activities, "79cf1788-a087-4fa3-92e4-92e43d9699a7:2016-06-30T19:43:07.951").size());
        
        // It's adjusted, magically it's still 6/30 I haven't figured out why yet.
        List<ScheduledActivity> list = filterByLabel(activities, "Do Persistent Activity");
        assertEquals(1, list.size());
        assertEquals("21e97935-6d64-4cd5-ae70-653caad7b2f9:2016-06-30T00:00:00.000", list.get(0).getGuid());
        // This one hasn't been started, obviously
        assertNull(filterByLabel(activities, "Training Session 1").get(0).getStartedOn());
    }
    
    private List<ScheduledActivity> filterByGuid(List<ScheduledActivity> activities, String guid) {
        return activities.stream().filter(act -> {
            return act.getGuid().startsWith(guid);
        }).collect(Collectors.toList());
    }

    private List<ScheduledActivity> filterByLabel(List<ScheduledActivity> activities, String label) {
        return activities.stream().filter(act -> {
            return act.getActivity().getLabel().equals(label);
        }).collect(Collectors.toList());
    }
       
}
