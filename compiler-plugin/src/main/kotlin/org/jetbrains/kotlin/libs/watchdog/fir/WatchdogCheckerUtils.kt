package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object WatchdogClassIds {
    private val annotationsPackage = FqName("org.jetbrains.kotlin.libs.watchdog")

    val SubclassOptInRequired: ClassId = ClassId(FqName("kotlin"), Name.identifier("SubclassOptInRequired"))
    val IntentionallyOpen: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyOpen"))
    val IntentionallyExhaustive: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyExhaustive"))
    val IntentionallyUndocumented: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyUndocumented"))
    val IntentionallyFunctionTypeAlias: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyFunctionTypeAlias"))
    val InternalAnnotationMarker: ClassId = ClassId(annotationsPackage, Name.identifier("InternalAnnotationMarker"))
}

/**
 * Only declarations written in real sources and visible to library clients (public or protected)
 * are worth watching: everything else cannot be referenced from outside the library. Properties
 * created from constructor `val`/`var` parameters carry a fake source pointing at the parameter,
 * but they are still hand-written public API, so they count as real.
 *
 * `@PublishedApi` declarations are internal in sources but belong to the published binary API:
 * public inline functions expose them to clients, so they are watched like public declarations.
 *
 * Declarations marked as internal API — annotated, directly or on an enclosing declaration, with
 * an annotation whose class carries `@InternalAnnotationMarker` — offer no compatibility contract
 * despite their visibility, so they are not watched either.
 */
context(context: CheckerContext)
internal fun FirMemberDeclaration.isWatchedPublicApi(): Boolean {
    val sourceKind = source?.kind
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }

    if (visibility == Visibilities.Local || this is FirRegularClass && isLocal) {
        return false
    }

    if (!effectiveVisibility.publicApi && publishedApiEffectiveVisibility?.publicApi != true) {
        return false
    }

    return !isMarkedAsInternalApi()
}

/**
 * The marker on an enclosing declaration covers the whole subtree: an internal API class cannot
 * be used by clients, so nothing declared inside it is usable public API.
 */
context(context: CheckerContext)
private fun FirMemberDeclaration.isMarkedAsInternalApi(): Boolean =
    symbol.hasInternalApiMarker() || context.containingDeclarations.any { it.hasInternalApiMarker() }

context(context: CheckerContext)
private fun FirBasedSymbol<*>.hasInternalApiMarker(): Boolean =
    resolvedAnnotationsWithClassIds.any { annotation ->
        annotation.toAnnotationClassLikeSymbol(context.session)
            ?.hasAnnotation(WatchdogClassIds.InternalAnnotationMarker, context.session) == true
    }
