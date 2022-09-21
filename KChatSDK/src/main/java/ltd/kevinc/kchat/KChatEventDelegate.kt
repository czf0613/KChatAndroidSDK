package ltd.kevinc.kchat

import service.chat.C2CChatMessage

interface KChatEventDelegate {
    fun onReceiveC2CMessage(message: C2CChatMessage) {

    }

    fun channelClose(e: Exception) {

    }
}