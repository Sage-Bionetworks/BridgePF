package org.sagebionetworks.bridge;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;

public enum Roles {
    DEVELOPER,
    RESEARCHER,
    ADMIN,
    TEST_USERS,
    WORKER;

    public static final Set<Roles> ADMINISTRATIVE_ROLES = EnumSet.of(DEVELOPER, RESEARCHER, ADMIN, WORKER);
    
    public static final Map<Roles,EnumSet<Roles>> CAN_BE_CREATED_BY = new ImmutableMap.Builder<Roles, EnumSet<Roles>>()
        .put(ADMIN, EnumSet.of(ADMIN))
        .put(RESEARCHER, EnumSet.of(ADMIN, RESEARCHER, ADMIN))
        .put(DEVELOPER, EnumSet.of(ADMIN, RESEARCHER, DEVELOPER)).build();
}
