package dev.raskol.classes.integrity.build;

/**
 * Defines the trust category of a RaskolClasses build.
 *
 * <p>The build type describes how the artifact was produced.
 * It does not independently establish authenticity.</p>
 */
public enum BuildType {

    /**
     * Official build produced by a trusted release pipeline.
     */
    OFFICIAL,

    /**
     * Local or development build.
     */
    DEVELOPMENT
}
