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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.fisheries.uvms.config.exception.ConfigServiceException;

import java.util.concurrent.TimeUnit;

@Singleton
@Startup
public class PingTimer {

    final static Logger LOG = LoggerFactory.getLogger(PingTimer.class);

    @Inject
    UVMSConfigService configService;

    @Resource(lookup="java:/uvmsConfigPingExecutorService")
    private ManagedScheduledExecutorService executorService;

    @PostConstruct
    public void sendPing() {
        LOG.info("PingTimer init");
        PingTask checkCommunicationTask = new PingTask(configService);
        executorService.scheduleWithFixedDelay(checkCommunicationTask, 0, 5, TimeUnit.MINUTES);
    }

}