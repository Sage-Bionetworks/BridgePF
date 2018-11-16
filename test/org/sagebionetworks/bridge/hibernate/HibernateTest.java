package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HibernateTest {

    private HibernateHelper helper;
    
    @Resource(name = "accountHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.helper = hibernateHelper;
    }
    
    @Test
    public void test() {
       String query = "SELECT acct FROM HibernateAccount AS acct "+
               "JOIN HibernateAccountSubstudy AS acctSubstudy "+
               "WHERE acct.id = acctSubstudy.accountId " +
               "AND acctSubstudy.substudyId IN (:substudies)";
       
       Map<String,Object> params = ImmutableMap.of("substudies", ImmutableSet.of("orgA"));
       
       List<HibernateAccount> accountList = helper.queryGet(query, params, null, null, HibernateAccount.class);
       
       for (HibernateAccount account : accountList) {
           System.out.println(account);
       }
    }
    
}
