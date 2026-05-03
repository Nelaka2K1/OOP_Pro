package com.medstore.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/** Encapsulates password hashing algorithm details. */
public final class PasswordHasher {
    private static final int COST = 10;

    private PasswordHasher() {
    }

    public static String hash(String plain) {
        return BCrypt.withDefaults().hashToString(COST, plain.toCharArray());
    }

    public static boolean verify(String plain, String hash) {
        return BCrypt.verifyer().verify(plain.toCharArray(), hash).verified;
    }
}
