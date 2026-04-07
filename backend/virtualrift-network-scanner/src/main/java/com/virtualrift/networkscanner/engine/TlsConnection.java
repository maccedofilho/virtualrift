package com.virtualrift.networkscanner.engine;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

public interface TlsConnection {

    Optional<X509Certificate> fetchCertificate(String host, int port);

    List<String> getSupportedProtocols(String host, int port);

    List<String> getCipherSuites(String host, int port);

    List<String> getKeyExchangeMethods(String host, int port);

    List<String> getHttpHeaders(String host, int port);

    boolean isSecureConnection(String host, int port);
}
