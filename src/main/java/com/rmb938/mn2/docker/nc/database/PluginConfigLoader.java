package com.rmb938.mn2.docker.nc.database;

import com.rmb938.mn2.docker.db.aerospike.ASNamespace;
import com.rmb938.mn2.docker.db.aerospike.ASSet;
import com.rmb938.mn2.docker.nc.entity.PluginConfig;

import java.util.UUID;

public class PluginConfigLoader extends EntityLoader<PluginConfig> {

    public PluginConfigLoader(ASNamespace namespace) {
        super(namespace.registerSet(new ASSet(namespace, "mn2_plugin_configs")));
    }

    @Override
    public PluginConfig loadEntity(UUID uuid) {
        return null;
    }

    @Override
    public void saveEntity(PluginConfig entity) {

    }
}
