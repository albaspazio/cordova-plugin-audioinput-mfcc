package com.allspeak.audiocapture;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Handler;

import com.allspeak.utility.Messaging;
import com.allspeak.audiocapture.AudioInputCapture;


import java.io.InterruptedIOException;

public class AudioInputReceiver extends Thread 
{

    private final int RECORDING_BUFFER_FACTOR   = 5;
    private int channelConfig                   = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat                     = AudioFormat.ENCODING_PCM_16BIT;
    private int sampleRateInHz                  = 44100;
    private int nTotalReadBytes                 = 0;
    private static float fNormalizationFactor   = (float)32767.0;

    // For the recording buffer
    private int minBufferSize                   = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
    private int recordingBufferSize             = minBufferSize * RECORDING_BUFFER_FACTOR;

    // Used for reading from the AudioRecord buffer
    private int readBufferSize                  = minBufferSize;

    private AudioRecord recorder;
    private Handler mStatusCallback             = null;   // destination handler of status messages
    private Handler mResultCallback             = null;   // destination handler of data result
    private Handler mCommandCallback            = null;   // destination handler of output command
    
    //==================================================================================================
    public AudioInputReceiver() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz, channelConfig, audioFormat, minBufferSize * RECORDING_BUFFER_FACTOR);
    }

    public AudioInputReceiver(int sampleRate, int bufferSizeInBytes, int channels, String format, int audioSource)  {
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
        mStatusCallback     = handler;        
        mCommandCallback    = handler;        
        mResultCallback     = handler;     
    }    

    public void setHandler(Handler scb, Handler ccb, Handler rcb)  {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;     
    }    

    //==================================================================================================
    @Override
    public void run() 
    {
        int numReadBytes    = 0;
        nTotalReadBytes     = 0;
        short[] audioBuffer = new short[readBufferSize];
         
        synchronized(this) 
        {
            recorder.startRecording();
            Messaging.sendMessageToHandler(mStatusCallback, ENUMS.CAPTURE_STATUS_STARTED, "", "");
            while (!isInterrupted()) 
            {
                try
                {
                    numReadBytes = recorder.read(audioBuffer, 0, readBufferSize);
                    if (numReadBytes > 0)
                    {
                        nTotalReadBytes         += numReadBytes; 
                        float[] normalizedData  = AudioInputCapture.normalizeAudio(audioBuffer, fNormalizationFactor);
                        Messaging.sendDataToHandler(mResultCallback, ENUMS.CAPTURE_RESULT, "data", normalizedData);
                    }
                }
                catch(Exception ex) 
                {
                    Messaging.sendMessageToHandler(mStatusCallback, ERRORS.CAPTURE_ERROR, "error", ex.toString());
                    break;
                }
            }
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) 
                recorder.stop();
            
            Messaging.sendMessageToHandler(mStatusCallback, ENUMS.CAPTURE_STATUS_STOPPED, "stop", Integer.toString(nTotalReadBytes));
            recorder.release();
            recorder = null;
        }
    }
}