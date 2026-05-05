package com.wms.po.plugin.loader;

import com.wms.po.plugin.config.ClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads client configurations from YAML files
 */
@Service
@Slf4j
public class ClientConfigLoader {

    @Value("${po.config.clients.path:classpath*:config/clients/*.yaml}")
    private String clientConfigPath;

    private final Map<String, ClientConfig> clientConfigs = new HashMap<>();
    private final Yaml yaml = new Yaml();

    @PostConstruct
    public void loadConfigs() {
        log.info("Loading client configurations from: {}", clientConfigPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(clientConfigPath);

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    ClientConfig config = yaml.loadAs(is, ClientConfig.class);
                    if (config != null && config.getClientCode() != null) {
                        clientConfigs.put(config.getClientCode(), config);
                        log.info("Loaded config for client: {}", config.getClientCode());
                    }
                } catch (Exception e) {
                    log.warn("Skipping config file {}: {}", resource.getFilename(), e.getMessage());
                }
            }

            log.info("Loaded {} client configurations", clientConfigs.size());
        } catch (IOException e) {
            log.warn("Could not load client configs: {}", e.getMessage());
        }
    }

    /**
     * Get configuration for a client
     */
    public ClientConfig getConfig(String clientCode) {
        return clientConfigs.get(clientCode);
    }

    /**
     * Get all client configurations
     */
    public Map<String, ClientConfig> getAllConfigs() {
        return Map.copyOf(clientConfigs);
    }

    /**
     * Check if client has configuration
     */
    public boolean hasConfig(String clientCode) {
        return clientConfigs.containsKey(clientCode);
    }

    /**
     * Reload configurations
     */
    public void reload() {
        clientConfigs.clear();
        loadConfigs();
    }
}
