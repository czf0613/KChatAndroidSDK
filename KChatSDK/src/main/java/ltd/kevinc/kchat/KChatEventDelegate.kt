package ltd.kevinc.kchat

import service.chat.Chat

interface KChatEventDelegate {
    /**
     * @param message 这个参数是回调的聊天消息体，包含了一些必要的元信息
     * 由于KChat设计的传输是二进制的，因此真正的消息内容是包含在message.content中
     * 是一个ByteString类型，本质上就是一个不可变的字节数组，因此客户端可以自行定义解码协议以定制个性化的信息
     */
    fun onReceiveC2CMessage(message: Chat.C2CChatMessage) {

    }

    fun onError(e: Throwable) {

    }

    fun channelClose(e: Throwable?) {

    }
}