package com.allspeak.audiocapture;

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
    private Handler handler;
    private Message message;
    private Bundle messageBundle = new Bundle();
    
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

    public void setHandler(Handler handler) {
        this.handler = handler;
    }
    
    public void sendMessageToHandler(String field, String info)
    {
        message = handler.obtainMessage();
        messageBundle.putString(field, info);
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }    
    
    public void sendMessageToHandler(String field, int num)
    {
        message = handler.obtainMessage();
        messageBundle.putInt(field, num);
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }    
    
    public void sendDataToHandler(String field, float[] normalized_audio)
    {
        message = handler.obtainMessage();
        messageBundle.putFloatArray(field, normalized_audio);
        message.setData(messageBundle);
        handler.sendMessage(message);        
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


    @Override
    public void run() {
        int numReadBytes    = 0;
        nTotalReadBytes     = 0;
        short[] audioBuffer = new short[readBufferSize];
         
        synchronized(this) 
        {
            recorder.startRecording();
            while (!isInterrupted()) 
            {
                try
                {
                    numReadBytes = recorder.read(audioBuffer, 0, readBufferSize);
                    if (numReadBytes > 0)
                    {
                        nTotalReadBytes         += numReadBytes; 
                        float[] normalizedData  = normalizeAudio(audioBuffer);
                        sendDataToHandler("data", normalizedData);
                    }
                }
                catch(Exception ex) {
                    sendMessageToHandler("error", ex.toString());
                    break;
                }
            }
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
            }
            sendMessageToHandler("stop", Integer.toString(nTotalReadBytes));
            recorder.release();
            recorder = null;
        }
    }
}