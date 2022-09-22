package ltd.kevinc.kchat

import service.chat.Chat

interface KChatEventDelegate {
    fun onReceiveC2CMessage(message: Chat.C2CChatMessage) {

    }

    fun onError(e: Throwable) {

    }

    fun channelClose(e: Throwable?) {

    }
}