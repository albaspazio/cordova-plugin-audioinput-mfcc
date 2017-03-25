package com.allspeak.audiocapture;

import org.apache.cordova.CordovaPlugin;

import android.os.Handler;
import android.os.Message;
import android.os.Bundle;

import android.content.pm.PackageManager;
import org.apache.cordova.PermissionHelper;
import android.Manifest;

public class AudioInputCapture
{
    private static final String LOG_TAG         = "AudioInputCapture";

    private AudioInputReceiver mReceiver        = null;
    private AudioPlayback mPlayback             = null;
    private CordovaPlugin plugin                = null;

    public static String[]  permissions         = { Manifest.permission.RECORD_AUDIO };
    public static int       RECORD_AUDIO        = 0;
    public static final int PERMISSION_DENIED_ERROR = 20;

    private CFGParams cfgParams                 = null;     // Capture parameters

    public static final int CAPTURE_MODE        = 0;
    public static final int PLAYBACK_MODE       = 1;
    private int nMode                           = CAPTURE_MODE;
    
    private boolean bIsCapturing                = false;
    private Handler handler;
    private Message message;
    private Bundle messageBundle                = new Bundle();    
    //======================================================================================================================
    public AudioInputCapture(CFGParams params, Handler handl, CordovaPlugin _plugin)
    {
        cfgParams   = params;
        handler     = handl;
        plugin      = _plugin;
    }    
    
    public AudioInputCapture(CFGParams params, Handler handl, CordovaPlugin _plugin, int mode)
    {
        this(params, handl, _plugin);
        nMode = mode;
    }    
    
    public void start()
    {
        promptForRecord();
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
    //===========================================================================
    // PRIVATE
    //===========================================================================
    private void startCapturing()
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
    }
    /**
     * Ensure that we have gotten record audio permission
     */
    private void promptForRecord() 
    {
        if(PermissionHelper.hasPermission(plugin, permissions[RECORD_AUDIO])) 
            startCapturing();
        else
            getMicPermission(RECORD_AUDIO);
    }

    /**
    * Prompt user for record audio permission
    */
    protected void getMicPermission(int requestCode) 
    {
        PermissionHelper.requestPermission(plugin, requestCode, permissions[RECORD_AUDIO]);
    }
    /**
     * Handle request permission result
     */
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
    {
        for(int r:grantResults) 
        {
            if(r == PackageManager.PERMISSION_DENIED) 
            {
                sendMessageToHandler("error", "PERMISSION_DENIED_ERROR");
                return;
            }
        }
        startCapturing();
    }    

    private void sendMessageToHandler(String field, String info)
    {
        messageBundle.putString(field, info);
        message = handler.obtainMessage();
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }    
}