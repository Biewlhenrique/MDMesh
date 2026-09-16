/*
 * MDMesh agent-v1: turns a configuration's device settings into queued commands.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */

package com.hmdm.rest.resource.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdm.notification.AgentWakeHub;
import com.hmdm.persistence.AgentCommandDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.AgentCommand;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Pushes the device settings a configuration describes — the rest of treating a configuration as a
 * "golden image", alongside {@link ConfigAppInstaller} (apps) and {@link ConfigKioskApplier}
 * (kiosk). The agent only ever obeys commands and never reads the configuration, so anything here
 * that isn't turned into one is a switch the console collects and the device never sees.
 *
 * Every command carries the capability it needs, so the gate in AgentResource drops the ones a
 * given device can't honour rather than handing it work that will fail.
 *
 * Tri-state fields (Boolean) are only sent when the admin actually set them: null means "leave the
 * device alone", not "turn it off".
 */
@Singleton
public class ConfigSettingsApplier {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSettingsApplier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UnsecureDAO unsecureDAO;
    private final AgentCommandDAO commandDAO;
    private final AgentWakeHub wakeHub;

    @Inject
    public ConfigSettingsApplier(UnsecureDAO unsecureDAO, AgentCommandDAO commandDAO, AgentWakeHub wakeHub) {
        this.unsecureDAO = unsecureDAO;
        this.commandDAO = commandDAO;
        this.wakeHub = wakeHub;
    }

    /**
     * Queue the settings commands [device]'s configuration describes. Returns how many were
     * queued. Never throws — a dirty configuration must not fail an enrollment.
     */
    public int enqueueSettings(Device device) {
        if (device == null || device.getConfigurationId() == null) {
            return 0;
        }
        int queued = 0;
        try {
            Configuration c = unsecureDAO.getConfigurationById(device.getConfigurationId());
            if (c == null) {
                return 0;
            }
            String number = device.getNumber();

            queued += togglePolicy(number, "wifi", c.getWifi());
            queued += togglePolicy(number, "bluetooth", c.getBluetooth());
            queued += togglePolicy(number, "usbStorage", c.getUsbStorage());
            // The console phrases these as the restriction ("block screenshots", "lock status
            // bar"); the agent policies are phrased as the capability. Invert once, here.
            queued += togglePolicy(number, "screenshots", invert(c.getDisableScreenshots()));
            queued += togglePolicy(number, "statusBar", invert(c.isBlockStatusBar()));
            // "Keep screen on" in the Kiosk group is exactly what the stayAwake policy does.
            queued += togglePolicy(number, "stayAwake", c.getKioskScreenOn());

            queued += brightness(number, c);

            if (queued > 0) {
                wakeHub.wake(number, "commands");
            }
        } catch (Exception e) {
            logger.warn("Failed to queue configuration settings for device {}", device.getNumber(), e);
        }
        return queued;
    }

    private static Boolean invert(Boolean value) {
        return value == null ? null : !value;
    }

    /** Queues one {@code policy.apply}, or nothing when the admin left the toggle unmanaged. */
    private int togglePolicy(String deviceNumber, String policy, Boolean value) {
        if (value == null) {
            return 0;
        }
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("policy", policy);
        payload.put("value", value);
        return queue(deviceNumber, "policy.apply", payload, "policy." + policy);
    }

    /**
     * Queues {@code device.brightness} when the configuration says anything about brightness. A
     * pinned level with auto-brightness left unmanaged still turns auto off — otherwise the sensor
     * would immediately override the level the admin chose.
     */
    private int brightness(String deviceNumber, Configuration c) {
        Boolean auto = c.getAutoBrightness();
        Integer level = c.getBrightness();
        if (auto == null && level == null) {
            return 0;
        }
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("auto", auto != null ? auto : Boolean.FALSE);
        if (level != null) {
            payload.put("value", level);
        }
        return queue(deviceNumber, "device.brightness", payload, "device.brightness");
    }

    private int queue(String deviceNumber, String type, ObjectNode payload, String capability) {
        try {
            AgentCommand cmd = new AgentCommand();
            cmd.setDeviceNumber(deviceNumber);
            cmd.setType(type);
            cmd.setPayload(MAPPER.writeValueAsString(payload));
            cmd.setRequiresCapability(capability);
            cmd.setStatus("pending");
            cmd.setCreatedAt(System.currentTimeMillis());
            commandDAO.insert(cmd);
            return 1;
        } catch (Exception e) {
            logger.warn("Failed to queue {} for device {}", type, deviceNumber, e);
            return 0;
        }
    }
}
