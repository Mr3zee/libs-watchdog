package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object WatchdogClassIds {
    private val annotationsPackage = FqName("org.jetbrains.kotlin.libs.watchdog")

    val SubclassOptInRequired: ClassId =
        ClassId(FqName("kotlin"), Name.identifier("SubclassOptInRequired"))
    val IntentionallyOpen: ClassId =
        ClassId(annotationsPackage, Name.identifier("IntentionallyOpen"))
    val IntentionallyExhaustive: ClassId =
        ClassId(annotationsPackage, Name.identifier("IntentionallyExhaustive"))
}

/**
 * Only declarations written in real sources and visible to library clients (public or protected)
 * are worth watching: everything else cannot be referenced from outside the library.
 */
internal fun FirRegularClass.isWatchedPublicApi(): Boolean {
    if (source?.kind != KtRealSourceElementKind) return false
    if (isLocal) return false
    return effectiveVisibility.publicApi
}
