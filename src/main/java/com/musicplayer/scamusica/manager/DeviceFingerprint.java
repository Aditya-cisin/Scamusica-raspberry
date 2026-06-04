package com.musicplayer.scamusica.manager;

import com.musicplayer.scamusica.util.DeviceUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

//public class DeviceFingerprint {
//    public static String getFingerprint() {
//        try {
//            String mac = DeviceUtil.getDeviceId();
//            String os = System.getProperty("os.name");
//            String cpu = System.getenv("PROCESSOR_IDENTIFIER"); // Windows
//            if (cpu == null) cpu = System.getenv("HOSTTYPE");   // Linux / Mac
//            if (cpu == null) cpu = System.getenv("MACHTYPE");   //Linux / Mac
//            if (cpu == null) cpu = "UNKNOWN_CPU"; // Fallback
//
//            String raw = mac + os + cpu;
//            // Hash everything into a unique fingerprint
//            MessageDigest md = MessageDigest.getInstance("SHA-256");
//            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
//
//            StringBuilder hex = new StringBuilder();
//            for (byte b : hash) {
//                hex.append(String.format("%02x", b));
//            }
//            return hex.toString();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "UNKNOWN";
//        }
//    }
//}
public class DeviceFingerprint {
    public static String getFingerprint() {
        try {
            String mac      = DeviceUtil.getDeviceId();
            String os       = System.getProperty("os.name");
            String osArch   = System.getProperty("os.arch");
            String osVer    = System.getProperty("os.version");
            String userName = System.getProperty("user.name"); // OS login user

            // CPU — Windows
            String cpu = System.getenv("PROCESSOR_IDENTIFIER");

            // Linux fallback — /proc/cpuinfo se real CPU info
            if (cpu == null) {
                cpu = readLinuxCpuInfo();
            }

            // Mac fallback
            if (cpu == null) {
                cpu = runCommand("sysctl -n machdep.cpu.brand_string");
            }

            if (cpu == null) cpu = "UNKNOWN_CPU";

            // Disk serial — cross platform
            String diskSerial = getDiskSerial();

            String raw = mac + os + osArch + osVer + cpu + userName + diskSerial;

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "UNKNOWN";
        }
    }

    // Linux: /proc/cpuinfo se "model name" padhna
    private static String readLinuxCpuInfo() {
        try {
            java.io.File file = new java.io.File("/proc/cpuinfo");
            if (!file.exists()) return null;

            try (java.util.Scanner sc = new java.util.Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith("model name")) {
                        return line.split(":")[1].trim(); // e.g. "Intel Core i7-9750H"
                        // Yeh machine-specific hoga ✅
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    // Command run karna (Mac ke liye)
    private static String runCommand(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            byte[] bytes = p.getInputStream().readAllBytes();
            return new String(bytes).trim();
        } catch (Exception e) { return null; }
    }

    // Disk serial number — Windows/Linux/Mac
    private static String getDiskSerial() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows
                Process p = Runtime.getRuntime().exec(
                        new String[]{"cmd", "/c", "wmic diskdrive get SerialNumber"}
                );
                byte[] out = p.getInputStream().readAllBytes();
                return new String(out).replaceAll("\\s+", "").replace("SerialNumber", "");

            } else if (os.contains("linux")) {
                // Linux (root permission chahiye ho sakti hai)
                Process p = Runtime.getRuntime().exec(
                        new String[]{"bash", "-c", "lsblk -d -o SERIAL 2>/dev/null | tail -1"}
                );
                byte[] out = p.getInputStream().readAllBytes();
                return new String(out).trim();

            } else if (os.contains("mac")) {
                // Mac
                Process p = Runtime.getRuntime().exec(
                        new String[]{"bash", "-c",
                                "system_profiler SPStorageDataType | grep 'Serial Number' | head -1"}
                );
                byte[] out = p.getInputStream().readAllBytes();
                return new String(out).trim();
            }
        } catch (Exception e) { /* ignore */ }
        return "UNKNOWN_DISK";
    }
}