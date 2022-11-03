package ltd.kevinc.kchat

import android.util.Log
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import service.chat.ChattingServiceGrpcKt
import service.user.User
import service.user.UserServiceGrpcKt

object KChatSDKClient {
    private lateinit var mAppId: String
    private lateinit var mAppKey: String
    internal lateinit var mUserUid: String
    internal lateinit var mdeviceId: String
    internal lateinit var header: Metadata

    private val channel by lazy {
        OkHttpChannelBuilder.forAddress("chat.kevinc.ltd", 10000)
            .useTransportSecurity()
            .enableRetry()
            .executor(Dispatchers.IO.asExecutor())
            .build()
    }

    private val userClient by lazy {
        UserServiceGrpcKt.UserServiceCoroutineStub(channel)
    }
    internal val chatClient by lazy {
        ChattingServiceGrpcKt.ChattingServiceCoroutineStub(channel)
    }

    /**
     * 在开始使用前，需要注册App到环境中
     * @param deviceId 设备标识符，每个用户内保证唯一即可。只需保证在一个用户底下不发生重复即可，为保证隐私安全，我们强烈不建议使用硬件地址等等的信息。一般来讲，为了能过审核，我们推荐使用安卓内置的SSAID，或者一个自行储存的UUID
     */
    fun registerApp(appId: String, appKey: String, deviceId: String) {
        this.mAppId = appId
        this.mAppKey = appKey
        this.mdeviceId = deviceId

        this.header = Metadata()
        header.put(Metadata.Key.of("X-AppId", Metadata.ASCII_STRING_MARSHALLER), mAppId)
        header.put(Metadata.Key.of("X-AppKey", Metadata.ASCII_STRING_MARSHALLER), mAppKey)
    }

    /**
     * 获取一个用户的Guid，用于后续的SDK调用
     * userTag是B端用户保存的一个唯一键，用于标记ChatSDK中的用户与B端客户自己的系统的联系
     * @param userTag 长度小于255，最好使用ASCII字符（非ASCII字符可能引发乱码问题，因为Java String底层实现为UTF16，而网络传输过程中使用的是UTF8，有概率引起一些问题）
     * @return 返回ChatSDK中的用户Guid，用于进行后续操作
     */
    suspend fun getOrCreateUser(userTag: String): String {
        val request = User.CreateUserRequest.newBuilder()
            .setUserTag(userTag)
            .build()

        return try {
            mUserUid = userClient.createAppUser(request, header)
                .userUid

            mUserUid
        } catch (e: Exception) {
            Log.e("KChat.UserCreate", "Failed to create a user.")
            mUserUid = ""
            throw e
        }
    }
}