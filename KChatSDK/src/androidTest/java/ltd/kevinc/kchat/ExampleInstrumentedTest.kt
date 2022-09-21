package ltd.kevinc.kchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import service.chat.C2CChatMessage

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private var userGuid: String

    init {


        runBlocking {
            userGuid = KChatSDKClient.getOrCreateUser("czf0613")
        }
    }

    @Test
    fun sendMessageAndReceive() {
        val client = KChatServiceClient()

        runBlocking {



            for (i in 1..10) {
                client.sendAnyC2CMessage(ByteString.of(*"Hello $i".encodeToByteArray()), userGuid)
                delay(1000)
            }
        }
    }
}