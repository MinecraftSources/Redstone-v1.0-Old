package com.rmb938.mn2.docker.nc.database;

import com.mongodb.BasicDBObject;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.nc.entity.Server;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import org.bson.types.ObjectId;

public class ServerLoader extends EntityLoader<Server> {

    public ServerLoader(MongoDatabase db) {
        super(db, "server");
    }

    public Long getCount(ServerType serverType) {
        return getDb().count(getCollection(), new BasicDBObject("serverType", serverType.get_id()));
    }

    @Override
    public Server loadEntity(ObjectId _id) {
        return null;
    }

    @Override
    public void saveEntity(Server entity) {

    }
}
