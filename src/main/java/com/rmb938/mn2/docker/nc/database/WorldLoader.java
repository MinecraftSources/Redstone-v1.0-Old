package com.rmb938.mn2.docker.nc.database;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.rmb938.mn2.docker.db.aerospike.ASNamespace;
import com.rmb938.mn2.docker.db.aerospike.ASSet;
import com.rmb938.mn2.docker.nc.entity.World;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.UUID;

@Log4j2
public class WorldLoader extends EntityLoader<World> {

    public WorldLoader(ASNamespace namespace) {
        super(namespace.registerSet(new ASSet(namespace, "mn2_world")));
    }

    @Override
    public World loadEntity(UUID uuid) {
        try {
            Map.Entry<Key, Record> worldEntry = getSet().getRecord(uuid.toString());

            if (worldEntry != null) {
                World world = new World();

                world.setUuid(uuid);
                world.setName((String)worldEntry.getValue().getValue("name"));
                world.setFolder((String)worldEntry.getValue().getValue("folder"));
                world.setEnvironment((String)worldEntry.getValue().getValue("environment"));
                world.setGenerator((String)worldEntry.getValue().getValue("generator"));
                world.setPersistent((Boolean)worldEntry.getValue().getValue("persistent"));

                return world;
            }
        } catch (AerospikeException e) {
            e.printStackTrace();
        }
        log.info("Unknown World "+uuid.toString());
        return null;
    }

    @Override
    public void saveEntity(World entity) {

    }
}
