import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

fun main(): Unit = runBlocking {
    // Sequential
    somethingUseful()
    printSeparator()

    // Concurrent
    somethingUsefulAsync()
    printSeparator()

    // Concurrent Lazily
    somethingUsefulAsyncLazily()
    printSeparator()

    // Structured concurrency with async
    structuredConcurrencyWithAsync()
    printSeparator()

    // Catching Exception for Structured concurrency
    catchingExceptionForStructuredConcurrencyWithAsync()
}


suspend fun doSomethingUsefulOne(): Int {
    delay(1000L) // pretend we are doing something useful here
    return 13
}

suspend fun doSomethingUsefulTwo(): Int {
    delay(1000L) // pretend we are doing something useful here, too
    return 29
}

/* Sequential by default */
suspend fun somethingUseful() = coroutineScope {
    val time = measureTimeMillis {
        val one = doSomethingUsefulOne()
        val two = doSomethingUsefulTwo()
        println("The answer is ${one + two}")
    }
    println("Completed in $time ms")
}

/* Concurrent using Async */
// async: is just like launch.
// it starts a separate coroutine which is a light-weight thread that works concurrently with all the other coroutines.
// launch returns a 'job', while async returns a 'Deferred'
suspend fun somethingUsefulAsync() = coroutineScope {
    val time = measureTimeMillis {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}

/* Lazily started Async */
// lazy by setting start parameter to CoroutineStart.LAZY
// Only starts the coroutine when its result is required by await, or if it's Job's start function is invoked.
suspend fun somethingUsefulAsyncLazily() = coroutineScope {
    val time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
        val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
        one.start()
        two.start() // without start, it will run sequentially
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")
}

/* Structured concurrency with async */
fun structuredConcurrencyWithAsync() = runBlocking {
    val time = measureTimeMillis {
        println("The answer is ${concurrentSum()}")
    }
    println("Completed in $time ms")
}

suspend fun concurrentSum(): Int = coroutineScope {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    // one.cancel() --> if something goes wrong, all the coroutines that were launched in this scope will be canceled
    one.await() + two.await()
}

// catching exception
fun catchingExceptionForStructuredConcurrencyWithAsync() = runBlocking {
    try {
        failedConcurrentSum()
    } catch (e: ArithmeticException) {
        println("Computation failed with ArithmeticException!")
    }
}

suspend fun failedConcurrentSum(): Int = coroutineScope {
    val one = async {
        try {
            delay(Long.MAX_VALUE)
            42
        } finally {
            println("First child was cancelled!")
        }
    }
    val two = async<Int> {
        println("Second child throws an exception!")
        throw ArithmeticException()
    }
    one.await() + two.await()
}
/* results */
// Second child throws an exception
// First child was cancelled
// Computation failed with ArithmeticException