package org.sagebionetworks.bridge;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public enum Roles {
    DEVELOPER,
    RESEARCHER,
    ADMIN,
    TEST_USERS,
    WORKER;

    public static final Set<Roles> ADMINISTRATIVE_ROLES = EnumSet.of(DEVELOPER, RESEARCHER, ADMIN, WORKER);
    
    public static final Map<Roles,List<Roles>> CAN_CREATE = new ImmutableMap.Builder<Roles, List<Roles>>()
            .put(ADMIN, Lists.newArrayList(ADMIN, RESEARCHER, DEVELOPER))
            .put(RESEARCHER, Lists.newArrayList(RESEARCHER, DEVELOPER))
            put(DEVELOPER, Lists.newArrayList(DEVELOPER)).build();
}
