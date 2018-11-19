package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.ImmutableSet;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class HibernateTest {
    
    static final String SUMMARY_PREFIX = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, acct.firstName, "+
            "acct.lastName, acct.email, acct.phone, acct.externalId, acct.id, acct.status) FROM HibernateAccount acct";
            
    static final String FULL_PREFIX = "SELECT acct FROM HibernateAccount AS acct";
    
    static final String COUNT_PREFIX = "SELECT count(*) FROM HibernateAccount AS acct";
    
    private HibernateHelper helper;
    
    @Resource(name = "accountHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.helper = hibernateHelper;
    }
    
    @Test
    public void test() throws Exception {
       
        AccountId accountId = AccountId.forEmail("alx-sandbox-study", "alx.dark+orgAdude@sagebase.org");
        
        QueryMaker maker = makeQuery(FULL_PREFIX, "alx-sandbox-study", null, ImmutableSet.of());
        
        List<HibernateAccount> accountList = helper.queryGet(maker.getQuery(), maker.getParameters(), null, null,
                HibernateAccount.class);
        
        maker = makeQuery(COUNT_PREFIX, "alx-sandbox-study", null, ImmutableSet.of());
        
        int count = helper.queryCount(maker.getQuery(), maker.getParameters());
       
        System.out.println("Count: " + count);
        for (HibernateAccount account : accountList) {
            System.out.println(BridgeObjectMapper.get().writeValueAsString(account));
            System.out.println("");
        }
    }
    
    private static class QueryMaker {
        List<String> phrases = new ArrayList<>();
        Map<String,Object> params = new HashMap<>();
        public void append(String string) {
            phrases.add(string);
        }
        public void append(String string, String key, Object value) {
            phrases.add(string);
            params.put(key, value);
        }
        public void append(String string, String key1, Object value1, String key2, Object value2) {
            phrases.add(string);
            params.put(key1, value1);
            params.put(key2, value2);
        }
        public String getQuery() {
            return BridgeUtils.SPACE_JOINER.join(phrases);
        }
        public Map<String,Object> getParameters() {
            return params;
        }
    }
    
    private QueryMaker makeQuery(String prefix, String studyId, AccountId accountId, Set<String> callerSubstudies) {
        QueryMaker maker = new QueryMaker();
        maker.append(prefix);

        maker.append("LEFT OUTER JOIN acct.accountSubstudies AS acctSubstudy");
        maker.append("WITH acct.id = acctSubstudy.accountId");
        maker.append("WHERE acct.studyId = :studyId", "studyId", studyId);

        if (accountId != null) {
            AccountId unguarded = accountId.getUnguardedAccountId();
            if (unguarded.getEmail() != null) {
                maker.append("AND email=:email", "email", unguarded.getEmail());
            } else if (unguarded.getHealthCode() != null) {
                maker.append("AND healthCode=:healthCode","healthCode", unguarded.getHealthCode());
            } else if (unguarded.getPhone() != null) {
                maker.append("AND phone.number=:number and phone.regionCode=:regionCode",
                        "number", unguarded.getPhone().getNumber(),
                        "regionCode", unguarded.getPhone().getRegionCode());
            } else {
                maker.append("AND externalId=:externalId", "externalId", unguarded.getExternalId());
            }
            if (!callerSubstudies.isEmpty()) {
                maker.append("AND acctSubstudy.substudyId IN (:substudies)", "substudies", callerSubstudies);
            }
        }
        return maker;
    }
    
}
