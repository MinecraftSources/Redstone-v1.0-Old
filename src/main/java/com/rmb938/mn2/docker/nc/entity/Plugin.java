package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.UUID;

public class Plugin extends Entity {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private PluginType type;

    @Getter
    @Setter
    private String baseFolder;

    @Getter
    @Setter
    private String configFolder;

    @Getter
    private HashMap<ObjectId, PluginConfig> configs = new HashMap<>();

    public enum PluginType {

        BUKKIT,
        BUNGEE

    }

}
