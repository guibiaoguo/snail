package com.acgist.snail.system.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acgist.snail.utils.StringUtils;

/**
 * <p>Tracker服务列表配置</p>
 * <p>优先加载{@linkplain TrackerConfig#TRACKER_CONFIG_USER 用户配置}，如果不存在加载默认配置{@linkplain TrackerConfig#TRACKER_CONFIG 默认配置}。</p>
 * 
 * @author acgist
 * @since 1.0.0
 */
public class TrackerConfig extends PropertiesConfig {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TrackerConfig.class);

	/**
	 * 最大失败次数，超过 这个次数将会被标记无效，以后也不再使用。
	 */
	public static final int MAX_FAIL_TIMES = 3;
	
	/**
	 * announce事件
	 */
	public enum Event {
		
		none(0), // none
		completed(1), // 完成
		started(2), // 开始
		stopped(3); // 停止
		
		private int event;

		private Event(int event) {
			this.event = event;
		}

		public int event() {
			return this.event;
		}

	}
	
	/**
	 * 动作
	 */
	public enum Action {
		
		connect(0), // 连接
		announce(1), // 声明信息
		scrape(2), // 刷新信息
		error(3); // 错误
		
		private int action;

		private Action(int action) {
			this.action = action;
		}
		
		public int action() {
			return this.action;
		}
		
	}
	
	private static final TrackerConfig INSTANCE = new TrackerConfig();
	
	private static final String TRACKER_CONFIG = "/config/bt.tracker.properties";
	private static final String TRACKER_CONFIG_USER = "/config/bt.tracker.user.properties";
	
	public TrackerConfig() {
		super(TRACKER_CONFIG_USER, TRACKER_CONFIG);
	}
	
	static {
		LOGGER.info("初始化Tracker配置");
		INSTANCE.init();
	}
	
	public static final TrackerConfig getInstance() {
		return INSTANCE;
	}
	
	/**
	 * 默认Tracker声明地址
	 */
	private List<String> announces = new ArrayList<>();
	
	private void init() {
		final Properties properties = this.properties.properties();
		properties.entrySet().forEach(entry -> {
			final String announce = (String) entry.getValue();
			if(StringUtils.isNotEmpty(announce)) {
				announces.add(announce);
			} else {
				LOGGER.warn("注册默认Tracker失败：{}", announce);
			}
		});
	}

	/**
	 * 获取配置的Tracker
	 */
	public List<String> announces() {
		return this.announces;
	}
	
}
