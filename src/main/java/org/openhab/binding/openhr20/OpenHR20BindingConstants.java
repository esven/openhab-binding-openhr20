/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openhr20;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link OpenHR20Binding} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Sven Ebenfeld - Initial contribution
 */
public class OpenHR20BindingConstants {

    public static final String BINDING_ID = "openhr20";

    public final static String CONFIGURATION_PORT = "port";
    public final static String CONFIGURATION_MASTER = "controller_master";

    // List of all Thing Type UIDs
    public final static ThingTypeUID MASTER_THING_TYPE = new ThingTypeUID(BINDING_ID, "master");
    public final static ThingTypeUID THERMOSTAT_THING_TYPE = new ThingTypeUID(BINDING_ID, "thermostat");

    // List of all Channel ids
    public final static String CHANNEL_CURRENT_TEMP = "current_temp";
    public final static String CHANNEL_VALVE_POSITION = "valve_position";
    public final static String CHANNEL_THERMOSTAT_SETPOINT = "thermostat_setpoint";
    public final static String CHANNEL_BATTERY_LEVEL = "battery-level";
    public final static String CHANNEL_WINDOW_SENSOR = "sensor_window";
    public final static String CHANNEL_OPERATION_MODE = "mode";

}
