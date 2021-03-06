package com.acgist.snail;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acgist.snail.config.DhtConfig;
import com.acgist.snail.config.TrackerConfig;
import com.acgist.snail.context.EntityContext;
import com.acgist.snail.context.NatContext;
import com.acgist.snail.context.exception.DownloadException;
import com.acgist.snail.context.initializer.ConfigInitializer;
import com.acgist.snail.context.initializer.DhtInitializer;
import com.acgist.snail.context.initializer.DownloaderInitializer;
import com.acgist.snail.context.initializer.EntityInitializer;
import com.acgist.snail.context.initializer.Initializer;
import com.acgist.snail.context.initializer.LocalServiceDiscoveryInitializer;
import com.acgist.snail.context.initializer.NatInitializer;
import com.acgist.snail.context.initializer.TorrentInitializer;
import com.acgist.snail.context.initializer.TrackerInitializer;
import com.acgist.snail.downloader.DownloaderManager;
import com.acgist.snail.downloader.IDownloader;
import com.acgist.snail.net.application.ApplicationClient;
import com.acgist.snail.net.application.ApplicationServer;
import com.acgist.snail.net.torrent.TorrentServer;
import com.acgist.snail.net.torrent.lsd.LocalServiceDiscoveryServer;
import com.acgist.snail.net.torrent.peer.PeerServer;
import com.acgist.snail.net.torrent.tracker.TrackerServer;
import com.acgist.snail.net.torrent.utp.UtpRequestQueue;
import com.acgist.snail.pojo.ITaskSession;
import com.acgist.snail.protocol.Protocol;
import com.acgist.snail.protocol.ProtocolManager;
import com.acgist.snail.protocol.ftp.FtpProtocol;
import com.acgist.snail.protocol.hls.HlsProtocol;
import com.acgist.snail.protocol.http.HttpProtocol;
import com.acgist.snail.protocol.magnet.MagnetProtocol;
import com.acgist.snail.protocol.thunder.ThunderProtocol;
import com.acgist.snail.protocol.torrent.TorrentProtocol;

/**
 * <p>Snail下载工具</p>
 * <p>快速创建下载任务</p>
 * 
 * @author acgist
 * 
 * TODO：优化更多接口调用
 */
public final class Snail {

	private static final Logger LOGGER = LoggerFactory.getLogger(Snail.class);
	
	/**
	 * <p>全局唯一Snail对象</p>
	 */
	private static final Snail SNAIL = new Snail();
	
	public static final Snail getInstance() {
		return SNAIL;
	}
	
	/**
	 * <p>是否加锁</p>
	 */
	private boolean lock = false;
	/**
	 * <p>是否创建Torrent任务</p>
	 */
	private boolean buildTorrent = false;
	/**
	 * <p>是否加载已有任务</p>
	 */
	private boolean buildDownloader = false;
	/**
	 * <p>是否启动系统监听</p>
	 * <p>启动检测：开启监听失败表示已经存在系统实例，发送消息唤醒已有实例窗口。</p>
	 * 
	 */
	private boolean buildApplication = false;
	/**
	 * <p>系统状态</p>
	 */
	private volatile boolean available = false;
	
	/**
	 * <p>禁止创建实例</p>
	 */
	private Snail() {
	}
	
	/**
	 * <p>开始下载</p>
	 * 
	 * @param url 下载链接
	 * 
	 * @return 下载任务
	 * 
	 * @throws DownloadException 下载异常 
	 */
	public IDownloader download(String url) throws DownloadException {
		return DownloaderManager.getInstance().newTask(url);
	}
	
	/**
	 * <p>添加下载锁</p>
	 * <p>任务下载完成解除</p>
	 */
	public void lockDownload() {
		synchronized (this) {
			this.lock = true;
			while(DownloaderManager.getInstance().allTask().stream().anyMatch(ITaskSession::inThreadPool)) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					LOGGER.debug("线程等待异常", e);
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * <p>解除下载锁</p>
	 */
	public void unlockDownload() {
		if(this.lock) {
			synchronized (this) {
				this.notifyAll();
			}
		}
	}
	
	/**
	 * <p>判断系统是否可用</p>
	 * 
	 * @return 是否可用
	 */
	public static final boolean available() {
		return SNAIL.available;
	}
	
	/**
	 * <p>关闭资源</p>
	 */
	public static final void shutdown() {
		if(SNAIL.available) {
			SNAIL.available = false;
			if(SNAIL.buildApplication) {
				ApplicationServer.getInstance().close();
			}
			// 优先关闭任务
			DownloaderManager.getInstance().shutdown();
			if(SNAIL.buildTorrent) {
				PeerServer.getInstance().close();
				TorrentServer.getInstance().close();
				TrackerServer.getInstance().close();
				LocalServiceDiscoveryServer.getInstance().close();			
				NatContext.getInstance().shutdown();
				UtpRequestQueue.getInstance().shutdown();
				// DHT和Tracker需要启用BT任务才会保存
				DhtConfig.getInstance().persistent();
				TrackerConfig.getInstance().persistent();
			}
			EntityContext.getInstance().persistent();
		}
	}
	
	/**
	 * <p>SnailBuilder</p>
	 * 
	 * @author acgist
	 */
	public static final class SnailBuilder {
		
		private static final SnailBuilder BUILDER = new SnailBuilder();
		
		/**
		 * <p>获取SnailBuilder</p>
		 * 
		 * @return SnailBuilder
		 */
		public static final SnailBuilder getInstance() {
			return BUILDER;
		}
		
		/**
		 * <p>禁止创建实例</p>
		 */
		private SnailBuilder() {
			EntityInitializer.newInstance().sync();
			ConfigInitializer.newInstance().sync();
		}

		/**
		 * <p>同步创建Snail</p>
		 * 
		 * @return Snail
		 */
		public Snail buildSync() {
			return this.build(true);
		}
		
		/**
		 * <p>异步创建Snail</p>
		 * 
		 * @return Snail
		 */
		public Snail buildAsyn() {
			return this.build(false);
		}
		
		/**
		 * <p>创建Snail</p>
		 * 
		 * @param sync 是否同步初始化
		 * 
		 * @return Snail
		 * 
		 * @throws DownloadException 下载异常 
		 */
		public synchronized Snail build(boolean sync) {
			if(SNAIL.available) {
				return SNAIL;
			}
			SNAIL.available = true;
			if(SNAIL.buildApplication) {
				SNAIL.available = ApplicationServer.getInstance().listen();
			}
			if(SNAIL.available) {
				ProtocolManager.getInstance().available(true);
				this.buildInitializers().forEach(initializer -> {
					if(sync) {
						initializer.sync();
					} else {
						initializer.asyn();
					}
				});
			} else {
				LOGGER.info("已有系统实例：唤醒实例窗口");
				ApplicationClient.notifyWindow();
			}
			return SNAIL;
		}

		/**
		 * <p>加载初始化列表</p>
		 * 
		 * @return 初始化列表
		 */
		private List<Initializer> buildInitializers() {
			final List<Initializer> list = new ArrayList<>();
			if(SNAIL.buildTorrent) {
				list.add(NatInitializer.newInstance());
				list.add(DhtInitializer.newInstance());
				list.add(TorrentInitializer.newInstance());
				list.add(TrackerInitializer.newInstance());
				list.add(LocalServiceDiscoveryInitializer.newInstance());
			}
			if(SNAIL.buildDownloader) {
				list.add(DownloaderInitializer.newInstance());
			}
			return list;
		}

		/**
		 * <p>启动系统监听</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder application() {
			SNAIL.buildApplication = true;
			return this;
		}
		
		/**
		 * <p>加载已有任务</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder downloader() {
			SNAIL.buildDownloader = true;
			return this;
		}
		
		/**
		 * <p>注册下载协议</p>
		 * 
		 * @param protocol 下载协议
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder register(Protocol protocol) {
			ProtocolManager.getInstance().register(protocol);
			return this;
		}
		
		/**
		 * <p>注册FTP下载协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableFtp() {
			return this.register(FtpProtocol.getInstance());
		}
		
		/**
		 * <p>注册HLS下载协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableHls() {
			return this.register(HlsProtocol.getInstance());
		}
		
		/**
		 * <p>注册HTTP下载协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableHttp() {
			return this.register(HttpProtocol.getInstance());
		}
		
		/**
		 * <p>注册Magnet下载协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableMagnet() {
			SNAIL.buildTorrent = true;
			return this.register(MagnetProtocol.getInstance());
		}
		
		/**
		 * <p>注册Thunder下载协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableThunder() {
			return this.register(ThunderProtocol.getInstance());
		}
		
		/**
		 * <p>注册Torrent下载协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableTorrent() {
			SNAIL.buildTorrent = true;
			return this.register(TorrentProtocol.getInstance());
		}
		
		/**
		 * <p>注册所有协议</p>
		 * 
		 * @return SnailBuilder
		 */
		public SnailBuilder enableAllProtocol() {
			return this
				.enableFtp()
				.enableHls()
				.enableHttp()
				.enableMagnet()
				.enableThunder()
				.enableTorrent();
		}

	}
	
}
