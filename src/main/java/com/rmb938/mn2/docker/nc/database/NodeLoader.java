package com.rmb938.mn2.docker.nc.database;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.nc.entity.Node;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Log4j2
public class NodeLoader extends EntityLoader<Node> {

    public NodeLoader(MongoDatabase db) {
        super(db, "nodes");
    }

    public Node getMaster() {
        DBCursor dbCursor = getDb().findMany(getCollection(), new BasicDBObject("lastUpdate", new BasicDBObject("$gt", System.currentTimeMillis()-30000)));
        dbCursor = dbCursor.sort(new BasicDBObject("_id", 1));
        if (dbCursor.hasNext()) {
            DBObject dbObject = dbCursor.next();
            Node node = loadEntity((ObjectId)dbObject.get("_id"));
            dbCursor.close();
            return node;
        }
        return null;
    }

    @Override
    public Node loadEntity(ObjectId _id) {
        if (_id == null) {
            log.error("Error loading node. _id null");
            return null;
        }
        DBObject dbObject = getDb().findOne(getCollection(), new BasicDBObject("_id", _id));
        if (dbObject != null) {
            Node node = new Node();
            node.set_id(_id);
            try {
                node.setAddress(InetAddress.getByName((String) dbObject.get("host")));
            } catch (UnknownHostException e) {
                log.error("Error loading node. Unknown Inet Address");
                return null;
            }
            node.setLastUpdate((Long) dbObject.get("lastUpdate"));

            return node;
        }
        return null;
    }

    @Override
    public void saveEntity(Node entity) {

    }
}
