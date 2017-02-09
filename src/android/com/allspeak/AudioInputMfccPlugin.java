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

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.content.pm.PackageManager;
import android.Manifest;

import java.util.Arrays;
import java.lang.ref.WeakReference;

import com.allspeak.audioprocessing.mfcc.*;
import com.allspeak.audiocapture.*;


public class AudioInputMfccPlugin extends CordovaPlugin
{
    private static final String LOG_TAG         = "AudioInputMfccPlugin";
    
    private CallbackContext callbackContext     = null;
    private CordovaInterface cordovaInterface;
    
    //-----------------------------------------------------------------------------------------------
    // CAPTURE
    private final AudioCaptureHandler aicHandler= new AudioCaptureHandler(this);
    private AudioInputCapture aicCapture        = null;                                   // Capture instance

    // what to do with captured data
    public static final int DATADEST_NONE       = 0;
    public static final int DATADEST_JS         = 1;    
    private int nDataDest                       = DATADEST_JS;        // send back to Web Layer or not    
    private boolean isCapturing                 = false;
    //-----------------------------------------------------------------------------------------------
    // MFCC
    private final MFCCHandler mfccHandler       = new MFCCHandler(this);
    private MFCC mfcc                           = null;        
    private boolean bCalculateMFCC              = false;              // do not calculate any mfcc score on startCatpure

    //-----------------------------------------------------------------------------------------------
    // VAD
    
    
    //======================================================================================================================
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        cordovaInterface = cordova;
        Log.d(LOG_TAG, "Initializing AudioInputMfccPlugin");
    }
    
    
    /**
     * 
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
        if (action.equals("startCapture")) 
        {
            if(aicCapture != null)
                if(aicCapture.isCapturing())
                {
                    callbackContext.error( "AudioInputMfccPlugin : plugin is already capturing.");
                    return true;
                }
            
            callbackContext = _callbackContext;

            // get params
            try 
            {
                // JS interface call params:     capture_json_params, mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
                CFGParams cfgParams         = new CFGParams(new JSONObject((String)args.get(0))); 
                aicCapture                  = new AudioInputCapture(cfgParams, aicHandler, this);                  
                
                if(!args.isNull(1))
                {
                    MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(1)));
                    mfcc                    = new MFCC(mfccParams, mfccHandler);                  
                }

                if(!args.isNull(2))
                    bCalculateMFCC          = (boolean)args.get(2);
            }
            catch (JSONException e) 
            {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
                aicCapture = null;
                callbackContext = null;
                return false;
            }

            try
            {
                aicCapture.start();  //asynchronous call, cannot return anything since a permission request may be called 
                sendNoResult2Web();
            }
            catch (Exception e) 
            {
                // decide if stop the receiver or destroy the istance.....
                aicCapture.stop();
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, AudioInputCapture.PERMISSION_DENIED_ERROR));
                callbackContext = null;                
                return false;
            }
        }
        else if (action.equals("stopCapture")) 
        {
            aicCapture.stop();
            callbackContext.success();
            callbackContext = null;
        }        
        else if (action.equals("startMFCC")) 
        {
            try 
            {        
                if(!args.isNull(0))
                {
                    MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0))); 
                    mfcc                    = new MFCC(mfccParams, mfccHandler);                   
                }
                bCalculateMFCC = true;
                sendNoResult2Web();
            }
            catch (Exception e) // !!!! I decide to stop capturing....
            {
                aicCapture.stop();
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
                callbackContext = null;                
                return false;
            }                
        }
        else if (action.equals("stopMFCC")) 
        {
            bCalculateMFCC = false;
            sendNoResult2Web();
        }
        else if(action.equals("getMFCC")) 
        {            
            // call by web layer...process a single file or an entire folder
            callbackContext = _callbackContext;

            try 
            {               
                // JS interface call params:     mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
                MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0))); 
                mfcc                    = new MFCC(mfccParams, mfccHandler);             
                String inputpathnoext   = args.getString(1); 

                mfcc.getMFCC(inputpathnoext, cordovaInterface.getThreadPool());
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
    public void onDestroy() {
        bCalculateMFCC = false;
        if(aicCapture != null)  aicCapture.stop();
    }
    
    @Override
    public void onReset() {
        bCalculateMFCC = false;
        if(aicCapture != null)  aicCapture.stop();
    }
    //===================================================================================================
    // CALLBACK TO WEB LAYER  (JAVA => JS)
    //===================================================================================================
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    private void sendUpdate(JSONObject info, boolean keepCallback) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }
    /**
     * Create a new plugin result and send it back to JavaScript
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
    private void sendError(JSONObject info, boolean keepCallback) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }

    //=========================================================================================
    // callback from handlers
    //=========================================================================================
    // receive new audio data from AudioInputReceiver:
    // - start calculating MFCC
    // - return raw data to web layer if selected
    public void onCapturedData(float[] data)
    {
        Log.d(LOG_TAG, "new raw data arrived in MainPlugin Activity");
//        faNewData = data;
        // send raw to WEB ??
        if(nDataDest == DATADEST_JS)
        {    
            // send raw data to Web Layer
            String decoded = Arrays.toString(data);
            JSONObject info = new JSONObject();
            try 
            {
                info.put("data", decoded);
            }
            catch (JSONException e) {
                e.printStackTrace();                  
                Log.e(LOG_TAG, e.getMessage(), e);
                try{
                    info.put("error", e.toString());
                }
                catch (JSONException e2) {
                    e.printStackTrace();  
                }                    
            }
            sendUpdate(info, true);        
        }
        
        // calculate MFCC/MFFILTERS ??
        if(bCalculateMFCC)   mfcc.getMFCC(data, cordovaInterface.getThreadPool());
    }
    
    public void onCaptureError(String error)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("error", error);        
            sendError(info, true);
        }
        catch (JSONException e){}
    }
    
    public void onMFCCData(float[][] params)
    {
        Log.d(LOG_TAG, "new CEPSTRA data arrived in MainPlugin Activity");      
        
        // send raw data to Web Layer
        JSONObject info = new JSONObject();
        try 
        {
            JSONArray data = new JSONArray(params);
            info.put("data", data);
        }
        catch (JSONException e) {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            try{
                info.put("error", e.toString());
            }
            catch (JSONException e2) {
                e.printStackTrace();  
            }                    
        }
        sendUpdate(info, true);           
    }    
    
    public void onMFCCProgress(String progr)
    {
        // send progress info to Web Layer
        JSONObject info = new JSONObject();            
        try 
        {
            info.put("progress", progr);
            sendUpdate(info, true);  
        }
        catch (JSONException e) {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            try{
                info.put("error", e.toString());
            }
            catch (JSONException e2) {
                e.printStackTrace();  
            }                    
        }
              
    }    
    //=================================================================================================
    // HANDLERS (from Runnables to this activity)   receive input from other Threads
    //=================================================================================================
    private static class AudioCaptureHandler extends Handler {
        private final WeakReference<AudioInputMfccPlugin> mActivity;

        public AudioCaptureHandler(AudioInputMfccPlugin activity) {
            mActivity = new WeakReference<AudioInputMfccPlugin>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            AudioInputMfccPlugin activity = mActivity.get();
            if (activity != null) 
            {
                try 
                {
                    Bundle b = msg.getData();   //get message type
                    if(b.getString("error") != null)
                        activity.onCaptureError(b.getString("error"));
                    else 
                        activity.onCapturedData(b.getFloatArray("data"));  // are data
                }
                catch (Exception e) 
                {
                    e.printStackTrace();                      
                    Log.e(LOG_TAG, e.getMessage(), e);
                    
                    try{
                        JSONObject info = new JSONObject();
                        info.put("error", e.toString());
                        activity.sendError(info, true); 
                    }
                    catch (JSONException e2) {
                        e2.printStackTrace();  
                    }                     
                }
            }
        }
    }

    private static class MFCCHandler extends Handler {
        private final WeakReference<AudioInputMfccPlugin> mActivity;

        public MFCCHandler(AudioInputMfccPlugin activity) {
            mActivity = new WeakReference<AudioInputMfccPlugin>(activity);
        }

        public void handleMessage(Message msg) {
            AudioInputMfccPlugin activity = mActivity.get();
            if (activity != null) 
            {
                try 
                {
                    //get message type
                    Bundle b = msg.getData();
                    if(b.getString("error") != null)
                    {
                        JSONObject info = new JSONObject();
                        info.put("error", b.getString("error"));
                        activity.sendError(info, true);
                    }
                    else if(b.getString("progress") != null)
                        activity.onMFCCProgress(b.getString("progress"));
                    else
                    {   // are data
                        int nframes = b.getInt("nframes");
                        int nparams = b.getInt("nparams");
                        float[] data= b.getFloatArray("data");
                        
                        float[][] res = deFlattenArray(data, nframes, nparams);
                        activity.onMFCCData(res);
                    }
                }
                catch (JSONException e) {
                    e.printStackTrace();                    
                    Log.e(LOG_TAG, e.getMessage(), e);
                    try{
                        JSONObject info = new JSONObject();
                        info.put("error", e.toString());
                        activity.sendError(info, true);                    
                    }catch(JSONException e2){
                        e2.printStackTrace();  
                    }
                }
            }
        }
    }
    //=================================================================================================
    // ACCESSORY FUNCTIONS
    //=================================================================================================
    // used to convert a 1dim array to a 2dim array
    private static float[][] deFlattenArray(float[] input, int dim1, int dim2)
    {
        float[][] output = new float[dim1][dim2];        

        for(int i = 0; i < input.length; i++){
            output[i/dim2][i % dim2] = input[i];
        }
        return output;        
    }
    //=================================================================================================
}