import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/*
Shared Mutable State and Concurrency
- Coroutines can be executed parallelly using a multi-threaded dispatcher like the Dispatchers.Default.
 */

fun main() {
    problem()
    printSeparator()
    problemWithUseOfVolatile()
    printSeparator()
    solutionUsingAtomicInteger()
    printSeparator()
    fineGrained()
    printSeparator()
    coarseGrained()
    printSeparator()
    mutualExclusion()
    printSeparator()
    useActorForIncrementation()
}

/* Problem
* What does it print at the end? It is highly unlikely to ever print "Counter = 100000",
* because a hundred coroutines increment the counter concurrently from multiple threads
* without any synchronization.
* */
fun problem() = runBlocking {
    var counter = 0
    withContext(Dispatchers.Default) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
    /* results
    Completed 100000 actions in 14 ms
    Counter = 76883
     */
}

suspend fun massiveRun(action: suspend () -> Unit) {
    val n = 100  // number of coroutines to launch
    val k = 1000 // times an action is repeated by each coroutine
    val time = measureTimeMillis {
        coroutineScope { // scope for coroutines
            repeat(n) {
                launch {
                    repeat(k) { action() }
                }
            }
        }
    }
    println("Completed ${n * k} actions in $time ms")
}

// Volatile does not work
@Volatile
var counterVolatile = 0
fun problemWithUseOfVolatile() = runBlocking {
    withContext(Dispatchers.Default) {
        massiveRun {
            counterVolatile++
        }
    }
    println("Counter = $counterVolatile")
    /* results
    Completed 100000 actions in 31 ms
    Counter = 70904
     */
}

/* Thread-safe data structures */
fun solutionUsingAtomicInteger() = runBlocking {
    val atomicCounter = AtomicInteger()
    withContext(Dispatchers.Default) {
        massiveRun {
            atomicCounter.incrementAndGet()
        }
    }
    println("Counter = $atomicCounter")
    /* results
    Completed 100000 actions in 10 ms
    Counter = 100000
     */
}

/* Thread confinement (Fine-Grained) */
fun fineGrained() = runBlocking {
    val counterContext = newSingleThreadContext("CounterContext")
    var counter = 0
    withContext(Dispatchers.Default) {
        massiveRun {
            // confine each increment to a single-threaded context
            withContext(counterContext) {
                counter++
            }
        }
    }
    println("Counter = $counter")
    /* results
    Completed 100000 actions in 576 ms
    Counter = 100000
    // VERY SLOW! (Because fine-grained thread-confinement: Each individual increment switches from multi-threaded
    // Dispatchers.Default context to the single-threaded context using withContext(counterContext) block.
     */
}

/* Thread confinement (Coarse-Grained) */
fun coarseGrained() = runBlocking {
    val counterContext = newSingleThreadContext("CounterContext")
    var counter = 0
    // confine everything to a single-threaded context
    withContext(counterContext) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
    /* results
    Completed 100000 actions in 5 ms
    Counter = 100000
     */
}

/* Mutual Exclusion */
fun mutualExclusion() = runBlocking {
    val mutex = Mutex()
    var counter = 0
    withContext(Dispatchers.Default) {
        massiveRun {
            mutex.withLock { // fine-grained
                counter++
            }
        }
    }
    println("Counter = $counter")
    /* results
    Completed 100000 actions in 210 ms
    Counter = 100000
     */
}

/* Actors */
// actor: an entity made up of a combination of a coroutine, the state that is confined and encapsulated into this coroutine,
// and a channel to communicate with other coroutines.
// It is a coroutine builder that conveniently combines actor's mailbox channel into its scope to receive messages from
// and combines the send channel into the resulting job object, so that a single reference to the actor can be carried
// around as its handle.
sealed class CounterMsg
object IncCounter : CounterMsg() // one-way message to increment counter
class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg() // a request with reply

fun CoroutineScope.counterActor() = actor<CounterMsg> {
    var counter = 0
    for(msg in channel){
        when(msg){
            is GetCounter -> msg.response.complete(counter)
            IncCounter -> counter++
        }
    }
}
/*
Actor is more efficient than locking under load,
because in this case it always has work to do and it does not have to switch to a different context at all.
 */
fun useActorForIncrementation() = runBlocking {
    val counter = counterActor()
    withContext(Dispatchers.Default){
        massiveRun {
            counter.send(IncCounter)
        }
    }
    val response = CompletableDeferred<Int>()
    counter.send(GetCounter(response))
    println("Counter = ${response.await()}")
    counter.close()

    /* results
    Completed 100000 actions in 191 ms
    Counter = 100000
     */
}