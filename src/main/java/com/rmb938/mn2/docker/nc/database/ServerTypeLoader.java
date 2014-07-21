package com.rmb938.mn2.docker.nc.database;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.rmb938.mn2.docker.db.aerospike.ASNamespace;
import com.rmb938.mn2.docker.db.aerospike.ASSet;
import com.rmb938.mn2.docker.nc.entity.Plugin;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import com.rmb938.mn2.docker.nc.entity.World;
import lombok.extern.log4j.Log4j2;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Log4j2
public class ServerTypeLoader extends EntityLoader<ServerType> {

    private final PluginLoader pluginLoader;
    private final WorldLoader worldLoader;

    public ServerTypeLoader(ASNamespace namespace) {
        super(namespace.registerSet(new ASSet(namespace, "mn2_server_type")));
        pluginLoader = new PluginLoader(namespace);
        worldLoader = new WorldLoader(namespace);
    }

    @Override
    public ServerType loadEntity(UUID uuid) {
        try {
            Map.Entry<Key, Record> serverTypeEntry = getSet().getRecord(uuid.toString());

            if (serverTypeEntry != null) {
                ServerType serverType = new ServerType();

                serverType.setUuid(uuid);
                serverType.setName((String) serverTypeEntry.getValue().getValue("name"));
                serverType.setPlayers((Integer)serverTypeEntry.getValue().getValue("players"));
                serverType.setMemory((Integer)serverTypeEntry.getValue().getValue("memory"));
                serverType.setAmount((Integer)serverTypeEntry.getValue().getValue("amount"));

                Map<?, ?> plugins = (Map<?, ?>) serverTypeEntry.getValue().getValue("plugins");
                for (Object obj : plugins.keySet()) {
                    String pluginUUID = (String) obj;
                    String configUUID = (String) plugins.get(obj);
                    Plugin plugin = pluginLoader.loadEntity(UUID.fromString(pluginUUID));
                    if (plugin != null) {
                        if (plugin.getConfigs().containsKey(UUID.fromString(configUUID))) {
                            serverType.getPlugins().add(new AbstractMap.SimpleEntry<>(plugin, plugin.getConfigs().get(UUID.fromString(configUUID))));
                        } else {
                            log.error("Error loading "+serverType.getName()+" a plugin config is null!");
                            return null;
                        }
                    } else {
                        log.error("Error loading "+serverType.getName()+" a plugin is null!");
                        return null;
                    }
                }

                List<?> worlds = (List<?>) serverTypeEntry.getValue().getValue("worlds");
                for (Object obj : worlds) {
                    String worldUUID = (String) obj;
                    World world = worldLoader.loadEntity(UUID.fromString(worldUUID));
                    if (world != null) {
                        serverType.getWorlds().add(world);
                    } else {
                        log.error("Error loading "+serverType.getName()+" a world is null!");
                        return null;
                    }
                }

                World defaultWorld = worldLoader.loadEntity(UUID.fromString((String)serverTypeEntry.getValue().getValue("defaultWorld")));
                if (defaultWorld != null) {
                    serverType.setDefaultWorld(defaultWorld);
                } else {
                    log.error("Error loading "+serverType.getName()+" default world is null!");
                    return null;
                }

                return serverType;
            }
        } catch (AerospikeException e) {
            e.printStackTrace();
        }
        log.info("Unknown Server Type "+uuid.toString());
        return null;
    }

    @Override
    public void saveEntity(ServerType entity) {

    }
}
