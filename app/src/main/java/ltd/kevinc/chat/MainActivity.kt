package ltd.kevinc.chat

import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import ltd.kevinc.chat.databinding.ActivityMainBinding
import ltd.kevinc.kchat.KChatEventDelegate
import ltd.kevinc.kchat.KChatSDKClient
import ltd.kevinc.kchat.KChatServiceClient
import service.chat.Chat

class MainActivity : AppCompatActivity() {
    private lateinit var userGuid: String
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()

        KChatSDKClient.registerApp(
            "8cf781bd-26b5-46a8-865b-b61d1242e3fc",
            "bbfd0d16-788b-460f-a175-76288d032381",
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        )

        lifecycleScope.launch {
            userGuid = KChatSDKClient.getOrCreateUser("114514")
            val client = KChatServiceClient()

            client.listenForChatMessage(object : KChatEventDelegate {
                override fun onReceiveC2CMessage(message: Chat.C2CChatMessage) {
                    val body = message.content.toStringUtf8()
                    println("receiving: $body")
                }

                override fun channelClose(e: Throwable?) {
                    println("channel被关闭")
                }

                override fun onError(e: Throwable) {
                    e.printStackTrace()
                }
            })

//            KChatServiceClient.fetChatRecords().forEach {
//                println("${it.c2CMessage.senderUserTag}__${it.c2CMessage.receiverUserTag}")
//            }
        }
    }
}