package com.rmb938.mn2.docker.nc.database;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.rmb938.mn2.docker.db.aerospike.ASNamespace;
import com.rmb938.mn2.docker.db.aerospike.ASSet;
import com.rmb938.mn2.docker.nc.entity.Plugin;
import com.rmb938.mn2.docker.nc.entity.PluginConfig;
import com.rmb938.mn2.docker.nc.entity.RunType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PluginLoader extends EntityLoader<Plugin> {

    private final PluginConfigLoader configLoader;

    public PluginLoader(ASNamespace namespace) {
        super(namespace.registerSet(new ASSet(namespace, "mn2_plugin")));
        configLoader = new PluginConfigLoader(namespace);
    }

    @Override
    public Plugin loadEntity(UUID uuid) {
        try {
            Map.Entry<Key, Record> pluginEntry = getSet().getRecord(uuid.toString());

            if (pluginEntry != null) {
                Plugin plugin = new Plugin();

                plugin.setUuid(uuid);
                plugin.setName((String) pluginEntry.getValue().bins.get("name"));
                plugin.setGit((String)pluginEntry.getValue().bins.get("git"));
                plugin.setFolder((String)pluginEntry.getValue().bins.get("folder"));
                plugin.setType(RunType.valueOf((String)pluginEntry.getValue().bins.get("type")));

                List<?> pluginList = (List<?>) pluginEntry.getValue().getValue("configs");
                for (Object object : pluginList) {
                    UUID configUUID = UUID.fromString((String)object);
                    PluginConfig pluginConfig = configLoader.loadEntity(configUUID);
                    plugin.getConfigs().add(pluginConfig);
                }

                return plugin;
            }
        } catch (AerospikeException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void saveEntity(Plugin plugin) {

    }
}
