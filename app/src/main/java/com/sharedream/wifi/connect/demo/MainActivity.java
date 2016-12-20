package com.sharedream.wifi.connect.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.sharedream.connect.wifi.demo.R;
import com.sharedream.wlan.sdk.api.WLANSDKManager;
import com.sharedream.wlan.sdk.conf.Constant;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();

        findViewById(R.id.btn_online).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ssid = ((EditText) findViewById(R.id.et_ssid)).getText().toString();
                String password = ((EditText) findViewById(R.id.et_ssid_password)).getText().toString();

                int securityType = Constant.AP_SECURITY_WPA;
                JSONObject jsonParams = new JSONObject();
                try {
                    jsonParams.put(Constant.SSID, ssid);
                    jsonParams.put(Constant.Security, securityType);
                    jsonParams.put(Constant.Password, password);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                WLANSDKManager.online(jsonParams, new WLANSDKManager.AsyncActionResult() {
                    @Override
                    public void handleResult(WLANSDKManager.Result result) {
                        Toast.makeText(getApplicationContext(), "Online Result: " + result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        findViewById(R.id.btn_offline).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WLANSDKManager.offline(new WLANSDKManager.AsyncActionResult() {
                    @Override
                    public void handleResult(WLANSDKManager.Result result) {
                        Toast.makeText(getApplicationContext(), "Offline Result: " + result, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void init() {
        JSONObject jsonParams = new JSONObject();
        try {
            jsonParams.put("Context", getApplicationContext());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        WLANSDKManager.registerApp(jsonParams, new WLANSDKManager.AsyncActionResult() {
            @Override
            public void handleResult(WLANSDKManager.Result result) {
                Toast.makeText(getApplicationContext(), "Init Result: " + result, Toast.LENGTH_LONG).show();
            }
        });
    }

}
