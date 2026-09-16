/*
 * MDMesh agent-v1: turns a configuration's kiosk settings into a queued kiosk.enter.
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
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Queues the {@code kiosk.enter} that a configuration describes — the kiosk half of treating a
 * configuration as a "golden image". The agent is command-driven and never reads the configuration
 * itself, so without this a device enrolled against a kiosk configuration comes up unlocked and
 * stays that way until an admin pushes kiosk.enter by hand, device by device.
 *
 * The configuration's admin password rides along as the payload's exit password, so leaving kiosk
 * on the device costs the PIN the admin set in the console.
 */
@Singleton
public class ConfigKioskApplier {

    private static final Logger logger = LoggerFactory.getLogger(ConfigKioskApplier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UnsecureDAO unsecureDAO;
    private final AgentCommandDAO commandDAO;
    private final AgentWakeHub wakeHub;

    @Inject
    public ConfigKioskApplier(UnsecureDAO unsecureDAO, AgentCommandDAO commandDAO, AgentWakeHub wakeHub) {
        this.unsecureDAO = unsecureDAO;
        this.commandDAO = commandDAO;
        this.wakeHub = wakeHub;
    }

    /**
     * Queue a {@code kiosk.enter} for [device] when its configuration asks for kiosk mode.
     * Returns true when one was queued. Never throws — a dirty configuration must not fail an
     * enrollment that has otherwise succeeded.
     */
    public boolean enqueueKiosk(Device device) {
        if (device == null || device.getConfigurationId() == null) {
            return false;
        }
        try {
            Configuration c = unsecureDAO.getConfigurationById(device.getConfigurationId());
            if (c == null || !c.isKioskMode()) {
                return false;
            }
            String pkg = mainAppPackage(c);
            if (pkg == null) {
                // Kiosk with no main app would pin the device to the agent itself — refuse, and say
                // why, rather than shipping a device that looks bricked to whoever unboxes it.
                logger.warn("Configuration {} has kioskMode but no resolvable main app — kiosk not queued for device {}",
                        c.getId(), device.getNumber());
                return false;
            }

            AgentCommand cmd = new AgentCommand();
            cmd.setDeviceNumber(device.getNumber());
            cmd.setType("kiosk.enter");
            cmd.setPayload(buildPayload(c, pkg));
            cmd.setStatus("pending");
            cmd.setCreatedAt(System.currentTimeMillis());
            commandDAO.insert(cmd);
            wakeHub.wake(device.getNumber(), "commands");
            return true;
        } catch (Exception e) {
            logger.warn("Failed to queue kiosk for device {}", device.getNumber(), e);
            return false;
        }
    }

    /** Resolves the configuration's main app (an applicationVersions id) to its package name. */
    private String mainAppPackage(Configuration c) {
        if (c.getMainAppId() == null) {
            return null;
        }
        ApplicationVersion version = unsecureDAO.findApplicationVersionById(c.getMainAppId());
        if (version == null) {
            return null;
        }
        Application app = unsecureDAO.findApplicationById(version.getApplicationId());
        if (app == null || app.getPkg() == null || app.getPkg().trim().isEmpty()) {
            return null;
        }
        return app.getPkg().trim();
    }

    private String buildPayload(Configuration c, String pkg) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("mode", "single");
        root.put("pinPackage", pkg);
        root.putArray("allowedPackages").add(pkg);
        // kioskExit is "show an exit button": when the admin turned it off we still leave the
        // hidden 7-tap gesture rather than "remote", so a technician standing at the machine is
        // never locked out of a device whose server is unreachable. Either way the password gates it.
        root.put("exitMode", Boolean.TRUE.equals(c.getKioskExit()) ? "visible" : "gesture");
        if (c.getPassword() != null && !c.getPassword().trim().isEmpty()) {
            root.put("password", c.getPassword());
        }
        ObjectNode features = root.putObject("features");
        putIfSet(features, "home", c.getKioskHome());
        putIfSet(features, "recents", c.getKioskRecents());
        putIfSet(features, "notifications", c.getKioskNotifications());
        putIfSet(features, "systemInfo", c.getKioskSystemInfo());
        putIfSet(features, "keyguard", c.getKioskKeyguard());
        putIfSet(features, "lockButtons", c.getKioskLockButtons());
        return MAPPER.writeValueAsString(root);
    }

    /** Tri-state: an unset toggle stays absent so the agent keeps its own default. */
    private static void putIfSet(ObjectNode node, String name, Boolean value) {
        if (value != null) {
            node.put(name, value);
        }
    }
}
