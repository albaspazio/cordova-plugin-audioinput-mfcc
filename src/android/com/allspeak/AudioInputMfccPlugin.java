package com.allspeak;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import android.util.Log;
import android.content.pm.PackageManager;
import android.Manifest;

import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.audiocapture.*;
import com.allspeak.MFCCService.LocalBinder;

public class AudioInputMfccPlugin extends CordovaPlugin
{
    private static final String LOG_TAG         = "AudioInputMfccPlugin";
    
    private Context mContext                    = null;
    
    //cordova stuffs
    private CallbackContext callbackContext     = null;
    private CordovaInterface cordovaInterface   = null;
    
    //record permissions
    public static String[]  permissions         = { Manifest.permission.RECORD_AUDIO };
    public static int       RECORD_AUDIO        = 0;
    public static final int PERMISSION_DENIED_ERROR = 20;    
    boolean isCapturingAllowed                  = false;
    
    
    boolean isCapturing                         = false;
    //-----------------------------------------------------------------------------------------------
    
    private MFCCService mService                = null;
    private boolean mBound                      = false;
    
    private CFGParams mCfgParams                = null;
    private MFCCParams mMfccParams              = null;
    
    //======================================================================================================================
    public void initialize(CordovaInterface cordova, CordovaWebView webView) 
    {
        super.initialize(cordova, webView);
        Log.d(LOG_TAG, "Initializing AudioInputMfccPlugin");

        //get plugin context
        cordovaInterface        = cordova;
        mContext                = cordovaInterface.getActivity();

        bindService();

        promptForRecordPermissions();
    }
    //======================================================================================================================
    //get Service interface    

    private void bindService()
    {
        // bind service
        Intent bindIntent = new Intent(mContext, MFCCService.class);  // Binding.this instead of mContext in the official sample.
        mContext.bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);        
    }

    private ServiceConnection mConnection = new ServiceConnection() 
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) 
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder  = (LocalBinder) service;
            mService            = binder.getService();
            mService.initService();
            mBound              = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) 
        {
            mService    = null;
            mBound      = false;
        }
    };    
    //======================================================================================================================

    /**
     * @param action
     *          startCapture:
     *          stopCapture :
     *          startMFCC   :
     *          stopMFCC    :
     *          getMFCC     :
     * @param args
     * @param _callbackContext
     * @return
     * @throws JSONException 
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext _callbackContext) throws JSONException 
    {
        callbackContext = _callbackContext;
        
        if(mService == null)
        {    
            bindService();
            sendErrorString2Web("MFCC service is down....retry", true);
            return false;
        }
        
        if (action.equals("startCapture")) 
        {
            if(mService.isCapturing())
            {
                _callbackContext.error( "AudioInputMfccPlugin : plugin is already capturing.");
                return true;
            }
            try 
            {
                mCfgParams                       = new CFGParams(new JSONObject((String)args.get(0))); 
                if(!args.isNull(1))  mMfccParams = new MFCCParams(new JSONObject((String)args.get(1))); 
                
                mService.startCapture(mCfgParams, mMfccParams, callbackContext);
                sendNoResult2Web();
            }
            catch (Exception e) 
            {
                sendErrorString2Web(e.toString(), true);
                callbackContext = null;
                return false;
            }
        }
        else if (action.equals("startMicPlayback")) 
        {
            if(mService.isCapturing())
            {
                _callbackContext.error( "AudioInputMfccPlugin : plugin is already capturing.");
                return true;
            }
            try 
            {
                mCfgParams = new CFGParams(new JSONObject((String)args.get(0))); 
                
                mService.startMicPlayback(mCfgParams, callbackContext);
                sendNoResult2Web();
            }
            catch (Exception e) 
            {
                sendErrorString2Web(e.toString(), true);
                callbackContext = null;
                return false;
            }            
        }  
        else if (action.equals("setPlayBackPercVol")) 
        {
            if(mService.isCapturing())
            {
                int newperc;
                if(!args.isNull(0)) newperc = args.getInt(0);
                else                newperc = 0;
                
                mService.setPlayBackPercVol(newperc, callbackContext);
            }   
            sendNoResult2Web();
        }        
        else if (action.equals("stopCapture")) 
        {
            // an interrupt command is sent to audioreceiver, when it exits from its last cycle, it sends an event here
            mService.stopCapture(callbackContext);
            sendNoResult2Web();
        } 
        else if(action.equals("getMFCC")) 
        {            
            try 
            {               
                // JS interface call params:     mfcc_json_params, source;  params have been validated in the js interface
                // should have a nDataDest > 0  web,file,both
                mMfccParams             = new MFCCParams(new JSONObject((String)args.get(0)));
                String inputpathnoext   = args.getString(1); 
                
                mService.getMFCC(mMfccParams, inputpathnoext, callbackContext);
                sendNoResult2Web();
            }
            catch (Exception e) 
            {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
                callbackContext = null;
                return false;
            }            
        }        
        return true;
    }
    
    @Override
    public void onDestroy() 
    {
        if (mBound) 
        {
            if(mService.isCapturing())  mService.stopCapture(callbackContext);
            mContext.unbindService(mConnection);
            mBound = false;
        }
    }
    
    @Override
    public void onReset() 
    {
        if (mBound) 
        {
            if(mService.isCapturing())  mService.stopCapture(callbackContext);
            mContext.unbindService(mConnection);
            mBound = false;
        }        
    }
    
//    @Override
//    public void onPause() 
//    {
//        super.onPause();
//    }
//    
//    @Override
//    public void onResume() 
//    {
//        super.onResume();
//    }
        
//    @Override
//    public void onNewIntent(Intent intent) {
//    }
    
    //===================================================================================================
    // CALLBACK TO WEB LAYER  (JAVA => JS)
    //===================================================================================================
    /**
     * Create a new plugin result and send it back to JavaScript
     */
//    public void sendUpdate2Web(JSONObject info, boolean keepCallback) {
//        if (callbackContext != null) {
//            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
//            result.setKeepCallback(keepCallback);
//            callbackContext.sendPluginResult(result);
//        }
//    }
    /**
     * Create a NO RESULT plugin result and send it back to JavaScript
     */
    private void sendNoResult2Web() {
        if (callbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        }
    }
    /**
     * Create a new plugin result and send it back to JavaScript
     */
//    private void sendError2Web(JSONObject info, boolean keepCallback) {
//        if (callbackContext != null) {
//            PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
//            result.setKeepCallback(keepCallback);
//            callbackContext.sendPluginResult(result);
//        }
//    }  
    
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    private void sendErrorString2Web(String msg, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            try
            {
                JSONObject info = new JSONObject(); info.put("error", msg);
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
                result.setKeepCallback(keepCallback);
                callbackContext.sendPluginResult(result);
            }
            catch(JSONException e)
            {
                e.printStackTrace();                  
            }
        }
    }  
    //=================================================================================================
    // GET RECORDING PERMISSIONS
    //=================================================================================================        
     //Ensure that we have gotten record audio permission
    private void promptForRecordPermissions() 
    {
        if(PermissionHelper.hasPermission(this, permissions[RECORD_AUDIO])) 
            isCapturingAllowed = true;
        else
            //Prompt user for record audio permission
            PermissionHelper.requestPermission(this, RECORD_AUDIO, permissions[RECORD_AUDIO]);
    }

    // Handle request permission result
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
    {
        for(int r:grantResults) 
        {
            if(r == PackageManager.PERMISSION_DENIED) 
            {
                sendErrorString2Web("RECORDING_PERMISSION_DENIED_ERROR", true);
                isCapturingAllowed = false;
                return;
            }
        }
        isCapturingAllowed = true;
    }     

    //=================================================================================================
    public static class RETURN_TYPE
    {
        public static int CAPTURE_DATA          = 1; //
        public static int CAPTURE_STOP          = 2; //
        public static int CAPTURE_ERROR         = 3; //
        public static int CAPTURE_START         = 4; //
        public static int MFCC_DATA             = 10; //
        public static int MFCC_PROGRESS_DATA    = 11; //
        public static int MFCC_PROGRESS_FILE    = 12; //
        public static int MFCC_PROGRESS_FOLDER  = 13; //
        public static int MFCC_ERROR            = 14; //
    }    
    //=================================================================================================
}




//        else if (action.equals("startMFCC")) 
//        {
//            try 
//            {        
//                if(!args.isNull(0))
//                {
//                    MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0))); 
//                    mfcc.setParams(mfccParams);
//                    mfcc.setWlCb(callbackContext);
//                    nMFCCDataDest           = mfccParams.nDataDest;
//                }
//                bIsCalculatingMFCC = true;
//                sendNoResult2Web();
//            }
//            catch (Exception e) // !!!! I decide to stop capturing....
//            {
//                aicCapture.stop();
//                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
//                callbackContext = null;                
//                return false;
//            }                
//        }
//        else if (action.equals("stopMFCC")) 
//        {
//            bIsCalculatingMFCC = false;
//            sendNoResult2Web();
//        }
