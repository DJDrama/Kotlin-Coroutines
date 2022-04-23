import kotlinx.coroutines.*

/* Coroutine context and dispatchers */
// All coroutine builders like launch and async accept an optional CoroutineContext
// parameters that can be used to explicitly specify the dispatcher for the new coroutine and other context elements.
fun main(): Unit = runBlocking {
    checkThreads()
    printSeparator()

    unconfinedVsConfinedDispatcher()
    printSeparator()

    cancelLaunchedCoroutines()
    printSeparator()

    parentalResponsibilitiesExample()
    printSeparator()

    debugCoroutinesWithNames()
    printSeparator()

    // CoroutineScope
    val activity = Activity()
    activity.doSomething()
    println("Launched coroutines")
    delay(500L)
    println("Destroying activity!")
    activity.destroy()
    delay(1000)
}

/*
launch(Dispatchers.Unconfined): In thread main
launch(Dispatchers.Default): In thread DefaultDispatcher-worker-1
launch(newSingleThreadContext("MyOwnThread")): In thread MyOwnThread
launch: In thread main
*/
suspend fun checkThreads() = coroutineScope {
    // without parameters
    // inherits the context from the CoroutineScope it is being launched from.
    // it inherits the context of the main runBlocking coroutine which runs in the main thread
    launch {
        println("launch: In thread ${Thread.currentThread().name}")
    }

    // Unconfined: dispatcher that also appears to run in the main thread(But different mechanisms)
    launch(Dispatchers.Unconfined) {
        println("launch(Dispatchers.Unconfined): In thread ${Thread.currentThread().name}")
    }

    // Default: When no other dispatcher is explicitly specified in the scope
    // Uses a shared background pool of threads.
    launch(Dispatchers.Default) {
        println("launch(Dispatchers.Default): In thread ${Thread.currentThread().name}")
    }

    // newSingleThreadContext: creates a thread for the coroutine to run
    // A dedicated thread is a very expensive resource!
    // use "close" function to release when
    // 1. when no longer needed
    // 2. or stored in a top-level variable and reused throughout the application.
    launch(newSingleThreadContext("MyOwnThread")) {
        println("launch(newSingleThreadContext(\"MyOwnThread\")): In thread ${Thread.currentThread().name}")
    }
}

/* Unconfined vs confined dispatcher */
// Unconfined: coroutine dispatcher starts a coroutine in the caller thread,
// but only until the first suspension point.
// After suspension it resumes the coroutine in the thread
// that is fully determined by the suspending function that was invoked.
// Usage: Appropriate for coroutines which neither consume CPU time
// nor update any shared data(like UI) confined to a specific thread.

// On the other side, the dispatcher is inherited from the outer CoroutineScope by default.
// The default dispatcher for the runBlocking coroutine, in particular, is confined to the invoker thread,
// so inheriting it has the effect of confining execution to this thread with predictable FIFO scheduling.

/* results
Unconfined: In thread main
runBlocking: In thread main
Unconfined: After delay in thread kotlinx.coroutines.DefaultExecutor
Unconfined: After delay in thread main
 */
suspend fun unconfinedVsConfinedDispatcher() = coroutineScope {
    /*
        The unconfined dispatcher is an advanced mechanism that can be helpful
        in certain corner cases where dispatching of a coroutine for its execution later
        is not needed or produces undesirable side-effects,
        because some operation in a coroutine must be performed right away.
        The unconfined dispatcher should not be used in general code.
     */
    // the unconfined one resumes in the default executor thread that the delay function is using.
    launch(Dispatchers.Unconfined) { // not confined: will work with main thread
        println("Unconfined: In thread ${Thread.currentThread().name}")
        delay(500)
        // After suspension it resumes the coroutine in the thread that is fully determined by the
        // suspending function that was invoked.
        println("Unconfined: After delay in thread ${Thread.currentThread().name}")
    }

    launch { // context of the parent, main runBlocking coroutine
        println("runBlocking: In thread ${Thread.currentThread().name}")
        delay(1000)
        println("Unconfined: After delay in thread ${Thread.currentThread().name}")
    }
}

/* Children of a coroutine */
// when a coroutine is launched in the CoroutineScope of another coroutine,
// 1. inherits context via CoroutineScope.coroutineContext
// 2. Job of the new coroutine becomes a child of the parent coroutine's job.
// When the parent coroutine is cancelled, all its children are recursively cancelled.

/* results
    job1: I run in my own Job and execute independently!
    job2: I am a child of the request coroutine
    job1: I am not affected by cancellation of the request
    Who has survived request cancellation?
 */
suspend fun cancelLaunchedCoroutines() = coroutineScope {
    val request = launch {
        // (Parent-Child relation can be explicitly overriden in one of two ways)
        // 1. When a different scope is explicitly specified
        // when launching a coroutine (for example, GlobalScope.launch),
        // then it does not inherit a Job from the parent scope.
        // 2. When a different Job object is passed as the context for the new coroutine
        // (as shown in the example below), then it overrides the Job of the parent scope.
        launch(Job()) {
            println("job1: I run in my own Job and execute independently!")
            delay(1000)
            println("job1: I am not affected by cancellation of the request")
        }
        // and the other inherits the parent context
        launch {
            delay(100)
            println("job2: I am a child of the request coroutine")
            delay(1000)
            println("job2: I will not execute this line if my parent request is cancelled")
        }
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("Who has survived request cancellation?")
}

/* Parental responsibilities */
// A parent coroutine always waits for completion of all its children.
suspend fun parentalResponsibilitiesExample() = coroutineScope {
    val request = launch {
        repeat(3) { i ->
            launch {
                delay((i + 1) * 200L)
                println("Coroutine $i is done!")
            }
        }
        println("Request: I'm done and I don't explicitly join my children that are still active!")
    }
    request.join()
    println("Now processing of the request is complete!")
}

/* Naming coroutines for debugging */
// It is better to name coroutines explicitly for debuggin purposes when doing some background tasks
suspend fun debugCoroutinesWithNames() = coroutineScope {
    println("Started main coroutine")
    val v1 = async(CoroutineName("v1coroutine")) {
        delay(500)
        println("${this.coroutineContext[CoroutineName]}: Computing v1") // CoroutineName(v1coroutine): Computing v1
        252
    }
    val v2 = async(CoroutineName("v2coroutine")) {
        delay(1000)
        println("${this.coroutineContext[CoroutineName]}: Computing v2") // CoroutineName(v2coroutine): Computing v2
        6
    }
    println("The answer for v1 / v2 = ${v1.await() / v2.await()}")
}

/* CoroutineScope */
// We manage lifecycles of our coroutines by creating an instance of CoroutineScope tied to the lifecycle of our activity.
// Instance can be created by the CoroutineScope() or MainScope() factory functions.
// CoroutineScope(): general-purpose scope
// MainScope(): creates a scope for UI applications and uses Disptachers.Main as the default dispatcher.

// Example
class Activity {
    private val mainScope = MainScope() // ContextScope(SupervisorJob() + Dispatchers.Main)
    fun destroy() {
        mainScope.cancel()
    }

    fun doSomething() {
        repeat(10) { i ->
            mainScope.launch {
                delay((i + 1) * 200L)
                println("Coroutine $i is done!")
            }
        }
    }
}