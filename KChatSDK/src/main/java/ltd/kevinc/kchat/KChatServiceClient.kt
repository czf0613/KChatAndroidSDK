package ltd.kevinc.kchat

import android.util.Log
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
         * @return 返回的是一个list of object。内部的object是一个联合体，它可能是以下几种类型之一：C2CChatMessage, GroupChatMessage, FriendApply等等，需要通过ChatMessageWrapperContentCase这个枚举进行判断
         */
        suspend fun fetChatRecords(
            startTime: String = "2022-01-01T00:00:00.000+08:00",
            endTime: String = "2099-12-31T23:59:59.999+08:00"
        ): List<Chat.ChatMessageWrapper> {
            val request = Chat.SyncChatRecordRequest.newBuilder()
                .setUserUid(KChatSDKClient.userUid)
                .setFromTime(startTime)
                .setToTime(endTime)
                .build()

            return try {
                KChatSDKClient.chatClient.syncChatRecord(request, KChatSDKClient.header).recordsList
            } catch (e: Exception) {
                Log.e("KChat.SyncRecord", "failed to fetch data")
                e.printStackTrace()

                emptyList()
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
    private lateinit var listener: KChatEventDelegate
    private lateinit var chatChannel: Flow<Chat.ChatMessageWrapper>

    /**
     * 这个方法需要传入一个delegate用于接收新消息的回调
     * 由于协程的特性，这个delegate被执行的地方是未知的，因此如果需要在回调中刷新UI
     * 请务必手动切换线程以避免非主线程刷新UI的bug
     */
    suspend fun listenForChatMessage(listener: KChatEventDelegate) {
        this.listener = listener

        val request = Chat.SubscribeChannelRequest.newBuilder()
            .setUserUid(KChatSDKClient.userUid)
            .setDeviceTag(KChatSDKClient.deviceId)
            .build()

        try {
            this.chatChannel = KChatSDKClient.chatClient
                .subscribeChatMessage(request, KChatSDKClient.header)
        } catch (e: Exception) {
            Log.e("KChat.Subscribe", "fail to establish a chat channel")
            listener.channelClose(e)
        }

        this.chatChannel
            .onStart {
                Log.i("KChat.Subscribe", "start listening for message.")
            }
            .catch { err ->
                this@KChatServiceClient.listener.onError(err)
                Log.e("KChat.Subscribe", "network err!")
            }
            .onCompletion { err ->
                this@KChatServiceClient.listener.channelClose(err)
                Log.e("KChat.Subscribe", "channel closed due to some reason.")
            }
            .collect { message ->
                when (message.contentCase) {
                    Chat.ChatMessageWrapper.ContentCase.C2CMESSAGE -> listener.onReceiveC2CMessage(
                        message.c2CMessage
                    )
                    else -> this@KChatServiceClient.listener.onError(IllegalArgumentException("unknown message type"))
                }
            }
    }
}