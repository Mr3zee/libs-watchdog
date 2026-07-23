// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

import org.jetbrains.kotlinx.libs.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyFunctionTypeAlias
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyUndocumented
import org.jetbrains.kotlinx.libs.watchdog.InternalAnnotationMarker

// A bare exemption defaults to reason = OTHER with an empty description, which explains
// nothing: should warn.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
public open class BareOpenExemption

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive<!>
public enum class BareExhaustiveExemption { ENTRY }

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyUndocumented<!>
public class BareUndocumentedExemption

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyFunctionTypeAlias<!>
public typealias BareAliasExemption = (Int) -> Unit

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyDataClass<!>
public data class BareDataClassExemption(val x: Int)

// @InternalAnnotationMarker needs no explanation: the marked annotation class documents the
// internal API surface itself.

@InternalAnnotationMarker
public annotation class UnexplainedMarker

// Spelling the unexplained default out changes nothing: should warn.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(reason = ExemptionReason.OTHER)<!>
public open class ExplicitOtherWithoutDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(ExemptionReason.OTHER, "")<!>
public open class PositionalEmptyDescription

// A whitespace-only description explains nothing either: should warn.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(description = "   ")<!>
public open class BlankDescription

// A specific reason explains the exemption by itself: no warning.

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class ReasonExplainedExemption

@IntentionallyOpen(ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public open class PositionalReasonExemption

// A non-empty description explains the exemption even with the OTHER reason: no warning.

@IntentionallyOpen(description = "extension point for custom transports")
public open class DescriptionExplainedExemption

@IntentionallyOpen(reason = ExemptionReason.OTHER, description = "kept open for mocking")
public open class OtherWithDescriptionExemption

// INTEROP and EXTERNAL_CONTRACT only categorize the exemption — which constraint applies is
// not obvious from the entry alone — so they still require a description.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(reason = ExemptionReason.INTEROP)<!>
public open class InteropWithoutDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive(reason = ExemptionReason.EXTERNAL_CONTRACT)<!>
public enum class ExternalContractWithoutDescription { ENTRY }

@IntentionallyOpen(reason = ExemptionReason.INTEROP, description = "Spring AOP subclasses this.")
public open class InteropWithDescription

@IntentionallyExhaustive(reason = ExemptionReason.EXTERNAL_CONTRACT, description = "Mirrors ISO 4217.")
public enum class ExternalContractWithDescription { ENTRY }

// Exemptions on properties from constructor parameters are validated once, on the property.

/** Documented holder. */
public class Holder(
    <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyUndocumented<!> public val bare: Int,
    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN) public val explained: Int,
)

// Exemptions on type parameters and type usages must explain themselves too. The type-use form
// never reaches the declaration checker and is validated where it is honored.

public fun <<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> T : MutableList<Int>> bareBound(source: T): List<Int> = source.toList()

public fun bareTypeUse(): <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()

public fun explainedTypeUse(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int> = mutableListOf()

// The check validates the annotation call itself, so it also fires on declarations that are
// not watched public API: non-public declarations and internal API subtrees.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
internal open class InternalBareExemption

@InternalAnnotationMarker
public annotation class InternalLibApi

@InternalLibApi
<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
public open class ExemptionInsideInternalApi
