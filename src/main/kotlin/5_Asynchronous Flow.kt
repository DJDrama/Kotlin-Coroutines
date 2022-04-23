import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/* Asynchronous Flow */
// suspending function asynchronously returns a single value
// How can we return multiple asynchronously computed values?
// Use Flow

fun main(): Unit {
    representingMultipleValues()
    printSeparator()

    sequences()
    printSeparator()

    suspendFunction()
    printSeparator()

    flow()
    printSeparator()

    flowsAreCold()
    printSeparator()

    timeoutFlow()
}

/* Representing multiple values */
fun simple(): List<Int> = listOf(1, 2, 3)
fun representingMultipleValues() {
    simple().forEach {
        println(it)
    }
}

/* Sequences */
fun simpleSequence(): Sequence<Int> = sequence {
    for (i in 1..3) {
        Thread.sleep(100) // waits 100ms before printing each one
        yield(i)
    }
}

fun sequences() {
    simpleSequence().forEach {
        println(it)
    }
}

/* Suspending functions */
suspend fun simpleDelay(): List<Int> {
    delay(1000)
    return listOf(1, 2, 3)
}

fun suspendFunction() = runBlocking {
    simple().forEach {
        println(it)
    }
}

/* Flows */
fun simpleFlow(): Flow<Int> = flow { // flow builder
    println("Flow Started")
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

// This code waits 100ms before printing each number without blocking the main thread.
/* results
    I'm not blocked 1
    1
    I'm not blocked 2
    2
    I'm not blocked 3
    3
 */
fun flow() = runBlocking {
    launch {
        for (k in 1..3) {
            println("I'm not blocked $k")
            delay(100) // if we use Thread.sleep(), then main thread will be blocked!
        }
    }
    // Collect the flow
    simpleFlow().collect {
        println(it)
    }
}

/* Flows are COLD */
// code inside a flow builder does not run until the flow is collected
/* results
    Calling simpleFlow function...
    Calling collect...
    Flow Started
    1
    2
    3
    Calling collect again...
    Flow Started
    1
    2
    3
 */
fun flowsAreCold() = runBlocking {
    println("Calling simpleFlow function...")
    // simpleFlow() is not marked with suspend modifier.
    // simpleFlow() call returns quickly and does not wait for anything.
    // The flow starts every time it is collected, that is why we see "Flow Started" when we call collect again.
    val flow = simpleFlow()
    println("Calling collect...")
    flow.collect { value -> println(value) }
    println("Calling collect again...")
    flow.collect { value -> println(value) }
}

/* Flow Cancellation Basics */
/* results
    Flow Started
    1
    2히
    Done
 */
fun timeoutFlow() = runBlocking {
    withTimeoutOrNull(250) {
        simpleFlow().collect { println(it) }
    }
    println("Done")
}

/* Intermediate flow operators */
