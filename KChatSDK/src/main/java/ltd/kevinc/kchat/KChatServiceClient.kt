package ltd.kevinc.kchat

import android.util.Log
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import service.chat.Chat

/**
 * 这是一个轻对象，哪里用，哪里new，不要跨线程使用
 * 其中的fetchRecord和sendAnyC2CMessage方法为了方便，改成了静态方法，反正它们无状态
 */
@Suppress("BlockingMethodInNonBlockingContext")
class KChatServiceClient {
    companion object {
        /**
         * 在app启动时，可以用这个接口进行数据的同步
         * @param startTime 同步聊天记录的开始区间，ISO8601时间戳，带时区
         * @param endTime 参照startTime即可
         * @param pageSize 分页查询的每一页大小，不要设置太大的值，手机可能处理不过来
         * @param currentPage 以0为基数的页码，超出的话后端接口不会报错，而是返回空
         * @return 返回的是一个元组，分别是list of object和totalPages。totalPages可以方便前端进行分页查询
         * 内部的object是一个联合体，它可能是以下几种类型之一：C2CChatMessage, GroupChatMessage, FriendApply等等，需要通过ChatMessageWrapperContentCase这个枚举进行判断
         */
        suspend fun fetchChatRecords(
            startTime: String = "2022-01-01T00:00:00.000+08:00",
            endTime: String = "2099-12-31T23:59:59.999+08:00",
            pageSize: Int = 10,
            currentPage: Int = 0
        ): Pair<List<Chat.ChatMessageWrapper>, Int> {
            val request = Chat.SyncChatRecordRequest.newBuilder()
                .setUserUid(KChatSDKClient.userUid)
                .setFromTime(startTime)
                .setToTime(endTime)
                .setMaxLength(pageSize)
                .setPage(currentPage)
                .build()

            return try {
                val resp = KChatSDKClient.chatClient.syncChatRecord(request, KChatSDKClient.header)
                Pair(resp.recordsList, resp.totalPages)
            } catch (e: Exception) {
                Log.e("KChat.SyncRecord", "failed to fetch data")
                e.printStackTrace()

                Pair(emptyList(), 0)
            }
        }

        /**
         * 用于发送任意的C2C消息，在这个模式下，无论两者是否为好友关系，这个消息都会发送，除非对方在黑名单中
         * @param content 消息内容为二进制格式，大小限制为10MB，非常不建议使用content承载大的对象，效率会很差，如有需求，请参见KCos项目
         * @param receiver 消息接收者的Guid，这个值由B端客户进行提供，也可以使用KChatClient.getOrCreateUser进行获取
         * @param notificationUrl 当消息发送成功后，会向此地址发送一个POST请求，用于通知B端的服务器进行动作
         * @return 返回消息的Guid，此记录唯一，前端可进行缓存
         */
        suspend fun sendAnyC2CMessage(
            content: ByteArray,
            receiver: String,
            notificationUrl: String = ""
        ): String {
            val request = Chat.C2CChatMessage.newBuilder()
                .setSenderUserUid(KChatSDKClient.userUid)
                .setSenderDeviceTag(KChatSDKClient.deviceId)
                .setReceiverUserUid(receiver)
                .setNotificationUrl(notificationUrl)
                .setContent(ByteString.copyFrom(content))
                .build()

            return try {
                KChatSDKClient.chatClient.sendAnyC2CMessage(
                    request,
                    KChatSDKClient.header
                ).messageUid
            } catch (e: Exception) {
                Log.e("KChat.SendAnyC2C", "send message failed!")
                throw e
            }
        }
    }

    // 为防止GC导致listener被意外中断，这里需要建立一个局部变量进行处理
    private lateinit var chatChannel: Flow<Chat.ChatMessageWrapper>
    private lateinit var syncChannel: Flow<Chat.SyncChatRecordReply>

    /**
     * 这个方法需要传入一个delegate用于接收新消息的回调
     * 由于协程的特性，这个delegate被执行的地方是未知的，因此如果需要在回调中刷新UI
     * 请务必手动切换线程以避免非主线程刷新UI的bug
     */
    suspend fun listenForChatMessage(listener: KChatEventDelegate) {
        val request = Chat.SubscribeChannelRequest.newBuilder()
            .setUserUid(KChatSDKClient.userUid)
            .setDeviceTag(KChatSDKClient.deviceId)
            .build()

        this.chatChannel = KChatSDKClient.chatClient
            .subscribeChatMessage(request, KChatSDKClient.header)

        this.chatChannel.flowOn(Dispatchers.IO)
            .onStart {
                Log.i("KChat.Subscribe", "start listening for message.")
            }
            .catch { err ->
                listener.onError(err)
                Log.e("KChat.Subscribe", "network err!")
            }
            .onCompletion { err ->
                listener.channelClose(err)
                Log.e("KChat.Subscribe", "channel closed due to some reason.")
            }
            .collect { message ->
                when (message.contentCase) {
                    Chat.ChatMessageWrapper.ContentCase.C2CMESSAGE -> listener.onReceiveC2CMessage(
                        message.c2CMessage
                    )
                    else -> listener.onError(IllegalArgumentException("unknown message type"))
                }
            }
    }

    /**
     * @see fetchChatRecords 这个也是用来同步聊天记录的接口，可以查看上面接口的定义
     * 不同的是，这个接口会以stream的形式返回数据，因此就可以慢慢处理所有的数据
     * 同样的道理，不应该在这个地方设置过大的pageSize，小心手机被卡爆
     *
     * @see listenForChatMessage 关于delegate的处理，可以看上面的函数
     */
    suspend fun fetchChatRecords(
        startTime: String = "2022-01-01T00:00:00.000+08:00",
        endTime: String = "2099-12-31T23:59:59.999+08:00",
        pageSize: Int = 10,
        delegate: KChatSyncRecordStreamDelegate
    ) {
        val request = Chat.SyncChatRecordRequest.newBuilder()
            .setUserUid(KChatSDKClient.userUid)
            .setFromTime(startTime)
            .setToTime(endTime)
            .setMaxLength(pageSize)
            .build()

        this.syncChannel =
            KChatSDKClient.chatClient.syncChatRecordStream(request, KChatSDKClient.header)

        this.syncChannel.flowOn(Dispatchers.IO)
            .onStart {
                Log.i("KChat.Sync", "start synking message.")
            }
            .catch { err ->
                err.printStackTrace()
                Log.e("KChat.Sync", "network err!")
            }
            .onCompletion { err ->
                err?.printStackTrace()
                Log.i("KChat.Sync", "sync finished.")
            }
            .collect { message ->
                Log.i("KChat.Sync", "pack size: ${message.recordsList.size}")
                delegate.processPack(message.recordsList)
            }
    }
}