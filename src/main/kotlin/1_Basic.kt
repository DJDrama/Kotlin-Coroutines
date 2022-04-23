import kotlinx.coroutines.*

fun main() = runBlocking {
    // CoroutineScope and Concurrency
    doWorld()
    println("Done!")

    // Job
    jobTest()

    // Light Weight
    lightWeight()
}

/* CoroutineScope & Concurrency */
// Structured Concurrency: New coroutines can be only launched in a specific CoroutineScope
suspend fun doWorld() = coroutineScope {
    // launch: coroutine builder --> launches a new coroutine concurrently with the rest of code
    launch {
        delay(2000L)
        println("World 2")
    }

    launch {
        delay(1000L)
        println("World 1")
    }
    println("Hello")
}

// launch coroutine builder returns a Job object
// job: a handle to the launched coroutine and can be used to wait for its completion.
suspend fun jobTest() = coroutineScope {
    val job = launch {
        delay(1000L)
        println("World")
    }
    println("Hello")
    job.join() // wait until child coroutine completes
    println("Done")
}

/* Coroutines are light-weight */
// less resource-intensive than JVM threads
// consumes very little memory
// * If we write same program using threads, it will consume too much memory and throw OOM
suspend fun lightWeight() = coroutineScope {
    repeat(100_000){
        launch{
            delay(5000L)
            print(".")
        }
    }
}