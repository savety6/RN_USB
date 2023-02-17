package com.rn_ppg_ekg.connectivity;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DpadConnector {

    private static final boolean DEBUG = true;

    private static final int CMD_IDENT = 0x01;
    private static final int CMD_KEY = 0x02;

    private final Context mContext;
    private final Handler mHandler;
    private final UsbManager mUsbManager;
    private final UsbDevice mUsbDevice;
    private final UsbEndpoint[] mEndpoints;
    private UsbDeviceConnection mDeviceConn;
    private  OnCommandReceivedListener mCommandReceivedListener;

    private volatile boolean mActive;

    private final Object mTransmitSyncRoot = new Object();

    public interface OnCommandReceivedListener {
        void onIdentReceived(String ident);
        void onKeyReceived(int action, int keyCode);
    }

    public static DpadConnector create(Context context, UsbManager manager, UsbDevice device, OnCommandReceivedListener l) {
        DpadConnector connector = new DpadConnector(context, manager, device);
        connector.setCommandListener(l);

        try {
            connector.connect();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return connector;
    }

    private DpadConnector(Context context, UsbManager manager, UsbDevice device) {
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
        mUsbManager = manager;
        mUsbDevice = device;
        mEndpoints = new UsbEndpoint[2];
    }

    private void debug(String text) {
        if (DEBUG) {
            System.out.println("<DpadConnector> " + text);
        }
    }

    private void setCommandListener(OnCommandReceivedListener l) {
        mCommandReceivedListener = l;
    }

    private UsbDeviceConnection openConnection(UsbDevice device, UsbEndpoint[] endpoints) throws IOException {
        // Can we connect to device ?
        if (!mUsbManager.hasPermission(device)) {
            throw new IOException("Permission denied");
        }

        // Enumerate interfaces
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            final UsbInterface usbInterface = device.getInterface(i);
            UsbEndpoint usbEpInp = null;
            UsbEndpoint usbEpOut = null;

            // Enumerate end points
            for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(j);

                // Check interface type
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;

                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    usbEpInp = endpoint;
                }

                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    usbEpOut = endpoint;
                }
            }

            if (usbEpInp != null && usbEpOut != null) {
                final UsbDeviceConnection usbDevConn = mUsbManager.openDevice(device);

                // Check connection
                if (usbDevConn == null) {
                    throw new IOException("Open failed");
                }

                if (!usbDevConn.claimInterface(usbInterface, true)) {
                    usbDevConn.close();
                    throw new IOException("Access denied");
                }

                endpoints[0] = usbEpInp;
                endpoints[1] = usbEpOut;
                return usbDevConn;
            }
        }

        throw new IOException("Open failed");
    }

    private void connect() throws IOException {
        mDeviceConn = openConnection(mUsbDevice, mEndpoints);
        runReceiver();
    }

    private void runReceiver() {
        mActive = true;
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[4096];
            byte[] packet = new byte[4096];
            int offset = 0;
            while (mActive) {
                int len = mDeviceConn.bulkTransfer(mEndpoints[0], buffer, buffer.length, 100);
                if (len > 0 && packet.length - offset >= len) {
                    System.arraycopy(buffer, 0, packet, offset, len);
                    offset += len;
                } else {
                    offset = 0;
                }

                // Check packet
                while (offset >= 4) {
                    int dataLen = ((packet[2] & 0xff) << 8) + (packet[3] & 0xff);
                    int packLen = 4 + dataLen;
                    if (offset >= packLen) {
                        int cmd = packet[0] & 0xff;
                        byte[] data = new byte[dataLen];
                        System.arraycopy(packet, 4, data, 0, dataLen);
                        handleCommand(cmd, data);
                        System.arraycopy(packet, packLen, packet, 0, offset - packLen);
                        offset -= packLen;
                    } else {
                        break;
                    }
                }
            }
        });
        t.setName("DpadConnector receiver");
        t.start();
    }

    private void handleCommand(int cmd, byte[] data) {
        debug("handleCommand() - cmd=" + cmd + ", data.length=" + data.length);
        switch (cmd) {
            case CMD_IDENT: {
                String s = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    s = new String(data, 0, data.length, StandardCharsets.UTF_8);
                }
                String finalS = s;
                mHandler.post(() -> mCommandReceivedListener.onIdentReceived(finalS));
                break;
            }

            case CMD_KEY: {
                if (data.length >= 2) {
                    int action = data[0] & 0xff;
                    int keyCode = data[1] & 0xff;
                    mHandler.post(() -> mCommandReceivedListener.onKeyReceived(action, keyCode));
                }
                break;
            }
        }
    }

    public synchronized void close() {
        mActive = false;

        if (mDeviceConn != null) {
            try {
                mDeviceConn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isDeviceAttached(UsbDevice device) {
        return  device.equals(mUsbDevice);
    }

    public synchronized void transmit(int cmd, byte[] data) {
        if (!mActive || mDeviceConn == null) {
            return;
        }

        byte[] buffer = new byte[4 + data.length];
        buffer[0] = (byte) cmd;
        buffer[1] = 0;
        buffer[2] = (byte) (data.length >> 8);
        buffer[3] = (byte) (data.length);
        System.arraycopy(data, 0, buffer, 4, data.length);

        new Thread(() -> {
            synchronized (mTransmitSyncRoot) {
                try {
                    int status = mDeviceConn.bulkTransfer(mEndpoints[1], buffer, buffer.length, 1000);
                    if (status < 0) {
                        debug("bulkTransfer error: " + status);
                    } else {
                        debug("bulkTransfer completed: " + status);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public synchronized void requestIdent() {
        debug("requestIdent()");
        transmit(CMD_IDENT, new byte[0]);
    }
}

