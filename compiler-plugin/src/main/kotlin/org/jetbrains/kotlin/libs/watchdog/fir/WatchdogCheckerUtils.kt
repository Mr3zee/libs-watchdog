package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.visibility
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
}

/**
 * Only declarations written in real sources and visible to library clients (public or protected)
 * are worth watching: everything else cannot be referenced from outside the library. Properties
 * created from constructor `val`/`var` parameters carry a fake source pointing at the parameter,
 * but they are still hand-written public API, so they count as real.
 */
internal fun FirMemberDeclaration.isWatchedPublicApi(): Boolean {
    val sourceKind = source?.kind
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }

    if (visibility == Visibilities.Local || this is FirRegularClass && isLocal) {
        return false
    }

    return effectiveVisibility.publicApi
}
