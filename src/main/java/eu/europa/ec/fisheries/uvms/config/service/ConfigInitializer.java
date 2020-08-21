/*
﻿Developed with the contribution of the European Commission - Directorate General for Maritime Affairs and Fisheries
© European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or any later version. The IFDM Suite is distributed in
the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. You should have received a
copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.europa.ec.fisheries.uvms.config.service;

import eu.europa.ec.fisheries.uvms.config.exception.ConfigServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
@Startup
public class ConfigInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigInitializer.class);

    @EJB
    private UVMSConfigService configService;

    @EJB
    private PingTimer pinger;

    @PostConstruct
    protected void startup() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.schedule(() -> {
            try {
                LOG.info("[CONFIG-STARTUP] Starting up config settings sync!");
                configService.syncSettingsWithConfig();
                LOG.info("[SYNC-END] Finished synching settings!");
                pinger.schedulePinger();
            } catch (ConfigServiceException e) {
                LOG.error("[ERROR] Error when sending ping to Config", e);
            }
        }, 20, TimeUnit.SECONDS);
    }

}