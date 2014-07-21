package com.rmb938.mn2.docker.nc.database;

import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.nc.entity.Entity;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;

import java.util.UUID;

public abstract class EntityLoader<T extends Entity> {

    @Getter
    private final MongoDatabase db;

    @Getter
    private final String collection;

    protected EntityLoader(MongoDatabase db, String collection) {
        this.db = db;
        this.collection = collection;
        db.createCollection(collection);
    }

    public abstract T loadEntity(ObjectId _id);

    public abstract void saveEntity(T entity);

}
