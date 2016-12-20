package com.sharedream.wlan.sdk.utils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

public class Utils {

    private Utils() {
    }

    public static boolean validateString(String string) {
        if (string != null && !string.equals("")) {
            return true;
        }

        return false;
    }

    public static void waitFor(long time) {
        try {
            Thread.sleep(time);
        } catch (Throwable e) {
        }
    }

    public static HashMap<String, Object> json2Map(JSONObject jsonObj) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        try {
            Iterator<?> iterator = jsonObj.keys();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                map.put(key, jsonObj.getString(key));
            }
        } catch (Throwable e) {
        }

        return map;
    }

}
