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
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Stateless
@LocalBean
public class PingTimer {

    private static final Logger LOG = LoggerFactory.getLogger(PingTimer.class);

    @EJB
    private UVMSConfigService configService;

    public void schedulePinger() {
        LOG.info("[START] PingTimer init..");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(()-> {
            try {
                LOG.info("Ping time arrived!");
                configService.sendPing();
            } catch (ConfigServiceException e) {
                LOG.error("[ERROR] Error when sending ping to Config ", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
        LOG.info("[END] PingTimer.sendPing() scheduled each 5 minutes!");
    }

}