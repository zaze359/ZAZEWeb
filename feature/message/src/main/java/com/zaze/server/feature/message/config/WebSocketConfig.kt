package com.zaze.server.feature.message.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.server.standard.ServerEndpointExporter


@Configuration
@EnableWebSocket
class WebSocketConfig {

    /**
     * 用于自动注册 @ServerEndpoint 注解声明 原生WebSocket EndPoint
     */
    @Bean
    fun serverEndpointExporter(): ServerEndpointExporter {
        return ServerEndpointExporter()
    }
}

//@Configuration
//@EnableWebSocketMessageBroker
//class WebSocketConfiguration : WebSocketMessageBrokerConfigurer {
//
//    /**
//     * 用于自动注册 @ServerEndpoint 注解声明 原生WebSocket EndPoint
//     */
//    @Bean
//    fun serverEndpointExporter(): ServerEndpointExporter {
//        return ServerEndpointExporter()
//    }
//
//    override fun configureMessageBroker(config: MessageBrokerRegistry) {
//        // /topic为广播模式
//        config.enableSimpleBroker("/topic")
//        // 设置应用程序全局目标前缀, 客户端向服务端发送消息需有/app 前缀
//        config.setApplicationDestinationPrefixes("/app")
//        // 指定用户发送（一对一）的前缀 /user/
//        config.setUserDestinationPrefix("/user/");
//    }
//
//    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
//        // 允许使用socketJs方式访问，访问端点为 ws，并允许跨域
//        registry.addEndpoint("/ws").setAllowedOrigins("*").withSockJS()
//    }
//}