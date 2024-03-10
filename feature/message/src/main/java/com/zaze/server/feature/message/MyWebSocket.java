package com.zaze.server.feature.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint(value = "/ws/{uid}", encoders = {MessageEncoder.class}, decoders = {MessageDecoder.class})
//@ServerEndpoint(value = "/ws/{uid}")
@Slf4j
@Component
public class MyWebSocket {

    //在线人数
    private final static AtomicInteger online = new AtomicInteger(0);
    //所有的对象，用于群发

    //建立连接
    @OnOpen
    public void onOpen(Session session, @PathParam("uid") String uid) {
        online.incrementAndGet();
        log.error("onOpen: {}, {}", uid, online.get());
    }

    //连接关闭
    @OnClose
    public void onClose(Session session, @PathParam("uid") String uid) {
        online.decrementAndGet();
        log.error("onClose: {}, {}", uid, online.get());
    }

    //收到客户端的消息
//    @OnMessage
//    public void onMessage(String message, @PathParam("uid") String uid, Session session, boolean last) {
//        log.error("onMessage: {}, {}, {}, {}", message, uid, session, last);
//    }
    @OnMessage
    public void onMessage(Message message, @PathParam("uid") String uid, Session session) {
        log.error("onMessage: {}, {}, {}", message, uid, session);
        if (message.isBye()) {
            // 由服务器主动关闭连接。状态码为 NORMAL_CLOSURE（正常关闭）。
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, Message.BYE));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        session.getAsyncRemote().sendText("[" + Instant.now().toEpochMilli() + "] Hello " + message);
    }

}