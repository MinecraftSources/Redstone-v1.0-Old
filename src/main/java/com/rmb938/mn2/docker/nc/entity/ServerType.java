package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Map;

public class ServerType extends Entity {

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private int players;

    @Getter
    @Setter
    private int memory;

    @Getter
    @Setter
    private int amount;

    @Getter
    @Setter
    private World defaultWorld;

    @Getter
    private ArrayList<Map.Entry<Plugin, PluginConfig>> plugins = new ArrayList<>();

    @Getter
    private ArrayList<World> worlds = new ArrayList<>();
}
