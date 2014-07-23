package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Map;

@Log4j2
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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ServerType && get_id().equals(((ServerType) obj).get_id());
    }

    @Override
    public int hashCode() {
        return get_id().hashCode();
    }
}
