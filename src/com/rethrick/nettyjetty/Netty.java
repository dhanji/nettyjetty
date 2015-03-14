package com.rethrick.nettyjetty;

import com.rethrick.nettyjetty.compute.ComputeIntensiveTask;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;


/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Netty {
  public static void main(String[] args) throws InterruptedException {
    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    ServerBootstrap b = new ServerBootstrap();

    b.option(ChannelOption.SO_BACKLOG, 1024);
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override protected void initChannel(SocketChannel socketChannel) throws Exception {
            ChannelPipeline p = socketChannel.pipeline();
            p.addLast(new HttpServerCodec());
            p.addLast(new ChannelHandlerAdapter() {
              @Override public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                ctx.flush();
              }

              @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof HttpRequest) {
                  HttpRequest req = (HttpRequest) msg;

                  if (HttpHeaderUtil.is100ContinueExpected(req)) {
                    ctx.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.CONTINUE));
                  }
                  boolean keepAlive = HttpHeaderUtil.isKeepAlive(req);
                  FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK,
                      Unpooled.wrappedBuffer(new ComputeIntensiveTask().compute()));
                  response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
                  response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

                  if (!keepAlive) {
                    ctx.write(response).addListener(ChannelFutureListener.CLOSE);
                  } else {
                    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    ctx.write(response);
                  }
                }
              }
            });
          }
        });

    Channel ch = b.bind(8080).sync().channel();

    ch.closeFuture().sync();
  }
}
