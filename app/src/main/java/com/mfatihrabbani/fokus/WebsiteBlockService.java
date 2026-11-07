package com.mfatihrabbani.fokus;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local, DNS-only VPN. Every DNS lookup on the phone comes here; blocked
 * domains get "not found", everything else is forwarded to a public resolver.
 * Normal traffic never passes through this service.
 */
public class WebsiteBlockService extends VpnService implements Runnable {
    static final String ACTION_STOP = "com.mfatihrabbani.fokus.STOP";

    private static final String VPN_ADDRESS = "10.111.222.1";
    private static final String VPN_DNS = "10.111.222.2";
    private static final String[] UPSTREAM_DNS = {"1.1.1.1", "8.8.8.8"};

    private ParcelFileDescriptor tun;
    private Thread thread;
    private ExecutorService forwarder;

    static void start(Context context) {
        context.startService(new Intent(context, WebsiteBlockService.class));
    }

    static void stop(Context context) {
        context.startService(new Intent(context, WebsiteBlockService.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (tun == null) {
            try {
                tun = new Builder()
                        .setSession("Fokus")
                        .addAddress(VPN_ADDRESS, 32)
                        .addDnsServer(VPN_DNS)
                        .addRoute(VPN_DNS, 32)
                        .setBlocking(true)
                        .establish();
            } catch (Exception e) {
                tun = null;
            }
            if (tun == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            forwarder = Executors.newFixedThreadPool(4);
            thread = new Thread(this, "fokus-dns");
            thread.start();
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        Prefs.setWebsiteOn(this, false);
        shutdown();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private synchronized void shutdown() {
        if (thread != null) thread.interrupt();
        if (forwarder != null) forwarder.shutdownNow();
        if (tun != null) {
            try { tun.close(); } catch (IOException ignored) {}
        }
        tun = null;
        thread = null;
        forwarder = null;
    }

    @Override
    public void run() {
        ParcelFileDescriptor fd = tun;
        if (fd == null) return;
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(fd.getFileDescriptor());
        byte[] buffer = new byte[32767];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int length = in.read(buffer);
                if (length <= 0) continue;
                byte[] packet = Arrays.copyOf(buffer, length);
                handle(packet, out);
            }
        } catch (IOException ignored) {
            // The tunnel was closed.
        }
    }

    private void handle(byte[] packet, FileOutputStream out) {
        // IPv4 + UDP to port 53 only.
        if ((packet[0] >> 4) != 4 || packet[9] != 17) return;
        int ipHeader = (packet[0] & 0x0f) * 4;
        int dnsStart = ipHeader + 8;
        if (packet.length < dnsStart + 12 || u16(packet, ipHeader + 2) != 53) return;
        byte[] query = Arrays.copyOfRange(packet, dnsStart, packet.length);

        String name = DnsMessage.questionName(query);
        if (name != null && isBlocked(name)) {
            reply(packet, ipHeader, DnsMessage.notFound(query), out);
            return;
        }

        ExecutorService pool = forwarder;
        if (pool == null) return;
        try {
            pool.execute(() -> {
                byte[] answer = forward(query);
                if (answer != null) reply(packet, ipHeader, answer, out);
            });
        } catch (Exception ignored) {
            // Shutting down.
        }
    }

    private boolean isBlocked(String name) {
        String host = name.toLowerCase(Locale.ROOT);
        Set<String> domains = Prefs.domains(this);
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    private byte[] forward(byte[] query) {
        for (String server : UPSTREAM_DNS) {
            try (DatagramSocket socket = new DatagramSocket()) {
                protect(socket);
                socket.setSoTimeout(4000);
                socket.send(new DatagramPacket(query, query.length, InetAddress.getByName(server), 53));
                byte[] buffer = new byte[4096];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                return Arrays.copyOf(buffer, response.getLength());
            } catch (IOException ignored) {
                // Try the next resolver.
            }
        }
        return null;
    }

    /** Wraps a DNS payload in an IPv4/UDP packet addressed back to the asker. */
    private void reply(byte[] request, int ipHeader, byte[] dns, FileOutputStream out) {
        int total = 20 + 8 + dns.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        p[2] = (byte) (total >> 8);
        p[3] = (byte) total;
        p[8] = 64; // TTL
        p[9] = 17; // UDP
        System.arraycopy(request, 16, p, 12, 4); // source = original destination
        System.arraycopy(request, 12, p, 16, 4); // destination = original source
        int sum = 0;
        for (int i = 0; i < 20; i += 2) sum += u16(p, i);
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        sum = ~sum;
        p[10] = (byte) (sum >> 8);
        p[11] = (byte) sum;

        p[20] = request[ipHeader + 2]; // source port = original destination port
        p[21] = request[ipHeader + 3];
        p[22] = request[ipHeader];     // destination port = original source port
        p[23] = request[ipHeader + 1];
        p[24] = (byte) ((8 + dns.length) >> 8);
        p[25] = (byte) (8 + dns.length);
        // UDP checksum 0 = not used, which IPv4 allows.
        System.arraycopy(dns, 0, p, 28, dns.length);

        synchronized (out) {
            try {
                out.write(p);
            } catch (IOException ignored) {
                // The tunnel was closed.
            }
        }
    }

    private static int u16(byte[] b, int offset) {
        return ((b[offset] & 0xff) << 8) | (b[offset + 1] & 0xff);
    }
}
