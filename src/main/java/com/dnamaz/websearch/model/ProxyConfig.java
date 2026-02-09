package com.dnamaz.websearch.model;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proxy configuration for routing requests through HTTP/SOCKS proxies.
 *
 * <p>Supports SOCKS5 stream isolation: each call to {@link #rotateStream()}
 * increments an internal counter. When a SOCKS5 connection is made, the
 * counter value is sent as the username/password pair. SOCKS5 proxies that
 * support authentication-based stream isolation will route each unique
 * credential pair through a separate connection path, providing privacy
 * between independent search queries.</p>
 */
public record ProxyConfig(
        boolean enabled,
        ProxyType type,
        String host,
        int port,
        String username,
        String password,
        boolean useOnionServices
) {
    /**
     * Monotonically increasing stream identifier for SOCKS5 stream isolation.
     * Each unique value signals the proxy to use a separate connection path.
     */
    private static final AtomicInteger STREAM_ID = new AtomicInteger(0);

    /** Whether the stream-isolation authenticator has been installed. */
    private static volatile boolean authenticatorInstalled = false;

    public static ProxyConfig disabled() {
        return new ProxyConfig(false, ProxyType.NONE, null, 0, null, null, false);
    }

    public boolean isSocks() {
        return type == ProxyType.SOCKS4 || type == ProxyType.SOCKS5;
    }

    /**
     * Advance the stream-isolation counter. The next SOCKS5 connection will
     * present new credentials, causing the proxy to route through a fresh
     * connection path for improved privacy between queries.
     *
     * @return the new stream id
     */
    public static int rotateStream() {
        return STREAM_ID.incrementAndGet();
    }

    /** Returns the current stream id (for logging). */
    public static int currentStreamId() {
        return STREAM_ID.get();
    }

    /**
     * Installs a global {@link Authenticator} that provides stream-isolation
     * credentials for SOCKS5 connections. Safe to call multiple times; the
     * authenticator is only installed once.
     */
    public void installStreamIsolationAuthenticator() {
        if (authenticatorInstalled || !enabled || !isSocks()) {
            return;
        }
        synchronized (ProxyConfig.class) {
            if (authenticatorInstalled) return;
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestingProtocol() != null
                            && getRequestingProtocol().equalsIgnoreCase("SOCKS5")) {
                        String id = "stream-" + STREAM_ID.get();
                        return new PasswordAuthentication(id, id.toCharArray());
                    }
                    return null;
                }
            });
            authenticatorInstalled = true;
        }
    }

    public Proxy toJavaProxy() {
        if (!enabled || type == ProxyType.NONE) {
            return Proxy.NO_PROXY;
        }
        Proxy.Type proxyType = isSocks() ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(proxyType, new InetSocketAddress(host, port));
    }
}
