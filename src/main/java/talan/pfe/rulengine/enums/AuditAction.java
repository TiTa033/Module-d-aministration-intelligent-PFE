package talan.pfe.rulengine.enums;
public enum AuditAction {
    // Tenant actions
    TENANT_CREATED,
    TENANT_ACTIVATED,
    TENANT_DEACTIVATED,

    // User actions
    USER_CREATED,
    USER_UPDATED,
    USER_DEACTIVATED,

    // ApiKey actions
    APIKEY_CREATED,
    APIKEY_REVOKED,

    // RuleSet actions
    RULESET_CREATED,
    RULESET_UPDATED,
    RULESET_ACTIVATED,
    RULESET_ARCHIVED,
    RULESET_DELETED,

    // Rule actions
    RULE_CREATED,
    RULE_UPDATED,
    RULE_ENABLED,
    RULE_DISABLED,
    RULE_DELETED,

    // Versioning actions
    VERSION_CREATED,
    ROLLBACK_PERFORMED,

    // Import/Export actions
    CONFIG_IMPORTED,
    CONFIG_EXPORTED,

    // AI actions
    INSIGHT_ACCEPTED,
    INSIGHT_REJECTED
}