package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketBase;

public class WebSocketPingPongHandler<WS extends  WebSocketBase> implements Handler<WS> {
  private static final byte[] PING_BYTES = "ping".getBytes();
  
  private Handler<WS> handler;
  private Vertx vertx;
  private int pingIntervalMilli;
  private int pongTimeoutMilli;

  public WebSocketPingPongHandler(Vertx vertx, Handler<WS> handler, int pingIntervalMilli, int pongTimeoutMilli) {
    this.vertx = vertx;
    this.handler = handler;
    this.pingIntervalMilli = pingIntervalMilli;
    this.pongTimeoutMilli = pongTimeoutMilli;
  }

  @Override
  public void handle(WS event) {
    WebSocketKeepAliveContext keepAliveContext = new WebSocketKeepAliveContext(event);
    event.closeHandler(vOid -> {
      keepAliveContext.stop();
    });
    event.pongHandler(pong ->{
      keepAliveContext.resetCloseTimeId();
    });
    keepAliveContext.resetPingPeriodic();
    keepAliveContext.resetCloseTimeId();
    handler.handle(event);
  }

  private class WebSocketKeepAliveContext {
    private Long closeTimerId = null;
    private Long pingPeriodicId = null;

    private boolean closed = false;
    private WS websocket;

    WebSocketKeepAliveContext(WS websocket) {
      this.websocket = websocket;
    }

    synchronized void resetCloseTimeId() {
      if(!closed) {
        if (closeTimerId != null) vertx.cancelTimer(closeTimerId);
        closeTimerId = vertx.setTimer(pongTimeoutMilli, timerId -> {
          stop();
        });
      }
    }

    synchronized void resetPingPeriodic() {
      if(!closed) {
        if (pingPeriodicId != null) vertx.cancelTimer(pingPeriodicId);
        pingPeriodicId = vertx.setPeriodic(pingIntervalMilli, periodicId -> {
          websocket.writePing(Buffer.buffer(PING_BYTES));
        });
      }
    }

    synchronized void stop() {
      closed = true;
      if(closeTimerId != null) vertx.cancelTimer(closeTimerId);
      if(pingPeriodicId != null) vertx.cancelTimer(pingPeriodicId);
      websocket.close();
    }
  }
}
