import kotlinx.coroutines.*
import java.io.IOException

/* Cancelled coroutine throws CancellationException */

fun main() {
    exceptionPropagation()
    printSeparator()
    exceptionHandlerExample()
    printSeparator()
    cancellationAndExceptions()
    printSeparator()
    cancellationAndExceptions2()
    printSeparator()
    exceptionsAggregation()
    printSeparator()
    transparentCancellationExceptions()
    printSeparator()
    supervision()
    printSeparator()
    supervisionScopeExample()
    printSeparator()
    exceptionsInSupervisedCoroutines()
}

/* Exception Propagation */
// When builders(launch, actor, async, produce) are used to create a root coroutine,
// that is not a child of another coroutine, the former builders treat exceptions as uncaught exceptions
fun exceptionPropagation() = runBlocking {
    val job = GlobalScope.launch { // root coroutine with launch
        println("Throwing exception from launch")
        throw IndexOutOfBoundsException()
    }
    job.join()

    println("Joined failed job!")
    val deferred = GlobalScope.async { // roto coroutine with async
        println("Throwing exception from async")
        throw ArithmeticException()
    }
    try {
        deferred.await()
        println("Unreached!")
    } catch (e: ArithmeticException) {
        println("Caught ArithmeticException")
    }

    /* Results
        Throwing exception from launch
        Exception in thread "DefaultDispatcher-worker-1" java.lang.IndexOutOfBoundsException
        Joined failed job!
        Throwing exception from async
        Caught ArithmeticException
     */
}

/* CoroutineExceptionHandler
-> Invoked only on uncaught exceptions that were not handled in any other way.
* async builder always catches all exceptions and represents them in the resulting Deferred object,
  so its CoroutineExceptionHandler has no effect.
 */
fun exceptionHandlerExample() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    val job = GlobalScope.launch(handler) {
        println("job")
        throw AssertionError()
    }
    val deferred = GlobalScope.async(handler) {
        println("deferred")
        throw ArithmeticException()
    }
    joinAll(job, deferred)

    /* Results
        CoroutineExceptionHandler got java.lang.AssertionError
     */
}

/* Cancellation and exceptions
* Coroutines internally use CancellationException for cancellation, these exceptions are ignored by all handlers,
* so they should be used only as the source of additional debug information, which can be obtained by catch block.
* When a coroutine is canclled using Job.cancel, it terminates, but does not cancel its parent.
* */
fun cancellationAndExceptions() = runBlocking {
    val job = launch {
        val child = launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                println("Child is cancelled")
            }
        }
        yield()
        println("Cancelling Child")
        child.cancel()
        child.join()
        yield()
        println("Parent is not cancelled")
    }
    job.join()

    /* results
    Cancelling Child
    Child is cancelled
    Parent is not canclled
     */
}

fun cancellationAndExceptions2() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    val job = GlobalScope.launch(handler) {
        launch { // the first child
            try {
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    println("Children are cancelled, but exception is not handled until all children terminate")
                    delay(100)
                    println("The first child finished its non cancellable block")
                }
            }
        }
        launch { // the second child
            delay(10)
            println("Second child throws an exception")
            throw ArithmeticException()
        }
    }
    job.join() // job.join needed to wait

    /* results
    Second child throws an exception
    Children are cancelled, but exception is not handled until all children terminate
    The first child finished its non cancellable block
    CoroutineExceptionHandler got java.lang.ArithmeticException
     */
}

/* Exceptions aggregation
* When multiple children of a coroutine fail with an exception, the general rule is "the first exception wins",
* so the first exception gets handled.
* All additional exceptions that happen after the first one are attached to the first exception as suppressed ones.
* */
fun exceptionsAggregation() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception with suppressed ${exception.suppressed.contentToString()}")
    }
    val job = GlobalScope.launch(handler) {
        launch {
            try {
                delay(Long.MAX_VALUE) // it gets cancelled when another sibling fails with IOException
            } finally {
                throw ArithmeticException() // the second exception
            }
        }
        launch {
            delay(100)
            throw IOException() // the first exception
        }
        delay(Long.MAX_VALUE)
    }
    job.join()

    /* results
        CoroutineExceptionHandler got java.io.IOException with suppressed [java.lang.ArithmeticException]
     */
}

fun transparentCancellationExceptions() = runBlocking {
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    val job = GlobalScope.launch(handler) {
        val inner = launch { // all this stack of coroutines will get cancelled
            launch {
                launch {
                    throw IOException() // the original exception
                }
            }
        }
        try {
            inner.join()
        } catch (e: CancellationException) {
            println("Rethrowing CancellationException with original cause")
            throw e // cancellation exception is rethrown, yet the original IOException gets to the handler
        }
    }
    job.join()
    /* results
    * Rethrowing CancellationException with original cause
    * CoroutineExceptionHandler got java.io.IOException
    * */
}

/* Supervision
* Cancellation is a bidirectional relationship propagating through the whole hierarchy of coroutines.
* */
fun supervision() = runBlocking {
    val supervisor = SupervisorJob()
    with(CoroutineScope(coroutineContext + supervisor)) {
        // launch the first child -- its exception is ignored for this example (don't do this in practice!)
        val firstChild = launch(CoroutineExceptionHandler { _, _ -> }) {
            println("The first child is failing")
            throw AssertionError("The first child is cancelled")
        }
        // launch the second child
        val secondChild = launch {
            firstChild.join()
            // Cancellation of the first child is not propagated to the second child
            println("The first child is cancelled: ${firstChild.isCancelled}, but the second one is still active")
            try {
                delay(Long.MAX_VALUE)
            } finally {
                // But cancellation of the supervisor is propagated
                println("The second child is cancelled because the supervisor was cancelled")
            }
        }
        // wait until the first child fails & completes
        firstChild.join()
        println("Cancelling the supervisor")
        supervisor.cancel()
        secondChild.join()
    }

    /* results
        The first child is failing
        The first child is cancelled: true, but the second one is still active
        Cancelling the supervisor
        The second child is cancelled because the supervisor was cancelled
    * */
}

/* Supervision scope
* We can use supervisorScope for scoped concurrency.
* It waits for all children before completion just like coroutineScope does.
* */
fun supervisionScopeExample() = runBlocking {
    try {
        supervisorScope {
            val child = launch {
                try {
                    println("The child is sleeping")
                    delay(Long.MAX_VALUE)
                } finally {
                    println("The child is cancelled")
                }
            }
            // Give our child a chance to execute and print using yield
            yield()
            println("Throwing an exception from the scope")
            throw AssertionError()
        }
    } catch (e: AssertionError) {
        println("Caught an assertion error")
    }
    /* results
        The child is sleeping
        Throwing an exception from the scope
        The child is cancelled
        Caught an assertion error
     */
}

/* Exceptions in supervised coroutines
* Every child should handle its exceptions by itself via the exception handling mechanism.
* Child's failure does not propagate to the parent.
* */
fun exceptionsInSupervisedCoroutines() = runBlocking{
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    supervisorScope {
        val child = launch(handler) {
            println("The child throws an exception")
            throw AssertionError()
        }
        println("The scope is completing")
    }
    println("The scope is completed")

    /* results
    The scope is completing
    The child throws an exception
    CoroutineExceptionHandler got java.lang.AssertionError
    The scope is completed
     */
}