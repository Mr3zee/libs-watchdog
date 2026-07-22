package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.CLASS_KIND
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.NAME
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.diagnostics.warning3
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject

object WatchdogDiagnostics : KtDiagnosticsContainer() {
    /** Parameters: class kind, declaration name. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by
        warning2<KtClassOrObject, ClassKind, Name>(NAME_IDENTIFIER)

    /** Parameters: class kind, declaration name, class kind again for the member wording. */
    val EXHAUSTIVE_PUBLIC_API by
        warning3<KtClassOrObject, ClassKind, Name, ClassKind>(NAME_IDENTIFIER)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = WatchdogErrorMessages
}

private object WatchdogErrorMessages : BaseDiagnosticRendererFactory() {
    private val MEMBER_KIND =
        Renderer { classKind: ClassKind ->
            if (classKind == ClassKind.ENUM_CLASS) "an entry" else "a subtype"
        }

    override val MAP by
        KtDiagnosticFactoryToRendererMap("LibsWatchdog") { map ->
            map.put(
                WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
                "The {0} ''{1}'' can be subclassed outside the library without restriction, " +
                    "which makes it hard to evolve. Mark it with @SubclassOptInRequired to control " +
                    "external subclassing, or with @IntentionallyOpen if unrestricted subclassing " +
                    "is intended.",
                CLASS_KIND,
                NAME,
            )
            map.put(
                WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API,
                "The {0} ''{1}'' can be matched exhaustively by clients, so adding {2} later is " +
                    "a breaking change. Mark it with @IntentionallyExhaustive if this " +
                    "exhaustive shape is an intended part of the API.",
                CLASS_KIND,
                NAME,
                MEMBER_KIND,
            )
        }
}
