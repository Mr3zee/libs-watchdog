package org.jetbrains.kotlinx.libs.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object WatchdogClassIds {
    private val annotationsPackage = FqName("org.jetbrains.kotlinx.libs.watchdog")

    val SubclassOptInRequired: ClassId = ClassId(FqName("kotlin"), Name.identifier("SubclassOptInRequired"))
    val IntentionallyOpen: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyOpen"))
    val IntentionallyExhaustive: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyExhaustive"))
    val IntentionallyUndocumented: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyUndocumented"))
    val IntentionallyFunctionTypeAlias: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyFunctionTypeAlias"))
    val IntentionallyDataClass: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyDataClass"))
    val IntentionallyWithoutToString: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWithoutToString"))
    val IntentionallyMutableCollection: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyMutableCollection"))
    val IntentionallyBooleanParameter: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyBooleanParameter"))
    val IntentionallyRequiredParameterAfterOptional: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyRequiredParameterAfterOptional"))
    val IntentionallyInconsistentParameterOrder: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyInconsistentParameterOrder"))
    val IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility: ClassId = ClassId(annotationsPackage, Name.identifier("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility"))
    val InternalAnnotationMarker: ClassId = ClassId(annotationsPackage, Name.identifier("InternalAnnotationMarker"))

    /**
     * The annotations that exempt a declaration from a check and must explain why they do.
     * [InternalAnnotationMarker] is deliberately not one of them: the marked annotation class
     * documents the internal API surface itself. Neither is
     * [IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility]: its only accepted reason is
     * baked into its name, so its description may stay empty.
     */
    val exemptionAnnotations: Set<ClassId> = setOf(
        IntentionallyOpen,
        IntentionallyExhaustive,
        IntentionallyUndocumented,
        IntentionallyFunctionTypeAlias,
        IntentionallyDataClass,
        IntentionallyWithoutToString,
        IntentionallyMutableCollection,
        IntentionallyBooleanParameter,
        IntentionallyRequiredParameterAfterOptional,
        IntentionallyInconsistentParameterOrder,
    )
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

/**
 * Symbol-based public API gate for overload siblings, which checkers only reach as symbols. The
 * declaration under check shares the containers its own [isWatchedPublicApi] gate already vets —
 * or, for an inherited sibling, subsumes them: a supertype visible in a public class's scope is
 * itself reachable by clients. So only the sibling's own state matters here. Library symbols
 * carry no real source, so dependencies never pass this gate.
 */
context(context: CheckerContext)
internal fun FirCallableSymbol<*>.isWatchedPublicApiSibling(): Boolean =
    source?.kind == KtRealSourceElementKind &&
            (effectiveVisibility.publicApi || publishedApiEffectiveVisibility?.publicApi == true) &&
            !hasInternalApiMarker()

private val reasonParameter = Name.identifier("reason")
private val descriptionParameter = Name.identifier("description")
private val otherReason = Name.identifier("OTHER")

/**
 * The reasons that explain an exemption on their own. Every other entry — including ones a
 * future annotations version may add — only categorizes the exemption and requires a
 * non-empty description next to it.
 */
private val selfSufficientReasons = setOf(
    Name.identifier("FOR_BACKWARDS_COMPATIBILITY"),
    Name.identifier("API_DESIGN"),
)

/**
 * The reason that fails to explain this exemption annotation on its own ([otherReason] when the
 * argument is absent), or null when the exemption is explained — by a self-sufficient reason or
 * by a non-blank description. Shared between [ExemptionExplanationChecker], which validates
 * exemptions on declarations, and the checkers that honor exemptions in positions declaration
 * checkers cannot see (type-use annotations).
 */
internal fun FirAnnotation.unexplainedExemptionReason(): Name? {
    val reasonArgument = findArgumentByName(reasonParameter, returnFirstWhenNotFound = false)
    val reason = if (reasonArgument == null) {
        otherReason
    } else {
        // An argument that resolves to no enum entry is already a compilation error, so it
        // is not reported again.
        reasonArgument.extractEnumValueArgumentInfo()?.enumEntryName ?: return null
    }
    if (reason in selfSufficientReasons) {
        return null
    }

    if (getStringArgument(descriptionParameter)?.isNotBlank() == true) {
        return null
    }

    return reason
}
