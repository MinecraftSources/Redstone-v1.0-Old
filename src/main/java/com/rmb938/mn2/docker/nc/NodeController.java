package com.rmb938.mn2.docker.nc;

import com.aerospike.client.Host;
import com.rmb938.mn2.docker.db.aerospike.ASNamespace;
import com.rmb938.mn2.docker.db.aerospike.AerospikeDatabase;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;

@Log4j2
public class NodeController {

    public static void main(String[] args) {
        new NodeController();
    }

    private AerospikeDatabase aerospikeDatabase;

    public NodeController() {
        log.info("Started Node Controller");

        String aerospikeHosts = System.getenv("AERO_HOSTS");
        if (aerospikeHosts != null) {
            ArrayList<Host> hosts = new ArrayList<>();

            for (String host : aerospikeHosts.split(",")) {
                String[] split = host.split(":");
                hosts.add(new Host(split[0], Integer.parseInt(split[1])));
            }

            aerospikeDatabase = new AerospikeDatabase(hosts.toArray(new Host[hosts.size()]));
        } else {
            log.error("The AERO_HOSTS environment variable must be set");
            return;
        }

        ASNamespace namespace = aerospikeDatabase.registerNamespace(new ASNamespace(aerospikeDatabase, "ssd"));

        System.getenv("RABBITMQ_HOSTS");
        System.getenv("RABBITMQ_USERNAME");
        System.getenv("RABBITMQ_PASSWORD");

    }

}
