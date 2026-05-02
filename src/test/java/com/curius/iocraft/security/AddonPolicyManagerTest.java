package com.curius.iocraft.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonPolicyManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AddonPolicyManager.initPersistence(tempDir.resolve("iocraft_addon_policies.json"));
    }

    @AfterEach
    void tearDown() {
        AddonPolicyManager.initPersistence(tempDir.resolve("iocraft_addon_policies.json"));
    }

    @Test
    void deniesTypeWhenRuleMatches() {
        AddonPolicyManager.denyType("owner-a", "addon/blocked");

        AddonPolicyManager.PolicyDecision blocked = AddonPolicyManager.canExecute("owner-a", "addon/blocked");
        AddonPolicyManager.PolicyDecision allowed = AddonPolicyManager.canExecute("owner-a", "addon/other");

        assertFalse(blocked.allowed());
        assertEquals("type_denied", blocked.reason());
        assertTrue(allowed.allowed());
    }

    @Test
    void allowListBlocksTypesOutsideAllowedSet() {
        AddonPolicyManager.allowType("owner-b", "addon/ok*");

        AddonPolicyManager.PolicyDecision allowed = AddonPolicyManager.canExecute("owner-b", "addon/ok/test");
        AddonPolicyManager.PolicyDecision blocked = AddonPolicyManager.canExecute("owner-b", "addon/nope");

        assertTrue(allowed.allowed());
        assertFalse(blocked.allowed());
        assertEquals("type_not_allowed", blocked.reason());
    }

    @Test
    void autoQuarantinesAfterErrorLimit() {
        AddonPolicyManager.setLimits("owner-c", 2, -1);

        AddonPolicyManager.recordExecution("owner-c", true, false);
        AddonPolicyManager.recordExecution("owner-c", true, false);

        AddonPolicyManager.PolicyDecision result = AddonPolicyManager.canExecute("owner-c", "addon/ping");
        assertFalse(result.allowed());
        assertEquals("owner_quarantined", result.reason());
    }
}

