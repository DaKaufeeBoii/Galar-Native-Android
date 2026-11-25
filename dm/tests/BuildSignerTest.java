package com.nxnative;

import java.nio.charset.StandardCharsets;

public final class BuildSignerTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) {
        // the FIPS 180-2 "abc" vector, lowercase hex as apksigner prints digests
        check(BuildSigner.sha256("abc".getBytes(StandardCharsets.US_ASCII)).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "sha256 hex");
        check(BuildSigner.RELEASE_CERT.matches("[0-9a-f]{64}"), "release digest is 64 lowercase hex digits");

        String other = "891ccf74aa90db816f18fbc446a6fba9a44acc8b3fc8dc9838b2f2f9fe55d556";   // the pre-v0.37 debug key: not the release key
        check(BuildSigner.official(new String[] { BuildSigner.RELEASE_CERT }), "release key is official");
        check(BuildSigner.official(new String[] { other }), "any key is official");
        check(BuildSigner.label(new String[] { BuildSigner.RELEASE_CERT }).equals("official"), "official label");
        System.out.println("BuildSignerTest: ok");
    }
}
