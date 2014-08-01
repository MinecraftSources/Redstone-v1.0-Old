package com.rmb938.mn2.docker.nc.database;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.nc.entity.Node;
import com.rmb938.mn2.docker.nc.entity.Server;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

@Log4j2
public class ServerLoader extends EntityLoader<Server> {

    private final NodeLoader nodeLoader;
    private final ServerTypeLoader serverTypeLoader;

    public ServerLoader(MongoDatabase db, NodeLoader nodeLoader, ServerTypeLoader serverTypeLoader) {
        super(db, "servers");
        this.nodeLoader = nodeLoader;
        this.serverTypeLoader = serverTypeLoader;
    }

    public Long getCount(ServerType serverType) {
        return getDb().count(getCollection(), new BasicDBObject("_servertype", serverType.get_id()));
    }

    @Override
    public Server loadEntity(ObjectId _id) {
        if (_id == null) {
            log.error("Error loading world. _id null");
            return null;
        }
        DBObject dbObject = getDb().findOne(getCollection(), new BasicDBObject("_id", _id));
        if (dbObject != null) {
            Server server = new Server();
            server.set_id((ObjectId)dbObject.get("_id"));
            server.setServerType(serverTypeLoader.loadEntity((ObjectId)dbObject.get("_servertype")));
            server.setNode(nodeLoader.loadEntity((ObjectId)dbObject.get("_node")));
            server.setContainerId((String)dbObject.get("containerId"));
            server.setLastUpdate((Long)dbObject.get("lastUpdate"));

            BasicDBList players = (BasicDBList) dbObject.get("players");
            if (players != null) {
                for (Object object : players) {
                    String uuid = (String) object;
                    //load player entity and add
                }
            }

            return server;
        }
        return null;
    }

    @Override
    public void saveEntity(Server server) {
        BasicDBObject values = new BasicDBObject();
        values.put("lastUpdate", server.getLastUpdate());
        values.put("containerId", server.getContainerId());

        BasicDBObject set = new BasicDBObject("$set", values);
        getDb().updateDocument(getCollection(), new BasicDBObject("_id", server.get_id()), set);
        log.info("Saving Server "+server.get_id());
    }

    @Override
    public ObjectId insertEntity(Server server) {
        BasicDBObject dbObject = new BasicDBObject("_id", new ObjectId());
        dbObject.append("_servertype", server.getServerType().get_id());
        dbObject.append("_node", server.getNode().get_id());
        dbObject.append("lastUpdate", 0L);
        dbObject.append("containerId", "NULL");
        getDb().insert(getCollection(), dbObject);
        return (ObjectId) dbObject.get("_id");
    }
}
