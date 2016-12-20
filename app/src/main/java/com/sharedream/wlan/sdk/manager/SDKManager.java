package com.sharedream.wlan.sdk.manager;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.sharedream.wlan.sdk.api.WLANSDKManager.AsyncActionResult;
import com.sharedream.wlan.sdk.api.WLANSDKManager.Result;
import com.sharedream.wlan.sdk.conf.Config;
import com.sharedream.wlan.sdk.conf.Constant;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class SDKManager {
    private final static String MESSAGE_TAG = "WLANSDK";
    private static SDKManager instance = null;
    private static Context context = null;
    private static ArrayList<Handler> mHandlers = new ArrayList<Handler>();
    private static ArrayList<HandlerThread> mThreads = new ArrayList<HandlerThread>();
    Result lastError = Result.Failed;
    private WiFiManager mWifi = null;
    private boolean authorized = false;
    private String SSID = null;
    private String BSSID = null;
    private ArrayList<Long> onlineHistory = new ArrayList<Long>();

    private SDKManager() {
        try {
            Log.w(MESSAGE_TAG, "Initializing SDKManager instance...Context is initialized: " + (context != null) + " mMainThread is initialized: " + (mThreads.size() < Constant.THREAD_COUNT));
            onlineHistory = onlineHistory == null ? new ArrayList<Long>() : onlineHistory;
            // create ui handler in current thread to handle user callbacks
            // create long time thread for all network operations
            if (mThreads.size() < Constant.THREAD_COUNT) {
                initThreads();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static SDKManager getInstance() {
        if (instance == null || mThreads.size() < Constant.THREAD_COUNT) {
            synchronized (SDKManager.class) {
                if (instance == null || mThreads.size() < Constant.THREAD_COUNT) {
                    instance = new SDKManager();
                }
            }
        }
        return instance;
    }

    private void initThreads() {
        try {
            for (int i = 0; i < Constant.THREAD_COUNT; i++) {
                HandlerThread thread = new HandlerThread(Constant.handlerThread + i);
                thread.setDaemon(true);
                thread.start();
                mThreads.add(thread);
                Handler handler = new Handler(thread.getLooper());
                mHandlers.add(handler);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public Context getContext() {
        return context;
    }

    public String getSSID() {
        return this.SSID == null ? WiFiManager.getInstance().getConnectingSSID() : this.SSID;
    }

    public String getBSSID() {
        return this.BSSID == null ? WiFiManager.getInstance().getCurrentBSSID() : this.BSSID;
    }

    public Handler getMainHandler() {
        try {
            if (mHandlers.size() < Constant.THREAD_COUNT) {
                initThreads();
            }

            return mHandlers.get(Constant.MAIN_THREAD_INDEX);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Handler getSecondaryHandler() {
        try {
            if (mHandlers.size() < Constant.THREAD_COUNT) {
                initThreads();
            }

            return mHandlers.get(Constant.SECONDARY_THREAD_INDEX);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Handler getBackgroundHandler() {
        try {
            if (mHandlers.size() < Constant.THREAD_COUNT) {
                initThreads();
            }

            return mHandlers.get(Constant.BACKGROUND_THREAD_INDEX);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isRegistered() {
        return this.authorized;
    }

    public void registerAppAsync(final JSONObject parameters, final AsyncActionResult resultAction) {
        try {
            if (mHandlers.size() < Constant.THREAD_COUNT) {
                initThreads();
            }

            if (mHandlers.size() >= Constant.THREAD_COUNT) {
                getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Result result = registerApp(parameters);    // the async version of interface is not fast version, which might involve network operations
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Log.i(MESSAGE_TAG, "Register result: " + result);
                                        if (resultAction != null) {
                                            resultAction.handleResult(result);
                                        }
                                    } catch (Throwable e) {
                                        Log.e(MESSAGE_TAG, e.getMessage() == null ? "No error mesage" : e.getMessage());
                                    }
                                }
                            });
                            if (result != Result.Success) {
                                lastError = result;
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Throwable e) {
            Log.e(MESSAGE_TAG, e.getMessage() == null ? "No error mesage" : e.getMessage());
        }
    }

    private boolean fastRegisterIfRequire() {
        if (isRegistered()) {
            return true;
        } else if (getContext() == null) {
            return false;
        }

        JSONObject parameter = new JSONObject();
        try {
            parameter.put(Constant.Context, getContext());
            parameter.put(Constant.FastRegister, true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return registerApp(parameter) == Result.Success ? true : false;
    }

    // register to SDK before all other API called
    public Result registerApp(JSONObject parameters) {
        Result status = Result.Failed;
        try {
            /**
             * If registered before, return success. Register flag will be stored both in memory and configuration file, however for some situation, the memory will be recycled by system,
             * we need to register again
             */
            if (this.authorized/* || PersistentModule.getInstance().getBoolean(Constant.authorized, false)*/) {
                if (parameters.has(Constant.Context)) {
                    SDKManager.context = (Context) parameters.opt(Constant.Context);
                }

                this.authorized = true;
                return Result.Success;
            }

            SDKManager.context = (Context) parameters.opt(Constant.Context);
            Config.loadConfig();
            mWifi = WiFiManager.getInstance();
            this.authorized = true;
            mWifi.init();

            Log.w(MESSAGE_TAG, "Register successed.");
            return Result.Success;    // don't log for register success since it's too many
        } catch (Throwable e) {
            e.printStackTrace();
        }

        this.authorized = false;
        return Result.Failed;
    }

    // handle online request in main thread
    public void onlineAsync(final JSONObject parameter, final AsyncActionResult resultAction) {
        try {
            if (mHandlers.size() < Constant.THREAD_COUNT) {
                initThreads();
            }

            if (getMainHandler() != null) {
                getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Result result = online(parameter);
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Log.w(MESSAGE_TAG, "Online result: " + result);
                                        if (resultAction != null) {
                                            resultAction.handleResult(result);
                                        }
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            if (result != Result.Success) {
                                lastError = result;
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // login to carrier network, and set auto logout conditions
    public Result online(JSONObject parameter) {
        if (parameter == null) {
            return Result.ParametersMismatch;
        }

        if (Config.CONNECT_PRIVATE_AP_SWITCH) {
            return WiFiManager.getInstance().connectTargetPrivateAp(parameter.optString(Constant.SSID), parameter.optString(Constant.BSSID), parameter.optString(Constant.Password), parameter.optInt(Constant.Security) == 0 ? 2 : parameter.optInt(Constant.Security)) ? Result.Success : WiFiManager.getInstance().getConnectPrivateApResult();
        } else {
            return Result.PrivateApDisabled;
        }
    }

    // handle offline request in main thread
    public void offlineAsync(final AsyncActionResult resultAction) {
        try {
            if (mHandlers.size() < Constant.THREAD_COUNT) {
                initThreads();
            }

            if (getMainHandler() != null) {
                getMainHandler().removeCallbacksAndMessages(null);
            }

            if (getSecondaryHandler() != null) {
                getSecondaryHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Result result = offline();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Log.w(MESSAGE_TAG, "Offline result: " + result);
                                        if (resultAction != null) {
                                            resultAction.handleResult(result);
                                        }
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            if (result != Result.Success) {
                                lastError = result;
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // logoff from carrier network
    public Result offline() {
        Result result = Result.Failed;
        if (!fastRegisterIfRequire()) {
            return Result.RequireRegister;
        }

    	/*
         * If currently is in the duration of connecting to a private ap, cancel the process
    	 */
        if (WiFiManager.getInstance().isConnectingPrivateAp()) {
            WiFiManager.getInstance().setCancelingPrivateAp(true);
        }

        if (!WiFiManager.getInstance().isCarrierApConnected()) {    // neither connected to carrier network nor supply carrier ssid to offline
            if (WiFiManager.getInstance().isUsingWifi()) {    // just disconnect current wifi
                String privateSsid = WiFiManager.getInstance().getCurrentSSID();
                String privateBssid = WiFiManager.getInstance().getCurrentBSSID();
                if (WiFiManager.getInstance().isConnectedPrivateAp()) {
                    WiFiManager.getInstance().removeAp(privateSsid, privateBssid);
                } else {
                    WiFiManager.getInstance().disconnect();
                }

                return Result.Success;
            }
            return Result.OfflineConditionError;
        }

        try {
            if (WiFiManager.getInstance().isCarrierApConnected()) {    // switch network if connected
                WiFiManager.getInstance().removeCurrentAp();
                result = Result.Success;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    public void deregisterApp() {
        try {
            if (WiFiManager.getInstance().isConnectedPrivateAp()) {
                WiFiManager.getInstance().removeCurrentAp();
            }

            if (fastRegisterIfRequire()) {
                WiFiManager.getInstance().destroy();
            }

            instance = null;
            context = null;
            mWifi = null;
            SSID = null;
            BSSID = null;
            if (onlineHistory != null) {
                onlineHistory.clear();
            }
            onlineHistory = null;

            lastError = null;
            if (mThreads.size() > 0) {
                for (HandlerThread t : mThreads) {
                    t.quit();
                }

                mThreads.clear();
            }

            if (mHandlers.size() > 0) {
                mHandlers.clear();
            }

            //            PersistentModule.getInstance().putBoolean(Constant.authorized, false);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

}