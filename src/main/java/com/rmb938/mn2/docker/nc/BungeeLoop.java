package com.rmb938.mn2.docker.nc;

import com.rmb938.mn2.docker.db.database.BungeeLoader;
import com.rmb938.mn2.docker.db.database.BungeeTypeLoader;

public class BungeeLoop implements Runnable {

    private final BungeeTypeLoader bungeeTypeLoader;
    private final BungeeLoader bungeeLoader;

    public BungeeLoop(BungeeTypeLoader bungeeTypeLoader, BungeeLoader bungeeLoader) {
        this.bungeeTypeLoader = bungeeTypeLoader;
        this.bungeeLoader = bungeeLoader;
    }

    @Override
    public void run() {

    }
}
