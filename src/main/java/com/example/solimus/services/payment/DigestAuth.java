package com.example.solimus.services.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authentification HTTP Digest (RFC 2617, MD5, qop=auth) — utilisée par l'API TouchPay GU.
 * Construit l'en-tête Authorization à partir du défi WWW-Authenticate reçu avec le 401.
 */
final class DigestAuth {

    private static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]*))");
    private static final SecureRandom RANDOM = new SecureRandom();

    private DigestAuth() {
    }

    static String authorization(String challenge, String username, String password, String method, String uri) {
        Map<String, String> p = parse(challenge);
        String realm = p.getOrDefault("realm", "");
        String nonce = p.getOrDefault("nonce", "");
        String opaque = p.get("opaque");
        String qop = p.get("qop");
        boolean useQop = qop != null && qop.contains("auth");

        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method + ":" + uri);
        String nc = "00000001";
        String cnonce = HexFormat.of().formatHex(randomBytes());
        String response = useQop
                ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);

        StringBuilder header = new StringBuilder("Digest ")
                .append("username=\"").append(username).append("\", ")
                .append("realm=\"").append(realm).append("\", ")
                .append("nonce=\"").append(nonce).append("\", ")
                .append("uri=\"").append(uri).append("\", ")
                .append("algorithm=MD5, ")
                .append("response=\"").append(response).append("\"");
        if (useQop) {
            header.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        }
        if (opaque != null) {
            header.append(", opaque=\"").append(opaque).append("\"");
        }
        return header.toString();
    }

    static Map<String, String> parse(String challenge) {
        Map<String, String> params = new HashMap<>();
        Matcher m = PARAM.matcher(challenge.substring(challenge.indexOf(' ') + 1));
        while (m.find()) {
            params.put(m.group(1).toLowerCase(), m.group(2) != null ? m.group(2) : m.group(3));
        }
        return params;
    }

    static String md5(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return b;
    }
}
