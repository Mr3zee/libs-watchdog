package org.jetbrains.kotlinx.libs.watchdog.fir

import com.intellij.psi.PsiElement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.CLASS_KIND
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.NAME
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeAlias

/** Severity with which a configurable watchdog diagnostic is reported, or [NONE] to disable it. */
enum class WatchdogSeverity {
    ERROR,
    WARNING,
    NONE,
}

/**
 * A diagnostic whose severity is chosen per compilation. A diagnostic factory bakes its severity
 * in at construction, so each configurable diagnostic keeps an [error] and a [warning] factory
 * under the same diagnostic name and the checkers pick one of them — or none — at report time.
 */
class ConfigurableWatchdogDiagnostic<out F : AbstractKtDiagnosticFactory>(
    val error: F,
    val warning: F,
) {
    val name: String get() = error.name

    /** The factory reporting with [severity], or null when the diagnostic is disabled. */
    fun withSeverity(severity: WatchdogSeverity): F? = when (severity) {
        WatchdogSeverity.ERROR -> error
        WatchdogSeverity.WARNING -> warning
        WatchdogSeverity.NONE -> null
    }
}

/**
 * Per-compilation severity overrides keyed by diagnostic name; unlisted diagnostics are errors.
 * Returns null for diagnostics overridden to [WatchdogSeverity.NONE]: their check is disabled,
 * and [WatchdogFirCheckers] does not even register a checker all of whose diagnostics are
 * disabled.
 */
class WatchdogDiagnosticSeverities(private val overrides: Map<String, WatchdogSeverity>) {
    operator fun <F : AbstractKtDiagnosticFactory> get(diagnostic: ConfigurableWatchdogDiagnostic<F>): F? =
        diagnostic.withSeverity(overrides[diagnostic.name] ?: WatchdogSeverity.ERROR)

    fun isEnabled(diagnostic: ConfigurableWatchdogDiagnostic<*>): Boolean = this[diagnostic] != null

    companion object {
        /** Every diagnostic reported with its default severity, an error. */
        val DEFAULT = WatchdogDiagnosticSeverities(emptyMap())
    }
}

object WatchdogDiagnostics : KtDiagnosticsContainer() {
    /**
     * Every diagnostic whose severity can be configured, for CLI option validation. Must be
     * declared before the diagnostics themselves: their delegate providers register into it
     * during class initialization.
     */
    val allDiagnostics: List<ConfigurableWatchdogDiagnostic<*>>
        field = mutableListOf()

    /** Parameters: class kind, declaration name. Reported on the class or on a constructor. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by configurable2<KtDeclaration, ClassKind, Name>(NAME_IDENTIFIER)

    val SUBCLASS_OPT_IN_WITHOUT_MARKERS by configurable0<KtAnnotationEntry>()

    /** Parameters: class kind, declaration name, class kind again for the member wording. */
    val EXHAUSTIVE_PUBLIC_API by configurable3<KtClassOrObject, ClassKind, Name, ClassKind>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val UNDOCUMENTED_PUBLIC_API by configurable2<KtDeclaration, String, Name>(NAME_IDENTIFIER)

    /** Parameter: the alias name. */
    val FUNCTION_TYPE_ALIAS_PUBLIC_API by configurable1<KtTypeAlias, Name>(NAME_IDENTIFIER)

    /** Parameter: the class name. */
    val DATA_CLASS_PUBLIC_API by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /** Parameter: the class name. */
    val STATEFUL_CLASS_WITHOUT_TO_STRING by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /**
     * Parameters: declaration kind in words, declaration name, the mutable type's name. Reported
     * on the offending type reference.
     */
    val MUTABLE_COLLECTION_PUBLIC_API by configurable3<KtElement, String, Name, Name>()

    /** Parameters: the parameter name, the callable name. Reported on the parameter name. */
    val REQUIRED_PARAMETER_AFTER_OPTIONAL by configurable2<KtParameter, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: the two swapped parameter names, the callable name. */
    val INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS by configurable3<KtDeclaration, Name, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: the function name, the parameter name. Reported on the parameter name. */
    val BOOLEAN_PARAMETER_PUBLIC_API by configurable2<KtParameter, Name, Name>(NAME_IDENTIFIER)

    /**
     * Parameters: the exemption annotation name, the reason that needs a description. Reported
     * on the annotation entry. Deliberately not configurable, unlike the other diagnostics: the
     * explanation requirement is what keeps every exemption honest, so it is always an error.
     */
    val EXEMPTION_WITHOUT_EXPLANATION by error2<KtAnnotationEntry, Name, Name>()

    /** Parameters: the marker name, the no-op target name. Reported on the `@Target` argument. */
    val DSL_MARKER_NOOP_TARGET by configurable2<KtExpression, Name, String>()

    /** Parameter: the marker name. */
    val DSL_MARKER_WITHOUT_EXPLICIT_TARGETS by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /** Parameters: the marker name, the type position in words. Reported on the annotation entry. */
    val DSL_MARKER_NOOP_TYPE_POSITION by configurable2<KtAnnotationEntry, Name, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = WatchdogErrorMessages

    /** Builds the error/warning factory pair, deriving the diagnostic name from the property. */
    private fun <F : AbstractKtDiagnosticFactory> configurableDiagnostic(
        createFactory: (name: String, severity: Severity) -> F,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ConfigurableWatchdogDiagnostic<F>>> =
        PropertyDelegateProvider { _, property ->
            val diagnostic = ConfigurableWatchdogDiagnostic(
                error = createFactory(property.name, Severity.ERROR),
                warning = createFactory(property.name, Severity.WARNING),
            )
            allDiagnostics += diagnostic
            ReadOnlyProperty { _, _ -> diagnostic }
        }

    private inline fun <reified P : PsiElement> configurable0(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory0(name, severity, positioningStrategy, P::class, getRendererFactory())
    }

    private inline fun <reified P : PsiElement, A> configurable1(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory1<A>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }

    private inline fun <reified P : PsiElement, A, B> configurable2(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory2<A, B>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }

    private inline fun <reified P : PsiElement, A, B, C> configurable3(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory3<A, B, C>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }
}

private object WatchdogErrorMessages : BaseDiagnosticRendererFactory() {
    private val MEMBER_KIND = Renderer { classKind: ClassKind ->
        if (classKind == ClassKind.ENUM_CLASS) "an entry" else "a subtype"
    }

    override val MAP by KtDiagnosticFactoryToRendererMap("LibsWatchdog") { map ->
        map.put(
            diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            message = "The {0} ''{1}'' can be subclassed outside the library without restriction, " +
                    "which makes it hard to evolve. Mark it with @SubclassOptInRequired to control " +
                    "external subclassing, or with @IntentionallyOpen if unrestricted subclassing " +
                    "is intended.",
            rendererA = CLASS_KIND,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            message = "@SubclassOptInRequired lists no marker classes, so it does not restrict " +
                    "external subclassing. Pass at least one opt-in marker class " +
                    "with a description of why the subclassing is restricted.",
        )
        map.put(
            diagnostic = WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API,
            message = "The {0} ''{1}'' can be matched exhaustively by clients, so adding {2} later is " +
                    "a breaking change. Mark it with @IntentionallyExhaustive if this " +
                    "exhaustive shape is an intended part of the API.",
            rendererA = CLASS_KIND,
            rendererB = NAME,
            rendererC = MEMBER_KIND,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API,
            message = "The {0} ''{1}'' is part of the public API but has no KDoc. Document it " +
                    "so clients do not have to guess its purpose and usage contract, or mark it " +
                    "with @IntentionallyUndocumented if leaving it undocumented is intended.",
            rendererA = STRING,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API,
            message = "The type alias ''{0}'' abbreviates a function type, so clients bind to the " +
                    "bare function shape: the alias is erased from the compiled API and cannot " +
                    "evolve into a richer abstraction later. Declare a `fun interface` instead to " +
                    "keep lambda ergonomics behind a stable nominal type, or mark the alias with " +
                    "@IntentionallyFunctionTypeAlias if exposing the function type is intended.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DATA_CLASS_PUBLIC_API,
            message = "The data class ''{0}'' bakes its constructor property list into the " +
                    "compiled API through the generated `copy` and `componentN` functions and the " +
                    "constructor itself, so adding, removing, or reordering a property later is a " +
                    "breaking change. Declare a regular class and implement " +
                    "`equals`/`hashCode`/`toString` explicitly, or mark the class with " +
                    "@IntentionallyDataClass if this property list is an intended, stable part " +
                    "of the API.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING,
            message = "The class ''{0}'' holds state — at least one property with a backing " +
                    "field — but neither declares nor inherits a `toString` implementation, so " +
                    "instances render as the opaque class-name-with-hash-code default and reveal " +
                    "nothing in logs and debugger output. Override `toString` to render the " +
                    "current state, or mark the class with @IntentionallyWithoutToString if the " +
                    "opaque rendering is intended.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API,
            message = "The {0} ''{1}'' exposes the mutable collection type ''{2}''. Once mutable " +
                    "state is shared across the API boundary, it is unclear whether client-side " +
                    "and library-side mutations affect each other, and the library can no longer " +
                    "evolve its internal representation freely. Accept and return read-only " +
                    "types instead (arrays count as mutable collections too), handing out " +
                    "defensive copies where needed, or mark the declaration with " +
                    "@IntentionallyMutableCollection if sharing mutable state is intended.",
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL,
            message = "The parameter ''{0}'' of ''{1}'' is required but declared after an " +
                    "optional parameter, so it cannot be passed positionally without re-stating " +
                    "the defaults in front of it. Declare parameters from the general to the " +
                    "specific: essential inputs first, optional inputs — defaulted and vararg " +
                    "parameters — last. Move the required parameter in front of the optional " +
                    "ones, or mark the declaration with @IntentionallyRequiredParameterAfterOptional " +
                    "if this order is intended.",
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS,
            message = "The parameters ''{0}'' and ''{1}'' of ''{2}'' appear in the opposite " +
                    "order in another overload. Clients transfer their expectations between " +
                    "overloads, so an inconsistent order of same-named parameters invites " +
                    "silently swapped arguments. Keep shared parameters in the same relative " +
                    "order across overloads, or mark the declaration with " +
                    "@IntentionallyInconsistentParameterOrder if the differing order is intended.",
            rendererA = NAME,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API,
            message = "The function ''{0}'' takes the Boolean parameter ''{1}''. At the call " +
                    "site a positional `true`/`false` argument reveals nothing about its " +
                    "meaning, and clients cannot be forced to use named arguments. Introduce " +
                    "separate, descriptively named functions for each mode, or replace the " +
                    "parameter with an enum class, or mark it with " +
                    "@IntentionallyBooleanParameter if the Boolean parameter is intended.",
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
            "The @{0} exemption does not explain why it is applied: the {1} reason does not " +
                    "speak for itself, and the description is empty. Pass a self-explanatory " +
                    "reason (FOR_BACKWARDS_COMPATIBILITY, API_DESIGN), or describe the " +
                    "motivation in the description argument.",
            NAME,
            NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET,
            message = "The DSL marker ''{0}'' allows the {1} annotation target, but a DSL marker " +
                    "only takes effect on classifier declarations (CLASS, ANNOTATION_CLASS), type " +
                    "usages (TYPE), and type aliases (TYPEALIAS). Applied to a {1} element the " +
                    "marker restricts nothing and only gives a false sense of receiver scope " +
                    "control. Remove the target from @Target, or mark the marker with " +
                    "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility if the target " +
                    "must stay for compatibility with existing clients.",
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS,
            message = "The DSL marker ''{0}'' declares no explicit @Target, so it defaults to " +
                    "targets like functions and properties where a DSL marker has no effect, while " +
                    "the effective type usage (TYPE) and type alias (TYPEALIAS) targets stay " +
                    "unavailable. Declare @Target(CLASS, TYPE, TYPEALIAS) or a subset of it, or " +
                    "mark the marker with @IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility " +
                    "if the default targets must stay for compatibility with existing clients.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION,
            message = "The DSL marker ''{0}'' has no effect on this {1}: scope control only reacts " +
                    "to markers on the type of an implicit value — a receiver type, a context " +
                    "parameter type, or a function type with such implicit values. A named value " +
                    "is always accessed explicitly, so the marker restricts nothing here. Move it " +
                    "to the class or to a receiver position, or remove it.",
            rendererA = NAME,
            rendererB = STRING,
        )
    }

    private fun KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory0>,
        message: String,
    ) {
        put(diagnostic.error, message)
        put(diagnostic.warning, message)
    }

    private fun <A> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory1<A>>,
        message: String,
        rendererA: DiagnosticParameterRenderer<A>?,
    ) {
        put(diagnostic.error, message, rendererA)
        put(diagnostic.warning, message, rendererA)
    }

    private fun <A, B> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory2<A, B>>,
        message: String,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
    ) {
        put(diagnostic.error, message, rendererA, rendererB)
        put(diagnostic.warning, message, rendererA, rendererB)
    }

    private fun <A, B, C> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory3<A, B, C>>,
        message: String,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
        rendererC: DiagnosticParameterRenderer<C>?,
    ) {
        put(diagnostic.error, message, rendererA, rendererB, rendererC)
        put(diagnostic.warning, message, rendererA, rendererB, rendererC)
    }
}
