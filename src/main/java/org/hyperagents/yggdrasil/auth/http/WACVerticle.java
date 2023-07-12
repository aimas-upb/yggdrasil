package org.hyperagents.yggdrasil.auth.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class WACVerticle extends AbstractVerticle {
    public static final String BUS_ADDRESS = "org.hyperagents.yggdrasil.eventbus.wac";

    public static final String GET_WAC_RESOURCE = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".getWacResource";
    public static final String ADD_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".addAuthorization";
    public static final String REMOVE_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".removeAuthorization";
    public static final String VALIDATE_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".validateAuthorization";

    private static final Logger LOGGER = LoggerFactory.getLogger(WACVerticle.class.getName());
        
    @Override
    public void start() {
        //TODO add the code to start the verticle
    }
}
