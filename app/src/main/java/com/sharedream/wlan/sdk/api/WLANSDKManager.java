package com.sharedream.wlan.sdk.api;

import com.sharedream.wlan.sdk.manager.SDKManager;

import org.json.JSONObject;

public class WLANSDKManager {
    // async version register, init SDK before all other API called
    public static void registerApp(JSONObject parameters, AsyncActionResult resultAction) {
        SDKManager.getInstance().registerAppAsync(parameters, resultAction);
    }

    // async version online, login to carrier network if available
    public static void online(JSONObject parameter, AsyncActionResult resultAction) {
        SDKManager.getInstance().onlineAsync(parameter, resultAction);
    }

    // async version offline, logoff from carrier network
    public static void offline(AsyncActionResult resultAction) {
        SDKManager.getInstance().offlineAsync(resultAction);
    }

    // carrier network status
    public static enum Status {
        Initialize, AvailableCarrier, AvailableCommerce, AvailableAll, NotAvailable, Offline, StartConnecting, Attaching, Authenticating
    }

    // all function return values
    public static enum Result {
        Success, Failed, AlreadyLogin, AccountError, Break, RegisterExpired, NetworkBad, AttachNetworkTimeout, RequireRegister, LoginTooFrequently, AuthenticationFailed, InconsistentBssid, ServerRefused, GetAccountFailed, CommunicateNetworkFailed, CommunicateServerTimeout, CommunicateServerError, CommunicateServerStatusError, CommunicateServerNoNetwork, NoLifetime, PortalLoginFailed, PortalTimeout, VerificationFailed, TokenMismatch, ParametersMismatch, BlacklistAp, OfflineConditionError, RedirectFailed, ConnectBestPrivateApFailed, ConnectPrivateApFailed, ConnectFreeApFailed, InvalidCarrierId, InternalStorageError, PrivateApPasswordError, PrivateApDisabled, PrivateApNotAvailable, PrivateApConnectionCancelled, SessionExpired, UnknownReturnCode, LogoutForciblyFailed, NetworkNotAvailableCurrently
    }

    public static interface ConnectionCallback {
        public void apConnectionNotification(JSONObject info);
    }

    // async called from internel thread to UI thread
    public static interface AsyncActionResult {
        public void handleResult(Result result);
    }

}
