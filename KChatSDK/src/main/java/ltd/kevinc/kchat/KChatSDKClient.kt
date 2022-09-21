package ltd.kevinc.kchat

import android.provider.Settings
import android.util.Log
import com.squareup.wire.GrpcClient
import okhttp3.OkHttpClient
import service.chat.GrpcChattingServiceClient
import service.user.CreateUserRequest
import service.user.GrpcUserServiceClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

object KChatSDKClient {
    private lateinit var mAppId: String
    private lateinit var mAppKey: String
    internal lateinit var mUserUid: String
    internal const val deviceId = Settings.Secure.ANDROID_ID

    private val channel by lazy {
        GrpcClient.Builder()
            .baseUrl("https://chat.kevinc.ltd:8081")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .proxy(Proxy.NO_PROXY)
                    .build()
            )
            .build()
    }

    private val userClient = GrpcUserServiceClient(channel)
    internal val chatClient = GrpcChattingServiceClient(channel)

    fun registerApp(appId: String, appKey: String) {
        this.mAppId = appId
        this.mAppKey = appKey
    }

    internal fun makeHeader(): Map<String, String> = HashMap<String, String>().apply {
        put("X-AppId", mAppId)
        put("X-AppKey", mAppKey)
    }

    /**
     * 获取一个用户的Guid，用于后续的SDK调用
     * userTag是B端用户保存的一个唯一键，用于标记ChatSDK中的用户与B端客户自己的系统的联系
     * @param userTag 长度小于255，最好使用ASCII字符（非ASCII字符可能引发乱码问题）
     * @return 返回ChatSDK中的用户Guid，用于进行后续操作
     */
    suspend fun getOrCreateUser(userTag: String): String {
        val request = CreateUserRequest(userTag = userTag)

        return try {
            mUserUid = userClient.createAppUser().apply {
                requestMetadata = makeHeader()
            }.execute(request).userUid

            mUserUid
        } catch (e: Exception) {
            Log.e("KChat.UserCreate", "Failed to create a user.")
            mUserUid = ""
            throw e
        }
    }
}