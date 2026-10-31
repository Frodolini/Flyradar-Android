package no.frodolini.flyradar.ais

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * Koplar til Kystverket sin opne AIS-straum og les éi linje om gongen.
 * Ingen brukar eller nøkkel trengst. Straumen dekkjer heile norskekysten.
 *
 * Koplinga blir gjenoppretta automatisk viss ho fell ut.
 */
class AisStream(
    private val host: String = "153.44.253.27",
    private val port: Int = 5631,
) {
    enum class Status { CONNECTING, CONNECTED, ERROR }

    /**
     * Køyrer til korutinen blir avbroten. [onStatus] blir kalla når statusen
     * endrar seg, og [onMessage] for kvar melding som let seg dekode.
     */
    suspend fun run(onStatus: (Status, String?) -> Unit, onMessage: (AisMessage) -> Unit) {
        withContext(Dispatchers.IO) {
            var wait = 2000L
            while (coroutineContext.isActive) {
                onStatus(Status.CONNECTING, null)
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(host, port), 10_000)
                    socket.soTimeout = 60_000     // ingen data på eit minutt = prøv på nytt
                    onStatus(Status.CONNECTED, null)
                    wait = 2000L
                    val decoder = AisDecoder()
                    socket.getInputStream().bufferedReader(Charsets.US_ASCII).use { reader ->
                        while (coroutineContext.isActive) {
                            val line = reader.readLine() ?: break
                            decoder.decode(line)?.let(onMessage)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onStatus(Status.ERROR, e.message ?: e.javaClass.simpleName)
                } finally {
                    runCatching { socket.close() }
                }
                delay(wait)
                wait = (wait * 2).coerceAtMost(60_000L)
            }
        }
    }
}
