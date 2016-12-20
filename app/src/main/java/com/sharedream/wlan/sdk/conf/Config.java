/**
 * Configuration class
 *
 * @Author: Su Xing
 * @Date: 1-21-16
 * @Email: su_xing@mengxiang01.cn
 */
package com.sharedream.wlan.sdk.conf;

import com.sharedream.wlan.sdk.persistent.PersistentModule;

public class Config {
    /* static switch */
    public final static boolean SCAN_WIFI_SWITCH = false;    // only false with some special customer
    public final static boolean BLACK_LIST_SWITCH = false;
    public static int FILE_EXPIRATION_PERIOD = 24 * 60 * 60 * 1000;    // 24 hour
    public static int BUSINESS_AP_CACHE_EXPIRATION_PERIOD = 3 * 24 * 60 * 60 * 1000;    // 3 days
    public static int HEART_BEAT_INTERVAL = 2 * 60 * 1000;
    public static int COLLECT_AP_INTERVAL = 15 * 60 * 1000;
    public static int RETRY_INTERVAL = 30 * 1000;
    public static int REFRESH_SESSION_INTERVAL = 60 * 1000;
    public static int FAST_SCAN_INTERVAL = 60 * 1000;
    public static int SLOW_SCAN_INTERVAL = 2 * FAST_SCAN_INTERVAL;
    public static int QUERY_AP_INTERVAL = 2 * 60 * 1000;
    public static int SLOW_QUERY_AP_INTERVAL = 2 * QUERY_AP_INTERVAL;
    public static int FAST_DETECT_RSSI_INTERVAL = 7 * 1000;
    public static int AUTO_CONNECT_INTERVAL = 2 * 60 * 1000;
    public static int SAME_FREQUENCY_THRESHOLD = 20;
    public static int SIGNAL_LEVEL_THRESHOLD_HIGH = -60;
    public static int SIGNAL_LEVEL_THRESHOLD = -70;
    public static int SIGNAL_LEVEL_THRESHOLD_LOW = -95;
    public static int ATTACH_CARRIER_NETWORK_TIMEOUT = 30;
    public static int UUID_MASK = 0;
    public static int QUERY_COUNT = 16;
    public static int CONNECTING_LOOP_COUNT = 10;
    /* If reach this threshold, background thread will store ap data */
    public static int STORE_BUSINESS_AP_THRESHOLD = 5;
    /* config field */
    /* dynamic switch */
    public static boolean NOTIFY_IMMEDIATELY_SWITCH = true;    // stay condition switch
    public static boolean CONNECT_IMMEDIATELY_SWITCH = true;    // stay condition connection switch
    public static boolean NETWORK_SWITCH = true;        // switch network
    public static boolean CONNECT_FORCIBLY_SWITCH = true;    // connect anyway, ignore scan result
    public static boolean CONNECT_PRIVATE_AP_SWITCH = true;    // if this switch been opened, sdk is able to connect private ap
    public static boolean DEFAULT_NETWORK_SWITCH = false;    // if enabled, default network(CMCC-WEB) will be selected if no network specified
    public static boolean PRIORITY_LOGS_SWITCH = true;    // if enabled, prioritied logs will be sent tru GPRS

    public static void loadConfig() {
        // boolean switches
        Config.CONNECT_FORCIBLY_SWITCH = PersistentModule.getInstance().getBoolean(Constant.CONFIG_CONNECT_FORCIBLY_SWITCH, Config.CONNECT_FORCIBLY_SWITCH);
        Config.CONNECT_IMMEDIATELY_SWITCH = PersistentModule.getInstance().getBoolean(Constant.CONFIG_CONNECT_IMMEDIATELY_SWITCH, Config.CONNECT_IMMEDIATELY_SWITCH);
        Config.NETWORK_SWITCH = PersistentModule.getInstance().getBoolean(Constant.CONFIG_NETWORK_SWITCH, Config.NETWORK_SWITCH);
        Config.CONNECT_PRIVATE_AP_SWITCH = PersistentModule.getInstance().getBoolean(Constant.CONFIG_CONNECT_PRIVATE_AP_SWITCH, Config.CONNECT_PRIVATE_AP_SWITCH);
        Config.DEFAULT_NETWORK_SWITCH = PersistentModule.getInstance().getBoolean(Constant.CONFIG_DEFAULT_NETWORK_SWITCH, Config.DEFAULT_NETWORK_SWITCH);
        Config.PRIORITY_LOGS_SWITCH = PersistentModule.getInstance().getBoolean(Constant.CONFIG_PRIORITY_LOGS_SWITCH, Config.PRIORITY_LOGS_SWITCH);

        // int configurations
        Config.HEART_BEAT_INTERVAL = PersistentModule.getInstance().getInt(Constant.HeartbeatInterval, Config.HEART_BEAT_INTERVAL);
        Config.COLLECT_AP_INTERVAL = PersistentModule.getInstance().getInt(Constant.CollectApInterval, Config.COLLECT_AP_INTERVAL);
        Config.RETRY_INTERVAL = PersistentModule.getInstance().getInt(Constant.RetryInterval, Config.RETRY_INTERVAL);
        Config.REFRESH_SESSION_INTERVAL = PersistentModule.getInstance().getInt(Constant.RefreshSessionInterval, Config.REFRESH_SESSION_INTERVAL);
        Config.FAST_SCAN_INTERVAL = PersistentModule.getInstance().getInt(Constant.FastScanInterval, Config.FAST_SCAN_INTERVAL);
        Config.SLOW_SCAN_INTERVAL = PersistentModule.getInstance().getInt(Constant.SlowScanInterval, Config.SLOW_SCAN_INTERVAL);
        Config.QUERY_AP_INTERVAL = PersistentModule.getInstance().getInt(Constant.QueryApInterval, Config.QUERY_AP_INTERVAL);
        Config.SLOW_QUERY_AP_INTERVAL = PersistentModule.getInstance().getInt(Constant.SlowQueryApInterval, Config.SLOW_QUERY_AP_INTERVAL);
        Config.FAST_DETECT_RSSI_INTERVAL = PersistentModule.getInstance().getInt(Constant.FastDetectRssiInterval, Config.FAST_DETECT_RSSI_INTERVAL);
        Config.AUTO_CONNECT_INTERVAL = PersistentModule.getInstance().getInt(Constant.AutoConnectInterval, Config.AUTO_CONNECT_INTERVAL);
        Config.SAME_FREQUENCY_THRESHOLD = PersistentModule.getInstance().getInt(Constant.SameFrequencyThreshold, Config.SAME_FREQUENCY_THRESHOLD);
        Config.SIGNAL_LEVEL_THRESHOLD = PersistentModule.getInstance().getInt(Constant.SignalLevelThreshold, Config.SIGNAL_LEVEL_THRESHOLD);
        Config.SIGNAL_LEVEL_THRESHOLD_LOW = PersistentModule.getInstance().getInt(Constant.SignalLevelThresholdLow, Config.SIGNAL_LEVEL_THRESHOLD_LOW);
        Config.SIGNAL_LEVEL_THRESHOLD_HIGH = PersistentModule.getInstance().getInt(Constant.SignalLevelThresholdHigh, Config.SIGNAL_LEVEL_THRESHOLD_HIGH);
        Config.FILE_EXPIRATION_PERIOD = PersistentModule.getInstance().getInt(Constant.FileExpirationPeriod, Config.FILE_EXPIRATION_PERIOD);
        Config.BUSINESS_AP_CACHE_EXPIRATION_PERIOD = PersistentModule.getInstance().getInt(Constant.BusinessApCacheExpirationPeriod, Config.BUSINESS_AP_CACHE_EXPIRATION_PERIOD);
        Config.QUERY_COUNT = PersistentModule.getInstance().getInt(Constant.ScanQueryCount, Config.QUERY_COUNT);
        Config.UUID_MASK = PersistentModule.getInstance().getInt(Constant.UUIDMask, Config.UUID_MASK);
        Config.ATTACH_CARRIER_NETWORK_TIMEOUT = PersistentModule.getInstance().getInt(Constant.AttachCarrierNetworkTimeout, Config.ATTACH_CARRIER_NETWORK_TIMEOUT);
    }

}