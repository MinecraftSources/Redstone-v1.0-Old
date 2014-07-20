package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

public class Plugin extends Entity {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private RunType type;

    @Getter
    @Setter
    private String git;

    @Getter
    @Setter
    private String folder;

    @Getter
    private ArrayList<PluginConfig> configs = new ArrayList<>();

}
