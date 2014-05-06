package org.sagebionetworks.bridge.services;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.sagebionetworks.bridge.healthdata.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DumbTest {

    public static void main(String[] args) throws Exception {

        ApplicationContext ctx = new FileSystemXmlApplicationContext("conf/stub-application-context.xml");
        HealthDataService service = (HealthDataService)ctx.getBean("healthDataService");
        HealthDataKey key = new HealthDataKey(1, 1, "1");
        
        /*
        HealthDataKey key = new HealthDataKey(1, 1, "1");
        BloodPressureReading reading = new BloodPressureReading();
        reading.setStartDate(new Date().getTime());
        reading.setEndDate(new Date().getTime());
        reading.setDiastolic(80);
        reading.setSystolic(200);
        
        service.appendHealthData(key, reading);
        */
        
        System.out.println("------------------------------- getAllHealthData");
        List<HealthDataEntry> readings = service.getAllHealthData(key);
        System.out.println(readings.size());
        print(readings);

        System.out.println("------------------------------- Date getHealthDataByDateRange");
        readings = service.getHealthDataByDateRange(key, new Date(1399401022601L), new Date(1399401022601L));
        System.out.println(readings.size());
        print(readings);
    }
    
    private static void print(List<HealthDataEntry> entries) {
        for (HealthDataEntry read : entries) {
            BloodPressureReading bpr = new BloodPressureReading(read);
            System.out.println(bpr.toString());
        }
    }

}
