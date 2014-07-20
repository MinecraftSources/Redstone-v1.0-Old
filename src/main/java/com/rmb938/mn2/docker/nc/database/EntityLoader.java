package com.rmb938.mn2.docker.nc.database;

import com.rmb938.mn2.docker.db.aerospike.ASSet;
import com.rmb938.mn2.docker.nc.entity.Entity;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public abstract class EntityLoader<T extends Entity> {

    @Getter
    @NonNull
    private final ASSet set;

    public abstract T loadEntity(UUID uuid);

    public abstract void saveEntity(T entity);

}
