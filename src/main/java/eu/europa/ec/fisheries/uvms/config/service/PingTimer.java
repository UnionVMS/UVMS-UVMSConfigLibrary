package eu.europa.ec.fisheries.uvms.config.service;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.fisheries.uvms.config.exception.ConfigServiceException;

@Singleton
@Startup
public class PingTimer {

    /**
     * https://blogs.oracle.com/WebLogicServer/entry/concurrency_utilities_support_in_weblogic2
     * https://community.oracle.com/thread/3602407?start=0&tstart=0 => NO
     * support for Managed ExecutorService in WebLogic 12.1.3
     */
    final static Logger LOG = LoggerFactory.getLogger(PingTimer.class);

    @Inject
    UVMSConfigService configService;

    @Schedule(second = "0", minute = "*/5", hour = "*", persistent = false)
    public void sendPing() {
        try {
            LOG.info("Ping time arrived!");
            configService.sendPing();
        } catch (ConfigServiceException e) {
            LOG.error("[ Error when sending ping to Config. ] {}", e.getMessage());
        }
    }

    //@Resource(mappedName = "uvmsConfigPingExecutorService")
    //private ManagedScheduledExecutorService executorService;
    //@PostConstruct
    //public void sendPing() {
    //    LOG.info("PingTimer init");
    //PingTask checkCommunicationTask = new PingTask(configService);
    //executorService.scheduleWithFixedDelay(checkCommunicationTask, 0, 5, TimeUnit.MINUTES);
    //}
}
