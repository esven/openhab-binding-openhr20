/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openhr20.handler;

import static org.openhab.binding.openhr20.OpenHR20BindingConstants.CONFIGURATION_PORT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TooManyListenersException;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * The {@link OpenHR20Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Sven Ebenfeld - Initial contribution
 */
public class OpenHR20Handler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(OpenHR20Handler.class);

    private String portId;

    private SerialPort serialPort;

    private OpenHR20ReceiveThread receiveThread;

    public OpenHR20Handler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(CHANNEL_1)) {
            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    @Override
    public void initialize() {
        logger.debug("Initializing OpenHR20 Serial Controller.");

        portId = (String) getConfig().get(CONFIGURATION_PORT);

        if (portId == null || portId.length() == 0) {
            logger.error("OpenHR20 port is not set.");
            return;
        }

        super.initialize();
        logger.info("Connecting to serial port '{}'", portId);
        try {
            CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portId);
            CommPort commPort = portIdentifier.open("org.openhab.binding.openhr20", 2000);
            this.serialPort = (SerialPort) commPort;
            this.serialPort.setSerialPortParams(38400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            this.serialPort.enableReceiveThreshold(1);
            this.serialPort.enableReceiveTimeout(1000);
            logger.debug("Starting receive thread");
            this.receiveThread = new OpenHR20ReceiveThread();
            this.receiveThread.start();

            // RXTX serial port library causes high CPU load
            // Start event listener, which will just sleep and slow down event loop
            serialPort.addEventListener(this.receiveThread);
            serialPort.notifyOnDataAvailable(true);

            logger.info("Serial port is initialized");

        } catch (NoSuchPortException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Port " + portId + " does not exist");
        } catch (PortInUseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Port " + portId + " in use");
        } catch (UnsupportedCommOperationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Unsupported comm operation on Port " + portId);
        } catch (TooManyListenersException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Serial Error: Too many listeners on Port " + portId);
        }

        updateStatus(ThingStatus.OFFLINE);

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work
        // as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    /**
     * ZWave controller Receive Thread. Takes care of receiving all messages.
     * It uses a semaphore to synchronize communication with the sending thread.
     */
    private class OpenHR20ReceiveThread extends Thread implements SerialPortEventListener {

        private final Logger logger = LoggerFactory.getLogger(OpenHR20ReceiveThread.class);

        private static final int DATA = 0x44; /* D */
        private static final int VERSION = 0x56; /* V */
        private static final int DEBUG_VAR = 0x54; /* T */
        private static final int GET_CONFIG_BYTE = 0x47; /* G */
        private static final int SET_CONFIG_BYTE = 0x53; /* S */
        private static final int GET_HEATING_INTERVAL = 0x52; /* R */
        private static final int SET_HEATING_INTERVAL = 0x57; /* W */
        private static final int REBOOT_THERMOSTAT = 0x42; /* B */
        private static final int SET_TEMP = 0x41; /* A */
        private static final int SET_MODE = 0x4D; /* M */
        private static final int TIME_REQUEST = 0x54; /* RTC? */

        OpenHR20ReceiveThread() {
            super("OpenHR20ReceiveThread");
        }

        @Override
        public void serialEvent(SerialPortEvent arg0) {
            try {
                logger.trace("RXTX library CPU load workaround, sleep forever");
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
            }
        }

        /**
         * Sends the Date and the Time to the Master.
         *
         */
        private void sendDateTime() {
            Date date = new GregorianCalendar().getTime();
            String dateString = new SimpleDateFormat("'Y'yyMMdd'\n'").format(date);
            String timeString = new SimpleDateFormat("'H'HHmmss'\n'").format(date);
            // Send a Date and Time to resynchronise communications
            sendCommand(dateString);
            sendCommand(timeString);
        }

        /**
         * Sends a String to the Master.
         *
         * @param command
         *            the command to send.
         */
        private void sendCommand(String command) {
            try {
                byte[] bytes = command.getBytes(StandardCharsets.ISO_8859_1);
                if (bytes[bytes.length - 1] == 0) {
                    /* Remove trailing 0 byte */
                    bytes = Arrays.copyOf(bytes, bytes.length - 1);
                }
                synchronized (serialPort.getOutputStream()) {
                    serialPort.getOutputStream().write(bytes);
                    serialPort.getOutputStream().flush();
                    logger.trace("Command SENT: {} = {}", command, Arrays.toString(bytes));
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

        /**
         * Run method. Runs the actual receiving process.
         */
        @Override
        public void run() {
            logger.debug("Starting OpenHR20 thread: Receive");
            try {
                while (!interrupted()) {
                    sendDateTime();

                    int nextByte;

                    try {
                        nextByte = serialPort.getInputStream().read();

                        if (nextByte == -1) {
                            continue;
                        }
                    } catch (IOException e) {
                        logger.error("Got I/O exception {} during receiving. exiting thread.", e.getLocalizedMessage());
                        break;
                    }
                    logger.trace("Read {}", nextLine);

                    switch (nextByte) {
                        case SOF:
                            // Keep track of statistics
                            SOFCount++;
                            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SERIAL_SOF),
                                    new DecimalType(SOFCount));

                            int messageLength;
                            try {
                                messageLength = serialPort.getInputStream().read();
                            } catch (IOException e) {
                                logger.error("Got I/O exception {} during receiving. exiting thread.",
                                        e.getLocalizedMessage());

                                break;
                            }

                            byte[] buffer = new byte[messageLength + 2];
                            buffer[0] = SOF;
                            buffer[1] = (byte) messageLength;
                            int total = 0;

                            while (total < messageLength) {
                                try {
                                    int read = serialPort.getInputStream().read(buffer, total + 2,
                                            messageLength - total);
                                    total += (read > 0 ? read : 0);
                                } catch (IOException e) {
                                    logger.error("Got I/O exception {} during receiving. exiting thread.",
                                            e.getLocalizedMessage());
                                    return;
                                }
                            }

                            logger.debug("Receive Message = {}", SerialMessage.bb2hex(buffer));
                            SerialMessage recvMessage = new SerialMessage(buffer);
                            if (recvMessage.isValid) {
                                logger.trace("Message is valid, sending ACK");
                                sendResponse(ACK);

                                incomingMessage(recvMessage);
                            } else {
                                logger.error("Message is invalid, discarding");
                                sendResponse(NAK);
                            }
                            break;
                        case ACK:
                            // Keep track of statistics
                            ACKCount++;
                            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SERIAL_ACK),
                                    new DecimalType(ACKCount));
                            logger.trace("Received ACK");
                            break;
                        case NAK:
                            NAKCount++;
                            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SERIAL_NAK),
                                    new DecimalType(NAKCount));
                            logger.error("Protocol error (NAK), discarding");

                            // TODO: Add NAK processing
                            break;
                        case CAN:
                            CANCount++;
                            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SERIAL_CAN),
                                    new DecimalType(CANCount));
                            logger.error("Protocol error (CAN), resending");
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                break;
                            }

                            // TODO: Add CAN processing (Resend?)
                            break;
                        default:
                            OOFCount++;
                            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SERIAL_OOF),
                                    new DecimalType(OOFCount));
                            logger.warn(String.format("Protocol error (OOF). Got 0x%02X. Sending NAK.", nextByte));
                            sendResponse(NAK);
                            break;
                    }
                }
            } catch (Exception e) {
                logger.error("Exception during OpenHR20 thread: Receive {}", e);
            }
            logger.debug("Stopped OpenHR20 thread: Receive");

            serialPort.removeEventListener();
        }
    }
}
