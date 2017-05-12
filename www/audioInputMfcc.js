var argscheck   = require('cordova/argscheck'),
    utils       = require('cordova/utils'),
    exec        = require('cordova/exec'),
    channel     = require('cordova/channel');

var audioinput          = {};

audioinput.pluginName   = "AudioInputCaptureMFCC";
audioinput.ENUM         = {};
audioinput.ENUM.capture = {};
audioinput.ENUM.mfcc    = {};

//=====================================================================================================
// PLUGIN RETURN VALUES
//=====================================================================================================
// MUST MAP a plugin's ENUMS.java subset
// it maps:
//      1) native=>js events (results & statuses)
//      2) native behaviour  (data origin - destination - type - return type  etc..)
//      
//      (results & statuses, excluding internal CMD & enums)
audioinput.ENUM.PLUGIN   = 
{
    CAPTURE_STATUS_STARTED          : 11, 
    CAPTURE_RESULT                  : 12, 
    CAPTURE_STATUS_STOPPED          : 13, 

    SPEECH_STATUS_STARTED           : 20,
    SPEECH_STATUS_STOPPED           : 21,
    SPEECH_STATUS_SENTENCE          : 22,
    SPEECH_STATUS_MAX_LENGTH        : 23,
    SPEECH_STATUS_MIN_LENGTH        : 24,
    
    MFCC_RESULT                     : 40,
    MFCC_STATUS_PROGRESS_DATA       : 31,
    MFCC_STATUS_PROGRESS_FILE       : 32,
    MFCC_STATUS_PROGRESS_FOLDER     : 33, //   suspended for a bug
    
    TF_STATUS_PROCESS_STARTED       : 60,
    TF_RESULT                       : 62,
    
    CAPTURE_DATADEST_NONE           : 200,
    CAPTURE_DATADEST_JS_RAW         : 201,
    CAPTURE_DATADEST_JS_DB          : 202,    
    CAPTURE_DATADEST_JS_RAWDB       : 203,    
    
    CAPTURE_MODE                    : 211,
    PLAYBACK_MODE                   : 212,

    MFCC_DATADEST_NOCALC            : 230,    // DO NOT CALCULATE MFCC during capture
    MFCC_DATADEST_NONE              : 231,    // data(float[][])=> PLUGIN
    MFCC_DATADEST_JSPROGRESS        : 232,    // data=> PLUGIN + progress(filename)=> WEB
    MFCC_DATADEST_JSDATA            : 233,    // data=> PLUGIN + progress(filename) + data(JSONArray)=> WEB
    MFCC_DATADEST_JSDATAWEB         : 234,    // data(JSONArray)=> WEB
    MFCC_DATADEST_FILE              : 235,    // progress(filename)=> PLUGIN + data(String)=> FILE
    MFCC_DATADEST_FILEWEB           : 236,    // progress(filename)=> PLUGIN + data(String)=> FILE + data(JSONArray)=> WEB
    MFCC_DATADEST_ALL               : 237,    // data=> PLUGIN + data(String)=> FILE + data(JSONArray)=> WEB
    
    MFCC_DATAORIGIN_JSONDATA        : 240,
    MFCC_DATAORIGIN_FILE            : 241,
    MFCC_DATAORIGIN_FOLDER          : 242,
    MFCC_DATAORIGIN_RAWDATA         : 243,   
    
    MFCC_DATATYPE_MFPARAMETERS      : 250,
    MFCC_DATATYPE_MFFILTERS         : 251       
    
    
}; 

// MUST MAP plugin's ERRORS.java
audioinput.ENUM.ERRORS   = 
{
    SERVICE_INIT                    : 100, 
    
    INVALID_PARAMETER               : 101, 
    MISSING_PARAMETER               : 102, 
    ENCODING_ERROR                  : 103, 
    
    CAPTURE_ERROR                   : 110, 
    PLUGIN_INIT_CAPTURE             : 111, 
    SERVICE_INIT_CAPTURE            : 112, 
    PLUGIN_INIT_PLAYBACK            : 113,    
    SERVICE_INIT_PLAYBACK           : 114,    
    CAPTURE_ALREADY_STARTED         : 115, 
    
    VAD_ERROR                       : 120,
    PLUGIN_INIT_RECOGNITION         : 121,
    SERVICE_INIT_RECOGNITION        : 122,
    
    MFCC_ERROR                      : 130, 
    PLUGIN_INIT_MFCC                : 131,
    SERVICE_INIT_MFCC               : 132,
    
    TF_ERROR                        : 150, 
   
    UNSPECIFIED                     : 999
}; 

//=====================================================================================================
// ENUMS
//=====================================================================================================
audioinput.ENUM.capture  = {};
audioinput.ENUM.mfcc     = {};
audioinput.ENUM.vad      = {};
audioinput.ENUM.tf       = {};

// Supported audio formats
audioinput.ENUM.capture.FORMAT = {
    PCM_16BIT           : 'PCM_16BIT',
    PCM_8BIT            : 'PCM_8BIT'
};

// Possible channels num
audioinput.ENUM.capture.CHANNELS = {
    MONO                : 1,
    STEREO              : 2
};

// Common Sampling rates
audioinput.ENUM.capture.SAMPLERATE = {
    TELEPHONE_8000Hz    : 8000,
    CD_QUARTER_11025Hz  : 11025,
    VOIP_16000Hz        : 16000,
    CD_HALF_22050Hz     : 22050,
    MINI_DV_32000Hz     : 32000,
    CD_XA_37800Hz       : 37800,
    NTSC_44056Hz        : 44056,
    CD_AUDIO_44100Hz    : 44100
};

// Buffer Size
audioinput.ENUM.capture.BUFFER_SIZES = {
    BS_256              : 256,
    BS_512              : 512,
    BS_1024             : 1024,
    BS_2048             : 2048,
    BS_4096             : 4096
};

// Audio Source types
audioinput.ENUM.capture.AUDIOSOURCE_TYPE = {
    DEFAULT             : 0,
    CAMCORDER           : 5,
    MIC                 : 1,
    UNPROCESSED         : 9,
    VOICE_COMMUNICATION : 7,
    VOICE_RECOGNITION   : 6
};

//-----------------------------------------------------------------------------------------------------
// Default values
//-----------------------------------------------------------------------------------------------------
audioinput.ENUM.capture.DEFAULT = {
    SAMPLERATE              : audioinput.ENUM.capture.SAMPLERATE.TELEPHONE_8000Hz,
    BUFFER_SIZE             : audioinput.ENUM.capture.BUFFER_SIZES.BS_1024,
    CHANNELS                : audioinput.ENUM.capture.CHANNELS.MONO,
    FORMAT                  : audioinput.ENUM.capture.FORMAT.PCM_16BIT,
    AUDIOSOURCE_TYPE        : audioinput.ENUM.capture.AUDIOSOURCE_TYPE.DEFAULT,
    NORMALIZE               : true,
    NORMALIZATION_FACTOR    : 32767.0,
    CONCATENATE_MAX_CHUNKS  : 10,
    DATA_DEST               : audioinput.ENUM.PLUGIN.CAPTURE_DATADEST_JS_DB
};

audioinput.ENUM.mfcc.DEFAULT = {
    nNumberOfMFCCParameters : 12,
    dSamplingFrequency      : 8000.0,
    nNumberofFilters        : 24,
    nFftLength              : 256,
    bIsLifteringEnabled     : true,
    nLifteringCoefficient   : 22,
    bCalculate0ThCoeff      : true,
    nWindowDistance         : 80,
    nWindowLength           : 200,
    nDataType               : audioinput.ENUM.PLUGIN.MFCC_DATATYPE_MFFILTERS,
    nDataDest               : audioinput.ENUM.PLUGIN.MFCC_DATADEST_NOCALC,
    nDataOrig               : audioinput.ENUM.PLUGIN.MFCC_DATAORIGIN_RAWDATA,
    sOutputPath             : "",
    nContextFrames          : 11
};
//-----------------------------------------------------------------------------------------------------

audioinput.mfcc = {};
audioinput.mfcc.params = {};

audioinput.capture = {};
audioinput.capture.params = {};

audioinput.checkCaptureParams = function(capture_params)
{
    audioinput.capture.params.nSampleRate               = capture_params.nSampleRate            || audioinput.ENUM.capture.DEFAULT.SAMPLERATE;
    audioinput.capture.params.nBufferSize               = capture_params.nBufferSize            || audioinput.ENUM.capture.DEFAULT.BUFFER_SIZE;
    audioinput.capture.params.nChannels                 = capture_params.nChannels              || audioinput.ENUM.capture.DEFAULT.CHANNELS;
    audioinput.capture.params.sFormat                   = capture_params.sFormat                || audioinput.ENUM.capture.DEFAULT.FORMAT;
    audioinput.capture.params.nAudioSourceType          = capture_params.nAudioSourceType       || 0;
    audioinput.capture.params.nNormalize                = typeof capture_params.nNormalize == 'boolean' ? audioinput.ENUM.capture.nNormalize : audioinput.ENUM.capture.DEFAULT.NORMALIZE;
    audioinput.capture.params.fNormalizationFactor      = capture_params.fNormalizationFactor   || audioinput.ENUM.capture.DEFAULT.NORMALIZATION_FACTOR;
    audioinput.capture.params.nConcatenateMaxChunks     = capture_params.nConcatenateMaxChunks  || audioinput.ENUM.capture.DEFAULT.CONCATENATE_MAX_CHUNKS;
    audioinput.capture.params.nDataDest                 = capture_params.nDataDest              || audioinput.ENUM.capture.DEFAULT.DATA_DEST;

    if (audioinput.capture.params.nChannels < 1 && audioinput.capture.params.nChannels > 2) {
        throw "Invalid number of channels (" + audioinput.capture.params.nChannels + "). Only mono (1) and stereo (2) is" +" supported.";
    }
    if (audioinput.capture.params.sFormat != "PCM_16BIT" && audioinput.capture.params.sFormat != "PCM_8BIT") {
        throw "Invalid format (" + audioinput.capture.params.sFormat + "). Only 'PCM_8BIT' and 'PCM_16BIT' is" + " supported.";
    }
    if (audioinput.capture.params.nBufferSize <= 0) {
        throw "Invalid bufferSize (" + audioinput.capture.params.nBufferSize + "). Must be greater than zero.";
    }
    if (audioinput.capture.params.nConcatenateMaxChunks <= 0) {
        throw "Invalid concatenateMaxChunks (" + audioinput.capture.params.nConcatenateMaxChunks + "). Must be greater than zero.";
    }
    
    return JSON.stringify(audioinput.capture.params); 
};

audioinput.checkMfccParams = function(mfcc_params)
{
    audioinput.mfcc.params.nNumberOfMFCCParameters      = mfcc_params.nNumberOfMFCCParameters   || audioinput.ENUM.mfcc.DEFAULT.nNumberOfMFCCParameters; //without considering 0-th  
    audioinput.mfcc.params.dSamplingFrequency           = mfcc_params.dSamplingFrequency        || audioinput.ENUM.mfcc.DEFAULT.dSamplingFrequency;    
    audioinput.mfcc.params.nNumberofFilters             = mfcc_params.nNumberofFilters          || audioinput.ENUM.mfcc.DEFAULT.nNumberofFilters;    
    audioinput.mfcc.params.nFftLength                   = mfcc_params.nFftLength                || audioinput.ENUM.mfcc.DEFAULT.nFftLength;      
    audioinput.mfcc.params.bIsLifteringEnabled          = mfcc_params.bIsLifteringEnabled       || audioinput.ENUM.mfcc.DEFAULT.bIsLifteringEnabled ;    
    audioinput.mfcc.params.nLifteringCoefficient        = mfcc_params.nLifteringCoefficient     || audioinput.ENUM.mfcc.DEFAULT.nLifteringCoefficient;
    audioinput.mfcc.params.bCalculate0ThCoeff           = mfcc_params.bCalculate0ThCoeff        || audioinput.ENUM.mfcc.DEFAULT.bCalculate0ThCoeff;
    audioinput.mfcc.params.nWindowDistance              = mfcc_params.nWindowDistance           || audioinput.ENUM.mfcc.DEFAULT.nWindowDistance;
    audioinput.mfcc.params.nWindowLength                = mfcc_params.nWindowLength             || audioinput.ENUM.mfcc.DEFAULT.nWindowLength;          
    audioinput.mfcc.params.nDataType                    = mfcc_params.nDataType                 || audioinput.ENUM.mfcc.DEFAULT.nDataType;          
    audioinput.mfcc.params.nDataDest                    = mfcc_params.nDataDest                 || audioinput.ENUM.mfcc.DEFAULT.nDataDest;          
    audioinput.mfcc.params.nDataOrig                    = mfcc_params.nDataOrig                 || audioinput.ENUM.mfcc.DEFAULT.nDataOrig;          
    audioinput.mfcc.params.sOutputPath                  = mfcc_params.sOutputPath               || audioinput.ENUM.mfcc.DEFAULT.sOutputPath;          
    audioinput.mfcc.params.nContextFrames               = mfcc_params.nContextFrames            || audioinput.ENUM.mfcc.DEFAULT.nContextFrames;          
    
    return JSON.stringify(audioinput.mfcc.params); 
};
//---------------------------------------------------------------
/**
 * Start capture of Audio input
 *
 * @param {Object} cfg
 * keys:
 *  sampleRateInHz (44100),
 *  bufferSize (16384),
 *  channels (1 (mono) or 2 (stereo)),
 *  format ('PCM_8BIT' or 'PCM_16BIT'),
 *  normalize (true || false),
 *  normalizationFactor (create float data by dividing the audio data with this factor; default: 32767.0)
 *  streamToWebAudio (The plugin will handle all the conversion of raw data to audio)
 *  audioContext (If no audioContext is given, one will be created)
 *  concatenateMaxChunks (How many packets will be merged each time, low = low latency but can require more resources)
 *  audioSourceType (Use audioinput.AUDIOSOURCE_TYPE.)
 */
audioinput.startCapture = function (captureCfg, mfccCfg) {
    if (!audioinput._capturing) 
    {
        if(captureCfg == null)  captureCfg = {};
        if(mfccCfg == null)     mfccCfg = {};
        
        // overwrite default params with exogenous ones
        var json_capture_params     = audioinput.checkCaptureParams(captureCfg);
        var json_mfcc_params        = audioinput.checkMfccParams(mfccCfg);
        
        exec(audioinput._pluginEvent, audioinput._pluginError, audioinput.pluginName, "startCapture",
            [json_capture_params,
             json_mfcc_params]);
    }
    else {
        throw "Already capturing!";
    }
};

audioinput.startMicPlayback = function (captureCfg) 
{
    if (!audioinput._capturing) 
    {
        if(captureCfg == null)  captureCfg = {};
        var json_capture_params     = audioinput.checkCaptureParams(captureCfg);  // overwrite default params with exogenous ones
        exec(audioinput._pluginEvent, audioinput._pluginError, audioinput.pluginName, "startMicPlayback", [json_capture_params]);
    }
    else {
        throw "Already capturing!";
    }
};

/**
 * Stop capturing audio
 */
audioinput.stopCapture = function () {
    if (audioinput._capturing) {
        exec(audioinput._pluginEvent, audioinput._pluginError, audioinput.pluginName, "stopCapture", []);
    }
};


audioinput.setPlayBackPercVol = function (perc) {
    if (audioinput._capturing) 
        exec(null, audioinput._pluginError, audioinput.pluginName, "setPlayBackPercVol", [perc]);
};

audioinput.getMFCC = function(mfcc_params, source, filepath_noext)
{
    var mfcc_json_params    = audioinput.checkMfccParams(mfcc_params);
    
    //check params consistency
    if(source == null || !source.length)
    {
        errorCB("ERROR in mfccCalculation: source is empty")
        return false;
    }
    else if(mfcc_params.nDataOrig == audioinput.ENUM.PLUGIN.MFCC_DATAORIGIN_JSONDATA && source.constructor !== Array) 
    {
        errorCB("ERROR in mfccCalculation: you set data origin as data, but did not provide a data array");
        return false;
    }
    else if ((mfcc_params.nDataOrig == audioinput.ENUM.PLUGIN.MFCC_DATAORIGIN_FILE || mfcc_params.nDataOrig == audioinput.ENUM.PLUGIN.MFCC_DATAORIGIN_FOLDER) && source.constructor !== String)        
    {
        errorCB("ERROR in mfccCalculation: you set data origin as file/folder, but did not provide a string parameter");
        return false;
    }
    
    exec(audioinput._pluginEvent, audioinput._pluginError, audioinput.pluginName, 'getMFCC', [mfcc_json_params, source, filepath_noext]);            
};

//==================================================================================================================
// PLUGIN CALLBACKS (from JAVA => JS)
//==================================================================================================================
/**
 * Callback from plugin
 *
 * @param {Object} audioInputData     keys: data (PCM)
 */
audioinput._pluginEvent = function (data) {
    try {
        switch(data.type)
        {
            case audioinput.ENUM.PLUGIN.CAPTURE_RESULT:
                
                switch(data.data_type)
                {
                    case audioinput.ENUM.PLUGIN.CAPTURE_DATADEST_JS_RAW:
                        var audioData = JSON.parse(data.data);
                        cordova.fireWindowEvent("audioinput", {data: audioData});
                        break;
                    
                    case audioinput.ENUM.PLUGIN.CAPTURE_DATADEST_JS_RAWDB:
                        var audioData = JSON.parse(data.data);
                        var decibels  = JSON.parse(data.decibels);
                        cordova.fireWindowEvent("audioinput", {data: audioData, decibels: decibels});
                        break;
                        
                    case audioinput.ENUM.PLUGIN.CAPTURE_DATADEST_JS_DB:
                        cordova.fireWindowEvent("audiometer", {decibels: Math.round(JSON.parse(data.decibels))});
                        break;
                }
                break;
                
            case audioinput.ENUM.PLUGIN.CAPTURE_STATUS_STARTED:
                console.log("audioInputMfcc._startaudioInputEvent");
                cordova.fireWindowEvent("capturestarted", {});
                audioinput._capturing = true;                   
                break;
                
            case audioinput.ENUM.PLUGIN.CAPTURE_STATUS_STOPPED:
                console.log("audioInputMfcc._stopaudioInputEvent: captured " + parseInt(data.bytesread) + "bytes, " + parseInt(data.datacaptured)*12 + " time windows, dataprocessed: " + parseInt(data.dataprocessed)*12);
                cordova.fireWindowEvent("capturestopped", {data: data});
                audioinput._capturing = false;                   
                break;
                
            case audioinput.ENUM.PLUGIN.MFCC_RESULT:
                cordova.fireWindowEvent("mfccdata", {data: data});
                break;
                
            case audioinput.ENUM.PLUGIN.MFCC_STATUS_PROGRESS_DATA:
                cordova.fireWindowEvent("mfccprogressdata", {data: data});
                break;
                
            case audioinput.ENUM.PLUGIN.MFCC_STATUS_PROGRESS_FILE:
                cordova.fireWindowEvent("mfccprogressfile", {data: data});
                break;
                
            // removed for a BUG in the code....folder processing completion is presently resolved in the web layer.
//            case audioinput.ENUM.PLUGIN.MFCC_PROGRESS_FOLDER:
//                cordova.fireWindowEvent("mfccprogressfolder", {data: data});
//                break;

                
        }
    }
    catch (ex) {
        audioinput._pluginError("audioinput._audioInputEvent ex: " + ex);
    }
};

/**
 * Error callback for AudioInputCapture start
 * @private
 */
// TODO : receive an error code from plugin
audioinput._pluginError = function (e) 
{
    cordova.fireWindowEvent("pluginError", {message: e});
    audioinput._capturing = false;                   
};

//==================================================================================================================
// INTERNAL
//==================================================================================================================

/**
 *
 * @returns {*}
 */
audioinput.getCfg = function () {
    return audioinput.capture.params;
};

/**
 *
 * @returns {boolean|Array}
 */
audioinput.isCapturing = function () {
    return audioinput._capturing;
};

//=========================================================================================
module.exports = audioinput;
