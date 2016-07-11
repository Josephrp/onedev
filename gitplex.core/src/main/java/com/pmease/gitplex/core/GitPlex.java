package com.pmease.gitplex.core;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.util.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pmease.commons.hibernate.Sessional;
import com.pmease.commons.loader.AbstractPlugin;
import com.pmease.commons.loader.AppLoader;
import com.pmease.commons.loader.AppName;
import com.pmease.commons.loader.ManagedSerializedForm;
import com.pmease.commons.util.init.InitStage;
import com.pmease.commons.util.init.ManualConfig;
import com.pmease.gitplex.core.listener.LifecycleListener;
import com.pmease.gitplex.core.manager.AccountManager;
import com.pmease.gitplex.core.manager.ConfigManager;
import com.pmease.gitplex.core.manager.DataManager;
import com.pmease.gitplex.core.setting.ServerConfig;

public class GitPlex extends AbstractPlugin implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(GitPlex.class);
	
	private final ConfigManager configManager;
	
	private final DataManager dataManager;
	
	private final AccountManager accountManager;
	
	private final ServerConfig serverConfig;
	
	private final String appName;
	
	private volatile InitStage initStage;
	
	private final Provider<Set<LifecycleListener>> listenersProvider;
	
	@Inject
	public GitPlex(ServerConfig serverConfig, DataManager dataManager, ConfigManager configManager,
            AccountManager accountManager, Provider<Set<LifecycleListener>> listenersProvider, 
            @AppName String appName) {
		this.configManager = configManager;
		this.dataManager = dataManager;
		this.serverConfig = serverConfig;
		this.accountManager = accountManager;
		this.listenersProvider = listenersProvider;
		
		this.appName = appName;
		
		initStage = new InitStage("Server is Starting...");
	}
	
	@Override
	@Sessional
	public void start() {
		List<ManualConfig> manualConfigs = dataManager.init();
		
		if (!manualConfigs.isEmpty()) {
			logger.warn("Please set up the server at " + guessServerUrl());
			initStage = new InitStage("Server Setup", manualConfigs);
			
			initStage.waitFor();
		}

		ThreadContext.bind(accountManager.getRoot().asSubject());
		
		for (LifecycleListener listener: listenersProvider.get())
			listener.systemStarting();
	}
	
	@Sessional
	@Override
	public void postStart() {
		initStage = null;
		
		for (LifecycleListener listener: listenersProvider.get())
			listener.systemStarted();
		
		ThreadContext.unbindSubject();
		
		logger.info("Server is ready at " + configManager.getSystemSetting().getServerUrl() + ".");
	}

	public String guessServerUrl() {
		String hostName;
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
		
		String serverUrl;
		if (serverConfig.getHttpPort() != 0)
			serverUrl = "http://" + hostName + ":" + serverConfig.getHttpPort();
		else 
			serverUrl = "https://" + hostName + ":" + serverConfig.getSslConfig().getPort();

		return StringUtils.stripEnd(serverUrl, "/");
	}
	
	public String getAppName() {
		return appName;
	}
	
	/**
	 * This method can be called from different UI threads, so we clone initStage to 
	 * make it thread-safe.
	 * <p>
	 * @return
	 * 			cloned initStage, or <tt>null</tt> if system initialization is completed
	 */
	public @Nullable InitStage getInitStage() {
		if (initStage != null) {
			return initStage.clone();
		} else {
			return null;
		}
	}
	
	public boolean isReady() {
		return initStage == null;
	}
	
	public static GitPlex getInstance() {
		return AppLoader.getInstance(GitPlex.class);
	}
	
	public static <T> T getInstance(Class<T> type) {
		return AppLoader.getInstance(type);
	}

	public static <T> Set<T> getExtensions(Class<T> extensionPoint) {
		return AppLoader.getExtensions(extensionPoint);
	}

	@Sessional
	@Override
	public void preStop() {
		ThreadContext.bind(accountManager.getRoot().asSubject());
		for (LifecycleListener listener: listenersProvider.get())
			listener.systemStopping();
	}

	@Sessional
	@Override
	public void stop() {
		for (LifecycleListener listener: listenersProvider.get())
			listener.systemStopped();
		ThreadContext.unbindSubject();
	}
	
	public Object writeReplace() throws ObjectStreamException {
		return new ManagedSerializedForm(GitPlex.class);
	}	
	
}
