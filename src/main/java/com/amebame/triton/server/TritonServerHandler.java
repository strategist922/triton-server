package com.amebame.triton.server;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import com.amebame.triton.entity.TritonError;
import com.amebame.triton.exception.TritonException;
import com.amebame.triton.json.Json;
import com.amebame.triton.protocol.TritonMessage;
import com.fasterxml.jackson.databind.JsonNode;

public class TritonServerHandler extends SimpleChannelUpstreamHandler {
	
	private static final Logger log = LogManager.getLogger(TritonServerHandler.class);
	
	private TritonServerContext context;
	
	@Inject
	public TritonServerHandler(TritonServerContext context) {
		this.context = context;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent evt) throws Exception {
		Channel channel = evt.getChannel();
		TritonMessage message = (TritonMessage) evt.getMessage();
		ExecutorService executor = context.getWorkerExecutor();
		executor.submit(new TritonServerWorker(message, channel));
	}
	
	/**
	 * Execution Worker
	 */
	private class TritonServerWorker implements Runnable {
		private TritonMessage message;
		private Channel channel;
		private TritonServerWorker(TritonMessage message, Channel channel) {
			this.message = message;
			this.channel = channel;
		}
		@Override
		public void run() {
			// parse json from body
			try {
				JsonNode node = Json.tree(message.getBody());
				String name = node.get("name").asText();
				if (name == null) {
					sendError(message.getCallId(), channel, "name should be specified in a body");
					return;
				}
				TritonServerMethod method = context.getServerMethod(name);
				if (method == null) {
					sendError(message.getCallId(), channel, "method " + name + " does not exist");
					return;
				}
				JsonNode body = node.get("body");
				if (log.isTraceEnabled()) {
					log.trace("message received {} - {}", name, body.toString());
				}
				// invoke reflected method
				Object result = method.invoke(channel, message, body);
				// send reply
				sendReply(message.getCallId(), channel, result);

			} catch (Exception e) {
				// get root cause
				Throwable ex = TritonException.getRootCause(e);
				// if failed to parse
				log.warn("method execution failed", ex);
				// return client as error
				sendError(message.getCallId(), channel, ex);
			}
		}
	}
	
	/**
	 * Send reply to the client
	 * @param callId
	 * @param channel
	 * @param body
	 */
	private void sendReply(int callId, Channel channel, Object body) {
		if (callId > 0) {
			TritonMessage message = new TritonMessage(TritonMessage.REPLY, callId, body);
			channel.write(message);
		}
	}
	
	/**
	 * Send error to the client as reply
	 * @param callId
	 * @param channel
	 * @param e
	 */
	private void sendError(int callId, Channel channel, Throwable e) {
		if (callId > 0) {
			String text = e.getMessage();
			// swap error message if received cassandra exception
			if (e.getClass() == InvalidRequestException.class) {
				text = ((InvalidRequestException) e).getWhy();
			}
			TritonMessage message = new TritonMessage(TritonMessage.ERROR, callId, new TritonError(text));
			channel.write(message);
		}
	}
	
	/**
	 * Send erro to the client as reply
	 * @param callId
	 * @param channel
	 * @param text
	 */
	private void sendError(int callId, Channel channel, String text) {
		if (callId > 0) {
			TritonMessage message = new TritonMessage(TritonMessage.ERROR, callId, new TritonError(text));
			channel.write(message);
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		log.error("exception occurs while processing request", e.getCause());
	}
	
	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		super.channelConnected(ctx, e);
		log.debug("client connected {}", e.getChannel().getId());
	}
	
	@Override
	public void channelDisconnected(ChannelHandlerContext ctx,
			ChannelStateEvent e) throws Exception {
		super.channelDisconnected(ctx, e);
		log.debug("client disconnected {}", e.getChannel().getId());
	}
	
}
