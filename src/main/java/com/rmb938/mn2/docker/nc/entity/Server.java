package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@RequiredArgsConstructor
public class Server extends Entity {

    @Getter
    private final ServerType serverType;

    @Getter
    private ArrayList<Player> players = new ArrayList<>();

}
