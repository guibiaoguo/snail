package com.acgist.snail.net.torrent.peer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.acgist.snail.net.codec.MessageCodec;
import com.acgist.snail.net.torrent.peer.bootstrap.PeerSubMessageHandler;
import com.acgist.snail.system.config.PeerConfig;
import com.acgist.snail.system.config.SystemConfig;
import com.acgist.snail.system.exception.NetException;
import com.acgist.snail.system.exception.OversizePacketException;

/**
 * <p>Peer消息处理器：拆包</p>
 * 
 * @author acgist
 * @since 1.1.0
 */
public class PeerUnpackMessageCodec extends MessageCodec<ByteBuffer, ByteBuffer> {

	/**
	 * int字符长度
	 */
	private static final int INT_BYTE_LENGTH = 4;
	/**
	 * <p>完整消息缓存</p>
	 * <p>消息按照长度读入进入消息缓存</p>
	 */
	private ByteBuffer buffer;
	/**
	 * 消息长度
	 */
	private final ByteBuffer lengthStick;
	/**
	 * Peer代理
	 */
	private final PeerSubMessageHandler peerSubMessageHandler;
	
	public PeerUnpackMessageCodec(PeerSubMessageHandler peerSubMessageHandler) {
		super(peerSubMessageHandler);
		this.lengthStick = ByteBuffer.allocate(INT_BYTE_LENGTH);
		this.peerSubMessageHandler = peerSubMessageHandler;
	}
	
	@Override
	public void decode(ByteBuffer buffer, InetSocketAddress address, boolean hasAddress) throws NetException {
		int length = 0; // 消息长度
		while(true) {
			if(this.buffer == null) {
				if(this.peerSubMessageHandler.handshake()) {
					for (int index = 0; index < buffer.limit() && buffer.hasRemaining(); index++) {
						this.lengthStick.put(buffer.get());
						if(this.lengthStick.position() == INT_BYTE_LENGTH) {
							break;
						}
					}
					if(this.lengthStick.position() == INT_BYTE_LENGTH) {
						this.lengthStick.flip();
						length = this.lengthStick.getInt();
						this.lengthStick.compact();
					} else { // 消息长度读取不完整跳出
						break;
					}
				} else { // 握手消息长度
					length = PeerConfig.HANDSHAKE_LENGTH;
				}
				// 心跳消息
				if(length <= 0) {
					this.peerSubMessageHandler.keepAlive();
					break;
				}
				if(length >= SystemConfig.MAX_NET_BUFFER_SIZE) {
					throw new OversizePacketException(length);
				}
				this.buffer = ByteBuffer.allocate(length);
			} else {
				length = this.buffer.capacity() - this.buffer.position();
			}
			final int remaining = buffer.remaining();
			if(remaining > length) { // 包含一个完整消息
				final byte[] bytes = new byte[length];
				buffer.get(bytes);
				this.buffer.put(bytes);
				this.doNext(this.buffer, address, hasAddress);
				this.buffer = null;
			} else if(remaining == length) { // 刚好一个完整消息
				final byte[] bytes = new byte[length];
				buffer.get(bytes);
				this.buffer.put(bytes);
				this.doNext(this.buffer, address, hasAddress);
				this.buffer = null;
				break;
			} else if(remaining < length) { // 不是完整消息
				final byte[] bytes = new byte[remaining];
				buffer.get(bytes);
				this.buffer.put(bytes);
				break;
			}
		}
	}

}