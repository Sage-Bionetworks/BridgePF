package org.sagebionetworks.bridge;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;

public enum Roles {
    DEVELOPER,
    RESEARCHER,
    ADMIN,
    WORKER;
    
    /**
     * This user has a role that marks the user as a user of the non-participant APIs and/or the 
     * Bridge Study Manager.
     */
    public static final Set<Roles> ADMINISTRATIVE_ROLES = EnumSet.of(DEVELOPER, RESEARCHER, ADMIN, WORKER);
    
    /**
     * To assess if an API caller can add or remove a role to/from an account, the caller must have one of
     * the roles that is mapped to the role through this map. For example, if the caller wants to add the 
     * RESEARCHER role ot an account (or remove it), the caller must be an administrator or a researcher.
     */
    public static final Map<Roles,EnumSet<Roles>> CAN_BE_EDITED_BY = new ImmutableMap.Builder<Roles, EnumSet<Roles>>()
        .put(ADMIN, EnumSet.of(ADMIN))
        .put(WORKER, EnumSet.of(ADMIN))
        .put(RESEARCHER, EnumSet.of(ADMIN, RESEARCHER))
        .put(DEVELOPER, EnumSet.of(ADMIN, RESEARCHER, DEVELOPER))
        .build();
}
