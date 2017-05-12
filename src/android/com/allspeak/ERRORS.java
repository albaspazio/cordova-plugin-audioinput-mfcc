package com.allspeak;

public class ERRORS
{
    public static final int SERVICE_INIT                = 100; // triggered in SpeechRecognitionService) while initializing the service
    
    public static final int INVALID_PARAMETER           = 101;
    public static final int MISSING_PARAMETER           = 102;
    public static final int ENCODING_ERROR              = 103;

    public static final int CAPTURE_ERROR               = 110;
    public static final int PLUGIN_INIT_CAPTURE         = 111; // triggered (in SpeechRecognitionPlugin) while starting a service function
    public static final int SERVICE_INIT_CAPTURE        = 112; // triggered (in SpeechRecognitionPlugin) while starting a service function
    public static final int PLUGIN_INIT_PLAYBACK        = 113; // triggered (in SpeechRecognitionPlugin) while starting a service function   
    public static final int SERVICE_INIT_PLAYBACK       = 114; // triggered (in SpeechRecognitionPlugin) while starting a service function   
    public static final int CAPTURE_ALREADY_STARTED     = 115;    
    
    public static final int VAD_ERROR                   = 120;
    public static final int PLUGIN_INIT_RECOGNITION     = 121; // triggered (in SpeechRecognitionPlugin) while starting a service function
    public static final int SERVICE_INIT_RECOGNITION    = 122; // triggered (in SpeechRecognitionPlugin) while starting a service function
    
    public static final int MFCC_ERROR                  = 130; 
    public static final int PLUGIN_INIT_MFCC            = 131; // triggered (in SpeechRecognitionPlugin) while starting a service function   
    public static final int SERVICE_INIT_MFCC           = 132; // triggered (in SpeechRecognitionPlugin) while starting a service function   
    
    public static final int TF_ERROR                    = 150;  

    
    public static final int UNSPECIFIED                 = 999;    
}