package io.jenkins.plugins.sample;

import org.apache.commons.compress.utils.ByteUtils;

public class Util {

    public static long x() {
        return ByteUtils.fromLittleEndian(new byte[] {3, 1}); // ⇒ 259
    }

    private Util() {}

}
