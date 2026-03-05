package talan.pfe.rulengine.enums;

public enum NotifType {
    // Versioning
    ROLLBACK_PERFORMED,

    // Security
    APIKEY_REVOKED,

    // AI Agent notifications
    AI_ANOMALY_DETECTED,
    AI_RECOMMENDATION_AVAILABLE,
    AI_SIMULATION_COMPLETE,
    AI_DOCUMENTATION_GENERATED,
    AI_EXTERNAL_ANALYSIS_READY,

    // Alerts
    ALERT_THRESHOLD_EXCEEDED,

    // Import/Export
    CONFIG_IMPORT_SUCCESS,
    CONFIG_IMPORT_FAILED
}