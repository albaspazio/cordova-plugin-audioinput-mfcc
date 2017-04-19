package com.allspeak.audiocapture;

import com.allspeak.ENUMS;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Base64;

import java.util.Arrays;
import java.io.InterruptedIOException;

public class AudioInputReceiver extends Thread {

    private final int RECORDING_BUFFER_FACTOR   = 5;
    private int channelConfig                   = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat                     = AudioFormat.ENCODING_PCM_16BIT;
    private int sampleRateInHz                  = 44100;
    private int nTotalReadBytes                 = 0;
    private static float fNormalizationFactor   = (float)32767.0;

    // For the recording buffer
    private int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
    private int recordingBufferSize = minBufferSize * RECORDING_BUFFER_FACTOR;

    // Used for reading from the AudioRecord buffer
    private int readBufferSize = minBufferSize;

    private AudioRecord recorder;
    private Handler mParentHandler;
    private Handler mSecondHandler      = null;
    private Message message;
    private Bundle messageBundle = new Bundle();
    
    //==================================================================================================
    public AudioInputReceiver() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz, channelConfig, audioFormat, minBufferSize * RECORDING_BUFFER_FACTOR);
    }

    public AudioInputReceiver(int sampleRate, int bufferSizeInBytes, int channels, String format, int audioSource) 
    {
        this(sampleRate, bufferSizeInBytes, channels, format, audioSource, fNormalizationFactor);
    }
    public AudioInputReceiver(int sampleRate, int bufferSizeInBytes, int channels, String format, int audioSource, float _normalizationFactor) 
    {
        sampleRateInHz      = sampleRate;
        fNormalizationFactor = _normalizationFactor;
        switch (channels) {
            case 2:
                channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                break;
            case 1:
            default:
                channelConfig = AudioFormat.CHANNEL_IN_MONO;
                break;
        }
        if(format == "PCM_8BIT") {
            audioFormat = AudioFormat.ENCODING_PCM_8BIT;
        }
        else {
            audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        }

        readBufferSize = bufferSizeInBytes;

        // Get the minimum recording buffer size for the specified configuration
        minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

        // We use a recording buffer size larger than the one used for reading to avoid buffer underrun.
        recordingBufferSize = readBufferSize * RECORDING_BUFFER_FACTOR;

        // Ensure that the given recordingBufferSize isn't lower than the minimum buffer size allowed for the current configuration
        if (recordingBufferSize < minBufferSize) {
            recordingBufferSize = minBufferSize;
        }

        recorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, recordingBufferSize);
    }

    public void setHandler(Handler parentHandler) {
        this.mParentHandler = parentHandler;
    }    

    public void setHandler(Handler parentHandler, Handler secondHandler) {
        this.mParentHandler = parentHandler;
        this.mSecondHandler = secondHandler;
    }    
    //==================================================================================================

    private void sendMessageToHandler(int action_code, String field, String info)
    {
        message = mParentHandler.obtainMessage();
        messageBundle.putString(field, info);
        message.setData(messageBundle);
        message.what    = action_code;
        mParentHandler.sendMessage(message);        
    }    
    
    private void sendMessageToHandler(int action_code, String field, int num)
    {
        message = mParentHandler.obtainMessage();
        messageBundle.putInt(field, num);
        message.setData(messageBundle);
        message.what    = action_code;
        mParentHandler.sendMessage(message);        
    }    
    
    private Bundle sendDataToHandler(int action_code, String field, float[] normalized_audio)
    {
        Bundle b        = new Bundle();
        b.putFloatArray(field, normalized_audio);        
        
        Message pmessage = mParentHandler.obtainMessage();
        pmessage.what    = action_code;
        pmessage.setData(b);
        mParentHandler.sendMessage(pmessage);  
        return b;
    }    
    
    private void sendDataToHandlers(int action_code, String field, float[] normalized_audio)
    {
        Bundle b        = sendDataToHandler(action_code, field, normalized_audio);
        
        if(mSecondHandler != null)
        {
            Message smessage = mSecondHandler.obtainMessage();
            smessage.what    = action_code;
            smessage.setData(b);
            mSecondHandler.sendMessage(smessage);        
        }
    }    
    
    private float[] normalizeAudio(short[] pcmData) 
    {
        int len         = pcmData.length;
        float[] data    = new float[len];
        for (int i = 0; i < len ; i++) {
            data[i]     = (float)(pcmData[i]/fNormalizationFactor);
        }

//        // If last value is NaN, remove it.
//        if (Float.isNaN(data[data.length - 1])) {
//            data = ArrayUtils.remove(data, data.length - 1);
//        }
        return data;
    }


    //==================================================================================================
    @Override
    public void run() {
        int numReadBytes    = 0;
        nTotalReadBytes     = 0;
        short[] audioBuffer = new short[readBufferSize];
         
        synchronized(this) 
        {
            recorder.startRecording();
            sendMessageToHandler(ENUMS.CAPTURE_STARTED, "", "");
            while (!isInterrupted()) 
            {
                try
                {
                    numReadBytes = recorder.read(audioBuffer, 0, readBufferSize);
                    if (numReadBytes > 0)
                    {
                        nTotalReadBytes         += numReadBytes; 
                        float[] normalizedData  = normalizeAudio(audioBuffer);
                        sendDataToHandlers(ENUMS.CAPTURE_DATA, "data", normalizedData);
                    }
                }
                catch(Exception ex) {
                    sendMessageToHandler(ENUMS.CAPTURE_ERROR, "error", ex.toString());
                    break;
                }
            }
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) 
                recorder.stop();
            
            sendMessageToHandler(ENUMS.CAPTURE_STOPPED, "stop", Integer.toString(nTotalReadBytes));
            recorder.release();
            recorder = null;
        }
    }
}