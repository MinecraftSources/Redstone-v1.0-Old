package com.rmb938.mn2.docker.nc;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.model.Bind;
import com.github.dockerjava.client.model.ContainerCreateResponse;
import com.github.dockerjava.client.model.Volume;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.rmb938.mn2.docker.db.database.BungeeLoader;
import com.rmb938.mn2.docker.db.database.BungeeTypeLoader;
import com.rmb938.mn2.docker.db.entity.MN2Bungee;
import com.rmb938.mn2.docker.db.entity.MN2BungeeType;
import com.rmb938.mn2.docker.db.entity.MN2Node;
import com.rmb938.mn2.docker.db.entity.MN2Server;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

@Log4j2
public class BungeeLoop implements Runnable {

    private final MN2Node node;
    private final BungeeTypeLoader bungeeTypeLoader;
    private final BungeeLoader bungeeLoader;

    public BungeeLoop(MN2Node node, BungeeTypeLoader bungeeTypeLoader, BungeeLoader bungeeLoader) {
        this.node = node;
        this.bungeeTypeLoader = bungeeTypeLoader;
        this.bungeeLoader = bungeeLoader;
    }

    @Override
    public void run() {
        while (true) {
            BasicDBList and = new BasicDBList();
            and.add(new BasicDBObject("_node", node.get_id()));
            and.add(new BasicDBObject("lastUpdate", new BasicDBObject("$lt", System.currentTimeMillis()-60000)));
            DBCursor dbCursor = bungeeLoader.getDb().findMany(bungeeLoader.getCollection(), and);
            while (dbCursor.hasNext()) {
                DBObject dbObject = dbCursor.next();
                MN2Bungee bungee = bungeeLoader.loadEntity((ObjectId) dbObject.get("_id"));
                if (bungee != null) {
                    if (bungee.getNode() != null) {
                        DockerClient dockerClient = new DockerClient("http://" + bungee.getNode().getAddress() + ":4243");

                        try {
                            log.info("Killing dead server " + bungee.getBungeeType().getName());
                            dockerClient.killContainerCmd(bungee.getContainerId()).exec();
                        } catch (Exception ex) {
                            log.error("Error killing dead bungee "+ex.getMessage());
                            continue;
                        }
                        try {
                            log.info("Remove dead server container " + bungee.getBungeeType().getName());
                            dockerClient.removeContainerCmd(bungee.getContainerId()).exec();
                        } catch (Exception ex) {
                            log.error("Error removing dead bungee "+ex.getMessage());
                            continue;
                        }
                    }

                    log.info("Removing dead bungee " + bungee.getBungeeType().getName());
                    bungeeLoader.removeEntity(bungee);
                }
            }
            dbCursor.close();

            for (MN2BungeeType bungeeType : bungeeTypeLoader.getTypes(node)) {
                try {
                    if (bungeeLoader.nodeBungeeType(node, bungeeType) == null) {
                        MN2Bungee bungee = new MN2Bungee();
                        bungee.setNode(node);
                        bungee.setBungeeType(bungeeType);
                        bungee.setLastUpdate(System.currentTimeMillis() + 300000);

                        bungeeLoader.saveEntity(bungee);

                        DockerClient dockerClient = new DockerClient("http://"+node.getAddress()+":4243");
                        ContainerCreateResponse response;
                        try {
                            log.info("Creating container for "+bungeeType.getName());
                            response = dockerClient.createContainerCmd("mnsquared/bungee")
                                    .withEnv("MONGO_HOSTS=" + System.getenv("MONGO_HOSTS"),
                                            "RABBITMQ_HOSTS=" + System.getenv("RABBITMQ_HOSTS"),
                                            "RABBITMQ_USERNAME=" + System.getenv("RABBITMQ_USERNAME"),
                                            "RABBITMQ_PASSWORD=" + System.getenv("RABBITMQ_PASSWORD"),
                                            "RACKSPACE_USERNAME=" + System.getenv("RACKSPACE_USERNAME"),
                                            "RACKSPACE_API=" + System.getenv("RACKSPACE_API"),
                                            "MY_BUNGEE_ID=" + bungee.get_id().toString())
                                    .withName(bungeeType.getName())
                                    .exec();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            log.error("Unable to create container for bungee " + bungeeType.getName());
                            continue;
                        }

                        if (response == null) {
                            log.error("Null docker response");
                            continue;
                        }

                        String containerId = response.getId();
                        bungee.setContainerId(containerId);

                        bungeeLoader.saveEntity(bungee);

                        try {
                            log.info("Starting container for "+bungeeType.getName());
                            dockerClient.startContainerCmd(containerId).withPublishAllPorts(true).withBinds(new Bind("/mnt/cloudfiles", new Volume("/mnt/cloudfiles"))).exec();
                        } catch (Exception ex) {
                            log.error("Unable to start container for bungee " + bungeeType.getName());
                            continue;
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.info("Stopping Tick");
                break;
            }
        }
    }
}
