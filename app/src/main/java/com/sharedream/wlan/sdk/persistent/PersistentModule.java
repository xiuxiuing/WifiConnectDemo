package com.sharedream.wlan.sdk.persistent;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.sharedream.wlan.sdk.conf.Constant;
import com.sharedream.wlan.sdk.manager.SDKManager;
import com.sharedream.wlan.sdk.utils.EncryptionModule;
import com.sharedream.wlan.sdk.utils.Utils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class PersistentModule {
    private static final String MESSAGE_TAG = "WLANSDK";
    private static PersistentModule mInstance = null;
    private static Context mContext = null;
    private static JSONObject mPersistent = null;

    private PersistentModule() {
        loadPersistent();
    }

    public static PersistentModule getInstance() {
        if (mInstance == null) {
            synchronized (PersistentModule.class) {
                if (mInstance == null) {
                    mInstance = new PersistentModule();
                }
            }
        }

        if (mPersistent == null || mPersistent.length() == 0) {
            loadPersistent();
        }

        return mInstance;
    }

    private static void loadPersistent() {
        try {
            if (mPersistent == null || mPersistent.length() == 0) {
                mPersistent = exists(Constant.persistentBytes) ? new JSONObject(new String(readFile(Constant.persistentBytes))) : new JSONObject();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            delete(Constant.persistentBytes);    // read file failed, delete the file
        }
    }

    public static byte[] readFile(String fileName) {
        return readFile(fileName, Constant.DEFAULT_PERSISTENT_KEY_INDEX);
    }

    public static byte[] readFile(String fileName, int keyIndex) {
        byte[] decrypted = null;
        try {
            decrypted = Base64.decode(readFileThrough(fileName), Base64.DEFAULT);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return decrypted;
    }

    public static byte[] readFileThrough(String fileName) {
        byte[] data = null;
        FileInputStream fin = null;
        try {
            mContext = SDKManager.getInstance().getContext();
            if (mContext != null && fileName != null) {
                fin = mContext.openFileInput(fileName);
                int length = fin.available();
                data = new byte[length];
                fin.read(data, 0, length);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return data;
    }

    public static boolean exists(String fileName) {
        try {
            mContext = SDKManager.getInstance().getContext();
            if (mContext != null && fileName != null) {
                String[] files = mContext.fileList();
                for (String name : files) {
                    try {
                        if (name.startsWith(fileName)) {
                            return true;
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getFullFileName(String fileName) {
        if (mContext == null) {
            return null;
        }

        try {
            return mContext.getFilesDir().getAbsolutePath() + "/" + fileName;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void delete(String fileName) {
        try {
            if (fileName != null && exists(fileName)) {
                String fullName = getFullFileName(fileName);
                Log.d("WLANSDK", fullName + ": current: " + System.currentTimeMillis() + ", lastModified: " + new File(fullName).lastModified());
                new File(fullName).delete();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void commit() {
        try {
            if (mPersistent != null) {
                writeFile(Constant.persistentBytes, mPersistent.toString().getBytes());
            }
            loadPersistent();
        } catch (Throwable e) {
            e.printStackTrace();
            delete(Constant.persistentBytes);    // read file failed, delte the file
        }
    }

    public void putString(String key, String data) {
        putString(key, data, Constant.DEFAULT_PERSISTENT_KEY_INDEX);
    }

    public void putString(String key, String data, int keyIndex) {
        try {
            if (key != null && data != null) {
                putStringThrough(key, EncryptionModule.base64EncodeToString(data.getBytes()));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void putStringThrough(String key, String data) {
        try {
            if (mPersistent == null || mPersistent.length() == 0) {
                loadPersistent();
            }

            if (key != null && data != null) {
                mPersistent.put(key, data);
                commit();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public String getString(String key) {
        loadPersistent();
        return getString(key, Constant.DEFAULT_PERSISTENT_KEY_INDEX);
    }

    public String getString(String key, int keyIndex) {
        String string = null;
        String content = null;
        try {
            if (key != null && Utils.validateString((content = getStringThrough(key)))) {
                string = new String(EncryptionModule.base64DecodeToBytes(content.getBytes()));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return string;
    }

    public String getStringThrough(String key) {
        if (key != null) {
            return getStringThrough(key, "");
        }

        return "";
    }

    public String getStringThrough(String key, String defValue) {
        String ret = defValue;
        try {
            if (key != null && mPersistent != null && mPersistent.has(key)) {
                ret = mPersistent.getString(key);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return ret;
    }

    public void putBoolean(String key, boolean defValue) {
        try {
            if (mPersistent == null || mPersistent.length() == 0) {
                loadPersistent();
            }

            if (key != null) {
                mPersistent.put(key, defValue);
                commit();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        loadPersistent();
        boolean ret = defValue;
        try {
            if (key != null && mPersistent != null && mPersistent.has(key)) {
                ret = mPersistent.getBoolean(key);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return ret;
    }

    public void putInt(String key, int value) {
        try {
            if (mPersistent == null || mPersistent.length() == 0) {
                loadPersistent();
            }

            if (key != null) {
                mPersistent.put(key, value);
                commit();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public int getInt(String key, int defValue) {
        int value = defValue;
        try {
            if (key != null && mPersistent != null && mPersistent.has(key)) {
                value = mPersistent.getInt(key);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return value;
    }

    public void writeFile(String fileName, byte[] data) {
        writeFile(fileName, data, Constant.DEFAULT_PERSISTENT_KEY_INDEX);
    }

    public void writeFile(String fileName, byte[] data, int keyIndex) {
        writeFile(fileName, data, keyIndex, false);
    }

    public void writeFile(String fileName, byte[] data, int keyIndex, boolean append) {
        try {
            if (fileName != null && data != null) {
                byte[] encryptData = Base64.encode(data, Base64.DEFAULT);
                writeFileThrough(fileName, encryptData, append);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    public void writeFileThrough(String fileName, byte[] data, boolean append) {
        FileOutputStream fout = null;
        try {
            if (fileName != null && data != null && mContext != null) {
                int mode = append ? Context.MODE_PRIVATE | Context.MODE_APPEND : Context.MODE_PRIVATE;
                fout = mContext.openFileOutput(fileName, mode);
                fout.write(data);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (fout != null) {
                try {
                    fout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean isFileExpired(String fileName, long livePeriod) {
        try {
            String fullName = getFullFileName(fileName);
            if (System.currentTimeMillis() - new File(fullName).lastModified() > livePeriod) {
                return true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }
}