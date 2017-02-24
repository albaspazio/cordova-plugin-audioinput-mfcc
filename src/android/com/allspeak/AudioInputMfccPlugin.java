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
    private CallbackContext callbackContextMFCC = null;
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
    
    private int nCapturedBlocks                 = 0;
    //-----------------------------------------------------------------------------------------------
    // MFCC
    private final MFCCHandler mfccHandler       = new MFCCHandler(this);
    private MFCC mfcc                           = null;        
    private boolean bCalculateMFCC              = false;              // do not calculate any mfcc score on startCatpure

    private int nMFCCDataDest                   = MFCCParams.DATADEST_NONE;        // send back to Web Layer or not     
    private int nMFCCProcessedBlocks            = 0;

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
                    nMFCCDataDest           = mfccParams.nDataDest;
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
                nCapturedBlocks = 0;
                nMFCCProcessedBlocks = 0;
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
            Log.d(LOG_TAG, "StopCapture: captured " + Integer.toString(nCapturedBlocks) + " blocks, processed " + Integer.toString(nMFCCProcessedBlocks) + " blocks");            
            
            callbackContext = _callbackContext;
            aicCapture.stop();
            sendNoResult2Web();
//            callbackContext.success(nCapturedBlocks);
//            callbackContext = null;
        }        
        else if (action.equals("startMFCC")) 
        {
            try 
            {        
                if(!args.isNull(0))
                {
                    MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0))); 
                    mfcc                    = new MFCC(mfccParams, mfccHandler); 
                    nMFCCDataDest           = mfccParams.nDataDest;
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
            callbackContextMFCC = _callbackContext;

            try 
            {               
                // JS interface call params:     mfcc_json_params, source;  params have been validated in the js interface
                // should have a nDataDest > 0  web,file,both
                MFCCParams mfccParams   = new MFCCParams(new JSONObject((String)args.get(0))); 
                mfcc                    = new MFCC(mfccParams, mfccHandler);             
                nMFCCDataDest           = mfccParams.nDataDest;
                String inputpathnoext   = args.getString(1); 

                mfcc.getMFCC(inputpathnoext, cordovaInterface.getThreadPool());
                sendNoResult2Web();
            }
            catch (Exception e) 
            {
                callbackContextMFCC.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.toString()));
                callbackContextMFCC = null;
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
    //=========================================================================================
    // callback from handlers => WEB LAYER
    //=========================================================================================
    // receive new audio data from AudioInputReceiver:
    // - start calculating MFCC
    // - return raw data to web layer if selected
    public void onCaptureData(float[] data)
    {
        nCapturedBlocks++;
        Log.d(LOG_TAG, "new raw data arrived in MainPlugin Activity: " + Integer.toString(nCapturedBlocks));

        // send raw to WEB ??
        if(nDataDest == DATADEST_JS)
        {    
            try 
            {
                // send raw data to Web Layer
                String decoded = Arrays.toString(data);
                JSONObject info = new JSONObject();

                info.put("type", RETURN_TYPE.CAPTURE_DATA);
                info.put("data", decoded);
                sendUpdate2Web(info, true);
                // calculate MFCC/MFFILTERS ??
                if(bCalculateMFCC)   mfcc.getMFCC(data, cordovaInterface.getThreadPool());                
            }
            catch (JSONException e) {
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
    
    public void onCaptureStop()
    {
        try
        {
            JSONObject info = new JSONObject();
            info.put("type", RETURN_TYPE.CAPTURE_STOP);
            info.put("data", nCapturedBlocks);        
            sendUpdate2Web(info, true);
            callbackContext = null;
        }
        catch (JSONException e){e.printStackTrace();}
    }

    
    public void onMFCCData(float[][] params, String source)
    {
        nMFCCProcessedBlocks++;       
        Log.d(LOG_TAG, "new CEPSTRA data arrived in MainPlugin Activity: " + Integer.toString(nMFCCProcessedBlocks));      
        
        // send raw data to Web Layer
        JSONObject info = new JSONObject();
        try 
        {
            if(nMFCCDataDest > MFCCParams.DATADEST_NONE)
            {
                switch(nMFCCDataDest)
                {
                    case MFCCParams.DATADEST_BOTH:
                    case MFCCParams.DATADEST_JSDATA:        //   "" + send progress(filename) + data(JSONArray) to WEB
                        info.put("type", RETURN_TYPE.MFCC_DATA);
                        JSONArray data = new JSONArray(params);
                        info.put("data", data);
                        info.put("progress", source);
                        break;

                    case MFCCParams.DATADEST_JSPROGRESS:    //   "" + send progress(filename) to WEB
                        info.put("type", RETURN_TYPE.MFCC_DATA_PROGRESS);
                        info.put("progress", source);
                        break;                   
                }
                sendUpdate2Web(info, true);
            }
        }
        catch (JSONException e) 
        {
            e.printStackTrace();                  
            Log.e(LOG_TAG, e.getMessage(), e);
            onMFCCError(e.toString());
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
                        activity.onCaptureStop();    
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

        public MFCCHandler(AudioInputMfccPlugin activity) {
            mActivity = new WeakReference<AudioInputMfccPlugin>(activity);
        }

        // progress_file & progress_folder are directly sent to WEB
        // error & data are sent to activity methods
        public void handleMessage(Message msg) 
        {
            AudioInputMfccPlugin activity = mActivity.get();
            if (activity != null) 
            {
                try 
                {
                    //get message type
                    Bundle b = msg.getData();
                    if(b.getString("error") != null)         // is an error
                        activity.onMFCCError(b.getString("error"));
                    else if(b.getString("progress_file") != null)
                    {
                        JSONObject info = new JSONObject();
                        info.put("type", RETURN_TYPE.MFCC_PROGRESS_FILE);
                        info.put("progress", b.getString("progress"));
                        activity.sendUpdate2Web(info, true);
                    }
                    else if(b.getString("progress_folder") != null)
                    {
                        JSONObject info = new JSONObject();
                        info.put("type", RETURN_TYPE.MFCC_PROGRESS_FOLDER);
                        info.put("progress", b.getString("progress"));
                        activity.sendUpdate2Web(info, true);
                    }
                    else
                    {   
                        // are DATA
                        String source   = b.getString("source");
                        int nframes     = b.getInt("nframes");
                        int nparams     = b.getInt("nparams");
                        float[] data    = b.getFloatArray("data");
                        float[][] res   = deFlattenArray(data, nframes, nparams);
                        
                        activity.onMFCCData(res, source);
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
    //=================================================================================================
    //=================================================================================================
    
    private static class RETURN_TYPE
    {
        public static int CAPTURE_DATA          = 1; //
        public static int CAPTURE_STOP          = 2; //
        public static int CAPTURE_ERROR         = 3; //
        public static int MFCC_DATA             = 10; //
        public static int MFCC_DATA_PROGRESS    = 11; //
        public static int MFCC_PROGRESS_FILE    = 12; //
        public static int MFCC_PROGRESS_FOLDER  = 13; //
        public static int MFCC_ERROR            = 14; //
    }    
    
    //=================================================================================================
}