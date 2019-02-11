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

import eu.europa.ec.fisheries.schema.config.module.v1.PullSettingsResponse;
import eu.europa.ec.fisheries.schema.config.module.v1.PushSettingsResponse;
import eu.europa.ec.fisheries.schema.config.module.v1.SettingEventType;
import eu.europa.ec.fisheries.schema.config.types.v1.PullSettingsStatus;
import eu.europa.ec.fisheries.schema.config.types.v1.SettingType;
import eu.europa.ec.fisheries.uvms.config.constants.ConfigHelper;
import eu.europa.ec.fisheries.uvms.config.event.ConfigSettingEvent;
import eu.europa.ec.fisheries.uvms.config.event.ConfigSettingEventType;
import eu.europa.ec.fisheries.uvms.config.event.ConfigSettingUpdatedEvent;
import eu.europa.ec.fisheries.uvms.config.exception.ConfigMessageException;
import eu.europa.ec.fisheries.uvms.config.exception.ConfigServiceException;
import eu.europa.ec.fisheries.uvms.config.message.ConfigMessageConsumer;
import eu.europa.ec.fisheries.uvms.config.message.ConfigMessageProducer;
import eu.europa.ec.fisheries.uvms.config.model.exception.ModelMapperException;
import eu.europa.ec.fisheries.uvms.config.model.exception.ModelMarshallException;
import eu.europa.ec.fisheries.uvms.config.model.mapper.JAXBMarshaller;
import eu.europa.ec.fisheries.uvms.config.model.mapper.ModuleRequestMapper;
import eu.europa.ec.fisheries.uvms.config.model.mapper.ModuleResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;

@Stateless
public class UVMSConfigServiceBean implements UVMSConfigService {

    private final static Logger LOG = LoggerFactory.getLogger(UVMSConfigServiceBean.class);

    @EJB
    private ParameterService parameterService;
    
    @EJB
    private ConfigHelper configHelper;
    
    @EJB
    private ConfigMessageProducer producer;
    
    @EJB
    private ConfigMessageConsumer consumer;

    @Inject
    @ConfigSettingUpdatedEvent
    private Event<ConfigSettingEvent> settingUpdated;

    @Override
    public void syncSettingsWithConfig() throws ConfigServiceException {
        try {
            boolean pullSuccess = pullSettingsFromConfig();
            if (!pullSuccess) {
                boolean pushSuccess = pushSettingsToConfig();
                if (!pushSuccess) {
                    throw new ConfigMessageException("Failed to push missing settings to Config.");
                }
            }
        } catch (ConfigMessageException | ModelMarshallException e) {
            LOG.error("[ Error when synchronizing settings with Config module. ] {}", e.getMessage());
            throw new ConfigServiceException(e.getMessage());
        }
    }

	@Override
	public void updateSetting(SettingType setting, SettingEventType eventType) throws ConfigServiceException {
		ConfigSettingEventType configSettingEventType = ConfigSettingEventType.UPDATE;
		try {
	    	if (eventType == SettingEventType.SET) {
				parameterService.setStringValue(setting.getKey(), setting.getValue(), setting.getDescription());
            } else if (eventType == SettingEventType.RESET) {
				configSettingEventType = ConfigSettingEventType.DELETE;
				parameterService.reset(setting.getKey());
			} else {
				throw new ConfigServiceException("SettingEventType " + eventType + " not implemented");
			}
        } catch (Exception e) {
			LOG.error("[ Error when updating setting. ] {} SETTING : {}", e.getMessage(), setting);
			throw new ConfigServiceException(e.getMessage());
		}
		settingUpdated.fire(new ConfigSettingEvent(configSettingEventType, setting.getKey()));
	}

    /**
     * @return true if settings were pulled successful, or false if they are
     * missing in the Config module
     * @throws ModelMarshallException 
     */
    private boolean pullSettingsFromConfig() throws ModelMarshallException, ConfigMessageException, ConfigServiceException {
        String request = ModuleRequestMapper.toPullSettingsRequest(configHelper.getModuleName());
        TextMessage response = sendSyncronousMsgWithResponseToConfig(request);
        PullSettingsResponse pullResponse = JAXBMarshaller.unmarshallTextMessage(response, PullSettingsResponse.class);
        if (pullResponse.getStatus() == PullSettingsStatus.MISSING) {
            return false;
        }
        storeSettings(pullResponse.getSettings());
        return true;
    }

    @Override
	public boolean pushSettingToConfig(SettingType setting, boolean remove) {
    	try {
    		String request;
    		if(!remove) {
    			request = ModuleRequestMapper.toSetSettingRequest(configHelper.getModuleName(), setting, "UVMS");
    		} else {
    			setting.setModule(configHelper.getModuleName());
    			request = ModuleRequestMapper.toResetSettingRequest(setting);
    		}
    		sendSyncronousMsgWithResponseToConfig(request);
    		return true;
        } catch (ModelMarshallException | ConfigMessageException e) {
        	return false;
        }
	}

    @Override
    public List<SettingType> getSettings(String keyPrefix) throws ConfigServiceException {
        try {
            String request = ModuleRequestMapper.toListSettingsRequest(configHelper.getModuleName());
            TextMessage response = sendSyncronousMsgWithResponseToConfig(request);
            List<SettingType> settings = ModuleResponseMapper.getSettingsFromSettingsListResponse(response);
            if (keyPrefix != null) {
                settings = getSettingsWithKeyPrefix(settings, keyPrefix);
            }
            return settings;
        } catch (ConfigMessageException | ModelMapperException | JMSException e) {
            LOG.error("[ Error when getting settings with key prefix. ] {}", e.getMessage());
            throw new ConfigServiceException("[ Error when getting settings with key prefix. ]");
        }
    }

    private TextMessage sendSyncronousMsgWithResponseToConfig(String request) throws ConfigMessageException {
        String messageId = producer.sendConfigMessage(request);
        return consumer.getConfigMessage(messageId, TextMessage.class);
    }

    /**
     * @return true if settings were pushed successfully
     * @throws ModelMarshallException
     * @throws ConfigMessageException 
     */
    private boolean pushSettingsToConfig() throws ConfigServiceException, ModelMarshallException, ConfigMessageException {
        String moduleName = configHelper.getModuleName();
        List<SettingType> moduleSettings = parameterService.getSettings(configHelper.getAllParameterKeys());
        String request = ModuleRequestMapper.toPushSettingsRequest(moduleName, moduleSettings, "UVMS");
        TextMessage response = sendSyncronousMsgWithResponseToConfig(request);
        PushSettingsResponse pushResponse = JAXBMarshaller.unmarshallTextMessage(response, PushSettingsResponse.class);
        if (pushResponse.getStatus() != PullSettingsStatus.OK) {
            return false;
        }
        storeSettings(pushResponse.getSettings());
        return true;
    }

    private void storeSettings(List<SettingType> settings) throws ConfigServiceException {
        parameterService.clearAll();
        for (SettingType setting: settings) {
            try {
                parameterService.setStringValue(setting.getKey(), setting.getValue(), setting.getDescription());
            } catch (Exception e) {
                LOG.error("[ Error when storing setting. ]", e);
            }
        }
        settingUpdated.fire(new ConfigSettingEvent(ConfigSettingEventType.STORE));
    }

    @Override
    public void sendPing() throws ConfigServiceException {
        try {
            producer.sendConfigMessage(ModuleRequestMapper.toPingRequest(configHelper.getModuleName()));
        } catch (ConfigMessageException | ModelMapperException e) {
            LOG.error("[ Error when sending ping to config. ] {}", e.getMessage());
            throw new ConfigServiceException(e.getMessage());
        }
    }

    private List<SettingType> getSettingsWithKeyPrefix(List<SettingType> settings, String keyPrefix) {
        List<SettingType> filteredSettings = new ArrayList<>();
        for (SettingType setting : settings) {
            if (setting.getKey().startsWith(keyPrefix)) {
                filteredSettings.add(setting);
            }
        }
        return filteredSettings;
    }

}
