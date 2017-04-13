package com.allspeak.audiocapture;

import org.apache.cordova.CordovaPlugin;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

//=========================================================================================================================
public class AudioInputCapture
{
    private static final String LOG_TAG         = "AudioInputCapture";

    private AudioInputReceiver mReceiver        = null;
    private AudioPlayback mPlayback             = null;
    private CordovaPlugin plugin                = null;

    private CFGParams cfgParams                 = null;     // Capture parameters

    public static final int CAPTURE_MODE        = 0;
    public static final int PLAYBACK_MODE       = 1;
    private int nMode                           = CAPTURE_MODE;
    
    private boolean bIsCapturing                = false;
    private Handler handler;
    private Message message;
    private Bundle messageBundle                = new Bundle();    

    public static final int STATUS_CAPTURE_START        = 1;
    public static final int STATUS_CAPTURE_DATA         = 2;    
    public static final int STATUS_CAPTURE_STOP         = 3;    
    public static final int STATUS_CAPTURE_ERROR        = 4;     
    //======================================================================================================================
    public AudioInputCapture(CFGParams params, Handler handl)
    {
        cfgParams   = params;
        handler     = handl;
    } 
    
    public AudioInputCapture(CFGParams params, Handler handl, CordovaPlugin _plugin)
    {
        this(params, handl);
        plugin      = _plugin;
    }    
    
    public AudioInputCapture(CFGParams params, Handler handl, int mode)
    {
        this(params, handl);
        nMode = mode;
    }    
    
    public AudioInputCapture(CFGParams params, Handler handl, CordovaPlugin _plugin, int mode)
    {
        this(params, handl, _plugin);
        nMode = mode;
    }    
    //======================================================================================================================
    
    public boolean start()
    {
        try
        {
            switch(nMode)
            {
                case CAPTURE_MODE:

                    mReceiver = new AudioInputReceiver(cfgParams.nSampleRate, cfgParams.nBufferSize, cfgParams.nChannels, cfgParams.sFormat, cfgParams.nAudioSourceType);
                    mReceiver.setHandler(handler);
                    mReceiver.start();   
                    bIsCapturing = true;
                    break;

                case PLAYBACK_MODE:

                    mPlayback = new AudioPlayback(cfgParams.nSampleRate, cfgParams.nBufferSize, cfgParams.nChannels, cfgParams.sFormat, cfgParams.nAudioSourceType);
                    mPlayback.setHandler(handler);
                    mPlayback.start();   
                    bIsCapturing = true;                
            }            
            return bIsCapturing;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            sendMessageToHandler("error", e.toString());
            return false;            
        }
    }

    public void stop()
    {
        try
        {
            switch(nMode)
            {
                case CAPTURE_MODE:

                    if(mReceiver != null)   if (!mReceiver.isInterrupted()) mReceiver.interrupt();
                    break;

                case PLAYBACK_MODE:

                    if(mPlayback != null)   if (!mPlayback.isInterrupted()) mPlayback.interrupt();                
            }            
            bIsCapturing = false;
        }
        catch (Exception e) 
        {
            sendMessageToHandler("error", e.toString());
        }        
    }
    
    public boolean isCapturing()
    {
        return bIsCapturing;
    }    
    
    public void setPlayBackPercVol(int perc)
    {
        if(nMode == PLAYBACK_MODE && bIsCapturing)
            mPlayback.setPlayBackPercVol(perc);
    }
    
    //======================================================================================================================
    // PRIVATE
    //======================================================================================================================
    private void sendMessageToHandler(String field, String info)
    {
        messageBundle.putString(field, info);
        message = handler.obtainMessage();
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }    
}
