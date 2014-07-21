package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.UUID;

public class Player extends Entity {

    @Getter
    @Setter
    private Server currentServer;

    @Getter
    @Setter
    private String playerName;

    @Getter
    @Setter
    private UUID uuid;

}
