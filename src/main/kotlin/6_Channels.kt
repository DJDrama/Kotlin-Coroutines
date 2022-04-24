import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/* Channels */
// Deferred values provide a convenient way to transfer a single value between coroutines.
// Channels provide a way to transfer a stream of values
// Conceptually very similar to BlockingQueue.
// Difference is that instead of blocking put operation it has a suspending send, and instead of blocking take
// it has a suspending receive.

fun main(): Unit = runBlocking {
    channelExample()
    printSeparator()

    channelClose()
    printSeparator()

    pipeline()
    printSeparator()

    primeNumbersWithPipeline()
    printSeparator()

    fanOutExample()
    printSeparator()

    fanInExample()
    printSeparator()

    bufferedChannels()
    printSeparator()

    channelsAreFair()
    printSeparator()

    tickerChannelsExample()
}

suspend fun channelExample() = coroutineScope {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
    }
    repeat(5) {
        println(channel.receive())
    }
    println("Done!")
}

// Closing and iteration over channels
// Unlike a queue, a channel can be closed to indicate that no more elements are coming.
// On the receiver side it is convenient to use a regular for loop to receive elements from the channel
// close: is like sending a special close token to the channel.
// Iteration stops as soon as close token is received, so there is a guarantee that all previously sent elements
// before the close are received.
suspend fun channelClose() = coroutineScope {
    // producer
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
        channel.close() // we are done sending
        // channel.send(1) // ClosedSendChannelException: Channel was closed
    }

    // consumer
//    channel.consumeEach { we can also use consumeEach(an extension function which replaces for loop on the consumer side)
//        println(it)
//    }
    for (y in channel)
        println(y) // print received values using for loop(until the channel is closed)
    println("Done!")
}

// Pipelines
// pipeline: a pattern where one coroutine is producing, possibly infinite, stream of values
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true)
        send(x++)
}

// consume
fun CoroutineScope.square(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
    for (x in numbers)
        send(x * x)
}

suspend fun pipeline() = coroutineScope {
    val numbers = produceNumbers()
    val squares = square(numbers)
    repeat(5) {
        println(squares.receive())
    }
    println("Done!")
    coroutineContext.cancelChildren() // cancel children coroutines
}

// Prime numbers with pipeline
// produce <-> iterator
// send <-> yield
// receive <-> next
// ReceiveChannel <-> Iterator
// Benefit of pipeline that uses channels is that it can actually
// use multiple CPU cores if run it in Dispatchers.Default context
fun CoroutineScope.numbersFrom(start: Int) = produce<Int> {
    var x = start
    while (true) {
        println("Sent1 ${x}")
        send(x++)
    }
}

fun CoroutineScope.filter(numbers: ReceiveChannel<Int>, prime: Int) = produce {
    for (x in numbers)
        if (x % prime != 0) {
            println("Sent2 $x")
            send(x)
        }
}

suspend fun primeNumbersWithPipeline() = coroutineScope {
    var cur = numbersFrom(start = 2)
    repeat(10) {
        val prime = cur.receive()
        println("prime: $prime")
        cur = filter(cur, prime)
    }
    coroutineContext.cancelChildren()
}

/* Fan Out */
// multiple coroutines may receive from the same channel, distributing work between themselves.
fun CoroutineScope.produceNumbersFanOut() = produce<Int> {
    var x = 1
    while (true) {
        send(x++)
        delay(100)
    }
}

fun CoroutineScope.launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
    for (msg in channel)
        println("Processor #$id received $msg")
}

// Unlike consumeEach, this for loop pattern is perfectly safe to use from multiple coroutines.
// If one of the processor coroutines fails, then others would still be processing the channel, while a processor
// that is written via consumeEach always consumes (cancels) the underlying channel on its normal or abnormal completion.
suspend fun fanOutExample() = coroutineScope {
    /* results
        Processor #0 received 1
        Processor #0 received 2
        Processor #1 received 3
        Processor #2 received 4
        Processor #3 received 5
        Processor #4 received 6
        Processor #0 received 7
        Processor #1 received 8
        Processor #2 received 9
        Processor #3 received 10
     */
    val producer = produceNumbersFanOut()
    repeat(5) { launchProcessor(it, producer) }
    delay(950)
    producer.cancel()
}

/* Fan In */
// multiple coroutines may send to the same channel.
suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
        delay(time)
        channel.send(s)
    }
}

suspend fun fanInExample() = coroutineScope {
    /* results
        foo
        foo
        Bar!
        foo
        foo
        Bar!
     */
    val channel = Channel<String>()
    launch { sendString(channel, "foo", 200L) }
    launch { sendString(channel, "Bar!", 500L) }
    repeat(6) {
        println(channel.receive())
    }
    coroutineContext.cancelChildren()
}

// Buffered Channels
// Unbuffered channels transfer elements when sender and receiver meet each other (aka rendezvous).
// if send is invoked first, then it is suspended until receive is invoked,
// if receive is invoked first, it is suspended until send is invoked.
// Both Channel() factory function and produce builder take an optional capacity parameter to specify buffer size.
// Buffer allows senders to send multiple elements before suspending,
// similar to the BlockingQueue with a specified capacity, which blocks when buffer is full.
suspend fun bufferedChannels() = coroutineScope {
    val channel = Channel<Int>(4)
    val sender = launch {
        repeat(10) {
            /* results
                Sending 0
                Sending 1
                Sending 2
                Sending 3 // The first four elements are added to the buffer
                Sending 4 // and the sender suspends when trying to send the fifth one.

             */
            println("Sending $it")
            channel.send(it)
        }
    }
    delay(1000)
    sender.cancel()
}

/* Channels are fair */
// Send and receive operations to channels are fair with respect to the order of their invocation from multiple coroutines.
// They are served in first-in first-out order, e.g. the first coroutine to invoke receive gets the element.
data class Ball(var hits: Int)

suspend fun player(name: String, table: Channel<Ball>) {
    for (ball in table) {
        ball.hits++
        println("$name $ball")
        delay(300)
        table.send(ball)
    }
}

suspend fun channelsAreFair() = coroutineScope {
    /* results
        ping Ball(hits=1)
        pong Ball(hits=2)
        ping Ball(hits=3)
        pong Ball(hits=4)
     */
    val table = Channel<Ball>()
    launch { player("ping", table) }
    launch { player("pong", table) }
    table.send(Ball(0))
    delay(1000)
    coroutineContext.cancelChildren()
}

/* Ticker Channels */
// A special rendezvous channel that produces Unit every time given delay passes since
// last consumption from this channel.
// Though it may seem to be useless standalone, it is a useful building block to create complex time-based produce
// pipelines and operations that do windowing and other time-dependent processing.
// Ticker channel can be used in select to perform "on tick" action.
// To create such channel use a factory method ticker.
// To indicate that no further elements are needed use ReceiveChannel.cancel method on it.
suspend fun tickerChannelsExample() = coroutineScope {
    val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0)
    var nextElement = withTimeoutOrNull(1){
        tickerChannel.receive()
    }
    println("Initial element is available immediately: $nextElement") // no initial delay

    nextElement = withTimeoutOrNull(50) { tickerChannel.receive() } // all subsequent elements have 100ms delay
    println("Next element is not ready in 50 ms: $nextElement")

    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
    println("Next element is ready in 100 ms: $nextElement")

    // Emulate large consumption delays
    println("Consumer pauses for 150ms")
    delay(150)
    // Next element is available immediately
    nextElement = withTimeoutOrNull(1) { tickerChannel.receive() }
    println("Next element is available immediately after large consumer delay: $nextElement")
    // Note that the pause between `receive` calls is taken into account and next element arrives faster
    nextElement = withTimeoutOrNull(60) { tickerChannel.receive() }
    println("Next element is ready in 50ms after consumer pause in 150ms: $nextElement")

    tickerChannel.cancel() // indicate that no more elements are needed
}