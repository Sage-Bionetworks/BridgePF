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

        // Issues
        // The key has to be converted at some point to use a patient code
        // Probably remove GUID and force timestamp to be unique, GUID alone not good enough
        ApplicationContext ctx = new FileSystemXmlApplicationContext("conf/stub-application-context.xml");
        HealthDataService service = (HealthDataService)ctx.getBean("healthDataService");
        
        HealthDataKey key = new HealthDataKey(1, 1, "1");
        
        BloodPressureReading reading = new BloodPressureReading();
        reading.setStartDate(new Date().getTime());
        reading.setEndDate(new Date().getTime());
        reading.setDiastolic(80);
        reading.setSystolic(200);
        
        service.appendHealthData(key, reading);
        
        System.out.println("------------------------------- getAllHealthData");
        
        List<HealthDataEntry> readings = service.getAllHealthData(key);
        for (HealthDataEntry read : readings) {
            BloodPressureReading bpr = new BloodPressureReading(read);
            System.out.println(bpr.toString());
        }

        System.out.println("------------------------------- Date getHealthDataByDateRange");

        readings = service.getHealthDataByDateRange(key, new Date(1399052716771L), new Date(1399052716771L));
        for (HealthDataEntry read : readings) {
            BloodPressureReading bpr = new BloodPressureReading(read);
            System.out.println(bpr.toString());
        }
        
        System.out.println("-------------------------------");
        System.out.println("Date range search");
        Date startDate = createADate(2014, Calendar.APRIL, 29, 1, 1, 1);
        Date endDate = new Date();
        readings = service.getHealthDataByDateRange(key, startDate, endDate);
        for (HealthDataEntry read : readings) {
            BloodPressureReading bpr = new BloodPressureReading(read);
            System.out.println(bpr.toString());
        }
        
        key = new HealthDataKey(2, 2, "2");
        Weight w = new Weight();
        w.setWeight(180);
        long date = new Date().getTime(); 
        w.setStartDate(date);
        w.setEndDate(date);
        String id = service.appendHealthData(key, w);
        
        HealthDataEntry entry = service.getHealthDataEntry(key, id);
        Weight w2 = new Weight(entry);
        System.out.println("---- AND FINALLY, RETRIEVE ----");
        System.out.println(w2.toString());
    }
    
    private static Date createADate(int year, int month, int date, int hours, int minutes, int seconds) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, date, hours, minutes, seconds);
        return cal.getTime();    
    }

}
