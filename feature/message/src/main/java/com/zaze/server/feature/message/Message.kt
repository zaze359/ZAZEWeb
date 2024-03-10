package com.zaze.server.feature.message

data class Message(val msg: String?) {

    companion object {
        const val BYE = "bye"
    }
    fun isEmpty(): Boolean {
        return msg.isNullOrEmpty()
    }

    fun isBye(): Boolean {
        return msg == BYE
    }
}