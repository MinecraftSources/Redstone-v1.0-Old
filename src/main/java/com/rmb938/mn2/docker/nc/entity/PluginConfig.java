package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.Setter;

public class PluginConfig extends Entity {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String location;
}
