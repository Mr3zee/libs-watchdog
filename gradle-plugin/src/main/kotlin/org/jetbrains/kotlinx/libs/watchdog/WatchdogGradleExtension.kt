package org.jetbrains.kotlinx.libs.watchdog

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Configures the severity of each watchdog diagnostic. Every diagnostic is reported as an error
 * unless demoted to [WatchdogSeverity.WARNING] here:
 *
 * ```kotlin
 * libsWatchdog {
 *     undocumentedPublicApi.set(WatchdogSeverity.WARNING)
 * }
 * ```
 */
public open class WatchdogGradleExtension(objectFactory: ObjectFactory) {
    /** Severity of `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`: unrestricted external subclassing. */
    public val openApiWithoutSubclassOptIn: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `SUBCLASS_OPT_IN_WITHOUT_MARKERS`: `@SubclassOptInRequired` with no markers. */
    public val subclassOptInWithoutMarkers: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `EXHAUSTIVE_PUBLIC_API`: exhaustively matchable enums and sealed hierarchies. */
    public val exhaustivePublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `UNDOCUMENTED_PUBLIC_API`: public declarations without KDoc. */
    public val undocumentedPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `FUNCTION_TYPE_ALIAS_PUBLIC_API`: type aliases that abbreviate function types. */
    public val functionTypeAliasPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DATA_CLASS_PUBLIC_API`: data classes in the public API. */
    public val dataClassPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `MUTABLE_COLLECTION_PUBLIC_API`: mutable collections and arrays in public signatures. */
    public val mutableCollectionPublicApi: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_NOOP_TARGET`: DSL marker targets without scope-control effect. */
    public val dslMarkerNoopTarget: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS`: DSL markers with the default target set. */
    public val dslMarkerWithoutExplicitTargets: Property<WatchdogSeverity> = objectFactory.severityProperty()

    /** Severity of `DSL_MARKER_NOOP_TYPE_POSITION`: DSL markers on type positions without effect. */
    public val dslMarkerNoopTypePosition: Property<WatchdogSeverity> = objectFactory.severityProperty()

    internal fun diagnosticSeverities(): Map<String, Property<WatchdogSeverity>> = mapOf(
        "OPEN_API_WITHOUT_SUBCLASS_OPT_IN" to openApiWithoutSubclassOptIn,
        "SUBCLASS_OPT_IN_WITHOUT_MARKERS" to subclassOptInWithoutMarkers,
        "EXHAUSTIVE_PUBLIC_API" to exhaustivePublicApi,
        "UNDOCUMENTED_PUBLIC_API" to undocumentedPublicApi,
        "FUNCTION_TYPE_ALIAS_PUBLIC_API" to functionTypeAliasPublicApi,
        "DATA_CLASS_PUBLIC_API" to dataClassPublicApi,
        "MUTABLE_COLLECTION_PUBLIC_API" to mutableCollectionPublicApi,
        "DSL_MARKER_NOOP_TARGET" to dslMarkerNoopTarget,
        "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS" to dslMarkerWithoutExplicitTargets,
        "DSL_MARKER_NOOP_TYPE_POSITION" to dslMarkerNoopTypePosition,
    )
}

private fun ObjectFactory.severityProperty(): Property<WatchdogSeverity> =
    property(WatchdogSeverity::class.java).convention(WatchdogSeverity.ERROR)
