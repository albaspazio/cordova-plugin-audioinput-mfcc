package com.allspeak.audiocapture;

import org.apache.cordova.CallbackContext;
import org.json.JSONException;
import org.json.JSONObject;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.AudioManager;

import android.os.Process;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import com.allspeak.utility.Messaging;
import com.allspeak.audiocapture.AudioInputCapture;


public class AudioPlayback extends Thread 
{

    private int channelConfigIN                 = AudioFormat.CHANNEL_IN_MONO;
    private int channelConfigOUT                = AudioFormat.CHANNEL_OUT_MONO;
    private int audioFormat                     = AudioFormat.ENCODING_PCM_16BIT;
    private int sampleRateInHz                  = 8000;
    private int nTotalReadBytes                 = 0;
    private static float fNormalizationFactor   = (float)32767.0;

    // For the recording buffer
    private int minBufferSize                   = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfigIN, audioFormat);

    // Used for reading from the AudioRecord buffer
    private int readBufferSize                  = minBufferSize;

    private AudioRecord mRecorder               = null;
    private AudioTrack mAudioTrack              = null;
    private AudioTrack mAudioManager            = null;
    
    private Handler mStatusCallback             = null;   // destination handler of status messages
    private Handler mResultCallback             = null;   // destination handler of data result
    private Handler mCommandCallback            = null;   // destination handler of output command
    private CallbackContext mWlCb               = null;   // access to web layer
    //==========================================================================================================
    public AudioPlayback() 
    {
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz, channelConfigIN, audioFormat, minBufferSize);
    }

    public AudioPlayback(int sampleRate, int bufferSizeInBytes, int channels, String format, int audioSource) 
    {
        sampleRateInHz      = sampleRate;
        switch (channels) {
            case 2:
                channelConfigIN     = AudioFormat.CHANNEL_IN_STEREO;
                channelConfigOUT    = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 1:
            default:
                channelConfigIN = AudioFormat.CHANNEL_IN_MONO;
                channelConfigOUT = AudioFormat.CHANNEL_OUT_MONO;
                break;
        }
        if(format == "PCM_8BIT")    audioFormat = AudioFormat.ENCODING_PCM_8BIT;
        else                        audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        readBufferSize = bufferSizeInBytes;

        // Get the minimum recording buffer size for the specified configuration
        minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfigIN, audioFormat);

        // Ensure that the given recordingBufferSize isn't lower than the minimum buffer size allowed for the current configuration
        if (readBufferSize < minBufferSize) readBufferSize = minBufferSize;

        mRecorder       = new AudioRecord(audioSource, sampleRateInHz, channelConfigIN, audioFormat, minBufferSize);
        mAudioTrack     = new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRateInHz, channelConfigOUT, audioFormat, minBufferSize, AudioTrack.MODE_STREAM);
    }
    //==========================================================================================================

    public void setHandler(Handler handler) 
    {
        mStatusCallback     = handler;        
        mCommandCallback    = handler;        
        mResultCallback     = handler;     
    }
    
    public void setHandler(Handler scb, Handler ccb, Handler rcb) 
    {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;     
    }
    
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;        
    }    
    //==========================================================================================================

    public void setPlayBackPercVol(int newperc) 
    {
        float max       = mAudioTrack.getMaxVolume();
        float newgain   = (newperc*max)/100;
        mAudioTrack.setVolume(newgain);
    }

    @Override
    public void run() 
    {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] audioBuffer  = new short[readBufferSize];
         
        synchronized(this) 
        {
            mRecorder.startRecording();
            mAudioTrack.setPlaybackRate(sampleRateInHz);
            mAudioTrack.play();      
            Messaging.sendMessageToHandler(mStatusCallback, ENUMS.CAPTURE_STATUS_STARTED, "", "");
            
            while (!isInterrupted()) 
            {
                try
                {
                    int numReadBytes = mRecorder.read(audioBuffer, 0, readBufferSize);
                    mAudioTrack.write(audioBuffer, 0, audioBuffer.length);                    
                    if (numReadBytes > 0)
                    {
                        float[] normalizedData  = AudioInputCapture.normalizeAudio(audioBuffer, fNormalizationFactor);
                        float rms               = AudioInputCapture.getAudioLevels(normalizedData);
                        float decibels          = AudioInputCapture.getDecibelFromAmplitude(rms);
                        
                        JSONObject info = new JSONObject(); 
                        info.put("data_type", ENUMS.CAPTURE_DATADEST_JS_DB); 
                        info.put("type", ENUMS.CAPTURE_RESULT);                        
                        info.put("decibels", Float.toString(decibels));
                        Messaging.sendUpdate2Web(mWlCb, info, true);
                    }
                }
                catch(Exception ex) 
                {
                    Messaging.sendMessageToHandler(mStatusCallback, ERRORS.CAPTURE_ERROR, "error", ex.toString());
                    break;
                }
            }
            if (mRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) 
            {
                mRecorder.stop();
                mAudioTrack.stop();
            }
            Messaging.sendMessageToHandler(mStatusCallback, ENUMS.CAPTURE_STATUS_STOPPED, "stop", Integer.toString(nTotalReadBytes));
            mRecorder.release();
            mRecorder = null;
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }
    //==========================================================================================================
}