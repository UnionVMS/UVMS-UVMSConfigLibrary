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

import eu.europa.ec.fisheries.schema.config.types.v1.SettingType;
import eu.europa.ec.fisheries.uvms.config.constants.ConfigHelper;
import eu.europa.ec.fisheries.uvms.config.exception.ConfigServiceException;
import eu.europa.ec.fisheries.uvms.config.model.exception.InputArgumentException;
import eu.europa.ec.fisheries.uvms.config.service.entity.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

@Stateless
@Transactional
public class ParameterServiceBean implements ParameterService {

    private static final Logger LOG = LoggerFactory.getLogger(ParameterServiceBean.class);

    @EJB
    private ConfigHelper configHelper;

    @Override
    public String getStringValue(String key) throws ConfigServiceException {
        try {
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.FIND_BY_ID, Parameter.class);
            query.setParameter("id", key);
            return query.getSingleResult().getParamValue();
        } catch (RuntimeException e) {
            LOG.error("[ Error when getting String value ]");
            throw new ConfigServiceException("[ Error when getting String value. ]", e);
        }
    }

    @Override
    public boolean removeParameter(String key) throws ConfigServiceException {
        try {
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.FIND_BY_ID, Parameter.class);
            query.setParameter("id", key);
            Parameter parameter = query.getSingleResult();
            configHelper.getEntityManager().remove(parameter);
            configHelper.getEntityManager().flush();
            return true;
        } catch (RuntimeException e) {
            LOG.error("[ Error when remove parameter " + key + " ]");
            throw new ConfigServiceException("[ Error when remove parameter " + key + " ]", e);
        }
    }

    public List<SettingType> getSettings(List<String> keys) throws ConfigServiceException {
        try {
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.LIST_ALL_BY_IDS, Parameter.class);
            query.setParameter("ids", keys);
            List<SettingType> settings = new ArrayList<>();
            for (Parameter parameter : query.getResultList()) {
                SettingType setting = new SettingType();
                setting.setKey(parameter.getParamId());
                setting.setValue(parameter.getParamValue());
                setting.setDescription(parameter.getParamDescription());
                settings.add(setting);
            }
            return settings;
        } catch (RuntimeException e) {
            LOG.error("[ Error when getting settings by IDs ]");
            throw new ConfigServiceException("[ Error when getting settings by IDs. ]", e);
        }
    }

    @Override
    public List<SettingType> getAllSettings() throws ConfigServiceException {
        try {
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.LIST_ALL, Parameter.class);
            List<SettingType> settings = new ArrayList<>();
            for (Parameter parameter : query.getResultList()) {
                SettingType setting = new SettingType();
                setting.setKey(parameter.getParamId());
                setting.setValue(parameter.getParamValue());
                setting.setDescription(parameter.getParamDescription());
                settings.add(setting);
            }
            return settings;
        } catch (RuntimeException e) {
            LOG.error("[ Error when getting all settings. ]");
            throw new ConfigServiceException("[ Error when getting all settings. ]", e);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean setStringValue(String key, String value, String description) throws ConfigServiceException {
        try {
            LOG.info("[INFO] Get setting by key [{}]", key);
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.FIND_BY_ID, Parameter.class);
            query.setParameter("id", key);
            List<Parameter> parameters = query.getResultList();
            LOG.info("[END] Setting exists and ResultSet size is {}", parameters != null ? parameters.size() : 0);
            if (parameters != null && parameters.size() == 1) {
                // Update existing parameter
                LOG.info("[INFO] Update existing parameter..");
                parameters.get(0).setParamValue(value);
                LOG.info("[END] Parameter updated [ {} = {} ]..", key, value);
            } else {
                if (parameters != null && !parameters.isEmpty()) {
                    // Remove all parameters occurring more than once
                    removeParameters(parameters);
                }
                // Create new parameter
                LOG.info("[INFO] Creating new parameter {} = {}", key, value);
                Parameter parameter = new Parameter();
                parameter.setParamId(key);
                parameter.setParamDescription(description != null ? description : "-");
                parameter.setParamValue(value);
                configHelper.getEntityManager().persist(parameter);
                LOG.info("[END] New parameter created!");
            }
            return true;
        } catch (Exception e) {
            LOG.error("[ Error when setting String value. ] Key = {} : Value = {}, Descr = {}", key, value, description);
            throw new ConfigServiceException("[ Error when setting String value. ]", e);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    private void removeParameters(List<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            configHelper.getEntityManager().remove(parameter);
        }
    }

    @Override
    public Boolean getBooleanValue(String key) throws ConfigServiceException {
        try {
            return parseBooleanValue(getStringValue(key));
        } catch (Exception e) {
            LOG.error("[ Error when getting Boolean value. ]");
            throw new ConfigServiceException("[ Error when getting Boolean value. ]", e);
        }
    }

    @Override
    public void reset(String key) throws ConfigServiceException {
        List<Parameter> parameters;
        try {
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.FIND_BY_ID, Parameter.class);
            query.setParameter("id", key);
            parameters = query.getResultList();
        } catch (Exception e) {
            LOG.error("[ Error when removing parameters. ]");
            throw new ConfigServiceException(e.getMessage());
        }
        //TODO: No, fix this!
        for (Parameter parameter : parameters) {
            try {
                configHelper.getEntityManager().remove(parameter);
            } catch (Exception e) {
                LOG.error("[ Error when removing parameter. ]");
            }
        }
    }

    @Override
    public void clearAll() throws ConfigServiceException {
        List<Parameter> parameters;
        try {
            TypedQuery<Parameter> query = configHelper.getEntityManager().createNamedQuery(Parameter.LIST_ALL, Parameter.class);
            parameters = query.getResultList();
        } catch (Exception e) {
            LOG.error("[ERROR] Error when clearing all settings :  {}");
            throw new ConfigServiceException("[ Error when clearing all settings. ]", e);
        }

        for (Parameter parameter : parameters) {
            try {
                configHelper.getEntityManager().remove(parameter);
            } catch (Exception e) {
                LOG.error("[ Error when removing parameter. ]", e);
            }
        }
    }

    private Boolean parseBooleanValue(String value) throws InputArgumentException {
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        } else if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        } else {
            LOG.error("[ Error when parsing Boolean value from String, The String provided dows not equal 'TRUE' or 'FALSE'. The value is {} ]", value);
            throw new InputArgumentException("The String value provided does not equal boolean value, value provided = " + value);
        }
    }
}