package ltd.kevinc.kchat

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import okio.ByteString
import service.chat.C2CChatMessage
import service.chat.ChatMessageWrapper
import service.chat.SubscribeChannelRequest
import service.chat.SyncChatRecordRequest

class KChatServiceClient {
    /**
     * 在app启动时，可以用这个接口进行数据的同步
     * @param startTime 同步聊天记录的开始区间，ISO8601时间戳，带时区
     * @param endTime 参照startTime即可
     * @return 返回的是一个list of object。内部的object是一个联合体，它可能是以下几种类型之一：C2CChatMessage, GroupChatMessage, FriendApply等等
     */
    suspend fun fetChatRecords(
        startTime: String = "2022-01-01T00:00:00.000+08:00",
        endTime: String = "2099-12-31T23:59:59.999+08:00"
    ): List<ChatMessageWrapper> {
        val request = SyncChatRecordRequest(
            userUid = KChatSDKClient.mUserUid,
            fromTime = startTime,
            toTime = endTime
        )

        return try {
            KChatSDKClient.chatClient.syncChatRecord().apply {
                requestMetadata = KChatSDKClient.makeHeader()
            }.execute(request).records
        } catch (e: Exception) {
            Log.e("KChat.SyncRecord", "failed to fetch data")
            e.printStackTrace()

            emptyList()
        }
    }

    /**
     * 用于发送任意的C2C消息，在这个模式下，无论两者是否为好友关系，这个消息都会发送，除非对方在黑名单中
     * @param content 消息内容为二进制格式，大小限制为10MB，非常不建议使用content承载大的对象，效率会很差
     * @param receiver 消息接收者的Guid，这个值由B端客户进行提供，也可以使用KChatClient.getOrCreateUser进行获取
     * @param notificationUrl 当消息发送成功后，会向此地址发送一个POST请求，用于通知B端的服务器进行动作
     * @return 返回消息的Guid，此记录唯一，前端可进行缓存
     */
    suspend fun sendAnyC2CMessage(
        content: ByteString,
        receiver: String,
        notificationUrl: String = ""
    ): String {
        val request = C2CChatMessage(
            senderUserUid = KChatSDKClient.mUserUid,
            senderDeviceTag = KChatSDKClient.deviceId,
            receiverUserUid = receiver,
            content = content,
            notificationUrl = notificationUrl
        )

        return try {
            KChatSDKClient.chatClient.sendAnyC2CMessage().apply {
                requestMetadata = KChatSDKClient.makeHeader()
            }.execute(request).messageUid
        } catch (e: Exception) {
            Log.e("KChat.SendAnyC2C", "send message failed!")
            throw e
        }
    }

    suspend fun listenForChatMessage(listener: KChatEventDelegate, coroutineScope: CoroutineScope) {
        val request = SubscribeChannelRequest(
            userUid = KChatSDKClient.mUserUid,
            deviceTag = KChatSDKClient.deviceId
        )

        KChatSDKClient.chatClient.subscribeChatMessage().apply {
            this.requestMetadata = KChatSDKClient.makeHeader()
        }.executeIn(coroutineScope)
            .let { (sendChannel, receiveChannel) ->
                try {
                    sendChannel.send(request)

                    for (update in receiveChannel) {
                        update.c2cMessage?.let {
                            listener.onReceiveC2CMessage(it)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KChat.Subscribe", "fail to establish a chat channel")
                    listener.channelClose(e)
                }
            }
    }
}