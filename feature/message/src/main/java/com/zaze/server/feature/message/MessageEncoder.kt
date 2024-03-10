package com.zaze.server.feature.message

import javax.websocket.Encoder
import javax.websocket.EndpointConfig

class MessageEncoder : Encoder.Text<Message> {
    override fun init(endpointConfig: EndpointConfig?) {
    }

    override fun destroy() {
    }

    override fun encode(`object`: Message?): String {
        return `object`?.msg ?: ""
    }
}