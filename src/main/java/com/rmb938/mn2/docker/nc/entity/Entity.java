package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public abstract class Entity {

    @Getter
    @Setter
    private UUID uuid;

}
