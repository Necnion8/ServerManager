package com.github.nova_27.mcplugin.servermanager.core.utils;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.OptionalLong;

public class StatisticsConfig {

    private final File file;
    private Configuration config;
    private long lobbyLastStartProcessTime;
    private long lobbyLastStartTime;

    public StatisticsConfig(File file) {
        this.file = file;
    }


    public void load() {
        if (file.isFile()) {
            try {
                config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            } catch (IOException e) {
                e.printStackTrace();
                config = new Configuration();
            }
        } else {
            config = new Configuration();
        }

        lobbyLastStartProcessTime = config.getLong("lobby-last-start-process-time", -1);
        lobbyLastStartTime = config.getLong("lobby-last-start-time", -1);
    }


    public void save() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public OptionalLong getLobbyLastStartProcessTime() {
        if (lobbyLastStartProcessTime < 0)
            return OptionalLong.empty();
        return OptionalLong.of(lobbyLastStartProcessTime);
    }

    public OptionalLong getLobbyLastStartTime() {
        if (lobbyLastStartTime < 0)
            return OptionalLong.empty();
        return OptionalLong.of(lobbyLastStartTime);
    }

    public void setLobbyLastStartProcessTime(Long delay) {
        if (delay == null)
            delay = -1L;
        this.lobbyLastStartProcessTime = delay;
        config.set("lobby-last-start-process-time", delay);
    }

    public void setLobbyLastStartTime(Long time) {
        if (time == null)
            time = -1L;
        this.lobbyLastStartTime = time;
        config.set("lobby-last-start-time", time);
    }

}
