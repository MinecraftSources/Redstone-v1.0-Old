package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.Setter;

public class World extends Entity {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String git;

    @Getter
    @Setter
    private boolean persistent;

    @Getter
    @Setter
    private String environment;

    @Getter
    @Setter
    private String generator;

}
