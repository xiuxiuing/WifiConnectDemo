package com.sharedream.wlan.sdk.conf;

public class Constant {
    public final static String SSID = "SSID";
    public final static String BSSID = "BSSID";
    public final static String Password = "Password";
    public final static String G3WLAN = "G3WLAN";
    public final static String Frequency = "Frequency";
    public final static String Security = "Security";
    public final static String BlackList = "BlackList";
    public final static String StartTime = "StartTime";
    public final static String EndTime = "EndTime";
    public final static String POST = "POST";
    public final static String GET = "GET";
    public final static String HeartbeatInterval = "HeartbeatInterval";
    public final static String CollectApInterval = "CollectApInterval";
    public final static String RetryInterval = "RetryInterval";
    public final static String RefreshSessionInterval = "RefreshSessionInterval";
    public final static String FastScanInterval = "FastScanInterval";
    public final static String SlowScanInterval = "SlowScanInterval";
    public final static String QueryApInterval = "QueryApInterval";
    public final static String SlowQueryApInterval = "SlowQueryApInterval";
    public final static String FastDetectRssiInterval = "FastDetectRssiInterval";
    public final static String AutoConnectInterval = "AutoConnectInterval";
    public final static String SameFrequencyThreshold = "SameFrequencyThreshold";
    public final static String SignalLevelThreshold = "SignalLevelThreshold";
    public final static String SignalLevelThresholdLow = "SignalLevelThresholdLow";
    public final static String SignalLevelThresholdHigh = "SignalLevelThresholdHigh";
    public final static String FileExpirationPeriod = "FileExpirationPeriod";
    public final static String ScanQueryCount = "ScanQueryCount";
    public final static String AttachCarrierNetworkTimeout = "AttachCarrierNetworkTimeout";
    public final static String BusinessApCacheExpirationPeriod = "BusinessApCacheExpirationPeriod";
    public final static String UUIDMask = "UUIDMask";
    public final static String Available = "Available";
    public final static String Level = "Level";
    public final static String tag = "tag";
    public final static String Rssi = "Rssi";
    public final static String Context = "Context";
    public final static String FastRegister = "FastRegister";
    public final static String Connected = "Connected";
    public final static String Version = "Version";
    public final static String CONFIG_NETWORK_SWITCH = "ConfigNetworkSwitch";
    public final static String CONFIG_CONNECT_FORCIBLY_SWITCH = "ConfigConnectForciblySwitch";
    public final static String CONFIG_CONNECT_PRIVATE_AP_SWITCH = "ConfigConnectPrivateApSwitch";
    public final static String CONFIG_DEFAULT_NETWORK_SWITCH = "ConfigDefaultNetworkSwitch";
    public final static String CONFIG_CONNECT_IMMEDIATELY_SWITCH = "ConfigConnectImmediatelySwitch";
    public final static String CONFIG_PRIORITY_LOGS_SWITCH = "ConfigPriorityLogsSwitch";
    public final static String defaultNetwork = "CMCC-WEB";
    public final static String handlerThread = "Handlers";

    public final static int Ap_Map_Carrier = 1;
    public final static int Ap_Map_Private = 2;

    /* Timeout value */
    public final static int MID_TIMEOUT = 10;

    /* Connection timeout value */
    public final static int CONNECT_TIMEOUT = 20;
    public final static int SOCKET_TIMEOUT = 10;
    public final static String persistentBytes = "230OOp11";

    /* AP Security type */
    public final static int AP_SECURITY_OPEN = 0;
    public final static int AP_SECURITY_WEP = 1;
    public final static int AP_SECURITY_WPA = 2;
    public final static int AP_SECURITY_EAP = 3;

    /* AP Key management type */
    public final static int AP_KEY_MGMT_OPEN = 0;
    public final static int AP_KEY_MGMT_WEP = 1;
    public final static int AP_KEY_MGMT_WPA = 2;
    public final static int AP_KEY_MGMT_WPA2 = 3;
    public final static int AP_KEY_MGMT_EAP = 4;

    /* Delay time unit */
    public final static int DELAY_TIME_UNIT = 1000;        // 1s

    /* stay threshold */
    public final static int STAY_THRESHOLD = 3;

    /* retry time */
    public final static int THREAD_COUNT = 3;
    public final static int MAIN_THREAD_INDEX = 0;
    public final static int SECONDARY_THREAD_INDEX = 1;
    public final static int BACKGROUND_THREAD_INDEX = 2;

    /* default key index */
    public final static int DEFAULT_PERSISTENT_KEY_INDEX = 7;

}