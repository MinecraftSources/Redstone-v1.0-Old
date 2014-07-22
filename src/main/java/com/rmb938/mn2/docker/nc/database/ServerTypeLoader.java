package com.rmb938.mn2.docker.nc.database;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.nc.entity.Plugin;
import com.rmb938.mn2.docker.nc.entity.PluginConfig;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import com.rmb938.mn2.docker.nc.entity.World;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

import java.util.ArrayList;

@Log4j2
public class ServerTypeLoader extends EntityLoader<ServerType> {

    private final PluginLoader pluginLoader;
    private final WorldLoader worldLoader;

    public ServerTypeLoader(MongoDatabase db) {
        super(db, "ServerType");
        pluginLoader = new PluginLoader(db);
        worldLoader = new WorldLoader(db);
    }

    public ArrayList<ServerType> getTypes() {
        ArrayList<ServerType> types = new ArrayList<>();
        DBCursor dbCursor = getDb().findMany(getCollection());
        while (dbCursor.hasNext()) {
            ServerType type = loadEntity((ObjectId)dbCursor.next().get("_id"));
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    @Override
    public ServerType loadEntity(ObjectId _id) {
        DBObject dbObject = getDb().findOne(getCollection(), new BasicDBObject("_id", _id));
        if (dbObject != null) {
            ServerType serverType = new ServerType();
            serverType.set_id(_id);
            serverType.setName((String)dbObject.get("name"));
            serverType.setAmount((Integer)dbObject.get("amount"));
            serverType.setMemory((Integer)dbObject.get("memory"));
            serverType.setPlayers((Integer)dbObject.get("players"));

            BasicDBList plugins = (BasicDBList) dbObject.get("plugins");
            for (Object obj : plugins) {
                DBObject dbObj = (DBObject) obj;
                ObjectId _pluginId = (ObjectId) dbObj.get("_id");
                Plugin plugin = pluginLoader.loadEntity(_pluginId);
                if (plugin == null) {
                    log.error("Error loading plugin for server "+serverType.getName());
                    return null;
                }

                if (plugin.getType() != Plugin.PluginType.BUKKIT) {
                    log.error("Trying to add Non-Bukkit plugin "+plugin.getName()+" to server "+serverType.getName());
                    return null;
                }

                ObjectId _configId = (ObjectId) dbObj.get("_configId");
                PluginConfig pluginConfig = plugin.getConfigs().get(_configId);
                if (pluginConfig == null) {
                    log.error("Plugin config "+_configId+" does not exist for plugin "+plugin.getName());
                    return null;
                }
            }

            BasicDBList worlds = (BasicDBList) dbObject.get("worlds");
            for (Object obj : worlds) {
                DBObject dbObj = (DBObject) obj;
                ObjectId _worldId = (ObjectId) dbObj.get("_worldId");
                World world = worldLoader.loadEntity(_worldId);
                if (world == null) {
                    log.error("Error loading world for server "+serverType.getName());
                    return null;
                }
                serverType.getWorlds().add(world);
            }

            ObjectId _defaultWorldId = (ObjectId) dbObject.get("_defaultWorldId");
            World world = worldLoader.loadEntity(_defaultWorldId);
            if (world == null) {
                log.error("Error loading default world for server "+serverType.getName());
                return null;
            }

            if (world.getEnvironment() != World.Environment.NORMAL) {
                log.error("Default world has to be normal environment");
                return null;
            }

            serverType.setDefaultWorld(world);

            return serverType;
        }
        log.info("Unknown Server Type "+_id.toString());
        return null;
    }

    @Override
    public void saveEntity(ServerType entity) {

    }
}
