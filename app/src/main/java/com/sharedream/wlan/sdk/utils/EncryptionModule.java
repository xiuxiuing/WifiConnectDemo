package com.sharedream.wlan.sdk.utils;

import android.util.Base64;

public class EncryptionModule {

    public static String base64EncodeToString(byte[] bytes) {
        return new String(Base64.encode(bytes, Base64.DEFAULT));
    }

    public static byte[] base64DecodeToBytes(byte[] bytes) {
        return Base64.decode(bytes, Base64.DEFAULT);
    }

}
