package com.allspeak.audiocapture;

import org.apache.cordova.CordovaPlugin;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

import com.allspeak.ENUMS;
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
    private Handler mParentHandler              = null;
    private Handler mSecondHandler              = null;
    private Message message;
    private Bundle messageBundle                = new Bundle();    
 
    //======================================================================================================================
    public AudioInputCapture(CFGParams params, Handler phandl)
    {
        cfgParams       = params;
        mParentHandler  = phandl;
        mSecondHandler  = null;
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
    
    
    public AudioInputCapture(CFGParams params, Handler phandl, Handler shandl)
    {
        cfgParams       = params;
        mParentHandler  = phandl;
        mSecondHandler  = shandl;
    } 
    
    public AudioInputCapture(CFGParams params, Handler phandl, Handler shandl, CordovaPlugin _plugin)
    {
        this(params, phandl, shandl);
        plugin      = _plugin;
    }       
    
    public AudioInputCapture(CFGParams params, Handler phandl, Handler shandl, int mode)
    {
        this(params, phandl,shandl);
        nMode = mode;
    }    
    
    public AudioInputCapture(CFGParams params, Handler phandl, Handler shandl, CordovaPlugin _plugin, int mode)
    {
        this(params, phandl, shandl, _plugin);
        nMode = mode;
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
                    mAIReceiver.setHandler(mParentHandler, mSecondHandler);
                    mAIReceiver.start();   
                    bIsCapturing = true;
                    break;

                case ENUMS.PLAYBACK_MODE:

                    mPlayback = new AudioPlayback(cfgParams.nSampleRate, cfgParams.nBufferSize, cfgParams.nChannels, cfgParams.sFormat, cfgParams.nAudioSourceType);
                    mAIReceiver.setHandler(mParentHandler, mSecondHandler);
                    mPlayback.start();   
                    bIsCapturing = true;                
            }            
            return bIsCapturing;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            sendMessageToHandler(ENUMS.CAPTURE_ERROR, "error", e.toString());
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
            sendMessageToHandler(ENUMS.CAPTURE_ERROR, "error", e.toString());
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
    
    //======================================================================================================================
    // PRIVATE
    //======================================================================================================================
    private void sendMessageToHandler(int action_code, String field, String str)
    {
        message = mParentHandler.obtainMessage();
        messageBundle.putString(field, str);
        message.setData(messageBundle);
        message.what    = action_code;
        mParentHandler.sendMessage(message);        
    }
}