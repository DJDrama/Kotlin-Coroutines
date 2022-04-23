import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.measureTimeMillis

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
    printSeparator()

    runBlocking {
        intermediateFlowOperators()
        printSeparator()

        transformOperatorExample()
        printSeparator()

        takeOperator()
        printSeparator()

        terminalFlowOperators()
        printSeparator()

        flowsAreSequential()
        printSeparator()

        simpleCollectForFlowContext()
        printSeparator()

        // simpleCollectDefaultDispatcher() // throws Exception

        simpleCollectDefaultDispatcherUsingFlowOn()
        printSeparator()

        simpleCollectWithDelay()
        printSeparator()

        simpleCollectWithBuffer()
        printSeparator()

        simpleCollectUsingConflation()
        printSeparator()

        simpleCollectUsingCollectLatest()
        printSeparator()

        zipOperator()
        printSeparator()

        onEachOperator()
        printSeparator()

        combineOperator()
        printSeparator()

        flatMapConcat()
        printSeparator()

        flatMapMerge()
        printSeparator()

        flatMapLatest()
        printSeparator()

        tryCatchFlow()
        printSeparator()

        tryCatchFlow2()
        printSeparator()

        // transparentCatch() // throws Exception
        printSeparator()

        catchDeclaratively()
        printSeparator()

        imperativeFinallyBlock()
        printSeparator()

        declarativeHandling()
        printSeparator()

        declarativeOnCompletionWithCatch()
        printSeparator()

        // successfulCompletion() // throws Exception

        launchIn()
        printSeparator()

        // cancelInsideFlow() // throws Exception

        cancellableBusyFlow()
    }
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
// Intermediate operators are applied to an upstream flow and return a downstream flow.
suspend fun performRequest(request: Int): String {
    delay(1000)
    return "response $request"
}

// we can't emit inside map intermediate operator
suspend fun intermediateFlowOperators() = coroutineScope {
    (1..3).asFlow()
        .map { request -> performRequest(request = request) }
        .collect { response -> println(response) }
}

// Transform operator
// used to imitate simple transformations like map and filter,
// using the transform operator, we can emit arbitrary values an arbitrary number of times
suspend fun transformOperatorExample() = coroutineScope {
    (1..3).asFlow()
        .transform { request ->
            emit("Making request $request")
            emit(performRequest(request = request))
        }.collect { response -> println(response) }
}

// size-limiting operators
// take cancel the execution of the flow when the corresponding limit is reached.
fun numbers(): Flow<Int> = flow {
    try {
        emit(1)
        emit(2)
        println("This line will not execute!")
        emit(3)
    } finally {
        println("Finally in numbers")
    }
}

suspend fun takeOperator() = coroutineScope {
    /* result
        1
        2
        Finally in numbers
     */
    numbers().take(2).collect { value -> println(value) }
}

// Terminal flow operators
// suspend functions that start a collection of the flow.
// collect operator is the most basic one, but there are other terminal operators
// ex) toList, toSet, first, reduce, fold
suspend fun terminalFlowOperators() = coroutineScope {
    val sum = (1..5).asFlow().map { it * it }.reduce { a, b ->
        println("a : $a, b : $b")
        a + b
    }
    // 1+4+9+16+25 = 55
    println(sum)
}

// Flows are sequential
suspend fun flowsAreSequential() = coroutineScope {
    /* results
    Filter 1
    Filter 2
    Map 2
    Collect string 2
    Filter 3
    Filter 4
    Map 4
    Collect string 4
    Filter 5
     */
    (1..5).asFlow().filter {
        println("Filter $it")
        it % 2 == 0
    }.map {
        println("Map $it")
        "string $it"
    }.collect {
        println("Collect $it")
    }
}

// Flow context
// collection of a flow always happens in the context of the calling coroutine.
// Context Preservation: Regardless of the implementation details of a flow,
// code will run in the context specified by the author of the code.
fun simpleForFlowContext(): Flow<Int> = flow {
    println("$this Started simple flow")
    for (i in 1..3) emit(i)
}

suspend fun simpleCollectForFlowContext() = coroutineScope {
    simpleForFlowContext().collect { value ->
        println("$this Collected $value")
    }
}

// Wrong emission withContext
// flow {...} builder has to honor the context preservation property and is not allowed to emit from a different context.
fun simpleUsingDefaultDispatcher(): Flow<Int> = flow {
    withContext(Dispatchers.Default) {
        for (i in 1..3) {
            Thread.sleep(100) // pretend we are computing it in CPU-consuming way
            emit(i)
        }
    }
}

suspend fun simpleCollectDefaultDispatcher() = coroutineScope {
    // Exception in thread "main" java.lang.IllegalStateException: Flow invariant is violated:
    // 		Flow was collected in [ScopeCoroutine{Active}@2683f372, BlockingEventLoop@70bf41e],
    //		but emission happened in [DispatchedCoroutine{Active}@17ef3107, Dispatchers.Default].
    //		Please refer to 'flow' documentation or use 'flowOn' instead
    simpleUsingDefaultDispatcher().collect { value -> println(value) }
}

// flowOn operator
// Correct way to change the context of flow is using flowOn operator
fun simpleUsingDefaultDispatcherAndFlowOn(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.sleep(100) // pretend we are computing it in CPU-consuming way
        println("Emitting $i")
        emit(i)
    }
}.flowOn(Dispatchers.Default)
// flowOn operator creates another coroutine for an upstream flow when it has to change the CoroutineDispatcher in its Context

suspend fun simpleCollectDefaultDispatcherUsingFlowOn() = coroutineScope {
    /* results
    [DefaultDispatcher-worker-1 @coroutine#2] Emitting 1
    [main @coroutine#1] Collected 1
    [DefaultDispatcher-worker-1 @coroutine#2] Emitting 2
    [main @coroutine#1] Collected 2
    [DefaultDispatcher-worker-1 @coroutine#2] Emitting 3
    [main @coroutine#1] Collected 3
     */
    simpleUsingDefaultDispatcherAndFlowOn().collect {
        println("Collected $it")
    }
}

/* Buffering */
fun simpleWithDelay(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

suspend fun simpleCollectWithDelay() = coroutineScope {
    val time = measureTimeMillis {
        simpleWithDelay().collect {
            delay(300)
            println(it)
        }
    }
    /* results
        1
        2
        3
        Collected in 1218 ms
     */
    // Takes too much time
    println("Collected in $time ms")
}

// We can use buffer operator on a flow to run emitting code concurrently with collecting code,
// as opposed to running them sequentially
suspend fun simpleCollectWithBuffer() = coroutineScope {
    val time = measureTimeMillis {
        simpleWithDelay()
            .buffer() // buffer emissions, don't wait
            .collect {
                delay(300)
                println(it)
            }
    }
    /* results
        1
        2
        3
        Collected in 1009 ms
     */
    // created a processing pipeline,
    // having to only wait 100ms for the first number
    // and then spending only 300ms to process each number.
    println("Collected in $time ms")
}

// Conflation
// One way to speed up processing when both the emitter and collector are slow.
// It drops emitted values.
suspend fun simpleCollectUsingConflation() = coroutineScope {
    val time = measureTimeMillis {
        simpleWithDelay()
            .conflate() // conflate emissions, don't process each one
            .collect {
                delay(300)
                println(it)
            }
    }
    /* results
        1
        3
        Collected in 714 ms
     */
    // while the first number was still processed the second, and third were already produced,
    // so the second one was conflated and only the most recent(the third one) was delivered to the collector
    // (100) -> emit(1) -> (100) -> emit(2) -> (100) -> emit(3)
    // delay(300) --> collect(1) ------- delay(300) ---------- collect(3)
    println("Collected in $time ms")
}

// Processing the latest value
// Cancel a slow collector and restart it every time a new value is emitted.
suspend fun simpleCollectUsingCollectLatest() = coroutineScope {
    val time = measureTimeMillis {
        simpleWithDelay().collectLatest { value ->
            println("Collecting $value")
            delay(300)
            println("Done $value")
        }
    }
    /* results
        Collecting 1
        Collecting 2
        Collecting 3
        Done 3
        Collected in 613 ms
     */
    println("Collected in $time ms")
}

// Composing multiple flows
suspend fun zipOperator() = coroutineScope {
    val nums = (1..3).asFlow()
    val strs = flowOf("one", "two", "three")
    nums.zip(strs) { a, b ->
        "$a -> $b" // compose a single string
    }.collect {
        /* results
            1 -> one
            2 -> two
            3 -> three
         */
        println(it)
    } // collect and print
}

// Combine
suspend fun onEachOperator() = coroutineScope {
    val nums = (1..3).asFlow().onEach { delay(300) }
    val strs = flowOf("one", "two", "three").onEach { delay(400) }
    val startTime = System.currentTimeMillis()
    /* results
        1 -> one at 406 ms from start
        2 -> two at 808 ms from start
        3 -> three at 1209 ms from start
        albeit results that are printed every 400 ms
     */
    nums.zip(strs) { a, b -> "$a -> $b" }
        .collect { value ->
            println("$value at ${System.currentTimeMillis() - startTime} ms from start")
        }
}

suspend fun combineOperator() = coroutineScope {
    val nums = (1..3).asFlow().onEach { delay(300) }
    val strs = flowOf("one", "two", "three").onEach { delay(400) }
    val startTime = System.currentTimeMillis()

    /* results
        1 -> one at 404 ms from start
        2 -> one at 609 ms from start
        2 -> two at 807 ms from start
        3 -> two at 912 ms from start
        3 -> three at 1208 ms from start

        line is printed at each emission from either nums or strs flows
     */
    nums.combine(strs) { a, b -> "$a -> $b" }
        .collect { value ->
            println("$value at ${System.currentTimeMillis() - startTime} ms from start")
        }
}

// Flattening flows
// Concatening mode is implemented by flatMapConcat and flattenConcat operators.
// They wait for the inner flow to complete before starting to collect the next one

// flatMapConcat
suspend fun flatMapConcat() = coroutineScope {
    val startTime = System.currentTimeMillis()
    /* results
        1: First at 104 ms from start
        1: Second at 607 ms from start
        2: First at 710 ms from start
        2: Second at 1215 ms from start
        3: First at 1316 ms from start
        3: Second at 1822 ms from start
     */
    (1..3).asFlow().onEach { delay(100) }
        .flatMapConcat { requestFlow(it) }
        .collect { value ->
            println("$value at ${System.currentTimeMillis() - startTime} ms from start")
        }
}

fun requestFlow(i: Int): Flow<String> = flow {
    emit("$i: First")
    delay(500) // wait 500 ms
    emit("$i: Second")
}

// flatMapMerge
// concurrently collect all the incoming flows and merge their values into a single flow
// so that values are emitted as soon as possible.
suspend fun flatMapMerge() = coroutineScope {
    val startTime = System.currentTimeMillis() // remember the start time
    /* results
        1: First at 123 ms from start
        2: First at 228 ms from start
        3: First at 333 ms from start
        1: Second at 626 ms from start
        2: Second at 732 ms from start
        3: Second at 834 ms from start
     */
    (1..3).asFlow().onEach { delay(100) } // a number every 100 ms
        .flatMapMerge { requestFlow(it) }
        .collect { value -> // collect and print
            println("$value at ${System.currentTimeMillis() - startTime} ms from start")
        }
}

// flatMapLatest
suspend fun flatMapLatest() = coroutineScope {
    val startTime = System.currentTimeMillis() // remember the start time
    /* results
        1: First at 106 ms from start
        2: First at 207 ms from start
        3: First at 308 ms from start
        3: Second at 810 ms from start
     */
    (1..3).asFlow().onEach { delay(100) } // a number every 100 ms
        .flatMapLatest { requestFlow(it) } // cancels all the code in this block on a new value, not-suspending, cannot be cancelled
        .collect { value -> // collect and print
            println("$value at ${System.currentTimeMillis() - startTime} ms from start")
        }
}

/* Flow Exceptions */
// Flow collection can complete with an exception when an emitter or code inside the operators throw an exceptiono

// Collector try and catch
suspend fun tryCatchFlow() = coroutineScope {
    /* results
        Flow Started
        1
        2
        Caught java.lang.IllegalStateException: Collected 2
     */
    try {
        simpleFlow().collect { value ->
            println(value)
            check(value <= 1) {
                "Collected $value"
            }
        }
    } catch (e: Throwable) {
        println("Caught $e")
    }
}

// Everything is caught
fun simpleWithMap(): Flow<String> = flow {
    for (i in 1..3) {
        println("Emitting $i")
        emit(i)
    }
}.map { value ->
    check(value <= 1) { "Crashed on $value" }
    "string $value"
}

suspend fun tryCatchFlow2() = coroutineScope {
    /* results
        Emitting 1
        string 1
        Emitting 2
        Caught java.lang.IllegalStateException: Crashed on 2
     */
    try {
        simpleWithMap().collect { value ->
            println(value)
        }
    } catch (e: Throwable) {
        println("Caught $e")
    }
}

/* Exception Transparency */
// Flows must be transparent to exceptions and it is a violation of the exception transparency to emit values in the flow
// builder from inside of try/catch block.
// This guarantees that a collector throwing an exception can always catch it using try/catch.
suspend fun transparentCatch() = coroutineScope {
    /* results
        Flow Started
        1
        Exception in thread "main" java.lang.IllegalStateException: Collected 2
     */
    simpleFlow().catch { e -> println("Caught $e") }
        .collect {
            check(it <= 1) { "Collected $it" }
            println(it)
        }
}

// Catching declaratively
suspend fun catchDeclaratively() = coroutineScope {
    /* results
        Flow Started
        1
        Caught java.lang.IllegalStateException: Collected 2
     */
    simpleFlow().onEach { value ->
        check(value <= 1) { "Collected $value" }
        println(value)
    }.catch { e -> println("Caught $e") }
        .collect()
}

/* Flow Completion */
// Imperative finally block
// use finally bock to execute an action upon collect completion
suspend fun imperativeFinallyBlock() = coroutineScope {
    try {
        simpleFlow().collect { println(it) }
    } finally {
        println("Done")
    }
}

// Declarative
suspend fun declarativeHandling() = coroutineScope {
    simpleFlow()
        .onCompletion { println("Done") }
        .collect { println(it) }
}

// advantage of onCompletion is a nullable Throwable parameter of the lambda that can be used to determine
// whether the flow collection was completed normally or exceptionally
suspend fun declarativeOnCompletionWithCatch() = coroutineScope {
    /* results
        1
        Flow Completed Exceptionally!
        Caught Exception
     */
    simpleFlowThrowingRuntimeException()
        // onCompletion does not handle the exception.
        // the exception still flows downstream, it will be delivered to further onCompletion operators
        // and can be handled with a catch operator.
        .onCompletion { if (it != null) println("Flow Completed Exceptionally!") }
        .catch { println("Caught Exception") }
        .collect { println(it) }
}

fun simpleFlowThrowingRuntimeException() = flow {
    emit(1)
    throw RuntimeException()
}

// Successful Completion
// onCompletion sees all exceptions and receives a null exception only on successful completion of the upstream flow
// (without cancellation or failure)
suspend fun successfulCompletion() = coroutineScope {
    /* results
        Flow Started
        1
        Flow completed with java.lang.IllegalStateException: Collected 2  // did not cancel operation
        Exception in thread "main" java.lang.IllegalStateException: Collected 2
     */
    simpleFlow().onCompletion {
        println("Flow completed with $it")
    }.collect {
        check(it <= 1) { "Collected $it" }
        println(it)
    }
}

// Launching flow
// launchIn: terminal operator replacing collect.
// We can launch a collection of the flow in a separate coroutine, so that execution of further code immediately continues
fun events(): Flow<Int> = (1..3).asFlow().onEach { delay(100) }

suspend fun launchIn() = coroutineScope {
    val job = events().onEach { event -> println("Event: $event") }
        .launchIn(this) // launchIn must specify a CoroutineScope in which the coroutine to collect the flow is launched.
    job.join()
    println("Done!")
}

// Flow cancellation checks
fun foo(): Flow<Int> = flow {
    for (i in 1..5) {
        println("Emitting $i")
        emit(i)
    }
}

suspend fun cancelInsideFlow() = coroutineScope {
    /* results
        Emitting 1
        1
        Emitting 2
        2
        Emitting 3
        3
        Emitting 4
        Exception in thread "main" kotlinx.coroutines.JobCancellationException: ScopeCoroutine was cancelled; job=ScopeCoroutine{Cancelled}@6121c9d6
     */
    // We get only numbers up to 3 and a CancellationException after trying to emit number 4
    // it seems that the cancel() does not cancel foo()
    foo().collect { value ->
        if (value == 3) cancel()
        println(value)
    }
}

// making busy flow cancellable
suspend fun cancellableBusyFlow() = coroutineScope {
    /* results
        1
        2
        3
        Exception in thread "main" kotlinx.coroutines.JobCancellationException: ScopeCoroutine was cancelled; job=ScopeCoroutine{Cancelled}@e720b71
     */
    (1..5).asFlow().cancellable().collect {
        if (it == 3) cancel()
        println(it)
    }

    /* we can also use .onEach{ currentCoroutineContext().ensureActive() }
    (1..5).asFlow()
        .onEach { currentCoroutineContext().ensureActive() }
        .collect {
            if (it == 3) cancel()
            println(it)
        }*/

}