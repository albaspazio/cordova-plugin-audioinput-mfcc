package com.allspeak;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.util.Log;

import android.content.Context;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.allspeak.audioprocessing.mfcc.*;
import com.allspeak.audiocapture.*;

//==========================================================================================================================
public class MFCCService extends Service 
{
    private final static String LOG_TAG = "MFCCService";
    private final IBinder mBinder = new LocalBinder();

    private CallbackContext callbackContext     = null;    
    private WakeLock cpuWeakLock                = null;
    
    
    // CAPTURE
    private CFGParams mCfgParams                = null;
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
    private MFCCParams mMfccParams              = null;
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
        
    
    //===============================================================================
    //binding
    public class LocalBinder extends Binder { MFCCService getService() { return MFCCService.this; } }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }
    
    //===============================================================================
    //called after plugin got the service interface (ServiceConnection::onServiceConnected)
    public void initService()
    {
        mAudioManager       = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);       
    }
    
    //===============================================================================
    //===============================================================================
    // PUBLIC SERVICE INTERFACE
    //
    //  startCapture
    //  startMicPlayback
    //  stopCapture
    //  setPlayBackPercVol
    //  getMFCC
    //  isCapturing
    //===============================================================================
    //===============================================================================
    public boolean startCapture(CFGParams cfgParams, MFCCParams mfccParams, CallbackContext cb)
    {
        try 
        {
            callbackContext             = cb;
            mCfgParams                  = cfgParams;
            aicCapture                  = new AudioInputCapture(mCfgParams, aicHandler);                  
            bIsCalculatingMFCC          = cfgParams.bStartMFCC;
            nCapturedDataDest           = cfgParams.nDataDest;

            if(mfccParams != null)
            {
                mMfccParams             = mfccParams;
                nMFCCDataDest           = mMfccParams.nDataDest;

                //start the MFCC HandlerThread
                mfcc                    = new MFCCHandlerThread(mMfccParams, mfccHandler, callbackContext, "MFCCHandlerThread", Process.THREAD_PRIORITY_MORE_FAVORABLE);
                mfcc.start();   
                mfcc.init();                  
            }
        }
        catch (Exception e) 
        {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
            aicCapture = null;
            callbackContext = null;
            return false;
        }

        try
        {
            // set PARTIAL_WAKE_LOCK to keep on using CPU resources also when the App is in background
            PowerManager pm         = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            cpuWeakLock             = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpuWeakLock");              
            
            
            bIsCapturing            = aicCapture.start();  //asynchronous call, cannot return anything since a permission request may be called 
            lockCPU();
            nCapturedBlocks         = 0;
            nCapturedBytes          = 0;
            nMFCCProcessedFrames    = 0;
            nMFCCFrames2beProcessed = 0;
            bTriggerAction          = false;
            return true;
        }
        catch (Exception e) 
        {
            // decide if stop the receiver or destroy the istance.....
            aicCapture.stop();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            callbackContext = null;                
            return false;
        }        
    }
    
    public boolean startMicPlayback(CFGParams cfgParams, CallbackContext cb)
    {
        try
        {
            callbackContext     = cb;
            mCfgParams          = cfgParams;            
            aicCapture          = new AudioInputCapture(mCfgParams, aicHandler, AudioInputCapture.PLAYBACK_MODE);                  
            aicCapture.start();  //asynchronous call, cannot return anything since a permission request may be called 
            return true;
        }
        catch (Exception e) 
        {
            aicCapture.stop();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getMessage()));
            return false;
        }        
    }
    
    public void stopCapture(CallbackContext cb)
    {
        callbackContext             = cb;        
        aicCapture.stop();
        bIsCalculatingMFCC          = false;
        nCapturedDataDest           = 0;          
    }    
    
    public void setPlayBackPercVol(int percvol, CallbackContext cb)
    {
        callbackContext             = cb;        
        aicCapture.setPlayBackPercVol(percvol);
    }    
    
    public void getMFCC(MFCCParams mfccParams, String inputpathnoext, CallbackContext cb)
    {
        callbackContext             = cb;   
        mMfccParams                 = mfccParams;           
        nMFCCDataDest               = mMfccParams.nDataDest;
        mfcc.setParams(mMfccParams);
        mfcc.setWlCb(callbackContext);                

        mfcc.getMFCC(inputpathnoext);        
    }
    
    public boolean isCapturing()
    {
        return bIsCapturing;
    }    
    
    //=========================================================================================
    // callback called by handlers 
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

                info.put("type", AudioInputMfccPlugin.RETURN_TYPE.CAPTURE_DATA);
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
            unlockCPU();            
            JSONObject info = new JSONObject();
            info.put("type", AudioInputMfccPlugin.RETURN_TYPE.CAPTURE_ERROR);
            info.put("error", error);        
            sendError2Web(info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }
    
    public void onCaptureStart()
    {
        try
        {
            bIsCapturing    = true;
            JSONObject info = new JSONObject();
            info.put("type", AudioInputMfccPlugin.RETURN_TYPE.CAPTURE_START);
            sendUpdate2Web(info, true);    
        }
        catch (JSONException e){e.printStackTrace();}            
    }
    
    public void onCaptureStop(String bytesread)
    {
//        Log.d(LOG_TAG, "StopCapture: read " + bytesread + "bytes, captured " + Integer.toString(nCapturedBlocks) + " blocks, processed " + Integer.toString(nMFCCFrames2beProcessed) + " frames");            
        bTriggerAction  = true;
        try
        {
            unlockCPU();
            bIsCapturing    = false;
            JSONObject info = new JSONObject();
            info.put("type", AudioInputMfccPlugin.RETURN_TYPE.CAPTURE_STOP);
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
                    info.put("type", AudioInputMfccPlugin.RETURN_TYPE.MFCC_DATA);
                    JSONArray data = new JSONArray(params);
                    info.put("data", data);
                    info.put("progress", source);
                    sendUpdate2Web(info, true);
                    break;

                case MFCCParams.DATADEST_JSPROGRESS:    //   "" + send progress(filename) to WEB
                    info.put("type", AudioInputMfccPlugin.RETURN_TYPE.MFCC_PROGRESS_DATA);
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
            info.put("type", AudioInputMfccPlugin.RETURN_TYPE.MFCC_ERROR);
            info.put("error", error);        
            sendError2Web(info, true);
        }
        catch (JSONException e){e.printStackTrace();}
    }    
    
    //=================================================================================================
    // HANDLERS (from THREADS to this SERVICE)   receive input from other Threads
    //=================================================================================================
    private static class AudioCaptureHandler extends Handler {
        private final WeakReference<MFCCService> mService;

        public AudioCaptureHandler(MFCCService service) {
            mService = new WeakReference<MFCCService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MFCCService service = mService.get();
            if (service != null) 
            {
                try 
                {
                    Bundle b = msg.getData();   //get message type
                    switch((int)msg.what) //get message type
                    {
                        case AudioInputCapture.STATUS_CAPTURE_DATA:
                            service.onCaptureData(b.getFloatArray("data"));
                            break;
                        
                        case AudioInputCapture.STATUS_CAPTURE_STOP:
                            service.onCaptureStop(b.getString("stop"));    
                            break;
                        
                        case AudioInputCapture.STATUS_CAPTURE_ERROR:
                            service.onCaptureError(b.getString("error"));
                            break;

                        case AudioInputCapture.STATUS_CAPTURE_START:
                            service.onCaptureStart();
                            break;
                    }                    
                }
                catch (Exception e) 
                {
                    e.printStackTrace();                      
                    Log.e(LOG_TAG, e.getMessage(), e);
                    service.onCaptureError(e.toString());
    }
            }
        }
        }

    private static class MFCCHandler extends Handler {
        private final WeakReference<MFCCService> mService;

        public MFCCHandler(MFCCService activity) { mService = new WeakReference<MFCCService>(activity);  }

        // progress_file & progress_folder are directly sent to WEB
        // error & data are sent to activity methods
        public void handleMessage(Message msg) 
        {
            MFCCService activity = mService.get();
            if (activity != null) 
            {
                // expected messeges: error, progress_file, progress_folder => web
                //                    data  => plugin onMFCCData
                try 
                {
                    JSONObject info = new JSONObject();
                    Bundle b        = msg.getData();
                    switch((int)msg.what) //get message type
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
                            
                            info.put("type", AudioInputMfccPlugin.RETURN_TYPE.MFCC_PROGRESS_FILE);
                            info.put("progress", b.getString("progress_file"));
                            activity.sendUpdate2Web(info, true);                            
                            break;
                            
                        case MFCC.STATUS_PROGRESS_FOLDER:
                            
                            info.put("type", AudioInputMfccPlugin.RETURN_TYPE.MFCC_PROGRESS_FOLDER);
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
    // used to convert a 1dim array to a 2dim array
    private static float[][] deFlattenArray(float[] input, int dim1, int dim2)
    {
        float[][] output = new float[dim1][dim2];        

        for(int i = 0; i < input.length; i++){
            output[i/dim2][i % dim2] = input[i];
        }
        return output;        
    }    
    
    private void lockCPU()
    {
        if(!cpuWeakLock.isHeld())    cpuWeakLock.acquire();       
    }
    
    private void unlockCPU()
    {
        if(cpuWeakLock.isHeld())    cpuWeakLock.release();
    }
    //=================================================================================================
}
