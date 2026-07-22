package org.jetbrains.kotlin.libs.watchdog.fir

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

/**
 * Reports publicly visible classes and interfaces that have no KDoc: undocumented API forces
 * clients to guess the usage contract. Only KDoc presence is checked, not its content. Authors
 * acknowledge deliberately undocumented declarations with `@IntentionallyUndocumented`.
 */
internal class UndocumentedApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    private companion object {
        /**
         * The same plugin jar runs both in the CLI compiler and in `kotlin-compiler-embeddable`,
         * which relocates the IntelliJ platform classes to another package. Referencing
         * `KDocTokens.KDOC` directly would hard-code the `com.intellij` field type in the bytecode
         * and fail to link in one of the two worlds, so the value is resolved reflectively. It is
         * only passed through generic signatures, which erase to `java.util.Set` and link everywhere.
         */
        @Suppress("UNCHECKED_CAST")
        private val kdocElementTypes: Set<IElementType> =
            setOf(
                Class.forName("org.jetbrains.kotlin.kdoc.lexer.KDocTokens").getField("KDOC").get(null)
            ) as Set<IElementType>
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass || !declaration.isWatchedPublicApi()) {
            return
        }

        // KDoc never reaches FIR, but the source element keeps the underlying parse tree, where
        // the KDoc is a direct child of the declaration node. `getChild` traverses both source
        // representations: the light tree (CLI) and PSI (Analysis API).
        val kdoc = declaration.source?.getChild(kdocElementTypes, index = 0, depth = 1, reverse = false)
        if (kdoc != null) {
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyUndocumented, context.session)) {
            return
        }

        reporter.reportOn(
            declaration.source,
            severities[WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API],
            declaration.classKind,
            declaration.name,
        )
    }
}
