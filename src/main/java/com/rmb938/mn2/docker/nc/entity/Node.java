package com.rmb938.mn2.docker.nc.entity;

import lombok.Getter;
import lombok.Setter;

import java.net.InetAddress;

public class Node extends Entity {

    @Getter
    @Setter
    private String address;

    @Getter
    @Setter
    private long lastUpdate = 0L;

}
