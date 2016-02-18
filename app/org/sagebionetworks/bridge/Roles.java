package org.sagebionetworks.bridge;

import java.util.EnumSet;
import java.util.Set;

public enum Roles {
    DEVELOPER,
    RESEARCHER,
    ADMIN,
    TEST_USERS,
    WORKER;

    public static final Set<Roles> ADMINISTRATIVE_ROLES = EnumSet.of(DEVELOPER, RESEARCHER, ADMIN, WORKER);
}
