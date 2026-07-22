package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.CLASS_KIND
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.NAME
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.diagnostics.warning3
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject

object WatchdogDiagnostics : KtDiagnosticsContainer() {
    /** Parameters: class kind, declaration name. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by warning2<KtClassOrObject, ClassKind, Name>(NAME_IDENTIFIER)

    val SUBCLASS_OPT_IN_WITHOUT_MARKERS by warning0<KtAnnotationEntry>()

    /** Parameters: class kind, declaration name, class kind again for the member wording. */
    val EXHAUSTIVE_PUBLIC_API by warning3<KtClassOrObject, ClassKind, Name, ClassKind>(NAME_IDENTIFIER)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = WatchdogErrorMessages
}

private object WatchdogErrorMessages : BaseDiagnosticRendererFactory() {
    private val MEMBER_KIND = Renderer { classKind: ClassKind ->
        if (classKind == ClassKind.ENUM_CLASS) "an entry" else "a subtype"
    }

    override val MAP by KtDiagnosticFactoryToRendererMap("LibsWatchdog") { map ->
        map.put(
            factory = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            message = "The {0} ''{1}'' can be subclassed outside the library without restriction, " +
                    "which makes it hard to evolve. Mark it with @SubclassOptInRequired to control " +
                    "external subclassing, or with @IntentionallyOpen if unrestricted subclassing " +
                    "is intended.",
            rendererA = CLASS_KIND,
            rendererB = NAME,
        )
        map.put(
            factory = WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            message = "@SubclassOptInRequired lists no marker classes, so it does not restrict " +
                    "external subclassing. Pass at least one opt-in marker class " +
                    "with a description of why the subclassing is restricted.",
        )
        map.put(
            factory = WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API,
            message = "The {0} ''{1}'' can be matched exhaustively by clients, so adding {2} later is " +
                    "a breaking change. Mark it with @IntentionallyExhaustive if this " +
                    "exhaustive shape is an intended part of the API.",
            rendererA = CLASS_KIND,
            rendererB = NAME,
            rendererC = MEMBER_KIND,
        )
    }
}
