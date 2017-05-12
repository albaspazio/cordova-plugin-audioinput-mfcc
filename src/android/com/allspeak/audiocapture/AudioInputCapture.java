package com.allspeak.audiocapture;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import com.allspeak.utility.Messaging;
//=========================================================================================================================
public class AudioInputCapture
{
    private static final String LOG_TAG         = "AudioInputCapture";

    private AudioInputReceiver mAIReceiver      = null;
    private AudioPlayback mPlayback             = null;
    private CordovaPlugin plugin                = null;

    private CFGParams cfgParams                 = null;     // Capture parameters

    private int nMode                           = ENUMS.CAPTURE_MODE;
    
    private boolean bIsCapturing                = false;
    private Handler mStatusCallback             = null;   // destination handler of status messages
    private Handler mResultCallback             = null;   // destination handler of data result
    private Handler mCommandCallback            = null;   // destination handler of output command
    private CallbackContext mWlCb               = null;   // access to web layer 
    //======================================================================================================================
    public AudioInputCapture(CFGParams params, Handler cb)
    {
        cfgParams       = params;
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;  
    } 
    public AudioInputCapture(CFGParams params, Handler scb, Handler ccb, Handler rcb)
    {
        cfgParams       = params;
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;  
    } 
    public AudioInputCapture(CFGParams params, Handler phandl, CordovaPlugin _plugin)
    {
        this(params, phandl);
        plugin      = _plugin;
    }    
    public AudioInputCapture(CFGParams params, Handler phandl, int mode)
    {
        this(params, phandl);
        nMode = mode;
    }     
    public AudioInputCapture(CFGParams params, Handler phandl, CordovaPlugin _plugin, int mode)
    {
        this(params, phandl, _plugin);
        nMode = mode;
    }     
    public AudioInputCapture(CFGParams params, Handler scb, Handler ccb, Handler rcb, CordovaPlugin _plugin)
    {
        this(params, scb, ccb, rcb);
        plugin      = _plugin;
    } 
    public AudioInputCapture(CFGParams params, Handler scb, Handler ccb, Handler rcb, int mode)
    {
        this(params, scb, ccb, rcb);
        nMode = mode;
    }       
    public AudioInputCapture(CFGParams params, Handler scb, Handler ccb, Handler rcb, CordovaPlugin _plugin, int mode)
    {
        this(params, scb, ccb, rcb, _plugin);
        nMode = mode;
    }    
    
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;        
    }    
    //======================================================================================================================
    
    public boolean start()
    {
        try
        {
            switch(nMode)
            {
                case ENUMS.CAPTURE_MODE:

                    mAIReceiver = new AudioInputReceiver(cfgParams.nSampleRate, cfgParams.nBufferSize, cfgParams.nChannels, cfgParams.sFormat, cfgParams.nAudioSourceType);
                    mAIReceiver.setHandler(mStatusCallback, mCommandCallback, mResultCallback);
                    mAIReceiver.start();   
                    bIsCapturing = true;
                    break;

                case ENUMS.PLAYBACK_MODE:

                    mPlayback = new AudioPlayback(cfgParams.nSampleRate, cfgParams.nBufferSize, cfgParams.nChannels, cfgParams.sFormat, cfgParams.nAudioSourceType);
                    mPlayback.setHandler(mStatusCallback, mCommandCallback, mResultCallback);
                    mPlayback.setWlCb(mWlCb);
                    mPlayback.start();   
                    bIsCapturing = true;                
            }            
            return bIsCapturing;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.CAPTURE_ERROR, "error", e.toString());
            return false;            
        }
    }

    public void stop()
    {
        try
        {
            switch(nMode)
            {
                case ENUMS.CAPTURE_MODE:

                    if(mAIReceiver != null)   if (!mAIReceiver.isInterrupted()) mAIReceiver.interrupt();
                    break;

                case ENUMS.PLAYBACK_MODE:

                    if(mPlayback != null)   if (!mPlayback.isInterrupted()) mPlayback.interrupt();                
            }            
            bIsCapturing = false;
        }
        catch (Exception e) 
        {
            Messaging.sendMessageToHandler(mStatusCallback, ERRORS.CAPTURE_ERROR, "error", e.toString());
        }        
    }
    
    public boolean isCapturing()
    {
        return bIsCapturing;
    }    
    
    public void setPlayBackPercVol(int perc)
    {
        if(nMode == ENUMS.PLAYBACK_MODE && bIsCapturing)
            mPlayback.setPlayBackPercVol(perc);
    }
    
    //========================================================================================
    // ACCESSORY STATIC FUNCTIONS
    //========================================================================================
    public static float[] normalizeAudio(short[] pcmData, float normFactor) 
    {
        int len         = pcmData.length;
        float[] data    = new float[len];
        for (int i = 0; i < len ; i++) {
            data[i]     = (float)(pcmData[i]/normFactor);
        }
//        // If last value is NaN, remove it.
//        if (Float.isNaN(data[data.length - 1])) {data = ArrayUtils.remove(data, data.length - 1);}
        return data;
    }

    /**
     * @param audioBuffer
     * @private
     * @returns {*}
     */
    public static float getAudioLevels(float[] audioBuffer) 
    {
        try 
        {
            float total = 0;
            int length  = audioBuffer.length;
            float absFreq;

            for (int i = 0; i < length; i++) 
            {
                absFreq = Math.abs(audioBuffer[i]);
                total += ( absFreq * absFreq );
            }
            return (float)Math.sqrt(total / length);
        }
        catch (Exception e) {
            throw e;
        }
    }    
    
    /**
     * Convert amplitude to decibel
     *
     * @param amplitudeLevel
     * @returns {number}
     * @private
     */
    public static float getDecibelFromAmplitude(float amplitudeLevel) {
        return (float) (20*Math.log10(amplitudeLevel));
    }    
    //========================================================================================
}