package com.musicplayer.scamusica.util;

import java.net.NetworkInterface;
import java.util.Enumeration;

public class DeviceUtil {
    public static String getDeviceId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();

                // skip loopback and virtual interfaces
                if (network.isLoopback() || network.isVirtual() || !network.isUp()) {
                    continue;
                }

                byte[] mac = network.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X:", b));
                    }
                    return sb.substring(0, sb.length() - 1); // remove trailing colon
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "UNKNOWN_DEVICE";
    }
}
