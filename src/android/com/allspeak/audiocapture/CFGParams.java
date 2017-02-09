package com.allspeak.audiocapture;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class CFGParams
{
    public int nSampleRate                   = DEFAULT.SAMPLERATE;         //44100,
    public int nBufferSize                   = DEFAULT.BUFFER_SIZE;            //audioinput.AUDIOSOURCE_TYPE.VOICE_COMMUNICATION,
    public int nAudioSourceType              = DEFAULT.AUDIOSOURCE_TYPE;            //audioinput.AUDIOSOURCE_TYPE.VOICE_COMMUNICATION,
    public int nChannels                     = DEFAULT.CHANNELS;            //audioinput.CHANNELS.MONO,
    public String sFormat                    = DEFAULT.FORMAT;  //audioinput.FORMAT.PCM_16BIT,
    public int nConcatenateMaxChunks         = DEFAULT.CONCATENATE_MAX_CHUNKS;    
    public float fNormalizationFactor       = (float)DEFAULT.NORMALIZATION_FACTOR;    
 
    public CFGParams(JSONObject init)
    {
        try
        {
            JSONArray labels = init.names();
            int len_labels   = labels.length();        

            for(int i=0; i<len_labels; i++)
            {
                String field = labels.getString(i);
                switch(field)
                {
                    case "nSampleRate":
                        nSampleRate             = init.getInt(field);
                        break;
                    case "nBufferSize":
                        nBufferSize             = init.getInt(field);
                        break;
                    case "nAudioSourceType":
                        nAudioSourceType        = init.getInt(field);
                        break;
                    case "nChannels":
                        nChannels               = init.getInt(field);
                        break;
                    case "sFormat":
                        sFormat                 = init.getString(field);
                        break;
                    case "nConcatenateMaxChunks":
                        nConcatenateMaxChunks   = init.getInt(field);
                        break;
                    case "fNormalizationFactor":
                        fNormalizationFactor    = (float)init.getDouble(field);
                        break;
                }
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }
    private static class DEFAULT
    {
        public static int SAMPLERATE                = 8000;         //44100,
        public static int AUDIOSOURCE_TYPE          = 7;            //audioinput.AUDIOSOURCE_TYPE.VOICE_COMMUNICATION,
        public static int CHANNELS                  = 1;            //audioinput.CHANNELS.MONO,
        public static String FORMAT                 = "PCM_16BIT";  //audioinput.FORMAT.PCM_16BIT,
        public static int BUFFER_SIZE               = 16384;
        public static int CONCATENATE_MAX_CHUNKS    = 10;
        public static double NORMALIZATION_FACTOR   = 32767.0;
    }    
}
