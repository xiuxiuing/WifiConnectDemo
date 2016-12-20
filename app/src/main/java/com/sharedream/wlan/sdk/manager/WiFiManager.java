/**
 * WiFi management class
 *
 * @author: Su Xing
 * @version: 1.25.2
 * @date: 1-21-16
 * @email: su_xing@mengxiang01.cn
 */

package com.sharedream.wlan.sdk.manager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.sharedream.wlan.sdk.api.WLANSDKManager.ConnectionCallback;
import com.sharedream.wlan.sdk.api.WLANSDKManager.Result;
import com.sharedream.wlan.sdk.api.WLANSDKManager.Status;
import com.sharedream.wlan.sdk.conf.Config;
import com.sharedream.wlan.sdk.conf.Constant;
import com.sharedream.wlan.sdk.persistent.PersistentModule;
import com.sharedream.wlan.sdk.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

@SuppressLint("UseSparseArrays")
public class WiFiManager {
    private static final String MESSAGE_TAG = "WLANSDK";
    private static WiFiManager instance = null;
    private static ConnectionCallback apConnectionCallback = null;
    List<ScanResult> resultList;    // store last scan result
    List<ScanResult> outputCarrierList = new ArrayList<ScanResult>();    // store last scan carrier filtered result
    List<ScanResult> outputPrivateList = new ArrayList<ScanResult>();    // store last scan private filtered result
    private WifiManager mWifiManager = null;
    private ConnectivityManager mConnectivityManager;
    private TelephonyManager mTelephonyManager;
    private boolean apConnected = false;
    private boolean scan = true;
    private boolean broadcastReceiverRegistered = false;
    private ArrayList<HashMap.Entry<String, ScanResult>> bestCarrierApList = null;
    private JSONObject apBlackList = null;
    private String bestCarrierSSID = null;
    private String bestCarrierBSSID = null;
    private String lastCarrierSSID = null;
    private String connectingSSID = null;
    private String connectingBSSID = null;
    private String lastSSID = null;
    private String lastBSSID = null;
    private String currentSSID = null;
    private String currentIP = null;
    private String passwordErrorSsid = "";
    private String connectingPrivateSsid = "";
    private String mac = null;
    private Result connectPrivateApResult = Result.ConnectPrivateApFailed;
    private boolean isWifiConnected = false;
    private boolean isWifiIpAllocated = false;
    private boolean isWifiDisconnected = false;
    private boolean isWifiPasswordError = false;
    private int queryCounter = Config.QUERY_COUNT;
    private int queriedBusinessApCounter = 0;
    private boolean isConnectingPrivateAp = false;
    private boolean isCancelingPrivateAp = false;
    private boolean isCreatedWifiConfig = false;
    private HashMap<String, JSONObject> apList = new HashMap<String, JSONObject>();    // key is BSSID, store ap data which will send to server
    private ArrayList<JSONObject> apData = new ArrayList<JSONObject>();    // store ap data from server
    private HashMap<String, JSONObject> lastCarrierApList = new HashMap<String, JSONObject>();    // key is BSSID
    private HashMap<String, JSONObject> lastPrivateApList = new HashMap<String, JSONObject>();    // key is BSSID
    private HashMap<String, String> queriedApList = new HashMap<String, String>();    // key is BSSID, cache queried ap list to reduce network traffic
    private HashMap<String, String> connectedList = new HashMap<String, String>();
    private Status lastStatus = Status.Initialize;

    private boolean isWifiPasswordVerifying;

    /**
     * Background collect and upload task
     */
    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                scanWifi();
                // force wifi scan at different interval, get MAC address when wifi connected
                if (isWifiConnected()) {
                    /*
                     * Mac address should only fetch once
                     */
                    if (!isMacAddressFetched()) {
                        setMacAddress(getCurrentMacAddress());
                    }

                    SDKManager.getInstance().getBackgroundHandler().postDelayed(this, Config.SLOW_SCAN_INTERVAL);
                } else {
                    SDKManager.getInstance().getBackgroundHandler().postDelayed(this, Config.FAST_SCAN_INTERVAL);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                try {
                    SDKManager.getInstance().getBackgroundHandler().postDelayed(this, Config.SLOW_SCAN_INTERVAL);
                } catch (Throwable e2) {
                    e2.printStackTrace();
                }
            }
        }
    };

    /**
     * Background wifi task
     */
    private Runnable wifiBackgroundRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!isConnectingPrivateAp() && !isConnectedPrivateAp() && connectedList != null && !connectedList.isEmpty()) {    // start removing connected aps by SDK
                    Log.w(MESSAGE_TAG, "Start removing cached password");
                    Iterator<?> iter = connectedList.entrySet().iterator();
                    while (iter.hasNext()) {
                        @SuppressWarnings("rawtypes")
                        HashMap.Entry entry = (HashMap.Entry) iter.next();
                        String ssid = (String) entry.getValue();
                        if (!ssid.equalsIgnoreCase(getCurrentSSID())) {
                            Log.w(MESSAGE_TAG, "Removing SSID: " + ssid + " BSSID: " + (String) entry.getKey());
                            removeAp(ssid, (String) entry.getKey());
                        }
                    }
                    connectedList.clear();
                }

                if (queriedBusinessApCounter > Config.STORE_BUSINESS_AP_THRESHOLD) {
                    queriedBusinessApCounter = 0;
                }
                SDKManager.getInstance().getBackgroundHandler().postDelayed(this, Config.SLOW_SCAN_INTERVAL);
            } catch (Throwable e) {
                e.printStackTrace();
                try {
                    SDKManager.getInstance().getBackgroundHandler().postDelayed(this, Config.SLOW_SCAN_INTERVAL);
                } catch (Throwable e2) {
                    e2.printStackTrace();
                }
            }
        }
    };
    private BroadcastReceiver receiver = new BroadcastReceiver() {    // all works will be put into async thread
        @SuppressLint("NewApi")
        public void onReceive(Context context, final Intent intent) {
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION) || intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                try {
                    SDKManager.getInstance().getBackgroundHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                updateConnectionStatus();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                if (/*!apConnected && */mWifiManager != null) {
                    try {
                        SDKManager.getInstance().getBackgroundHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (mWifiManager == null) {
                                        initManager(SDKManager.getInstance().getContext());
                                    }

                                    if (mWifiManager != null) {
                                        resultList = mWifiManager.getScanResults();
                                        outputCarrierList = outputCarrierList == null ? new ArrayList<ScanResult>() : outputCarrierList;
                                        outputCarrierList.clear();
                                        outputPrivateList = outputPrivateList == null ? new ArrayList<ScanResult>() : outputPrivateList;
                                        outputPrivateList.clear();
                                        HashMap<Integer, Integer> frequencyMap = new HashMap<Integer, Integer>();
                                        int threshold = Config.SIGNAL_LEVEL_THRESHOLD;
                                        if (lastStatus == Status.AvailableCarrier) {    // strict in, broaden out
                                            threshold = Config.SIGNAL_LEVEL_THRESHOLD_LOW;
                                        }

                                        for (ScanResult result : resultList) {
                                            // calculate ap counts for all frequencies
                                            if (frequencyMap.containsKey(result.frequency)) {
                                                frequencyMap.put(result.frequency, frequencyMap.get(result.frequency) + 1);
                                            } else {
                                                frequencyMap.put(result.frequency, 1);
                                            }

                                            if (getSecurity(result.capabilities) != 0) {        // private APs which require password, are store in private ap list
                                                outputPrivateList.add(result);
                                            } else if (result.level > threshold && !isInBlackList(result.SSID, result.BSSID)) {    // ssid must be in white list and not in black list
                                                // add carrier ap with strong signal only
                                                outputCarrierList.add(result);
                                            }
                                        }

                                        //					int max = threshold;
                                        // filter the aps which match the conditions
                                        final HashMap<String, ScanResult> mapCarrier = filterApList(outputCarrierList, frequencyMap, lastCarrierApList, Constant.Ap_Map_Carrier);    // ordered by key SSID
                                        final HashMap<String, ScanResult> mapPrivate = filterApList(outputPrivateList, frequencyMap, lastPrivateApList, Constant.Ap_Map_Private);    // ordered by key BSSID
                                        syncBestApList(outputCarrierList, lastCarrierApList);
                                        syncBestApList(outputPrivateList, lastPrivateApList);
                                        if (queryCounter > 0) {
                                            queryCounter -= 1;
                                        } else {
                                            queryCounter = Config.QUERY_COUNT;
                                        }

                                        if (!mapCarrier.isEmpty() && scan) {
                                            List<HashMap.Entry<String, ScanResult>> orderList;
                                            if (lastCarrierSSID != null && mapCarrier.containsKey(lastCarrierSSID)) {    // session alive duration and last connected SSID is in available list, just specify this SSID to connect
                                                HashMap<String, ScanResult> tmp = new HashMap<String, ScanResult>();
                                                tmp.put(lastCarrierSSID, mapCarrier.get(lastCarrierSSID));
                                                orderList = new ArrayList<HashMap.Entry<String, ScanResult>>(tmp.entrySet());
                                            } else {
                                                orderList = new ArrayList<HashMap.Entry<String, ScanResult>>(mapCarrier.entrySet());
                                            }

                                            // found valid carrier ap list, notify user
                                            bestCarrierApList = (ArrayList<Entry<String, ScanResult>>) orderList;
                                            bestCarrierSSID = trimQuotation(bestCarrierApList.get(0).getValue().SSID);
                                            bestCarrierBSSID = bestCarrierApList.get(0).getValue().BSSID;
                                            connectingSSID = bestCarrierSSID;
                                            connectingBSSID = bestCarrierBSSID;
                                            Log.w(MESSAGE_TAG, "Best SSID: " + bestCarrierSSID);
                                        }
                                    }
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }

            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    || intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                    || intent.getAction().equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                try {
                    SDKManager.getInstance().getBackgroundHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (mWifiManager == null) {
                                    initManager(SDKManager.getInstance().getContext());
                                }

                                if (mWifiManager != null) {
                                    WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                                    String processingSsid = trimQuotation(wifiInfo.getSSID());
                                    if (wifiInfo != null && !processingSsid.equalsIgnoreCase("0x") && !processingSsid.equalsIgnoreCase("<unknown ssid>")) {
                                        SupplicantState supplicantState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                                        Log.w(MESSAGE_TAG, "Supplicant state: " + supplicantState);
                                        if (supplicantState != null) {
                                            Log.d(MESSAGE_TAG, "Processing SSID " + processingSsid);

                                            /*
                                             * Only monitor password error for specific private ap.
                                             */
                                            if (isConnectingPrivateAp && processingSsid.equalsIgnoreCase(connectingPrivateSsid)) {
                                                if (intent.getAction().equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                                                    handleWifiPasswordError(processingSsid, intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1));
                                                }
                                            }

                                            if (supplicantState == SupplicantState.COMPLETED) {
                                                if (isConnectingPrivateAp && processingSsid.equalsIgnoreCase(connectingPrivateSsid)) {    // only monitor status changing when connect to private ap
                                                    isWifiConnected = true;
                                                }

                                                if (!isWifiIpAllocated && wifiInfo.getIpAddress() != 0) {
                                                    isWifiIpAllocated = true;
                                                    Log.d(MESSAGE_TAG, "Wifi " + getCurrentSSID() + " is connected.");
                                                    lastSSID = getCurrentSSID();
                                                    lastBSSID = getCurrentBSSID();
                                                    handleWifiConnected();
                                                }
                                            } else if (supplicantState == SupplicantState.FOUR_WAY_HANDSHAKE || supplicantState == SupplicantState.GROUP_HANDSHAKE) {
                                                isWifiPasswordVerifying = true;
                                            } else {
                                                isWifiIpAllocated = false;
                                            }
                                        }
                                    }

                                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                                    if (networkInfo != null) {
                                        NetworkInfo.State state = networkInfo.getState();
                                        if (state == NetworkInfo.State.DISCONNECTED) {
                                            if (isConnectingPrivateAp && processingSsid.equalsIgnoreCase(connectingPrivateSsid)) {    // only monitor status changing when connect to private ap
                                                isWifiConnected = false;
                                                isWifiIpAllocated = false;
                                                isWifiDisconnected = false;
                                            }

                                            handleWifiDisconnected();
                                            lastSSID = "";
                                            lastBSSID = "";
                                        }
                                    }
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleWifiPasswordError(String ssid, int error) {
            if (isConnectingPrivateAp && isWifiPasswordVerifying) {    // only handle requests from SDK
                if (error == WifiManager.ERROR_AUTHENTICATING) {
                    WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                    String processingSsid = trimQuotation(wifiInfo.getSSID());
                    Log.d(MESSAGE_TAG, "[ERROR_AUTHENTICATING] Processing SSID " + processingSsid);

                    if (processingSsid != null && processingSsid.equals(ssid)) {
                        isWifiPasswordError = true;
                        passwordErrorSsid = ssid;
                        Log.d(MESSAGE_TAG, "Remove password error ap " + ssid);
                        removeAp(ssid, null);    // remove password error ap to avoid further obstruction
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void handleWifiConnected() {
            try {
                if (apList.containsKey(getCurrentBSSID())) {    //
                    JSONObject entry = apList.get(getCurrentBSSID());
                    ArrayList<Long> list;
                    if (entry.has(Constant.StartTime)) {    // connected before, just add new entry into array
                        list = (ArrayList<Long>) entry.get(Constant.StartTime);
                    } else {
                        list = new ArrayList<Long>();
                    }

                    list.add(System.currentTimeMillis());
                    entry.put(Constant.Connected, true);
                    entry.put(Constant.StartTime, list);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @SuppressWarnings("unchecked")
        private void handleWifiDisconnected() {
            try {
                if (apList.containsKey(lastBSSID)) {
                    JSONObject entry = apList.get(lastBSSID);
                    ArrayList<Long> list;
                    if (entry.has(Constant.EndTime)) {    // connected before, just add new entry into array
                        list = (ArrayList<Long>) entry.get(Constant.EndTime);
                    } else {
                        list = new ArrayList<Long>();
                    }

                    list.add(System.currentTimeMillis());
                    entry.put(Constant.EndTime, list);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    };

    private WiFiManager() {
    }

    public static WiFiManager getInstance() {
        if (instance == null) {
            synchronized (WiFiManager.class) {
                if (instance == null) {
                    instance = new WiFiManager();
                }
            }
        }
        return instance;
    }

    /**
     * Set connecting private ap status.
     *
     * @param ssid  The ap SSID
     * @param bssid The ap BSSID
     * @param flag  Connecting flag
     */
    public void setConnectingPrivateAp(String ssid, String bssid, boolean flag) {
        this.connectingPrivateSsid = ssid;
        this.isConnectingPrivateAp = flag;
        this.isWifiPasswordError = false;
        this.passwordErrorSsid = "";
        if (!flag) {
            this.isCreatedWifiConfig = false;
        }
    }

    /**
     * Determine if is connecting private ap.
     */
    public boolean isConnectingPrivateAp() {
        return this.isConnectingPrivateAp;
    }

    /**
     * Determine if connecting to private ap is cancelled.
     */
    private boolean isCancelingPrivateAp() {
        return this.isCancelingPrivateAp;
    }

    /**
     * Set status of whether ap is connected.
     */
    public void setCancelingPrivateAp(boolean flag) {
        this.isCancelingPrivateAp = flag;
    }

    /**
     * Determine if connecting wifi password error.
     *
     * @param ssid The ap SSID
     * @return boolean Result whether password is error.
     */
    private boolean isWifiPasswordError(String ssid) {
        if (this.passwordErrorSsid.equalsIgnoreCase(ssid)) {
            return this.isWifiPasswordError;
        }

        return false;
    }

    /**
     * Determine if wifi is unable to connect.
     *
     * @param ssid The ap SSID
     * @return boolean Result if unable to connect.
     */
    private boolean isWifiUnableToConnect(String ssid) {
        if (this.isWifiDisconnected && this.connectingPrivateSsid.equalsIgnoreCase(ssid)) {
            return this.isWifiDisconnected;
        }

        return false;
    }

    /**
     * Determine if MAC address is fetched
     */
    public boolean isMacAddressFetched() {
        return this.mac != null ? true : false;
    }

    /**
     * Set MAC address.
     *
     * @param macAddress The MAC address
     */
    public void setMacAddress(String macAddress) {
        this.mac = macAddress;
    }

    /**
     * Initialize all manager instance.
     *
     * @param context The caller's context
     */
    private void initManager(Context context) {
        try {
            if (context == null) {
                context = SDKManager.getInstance().getContext();
            }

            if (context != null) {
                mWifiManager = mWifiManager == null ? (WifiManager) context.getSystemService(Context.WIFI_SERVICE) : mWifiManager;
                mConnectivityManager = mConnectivityManager == null ? (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE) : mConnectivityManager;
                mTelephonyManager = mTelephonyManager == null ? (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE) : mTelephonyManager;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize all lists.
     */
    private void initializeLists() {
        apList = apList == null ? new HashMap<String, JSONObject>() : apList;
        apData = apData == null ? new ArrayList<JSONObject>() : apData;    // store ap data from server
        outputCarrierList = outputCarrierList == null ? new ArrayList<ScanResult>() : outputCarrierList;
        outputPrivateList = outputPrivateList == null ? new ArrayList<ScanResult>() : outputPrivateList;
        lastCarrierApList = lastCarrierApList == null ? new HashMap<String, JSONObject>() : lastCarrierApList;
        lastPrivateApList = lastPrivateApList == null ? new HashMap<String, JSONObject>() : lastPrivateApList;
        queriedApList = queriedApList == null ? new HashMap<String, String>() : queriedApList;
        connectedList = connectedList == null ? new HashMap<String, String>() : connectedList;
    }

    /**
     * Initialize all data structures, register broadcast receiver, launch background scan and auto connect thread.
     */
    public void init() {
        try {
            Context context = SDKManager.getInstance().getContext();
            initializeLists();

            // start listen to system wifi events
            initManager(context);
            registerReceiver();
            SDKManager.getInstance().getBackgroundHandler().post(scanRunnable);
            enableScan();
            scanWifi();    // start scan immediately
            updateConnectionStatus();
            SDKManager.getInstance().getBackgroundHandler().post(wifiBackgroundRunnable);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Register broadcast receiver.
     */
    private void registerReceiver() {
        try {
            if (!this.broadcastReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
                filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
                filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
                filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                SDKManager.getInstance().getContext().registerReceiver(receiver, filter);
                this.broadcastReceiverRegistered = true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Uninitialize data structures, unregister broadcast receiver, stop scan and auto connect thread.
     */
    public void destroy() {
        try {
            Context context = SDKManager.getInstance().getContext();
            if (context != null && receiver != null && this.broadcastReceiverRegistered) {
                context.unregisterReceiver(receiver);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            this.broadcastReceiverRegistered = false;
            instance = null;
            mWifiManager = null;
            mConnectivityManager = null;
            apConnectionCallback = null;
            if (bestCarrierApList != null) {
                bestCarrierApList.clear();
            }
            apBlackList = null;

            if (SDKManager.getInstance().getBackgroundHandler() != null) {
                SDKManager.getInstance().getBackgroundHandler().removeCallbacks(scanRunnable);
            }

            if (apList != null) {
                apList.clear();
            }
            if (apData != null) {
                apData.clear();
            }
            if (resultList != null) {
                resultList.clear();
            }
            if (outputCarrierList != null) {
                outputCarrierList.clear();
            }
            if (outputPrivateList != null) {
                outputPrivateList.clear();
            }
            if (lastCarrierApList != null) {
                lastCarrierApList.clear();
            }
            if (lastPrivateApList != null) {
                lastPrivateApList.clear();
            }
            if (queriedApList != null) {
                queriedApList.clear();
            }
            if (connectedList != null) {
                connectedList.clear();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Wrapper of API disableNetwork.
     */
    public boolean disableNetwork(int networkId) {
        if (mWifiManager != null) {
            return mWifiManager.disableNetwork(networkId);
        }

        return false;
    }

    /**
     * Wrapper of API removeNetwork.
     */
    public boolean removeNetwork(int networkId) {
        if (mWifiManager != null) {
            return mWifiManager.removeNetwork(networkId);
        }

        return false;
    }

    /**
     * Determine if connected to a private ap by sdk.
     *
     * @return boolean Result whether connected.
     */
    public boolean isConnectedPrivateAp() {
        String ssid = getCurrentSSID();
        String bssid = getCurrentBSSID();
        if (!connectedList.isEmpty()) {    // connected list should be be emptry
            if (bssid != null && connectedList.containsKey(bssid)) {
                return true;
            }

            if (ssid != null && connectedList.containsValue(ssid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Connect to wifi using reflect method, calls connect after 4.2, calls connectNetwork in 4.0 ~ 4.1, doesn't handle before 4.0.
     *
     * @param netId The configuration network id
     * @return Method The reflect method.
     */
    private boolean connectWifiByReflectMethod(int netId) {
        boolean ret = false;
        if (netId < 0) {
            return ret;
        }

        try {
            Class<?> clazz = Class.forName("android.net.wifi.WifiManager");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Log.d(MESSAGE_TAG, "connectWifiByReflectMethod road 1");
                clazz.getMethod("connect", Integer.TYPE, Class.forName("android.net.wifi.WifiManager$ActionListener")).invoke(mWifiManager, netId, null);
                ret = true;
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                Log.d(MESSAGE_TAG, "connectWifiByReflectMethod road 2");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                Log.d(MESSAGE_TAG, "connectWifiByReflectMethod road 3");
                clazz.getMethod("connectNetwork", Integer.TYPE).invoke(mWifiManager, netId);
                ret = true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            ret = false;
        }

        return ret;
    }

    /**
     * Connect to specified secured wifi network with password.
     *
     * @param ssid     Wifi SSID to connect
     * @param password Wifi password
     * @param type     Wifi security type
     * @return networkId the connected network id, -1 if fails.
     */
    public int connectAccessPoint(String ssid, String bssid, String password, int type) {
        try {
            if (mWifiManager == null) {
                initManager(SDKManager.getInstance().getContext());
            }

            boolean connectPrivateAp = false;

            if (mWifiManager != null) {
                if (isUsingWifi()) {
                    mWifiManager.disconnect();
                }
            }

            WifiConfiguration config = getWifiConfiguration(ssid, bssid);
            WifiConfiguration createdConfig = createWifiConfiguration(ssid, password, type);
            if (type != Constant.AP_KEY_MGMT_OPEN) {
                connectPrivateAp = true;
            }

            if (config == null) {
                config = createdConfig;
                this.isCreatedWifiConfig = true;
            }

            int formerNetworkId = mWifiManager.updateNetwork(createdConfig);
            int networkId = formerNetworkId == -1 ? mWifiManager.addNetwork(createdConfig) : formerNetworkId;

            if (networkId < 0) {
                networkId = config.networkId;
            }

            if (networkId >= 0) {
                Log.w(MESSAGE_TAG, "Connecting SSID " + ssid + " with security " + type);
                isWifiPasswordVerifying = false;
                if (!connectWifiByReflectMethod(networkId)) {
                    Log.d(MESSAGE_TAG, "connect wifi normal road");
                    if (mWifiManager.enableNetwork(networkId, true)) {
                        if (connectPrivateAp) {
                            mWifiManager.saveConfiguration();    // important, otherwise the network won't switch immediately, even it'll not switch
                            mWifiManager.reconnect();
                        }
                        return networkId;
                    }
                } else {
                    return networkId;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Get SSID from supplied BSSID from scan result.
     *
     * @param bssid Wifi BSSID
     * @return ssid The corresponding SSID.
     */
    public String getSSIDByBSSID(String bssid) {
        String ssid = null;
        try {
            if (this.resultList != null) {    // search for resultList
                for (ScanResult entry : resultList) {
                    if (entry.BSSID.equalsIgnoreCase(bssid)) {
                        ssid = entry.SSID;
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return ssid;
    }

    /**
     * Get BSSID from supplied SSID from scan result.
     *
     * @param ssid Wifi BSSID
     * @return String The corresponding BSSID.
     */
    public String getBSSIDBySSID(String ssid) {
        String bssid = null;
        try {
            if (this.resultList != null) {    // search for resultList
                for (ScanResult entry : resultList) {
                    if (entry.SSID.equalsIgnoreCase(ssid)) {
                        bssid = entry.BSSID;
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return bssid;
    }

    /**
     * Create wifi configuration from supplied SSID and password
     *
     * @param ssid     Wifi SSID to connect
     * @param password Wifi password
     * @param type     Wifi security type
     * @return WifiConfiguration The created WifiConfiguration.
     */
    public WifiConfiguration createWifiConfiguration(String ssid, String password, int type) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.priority = 90000;

        if (type == Constant.AP_SECURITY_OPEN) {        // WIFICIPHER_NOPASS
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else if (type == Constant.AP_SECURITY_WEP) {    // WIFICIPHER_WEP
            config.hiddenSSID = true;
            config.wepKeys[0] = password;
            config.wepTxKeyIndex = 0;
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        } else if (type == Constant.AP_SECURITY_WPA) { // WIFICIPHER_WPA
            config.preSharedKey = "\"" + password + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.status = WifiConfiguration.Status.ENABLED;
        }

        return config;
    }

    /**
     * Set scan flag to false to disable scan
     */
    public void disableScan() {
        this.scan = false;
    }

    /**
     * Set scan flag to true to enable scan
     */
    public void enableScan() {
        this.scan = true;
    }

    /**
     * Get result of connect private ap result process
     *
     * @return Result The result after connect private returned.
     */
    public Result getConnectPrivateApResult() {
        return this.connectPrivateApResult;
    }

    /**
     * Set result of connect private ap result process
     *
     * @param result The result to set
     */
    public void setConnectPrivateApResult(Result result) {
        this.connectPrivateApResult = result;
    }

    /**
     * Get the depth of current available carrier ap list.
     *
     * @return int Depth of current available carrier ap list.
     */
    public int getCarrierApListDepth() {
        return this.bestCarrierApList != null ? this.bestCarrierApList.size() : 0;
    }

    /**
     * Connect to the best open wifi network with specified priority.
     *
     * @param priority The index of carrier network to connect.
     * @return boolean Whether connection is successed.
     */
    public boolean connectBestOpenAp(int priority) {
        String ssid = getBestCarrierSSID(priority);
        Log.w(MESSAGE_TAG, "Connecting best SSID: " + ssid);
        connectingSSID = ssid;
        if (connectAccessPoint(ssid, null, null, Constant.AP_SECURITY_OPEN) != -1) {
            return true;
        }

        return false;
    }

    /**
     * Connect to the best open free wifi network with no password
     *
     * @return boolean Whether connection is successed.
     */
    public boolean connectBestFreeAp() {
        try {
            if (mWifiManager == null) {
                initManager(SDKManager.getInstance().getContext());
            }

            if (mWifiManager != null) {
                scanWifi();
                Utils.waitFor(3 * Constant.DELAY_TIME_UNIT);
                List<ScanResult> list = mWifiManager.getScanResults();
                ArrayList<String> connectedAp = new ArrayList<String>();
                int networkId;
                for (ScanResult result : list) {
                    if (getSecurity(result.capabilities) == 0) {            // public APs
                        if (result.level <= Config.SIGNAL_LEVEL_THRESHOLD) {
                            continue;
                        }

                        String ssid = result.SSID;
                        String bssid = result.BSSID;
                        Log.w(MESSAGE_TAG, "Connecting best SSID: " + ssid);
                        SupplicantState state = SupplicantState.INACTIVE;
                        if (!connectedAp.contains(bssid) && (networkId = connectAccessPoint(ssid, bssid, null, Constant.AP_SECURITY_OPEN)) != -1) {    // not connected before
                            for (int i = 0; i < 6; i++) {
                                Utils.waitFor(2 * Constant.DELAY_TIME_UNIT);
                                WifiInfo info = mWifiManager.getConnectionInfo();
                                state = info.getSupplicantState();
                                Log.w(MESSAGE_TAG, "State: " + state);
                                if (state == SupplicantState.COMPLETED) {    // authentication completed
                                    connectedAp.add(bssid);
                                    removeNetwork(networkId);
                                    mWifiManager.saveConfiguration();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Connect to target private wifi by supplied SSID, BSSID and security
     *
     * @param ssid     Target private's SSID
     * @param bssid    Target private's BSSID
     * @param security Target private's security
     * @return boolean Result whether connected successfully.
     */
    public boolean connectTargetPrivateAp(String ssid, String bssid, String password, int security) {
        try {
            if (connectPrivateAp(ssid, bssid, password, security)) {
                JSONObject tmp = new JSONObject();
                try {
                    tmp.put(Constant.SSID, ssid);
                    tmp.put(Constant.BSSID, bssid);
                    tmp.put(Constant.Rssi, WiFiManager.getInstance().getRssi());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                if (WiFiManager.apConnectionCallback != null) {
                    WiFiManager.apConnectionCallback.apConnectionNotification(tmp);
                }
                return true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            setConnectPrivateApResult(Result.Failed);
        }

        return false;
    }

    /**
     * Connect to target private wifi by supplied SSID, BSSID, password and security
     *
     * @param ssid     Target private wifi's SSID
     * @param bssid    Target private wifi's BSSID
     * @param password Target private wifi's password
     * @param security Target private wifi's security
     * @return boolean Result whether connected successfully.
     */
    private boolean connectPrivateAp(final String ssid, final String bssid, String password, int security) {
        int initCount = 2;
        int networkId = 0;

        try {
            if (Utils.validateString(ssid) && ssid.equals(getCurrentSSID()) && WiFiManager.getInstance().isWifiConnected(ssid)) {
                return true;
            }

            setConnectingPrivateAp(ssid, bssid, true);
            if ((networkId = connectAccessPoint(ssid, bssid, password, security)) != -1) {
                for (int i = 0; i < Config.CONNECTING_LOOP_COUNT; i++) {
                    /**
                     * Check again to prevent inconsistent status
                     */
                    if (isConnectingPrivateAp()) {
                        /**
                         * Check if private ap connection is being canceled, if so, remove the network, reset the status
                         */
                        if (isCancelingPrivateAp()) {
                            setConnectingPrivateAp("", "", false);
                            setConnectPrivateApResult(Result.PrivateApConnectionCancelled);
                            setCancelingPrivateAp(false);
                            if (mWifiManager != null) {
                                mWifiManager.removeNetwork(networkId);
                                mWifiManager.saveConfiguration();
                            }
                            return false;
                        }

                        Utils.waitFor(initCount * Constant.DELAY_TIME_UNIT);
                        if (this.isWifiConnected) {    // authentication completed, now wait for IP been allocated
                            Utils.waitFor(initCount * Constant.DELAY_TIME_UNIT);
                            if (WiFiManager.getInstance().isWifiConnected(ssid)) {
                                if (this.isCreatedWifiConfig) {
                                    connectedList.put(bssid, ssid);
                                }

                                setConnectingPrivateAp("", "", false);
                                Log.w(MESSAGE_TAG, "Private ap connected.");
                                return true;
                            }
                        }

	                    /*
                         * Won't retry for password error or disconnected
	                     */
                        if (isWifiPasswordError(ssid) || (i >= Config.CONNECTING_LOOP_COUNT / 2 && isWifiUnableToConnect(ssid))) {
                            break;
                        }
                    }
                }
            }

            /*
             * For some scenario, even password error is returned, ap is still connected, this case will be treated as success
             */
            if (isWifiConnected(ssid)) {
                if (this.isCreatedWifiConfig) {
                    connectedList.put(bssid, ssid);
                }

                setConnectingPrivateAp("", "", false);
                Log.w(MESSAGE_TAG, "Private ap connected.");
                return true;
            }

            setConnectPrivateApResult(Result.ConnectPrivateApFailed);
            if (isWifiPasswordError(ssid)) {    // password error is caused by current connecting ssid, not the ssid connected asyncly by system
                Log.w(MESSAGE_TAG, "Password error.");
                setConnectPrivateApResult(Result.PrivateApPasswordError);
                Log.w(MESSAGE_TAG, "Password error ssid-->" + passwordErrorSsid);
            }

            /**
             * Cleanup the failed aps
             */
            if (mWifiManager != null) {
                mWifiManager.removeNetwork(networkId);
                mWifiManager.saveConfiguration();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        setConnectingPrivateAp("", "", false);
        return false;
    }

    private boolean toggleWifi(boolean enabled) {
        if (mWifiManager != null) {
            return mWifiManager.setWifiEnabled(enabled);
        }

        return false;
    }

    public void removeCurrentAp() {
        removeAp(getCurrentSSID(), getCurrentBSSID());
    }

    public void removeAp(String ssid, String bssid) {
        try {
            if (mWifiManager != null && (ssid != null || bssid != null)) {
                List<WifiConfiguration> list = mWifiManager.getConfiguredNetworks();
                if (list != null) {
                    for (WifiConfiguration conf : list) {
                        if (conf != null && ((conf.SSID != null && trimQuotation(conf.SSID).equalsIgnoreCase(ssid)) || (conf.BSSID != null && conf.BSSID.equalsIgnoreCase(bssid)))) {
                            if (mWifiManager.removeNetwork(conf.networkId)) {
                                mWifiManager.saveConfiguration();
                            } else {
                                mWifiManager.disableNetwork(conf.networkId);
                                mWifiManager.disconnect();
                            }

                            Log.w(MESSAGE_TAG, "Network " + ssid + " successfully removed.");
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public String formatIpAddress(int ip) {
        return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    }

    public boolean isCarrierApConnected() {
        if (isWifiConnected()) {
            WifiConfiguration conf = getCurrentWifiConfiguration();
            if (conf != null) {
                if (!conf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {    // ignore all encrypted networks
                    return false;
                }
            }
        }

        updateConnectionStatus();
        return this.apConnected;
    }

    public String getCurrentSSID() {
        String ssid = "";
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            ssid = trimQuotation(wifiInfo.getSSID());
        }
        return ssid;
    }

    public String getCurrentBSSID() {
        String bssid = "";
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            bssid = wifiInfo.getBSSID();
        }
        return bssid;
    }

    public String getCurrentMacAddress() {
        String mac = "";
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            mac = wifiInfo.getMacAddress();
        }

        return mac;
    }

    public WifiConfiguration getCurrentWifiConfiguration() {
        return getWifiConfiguration(getCurrentSSID(), getCurrentBSSID());
    }

    public WifiConfiguration getWifiConfiguration(String ssid, String bssid) {
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            List<WifiConfiguration> list = mWifiManager.getConfiguredNetworks();
            if (list != null) {
                for (WifiConfiguration conf : list) {
                    if ((Utils.validateString(bssid) && Utils.validateString(conf.BSSID) && conf.BSSID.equalsIgnoreCase(bssid)) || (Utils.validateString(ssid) && Utils.validateString(conf.SSID) && trimQuotation(conf.SSID).equalsIgnoreCase(ssid))) {
                        return conf;
                    }
                }
            }
        }

        return null;
    }

    public String getConnectingSSID() {
        return this.connectingSSID;
    }

    public boolean disconnect() {
        boolean flag = false;
        if (mWifiManager != null) {
            flag = mWifiManager.disconnect();
        }

        return flag;
    }

    public String getBestCarrierSSID(int priority) {
        String ssid = null;
        try {
            if (priority == 0) {
                ssid = this.bestCarrierSSID != null ? this.bestCarrierSSID : (Config.DEFAULT_NETWORK_SWITCH ? Constant.defaultNetwork : null);
            } else if (priority > 0 && this.bestCarrierApList != null && !this.bestCarrierApList.isEmpty() && priority <= this.bestCarrierApList.size()) {
                ssid = this.bestCarrierSSID = this.bestCarrierApList.get(priority).getValue().SSID;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return ssid;
    }

    public void setBestCarrierSSID(String ssid) {
        this.bestCarrierSSID = ssid;
    }

    public String getBestCarrierBSSID(int priority) {
        String bssid = null;
        try {
            if (priority == 0) {
                bssid = this.bestCarrierBSSID != null ? this.bestCarrierBSSID : null;
            } else if (priority > 0 && this.bestCarrierApList != null && !this.bestCarrierApList.isEmpty() && priority <= this.bestCarrierApList.size()) {
                bssid = this.bestCarrierBSSID = this.bestCarrierApList.get(priority).getValue().BSSID;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return bssid;
    }

    public String getCurrentIP() {
        String ip = "";
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            ip = formatIpAddress(wifiInfo.getIpAddress());
        }

        return ip;
    }

    public boolean isWifiConnected() {
        boolean flag = false;
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            flag = (mWifiManager.isWifiEnabled() && isUsingWifi() && wifiInfo.getIpAddress() != 0) ? true : false;//mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        }

        return flag;
    }

    public boolean isWifiConnected(String ssid) {
        boolean flag = false;
        WifiInfo wifiInfo = getConnectionInfo();
        if (mWifiManager != null && wifiInfo != null) {
            flag = (mWifiManager.isWifiEnabled() && /*isUsingWifi() && */wifiInfo.getIpAddress() != 0 && getCurrentSSID().equalsIgnoreCase(ssid)) ? true : false;//mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        }

        return flag;
    }

    public WifiInfo getConnectionInfo() {
        return mWifiManager != null ? mWifiManager.getConnectionInfo() : null;
    }

    public DhcpInfo getDhcpInfo(Context context) {
        DhcpInfo info = null;
        try {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            }

            info = mWifiManager.getDhcpInfo();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return info;
    }

    public String getGateWay() {
        int ip = 0;
        try {
            if (mWifiManager != null) {
                ip = mWifiManager.getDhcpInfo().gateway;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return formatIpAddress(ip);
    }

    public int getRssi() {        // if no best ap found or is online already, return real time rssi, otherwise return the rssi from the scan result
        int rssi = 0;
        try {
            rssi = mWifiManager.getConnectionInfo().getRssi();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return rssi;
    }

    public boolean isUsingGprs() {
        try {
            if (mConnectivityManager == null) {
                initManager(SDKManager.getInstance().getContext());
            }

            if (mConnectivityManager != null) {
                android.net.NetworkInfo.State state = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState();
                if (state == android.net.NetworkInfo.State.CONNECTED) {
                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isUsingWifi() {
        try {
            if (mConnectivityManager == null) {
                initManager(SDKManager.getInstance().getContext());
            }

            if (mConnectivityManager != null) {
                android.net.NetworkInfo.State state = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
                if (state == android.net.NetworkInfo.State.CONNECTED) {
                    return true;
                }

                NetworkInfo info = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (info != null && info.isAvailable() && info.isConnected()) {
                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Detect if current network is available to fetch data, either connected to wifi or connected to gprs and is not network roaming
     *
     * @return boolean Network availability
     */
    public boolean isNetworkAvailable() {
        try {
            if (mConnectivityManager == null) {
                initManager(SDKManager.getInstance().getContext());
            }

            if (mConnectivityManager != null) {
                NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
                if (info == null) {
                    return false;
                }

                int netType = info.getType();
                if (netType == ConnectivityManager.TYPE_WIFI) {
                    return info.isConnected();
                } else if (netType == ConnectivityManager.TYPE_MOBILE) {
                    if (info.isRoaming() || mTelephonyManager.isNetworkRoaming()) {    // 2G/3G/4G roaming
                        return false;
                    }

                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean scanWifi() {
        if (Config.SCAN_WIFI_SWITCH) {
            if (mWifiManager != null) {
                return mWifiManager.startScan();
            }

            return false;
        } else {
            return true;
        }
    }

    public List<ScanResult> getScanResults() {
        if (mWifiManager != null) {
            return mWifiManager.getScanResults();
        }
        return null;
    }

    public JSONObject getApBlackList() {
        if (Config.BLACK_LIST_SWITCH) {
            try {
                if (this.apBlackList != null) {
                    return this.apBlackList;
                } else {
                    return new JSONObject(PersistentModule.getInstance().getString(Constant.BlackList));
                }
            } catch (Throwable e) {
            }
        }
        return new JSONObject();
    }

    public void setApBlackList(String list) {
        if (Config.BLACK_LIST_SWITCH) {
            try {
                PersistentModule.getInstance().putString(Constant.BlackList, list);
                this.apBlackList = new JSONObject(list);
            } catch (Throwable e) {
            }
        }
    }

    // judge if current ssid is in white list
    private boolean isInBlackList(String ssid, String mac) {
        if (Config.BLACK_LIST_SWITCH) {
            if (ssid == null || apBlackList == null) {
                return false;
            }
            ssid = trimQuotation(ssid);
            try {
                if (apBlackList.has(mac)) {
                    if (apBlackList.getString(mac).equalsIgnoreCase(ssid)) {
                        return true;
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    // refresh current wifi connection status
    public void updateConnectionStatus() {
        boolean connected = false;
        try {
            if (mWifiManager == null) {
                initManager(SDKManager.getInstance().getContext());
            }

            if (mWifiManager != null) {
                WifiInfo info = getConnectionInfo();
                //if (info != null && info.getSupplicantState().equals(SupplicantState.COMPLETED)) {
                if (info != null && info.getIpAddress() != 0/*isWifiConnected()/*WifiInfo.getDetailedStateOf(info.getSupplicantState()).equals(DetailedState.CONNECTED).getSupplicantState().equals(SupplicantState.COMPLETED)*/) {
                    this.apConnected = true;
                    connected = true;
                    this.currentSSID = trimQuotation(info.getSSID());
                    if (!this.currentSSID.equals("") && this.currentSSID != null) {
                        this.lastCarrierSSID = currentSSID;
                    }
                    this.currentIP = formatIpAddress(info.getIpAddress());
                    Log.w(MESSAGE_TAG, this.currentIP);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (!connected) {
            this.apConnected = false;    // update the status
        }
    }

    public int getSecurity(String capabilities) {
        if (capabilities.toUpperCase().contains("WEP")) {
            return Constant.AP_SECURITY_WEP;
        } else if (capabilities.toUpperCase().contains("PSK")) {
            return Constant.AP_SECURITY_WPA;
        } else if (capabilities.toUpperCase().contains("EAP")) {
            return Constant.AP_SECURITY_EAP;
        }

        return Constant.AP_SECURITY_OPEN;
    }

    // sync outputList to lastApList
    private void syncBestApList(List<ScanResult> outputList, HashMap<String, JSONObject> lastApList) {
        JSONObject obj;
        if (outputList == null || lastApList == null) {
            return;
        }

        try {
            HashMap<String, JSONObject> tempList = new HashMap<String, JSONObject>();    // it's possible that in the same area multiple BSSID assign to the same SSID
            for (ScanResult result : outputList) {
                if (lastApList.containsKey(result.BSSID)) {
                    obj = lastApList.get(result.BSSID);
                    int available = obj.getInt(Constant.Available);
                    if (available < Constant.STAY_THRESHOLD) {
                        obj.put(Constant.Available, available + 1);
                    }
                } else {    // insert new entry
                    obj = new JSONObject();
                    obj.put(Constant.Available, 1);
                }

                obj.put(Constant.BSSID, result.BSSID);
                obj.put(Constant.SSID, result.SSID);
                obj.put(Constant.Security, getSecurity(result.capabilities));
                obj.put(Constant.Level, result.level);
                obj.put(Constant.Frequency, result.frequency);
                tempList.put(result.BSSID, obj);
            }

            lastApList.clear();
            Iterator<?> iter = tempList.entrySet().iterator();
            while (iter.hasNext()) {
                @SuppressWarnings("rawtypes")
                HashMap.Entry entry = (HashMap.Entry) iter.next();
                String key = (String) entry.getKey();
                JSONObject val = (JSONObject) entry.getValue();
                //			    this.lastCarrierApList.put(key, val);
                lastApList.put(key, val);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, ScanResult> filterApList(List<ScanResult> outputList, HashMap<Integer, Integer> frequencyMap, HashMap<String, JSONObject> lastApList, int type) {
        HashMap<String, ScanResult> map = new HashMap<String, ScanResult>();
        for (ScanResult result : outputList) {
            try {
                if (lastApList != null && (lastApList.containsKey(result.BSSID) && lastApList.get(result.BSSID).getInt(Constant.Available) >= Constant.STAY_THRESHOLD) || Config.NOTIFY_IMMEDIATELY_SWITCH) {
                    if (frequencyMap.get(result.frequency) > Config.SAME_FREQUENCY_THRESHOLD) {
                        // ignore carrier ap if too many aps in same frequency
                        continue;
                    }

                    if (type == Constant.Ap_Map_Carrier) {
                        map.put(result.SSID, result);
                    } else if (type == Constant.Ap_Map_Private) {
                        map.put(result.BSSID, result);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return map;
    }

    // remove " in ssid name for some android systems
    public String trimQuotation(String ssid) {
        if (ssid == null) {
            return null;
        }
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

}
