package com.zaze.server.feature.message

import javax.websocket.Decoder
import javax.websocket.EndpointConfig

class MessageDecoder : Decoder.Text<Message> {
    override fun init(endpointConfig: EndpointConfig?) {
    }

    override fun destroy() {
    }

    override fun decode(s: String?): Message {
        return Message(s)
    }

    override fun willDecode(s: String?): Boolean {
        return true
    }
}