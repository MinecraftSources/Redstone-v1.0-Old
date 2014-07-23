package com.rmb938.mn2.docker.nc.database;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.nc.entity.World;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

@Log4j2
public class WorldLoader extends EntityLoader<World> {

    public WorldLoader(MongoDatabase db) {
        super(db, "worlds");
    }

    @Override
    public World loadEntity(ObjectId _id) {
        if (_id == null) {
            log.error("Error loading world. _id null");
            return null;
        }
        DBObject dbObject = getDb().findOne(getCollection(), new BasicDBObject("_id", _id));
        if (dbObject != null) {
            World world = new World();
            world.set_id(_id);
            world.setName((String) dbObject.get("name"));
            world.setFolder((String) dbObject.get("folder"));
            try {
                world.setEnvironment(World.Environment.valueOf((String) dbObject.get("environment")));
            } catch (Exception ex) {
                log.error("Invalid environment for world "+world.getName());
                return null;
            }
            world.setGenerator((String)dbObject.get("generator"));

            //log.info("Loaded World "+world.getName());
            return world;
        }
        log.info("Unknown World "+_id.toString());
        return null;
    }

    @Override
    public void saveEntity(World entity) {

    }
}
