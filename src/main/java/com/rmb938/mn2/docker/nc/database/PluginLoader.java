package com.rmb938.mn2.docker.nc.database;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.rmb938.mn2.docker.db.aerospike.ASNamespace;
import com.rmb938.mn2.docker.db.aerospike.ASSet;
import com.rmb938.mn2.docker.nc.entity.Plugin;
import com.rmb938.mn2.docker.nc.entity.PluginConfig;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.UUID;

@Log4j2
public class PluginLoader extends EntityLoader<Plugin> {

    public PluginLoader(ASNamespace namespace) {
        super(namespace.registerSet(new ASSet(namespace, "mn2_plugin")));
    }

    @Override
    public Plugin loadEntity(UUID uuid) {
        try {
            Map.Entry<Key, Record> pluginEntry = getSet().getRecord(uuid.toString());

            if (pluginEntry != null) {
                Plugin plugin = new Plugin();

                plugin.setUuid(uuid);
                plugin.setName((String) pluginEntry.getValue().getValue("name"));
                plugin.setBaseFolder((String)pluginEntry.getValue().getValue("baseFolder"));
                plugin.setFolder((String)pluginEntry.getValue().getValue("folder"));
                try {
                    plugin.setType(Plugin.PluginType.valueOf((String) pluginEntry.getValue().getValue("type")));
                } catch (Exception ex) {
                    log.error("Error loading plugin "+plugin.getName()+" invalid plugin type!");
                    return null;
                }

                Map<?, ?> configList = (Map<?, ?>) pluginEntry.getValue().getValue("configs");
                for (Object obj : configList.keySet()) {
                    UUID configUUID = UUID.fromString((String)obj);
                    Map<?, ?> configInfo = (Map<?, ?>) configList.get(obj);
                    String configName = (String) configInfo.get("name");
                    String configLocation = (String) configInfo.get("location");
                    PluginConfig pluginConfig = new PluginConfig();
                    pluginConfig.setUuid(configUUID);
                    pluginConfig.setName(configName);
                    pluginConfig.setLocation(configLocation);
                    plugin.getConfigs().put(configUUID, pluginConfig);
                }

                return plugin;
            }
        } catch (AerospikeException e) {
            e.printStackTrace();
        }
        log.info("Unknown Plugin "+uuid.toString());
        return null;
    }

    @Override
    public void saveEntity(Plugin plugin) {

    }
}
