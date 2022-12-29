package io.onedev.server.product;

import com.google.common.base.Splitter;
import io.onedev.commons.bootstrap.Bootstrap;
import io.onedev.server.ServerConfig;
import io.onedev.server.persistence.HibernateConfig;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.internal.guava.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

@Singleton
public class DefaultServerConfig implements ServerConfig {

	private static final Logger logger = LoggerFactory.getLogger(DefaultServerConfig.class);
	
	private static final String PROP_HTTP_PORT = "http_port";
	
	private static final String PROP_SSH_PORT = "ssh_port";
	
	private static final String PROP_TRUST_CERTS = "trust_certs";
	
	private static final String PROP_CLUSTER_IP = "cluster_ip";
	
	private static final String PROP_CLUSTER_PORT = "cluster_port";
	
	private int httpPort;
	
	private int sshPort;
	
	private File trustCertsDir;
	
	private File keystoreFile;
	
	private String keystorePassword;
	
	private String clusterIp;
	
	private int clusterPort;
	
	private int serverCpu;
	
	private int serverMemory;
	
	@Inject
	public DefaultServerConfig(ServerProperties props, HibernateConfig hibernateConfig) {
		String httpPortStr = System.getenv(PROP_HTTP_PORT);
		if (StringUtils.isBlank(httpPortStr))
			httpPortStr = props.getProperty(PROP_HTTP_PORT);
		if (StringUtils.isNotBlank(httpPortStr)) { 
			httpPort = Integer.parseInt(httpPortStr.trim());
		} else {
        	logger.warn(PROP_HTTP_PORT + " not specified, default to 6610");
        	httpPort = 6610;
		}

		String sshPortStr = System.getenv(PROP_SSH_PORT);
        if (StringUtils.isBlank(sshPortStr))
            sshPortStr = props.getProperty(PROP_SSH_PORT);
        if (StringUtils.isNotBlank(sshPortStr)) {
            sshPort = Integer.parseInt(sshPortStr.trim());
        } else {
        	logger.warn(PROP_SSH_PORT + " not specified, default to 6611");
        	sshPort = 6611;
        }
		
		String trustCerts = System.getenv(PROP_TRUST_CERTS);
		if (StringUtils.isBlank(trustCerts))
			trustCerts = props.getProperty(PROP_TRUST_CERTS);
		if (StringUtils.isNotBlank(trustCerts)) {
			trustCertsDir = new File(trustCerts.trim());
			if (!trustCertsDir.isAbsolute())
				trustCertsDir = new File(Bootstrap.getConfDir(), trustCerts);
			Preconditions.checkState(trustCertsDir.exists(), 
					"Trust certs directory not exist: " + trustCertsDir.getAbsolutePath());
		}
		
		clusterIp = System.getenv(PROP_CLUSTER_IP);
		if (StringUtils.isBlank(clusterIp))
			clusterIp = props.getProperty(PROP_CLUSTER_IP);
		
		if (StringUtils.isBlank(clusterIp)) {
			String dbUrl = StringUtils.substringAfter(hibernateConfig.getUrl(), ":");
			if (dbUrl.startsWith("hsqldb")) {
				clusterIp = "127.0.0.1";
			} else {
				String dbHost; 
				int dbPort;
				if (dbUrl.startsWith("oracle")) {
					List<String> fields = Splitter.on(":").splitToList(dbUrl);
					dbHost = StringUtils.stripStart(fields.get(2), "@");
					String dbPortString = fields.get(3);
					if (dbPortString.contains("/"))
						dbPort = Integer.parseInt(StringUtils.substringBefore(dbPortString, "/"));
					else
						dbPort = Integer.parseInt(dbPortString);
				} else {
					String tempStr = StringUtils.substringAfter(dbUrl, "//");
					dbHost = StringUtils.substringBefore(tempStr, ":");
					tempStr = StringUtils.substringAfter(tempStr, ":");
					if (dbUrl.startsWith("sqlserver"))
						dbPort = Integer.parseInt(StringUtils.substringBefore(tempStr, ";"));
					else
						dbPort = Integer.parseInt(StringUtils.substringBefore(tempStr, "/"));
				}
				
				try (Socket socket = new Socket()) {
					socket.connect(new InetSocketAddress(dbHost, dbPort));
					clusterIp = socket.getLocalAddress().getHostAddress();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		String clusterPortStr = System.getenv(PROP_CLUSTER_PORT);
		if (StringUtils.isBlank(clusterPortStr))
			clusterPortStr = props.getProperty(PROP_CLUSTER_PORT);
		if (StringUtils.isBlank(clusterPortStr))
			clusterPort = 5701;
		else
			clusterPort = Integer.parseInt(clusterPortStr.trim());
	}
	
	@Override
	public int getHttpPort() {
		return httpPort;
	}

	@Override
	public File getKeystoreFile() {
		return keystoreFile;
	}

	@Override
	public String getKeystorePassword() {
		return keystorePassword;
	}

	@Override
	public File getTrustCertsDir() {
		return trustCertsDir;
	}

	@Override
    public int getSshPort() {
        return sshPort;
    }

	@Override
	public String getClusterIp() {
		return clusterIp;
	}

	@Override
	public int getClusterPort() {
		return clusterPort;
	}

}
