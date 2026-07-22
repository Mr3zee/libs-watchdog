package org.jetbrains.kotlin.libs.watchdog.fir

import com.intellij.psi.PsiElement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.CLASS_KIND
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.NAME
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * A diagnostic whose severity is chosen per compilation. A diagnostic factory bakes its severity
 * in at construction, so each configurable diagnostic keeps an [error] and a [warning] factory
 * under the same diagnostic name and the checkers pick one of them at report time.
 */
class ConfigurableWatchdogDiagnostic<out F : AbstractKtDiagnosticFactory>(
    val error: F,
    val warning: F,
) {
    val name: String get() = error.name

    fun withSeverity(severity: Severity): F =
        if (severity == Severity.WARNING) warning else error
}

/** Per-compilation severity overrides keyed by diagnostic name; unlisted diagnostics are errors. */
class WatchdogDiagnosticSeverities(private val overrides: Map<String, Severity>) {
    operator fun <F : AbstractKtDiagnosticFactory> get(diagnostic: ConfigurableWatchdogDiagnostic<F>): F =
        diagnostic.withSeverity(overrides[diagnostic.name] ?: Severity.ERROR)

    companion object {
        /** Every diagnostic reported with its default severity, an error. */
        val DEFAULT = WatchdogDiagnosticSeverities(emptyMap())
    }
}

object WatchdogDiagnostics : KtDiagnosticsContainer() {
    /** Parameters: class kind, declaration name. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by configurable2<KtClassOrObject, ClassKind, Name>(NAME_IDENTIFIER)

    val SUBCLASS_OPT_IN_WITHOUT_MARKERS by configurable0<KtAnnotationEntry>()

    /** Parameters: class kind, declaration name, class kind again for the member wording. */
    val EXHAUSTIVE_PUBLIC_API by configurable3<KtClassOrObject, ClassKind, Name, ClassKind>(NAME_IDENTIFIER)

    /** Parameters: class kind, declaration name. */
    val UNDOCUMENTED_PUBLIC_API by configurable2<KtClassOrObject, ClassKind, Name>(NAME_IDENTIFIER)

    /** Every diagnostic whose severity can be configured, for CLI option validation. */
    val allDiagnostics: List<ConfigurableWatchdogDiagnostic<*>>
        field = mutableListOf()

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
            rendererA = CLASS_KIND,
            rendererB = NAME,
        )
    }

    private fun KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory0>,
        message: String,
    ) {
        put(diagnostic.error, message)
        put(diagnostic.warning, message)
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
