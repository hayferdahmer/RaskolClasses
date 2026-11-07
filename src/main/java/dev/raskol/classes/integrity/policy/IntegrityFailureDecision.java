package dev.raskol.classes.integrity.policy;

/**
 * Action to take after startup integrity verification.
 */
public enum IntegrityFailureDecision {

    /**
     * Integrity verification permits plugin startup.
     */
    CONTINUE,

    /**
     * Integrity verification does not permit plugin startup.
     */
    DISABLE_PLUGIN
}
