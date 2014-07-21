package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.UUID;

public abstract class Entity {

    @Getter
    @Setter
    private ObjectId uuid;

}
