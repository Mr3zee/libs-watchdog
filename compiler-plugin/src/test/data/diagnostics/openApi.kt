// RUN_PIPELINE_TILL: BACKEND

package foo.bar

import org.jetbrains.kotlin.libs.watchdog.IntentionallyOpen

@RequiresOptIn
annotation class ExperimentalFooApi

// Unprotected subclassable API: should warn.

open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedOpenClass<!>

abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedAbstractClass<!>

interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedInterface<!>

fun interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedFunInterface<!> {
    fun run()
}

open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>PublicOpenOuter<!> {
    protected open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>ProtectedNestedOpenClass<!>
}

// Subclassing is gated with @SubclassOptInRequired: no warning.

@SubclassOptInRequired(ExperimentalFooApi::class)
open class OptInProtectedClass

@SubclassOptInRequired(ExperimentalFooApi::class)
interface OptInProtectedInterface

// Deliberately open: no warning.

@IntentionallyOpen
open class DeliberatelyOpenClass

@IntentionallyOpen
interface DeliberatelyOpenInterface

// Not subclassable: no warning.

class FinalClass

object SomeObject

annotation class SomeAnnotationClass

// Not visible outside the library: no warning.

internal open class InternalOpenClass

private open class PrivateOpenClass

internal interface InternalInterface

internal class InternalContainer {
    open class NestedInsideInternal
}
