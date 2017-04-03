package com.allspeak;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;
import android.media.AudioManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.Context;
import android.os.Process;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.content.pm.PackageManager;
import android.Manifest;

import java.util.Arrays;
import java.lang.ref.WeakReference;

import com.allspeak.audioprocessing.mfcc.*;
import com.allspeak.audiocapture.CFGParams;
import com.allspeak.audiocapture.*;


public class AudioInputMfccPlugin extends CordovaPlugin
{
    private static final String LOG_TAG         = "AudioInputMfccPlugin";
    
    private Context mContext                    = null;
    private CallbackContext callbackContext     = null;
    private CordovaInterface cordovaInterface   = null;
    private WakeLock cpuWeakLock                = null;
    
    //-----------------------------------------------------------------------------------------------
    // CAPTURE
    private final AudioCaptureHandler aicHandler= new AudioCaptureHandler(this);
    private AudioInputCapture aicCapture        = null;                                   // Capture instance

    private boolean bIsCapturing                = false;
    private int nCapturedBlocks                 = 0;
    private int nCapturedBytes                  = 0;

    // what to do with captured data
    public static final int DATADEST_NONE       = 0;        // don't send back to Web Layer
    public static final int DATADEST_JS         = 1;        // send back to Web Layer 
    private int nCapturedDataDest               = DATADEST_NONE;
    
    //-----------------------------------------------------------------------------------------------
    // PLAYBACK
    private AudioManager mAudioManager          = null;
    
    //-----------------------------------------------------------------------------------------------
    // MFCC
    private final MFCCHandler mfccHandler       = new MFCCHandler(this);
    private MFCCHandlerThread mfcc              = null;        

    private boolean bIsCalculatingMFCC          = false;              // do not calculate any mfcc score on startCatpure
    private int nMFCCProcessedFrames            = 0;
    private int nMFCCFrames2beProcessed         = 0;

    // what to do with MFCC data
    private int nMFCCDataDest                   = MFCCParams.DATADEST_NONE;        // send back to Web Layer or not  
    private boolean bTriggerAction              = false;    // monitor nMFCCFrames2beProcessed zero-ing, when it goes to 0 and bTriggerAction=true => 
    //-----------------------------------------------------------------------------------------------
    // VAD
    
    
    //======================================================================================================================
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.d(LOG_TAG, "Initializing AudioInputMfccPlugin");

        cordovaInterface        = cordova;
        mContext                = cordovaInterface.getActivity();//.getApplicationContext();
        
        // set PARTIAL_WAKE_LOCK to keep on using CPU resources also when the App is in background
        PowerManager pm         = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        cpuWeakLock             = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpuWeakLock");    
        
        //init the HandlerThread
        MFCCParams mfccParams   = new MFCCParams();
        mfcc                    = new MFCCHandlerThread(mfccParams, mfccHandler, null, "name", Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mfcc.start();      
        
        mAudioManager           = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }
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
        if (action.equals("startCapture")) 
        {
            if(aicCapture != null)
                if(aicCapture.isCapturing())
                {
                    _callbackContext.error( "AudioInputMfccPlugin : plugin is already capturing.");
                    return true;
                }
            
            callbackContext = _callbackContext;

            // get params
            try 
            {
                // JS interface call params:     capture_json_params, mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
                CFGParams cfgParams         = new CFGParams(new JSONObject((String)args.get(0))); 
                aicCapture                  = new AudioInputCapture(cfgParams, aicHandler, this);                  
                bIsCalculatingMFCC          = cfgParams.bStartMFCC;
                nCapturedDataDest           = cfgParams.nDataDest;
        
                if(!args.isNull(1))
                {
                    MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(1)));
                    nMFCCDataDest           = mfccParams.nDataDest;
                    mfcc.setParams(mfccParams);
                    mfcc.setWlCb(callbackContext);
                }
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
                nCapturedBlocks         = 0;
                nCapturedBytes          = 0;
                nMFCCProcessedFrames    = 0;
                nMFCCFrames2beProcessed = 0;
                bTriggerAction          = false;
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
        else if (action.equals("startMicPlayback")) 
        {
            if(aicCapture != null)
                if(aicCapture.isCapturing())
                {
                    _callbackContext.error( "AudioInputMfccPlugin : plugin is already capturing.");
                    return true;
                }
            // get params
            CFGParams cfgParams;
            try 
            {
                // JS interface call params:     capture_json_params, mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
                cfgParams         = new CFGParams(new JSONObject((String)args.get(0))); 
            }
            catch (JSONException e) 
            {
                _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
                return false;
            }

            try
            {
                aicCapture          = new AudioInputCapture(cfgParams, aicHandler, this, AudioInputCapture.PLAYBACK_MODE);                  
                aicCapture.start();  //asynchronous call, cannot return anything since a permission request may be called 
                callbackContext = _callbackContext;
                sendNoResult2Web();
            }
            catch (Exception e) 
            {
                aicCapture.stop();
                _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, AudioInputCapture.PERMISSION_DENIED_ERROR));
                return false;
            }
        }  
        else if (action.equals("setPlayBackPercVol")) 
        {
            if(aicCapture.isCapturing())
            {            
                int newperc;
                if(!args.isNull(0)) newperc = args.getInt(0);
                else                newperc = 0;
                
                aicCapture.setPlayBackPercVol(newperc);
            }
            sendNoResult2Web();
        }        
        else if (action.equals("stopCapture")) 
        {
            // an interrupt command is sent to audioreceiver, when it exits from its last cycle, it sends an event here
            callbackContext = _callbackContext;
            aicCapture.stop();
            bIsCalculatingMFCC          = false;
            nCapturedDataDest           = 0;            
            sendNoResult2Web();
        }        
        else if (action.equals("startMFCC")) 
        {
            try 
            {        
                if(!args.isNull(0))
                {
                    MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0))); 
                    mfcc.setParams(mfccParams);
                    mfcc.setWlCb(callbackContext);
                    nMFCCDataDest           = mfccParams.nDataDest;
                }
                bIsCalculatingMFCC = true;
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
            bIsCalculatingMFCC = false;
            sendNoResult2Web();
        }
        else if(action.equals("getMFCC")) 
        {            
            // call by web layer...process a single file or an entire folder
            callbackContext = _callbackContext;

            try 
            {               
                // JS interface call params:     mfcc_json_params, source;  params have been validated in the js interface
                // should have a nDataDest > 0  web,file,both
                MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0)));
                mfcc.setParams(mfccParams);
                mfcc.setWlCb(callbackContext);                
                nMFCCDataDest           = mfccParams.nDataDest;
                String inputpathnoext   = args.getString(1); 

                mfcc.getMFCC(inputpathnoext);
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
    public void onDestroy() {
        bIsCalculatingMFCC = false;
        if(aicCapture != null)  aicCapture.stop();
    }
    
    @Override
    public void onReset() {
        bIsCalculatingMFCC = false;
        if(aicCapture != null)  aicCapture.stop();
    }
    
    @Override
    public void onNewIntent(Intent intent) {
    }
    
    //=========================================================================================
    // callback from handlers => WEB LAYER
    //=========================================================================================
    // receive new audio data from AudioInputReceiver:
    // - start calculating MFCC
    // - return raw data to web layer if selected
    public void onCaptureData(float[] data)
    {
        nCapturedBlocks++;  
        nCapturedBytes += data.length;

//        Log.d(LOG_TAG, "new raw data arrived in MainPlugin Activity: " + Integer.toString(nCapturedBlocks));
        
        if(bIsCalculatingMFCC) mfcc.getQueueMFCC(data); // calculate MFCC/MFFILTERS ??
             
        // send raw to WEB ??
        if(nCapturedDataDest == DATADEST_JS)
        {    
            try 
            {
                // send raw data to Web Layer
                String decoded  = Arrays.toString(data);
                JSONObject info = new JSONObject();

                info.put("type", RETURN_TYPE.CAPTURE_DATA);
                info.put("data", decoded);
                sendUpdate2Web(info, true);
            }
            catch (JSONException e) 
            {
                e.printStackTrace();                  
                Log.e(LOG_TAG, e.getMessage(), e);
                onCaptureError(e.getMessage());
            }
        }
    }
    
    public void onCaptureError(String error)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", RETURN_TYPE.CAPTURE_ERROR);
            info.put("error", error);        
            sendError2Web(info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }
    
    public void onCaptureStop(String bytesread)
    {
//        Log.d(LOG_TAG, "StopCapture: read " + bytesread + "bytes, captured " + Integer.toString(nCapturedBlocks) + " blocks, processed " + Integer.toString(nMFCCFrames2beProcessed) + " frames");            
        bTriggerAction  = true;
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", RETURN_TYPE.CAPTURE_STOP);
            info.put("datacaptured", nCapturedBlocks);        
            info.put("dataprocessed", nMFCCFrames2beProcessed);        
            info.put("bytesread", bytesread);        
            sendUpdate2Web(info, true);
            callbackContext = null;
        }
        catch (JSONException e){e.printStackTrace();}
    }

    //------------------------------------------------------------------------------------------------
    // called by MFCC class when sending frames to be processed
    public void onMFCCStartProcessing(int nframes)
    {
        Log.d(LOG_TAG, "start to process: " + Integer.toString(nframes));
        nMFCCFrames2beProcessed += nframes;
    }
    
    public void onMFCCData(float[][] params, float[][] params_1st, float[][] params_2nd, String source)
    {
        onMFCCProgress(params.length);        
        
        // send raw data to Web Layer?
        JSONObject info = new JSONObject();
        try 
        {
            switch(nMFCCDataDest)
            {
                case MFCCParams.DATADEST_ALL:
                case MFCCParams.DATADEST_JSDATA:        //   "" + send progress(filename) + data(JSONArray) to WEB
                    info.put("type", RETURN_TYPE.MFCC_DATA);
                    JSONArray data = new JSONArray(params);
                    info.put("data", data);
                    info.put("progress", source);
                    sendUpdate2Web(info, true);
                    break;

                case MFCCParams.DATADEST_JSPROGRESS:    //   "" + send progress(filename) to WEB
                    info.put("type", RETURN_TYPE.MFCC_PROGRESS_DATA);
                    info.put("progress", source);
                    sendUpdate2Web(info, true);
                    break;                   
            }
        }
        catch (JSONException e) 
        {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            onMFCCError(e.toString());
        }
     }   
    
    public void onMFCCProgress(int frames)
    {
        nMFCCProcessedFrames    += frames;
        nMFCCFrames2beProcessed -= frames;
        Log.d(LOG_TAG, "processed frames : " + Integer.toString(nMFCCProcessedFrames) +  ", still to be processed: " + Integer.toString(nMFCCFrames2beProcessed));
        
        if(bTriggerAction && nMFCCFrames2beProcessed == 0)
        {
            Log.d(LOG_TAG, "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            bTriggerAction = false;
        }
    }
    
    public void onMFCCError(String error)
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", RETURN_TYPE.MFCC_ERROR);
            info.put("error", error);        
            sendError2Web(info, true);
        }
        catch (JSONException e){e.printStackTrace();}
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
                    else if(b.getString("stop") != null)
                        activity.onCaptureStop(b.getString("stop"));    
                    else 
                        activity.onCaptureData(b.getFloatArray("data"));  // are data
                }
                catch (Exception e) 
                {
                    e.printStackTrace();                      
                    Log.e(LOG_TAG, e.getMessage(), e);
                    activity.onCaptureError(e.toString());
                }
            }
        }
    }

    private static class MFCCHandler extends Handler {
        private final WeakReference<AudioInputMfccPlugin> mActivity;

        public MFCCHandler(AudioInputMfccPlugin activity) { mActivity = new WeakReference<AudioInputMfccPlugin>(activity);  }

        // progress_file & progress_folder are directly sent to WEB
        // error & data are sent to activity methods
        public void handleMessage(Message msg) 
        {
            AudioInputMfccPlugin activity = mActivity.get();
            if (activity != null) 
            {
                // expected messeges: error, progress_file, progress_folder => web
                //                    data  => plugin onMFCCData
                try 
                {
                    JSONObject info = new JSONObject();
                    Bundle b        = msg.getData();
                    switch(msg.what) //get message type
                    {
                        case MFCC.STATUS_DATAPROGRESS:
                            
                            activity.onMFCCProgress(Integer.parseInt(b.getString("progress")));
                            break;
                            
                        case MFCC.STATUS_NEWDATA:
                            
                            String source       = b.getString("source");
                            int nframes         = b.getInt("nframes");
                            int nparams         = b.getInt("nparams");
                            float[][] res       = deFlattenArray(b.getFloatArray("data"), nframes, nparams);
                            float[][] res_1st   = deFlattenArray(b.getFloatArray("data_1st"), nframes, nparams);
                            float[][] res_2nd   = deFlattenArray(b.getFloatArray("data_2nd"), nframes, nparams);
                            activity.onMFCCData(res, res_1st, res_2nd, source);                            
                            break;
                            
                        case MFCC.STATUS_PROGRESS_FILE:
                            
                            info.put("type", RETURN_TYPE.MFCC_PROGRESS_FILE);
                            info.put("progress", b.getString("progress_file"));
                            activity.sendUpdate2Web(info, true);                            
                            break;
                            
                        case MFCC.STATUS_PROGRESS_FOLDER:
                            
                            info.put("type", RETURN_TYPE.MFCC_PROGRESS_FOLDER);
                            info.put("progress", b.getString("progress_folder"));
                            activity.sendUpdate2Web(info, true);                        
                            break;
                            
                        case MFCC.STATUS_ERROR:
                            
                            activity.onMFCCError(b.getString("error"));     // is an error
                            break;
                            
                        case MFCC.STATUS_PROCESSINGSTARTED:
                            
                            activity.onMFCCStartProcessing((int)msg.obj);                            
                            break;
                    }
                }
                catch (JSONException e) 
                {
                    e.printStackTrace();                    
                    Log.e(LOG_TAG, e.getMessage(), e);
                    activity.onMFCCError(e.toString());
                }
            }
        }
    }
    //===================================================================================================
    // CALLBACK TO WEB LAYER  (JAVA => JS)
    //===================================================================================================
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public void sendUpdate2Web(JSONObject info, boolean keepCallback) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }
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
    private void sendError2Web(JSONObject info, boolean keepCallback) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }    
    //=================================================================================================
    // ACCESSORY FUNCTIONS
    //=================================================================================================
    private void lockCPU()
    {
        cpuWeakLock.acquire();       
    }
    
    private void unlockCPU()
    {
        cpuWeakLock.release();
    }
    
    
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
    //=================================================================================================
    
    public static class RETURN_TYPE
    {
        public static int CAPTURE_DATA          = 1; //
        public static int CAPTURE_STOP          = 2; //
        public static int CAPTURE_ERROR         = 3; //
        public static int MFCC_DATA             = 10; //
        public static int MFCC_PROGRESS_DATA    = 11; //
        public static int MFCC_PROGRESS_FILE    = 12; //
        public static int MFCC_PROGRESS_FOLDER  = 13; //
        public static int MFCC_ERROR            = 14; //
    }    
    
    //=================================================================================================
}
