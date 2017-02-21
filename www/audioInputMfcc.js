var argscheck = require('cordova/argscheck'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec'),
    channel = require('cordova/channel');

var audioinput          = {};

audioinput.pluginName   = "AudioInputCaptureMFCC";
audioinput.ENUM         = {};
audioinput.ENUM.capture = {};
audioinput.ENUM.mfcc    = {};

// Supported audio formats
audioinput.ENUM.capture.FORMAT = {
    PCM_16BIT           : 'PCM_16BIT',
    PCM_8BIT            : 'PCM_8BIT'
};

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

// Audio Source types
audioinput.ENUM.capture.AUDIOSOURCE_TYPE = {
    DEFAULT             : 0,
    CAMCORDER           : 5,
    MIC                 : 1,
    UNPROCESSED         : 9,
    VOICE_COMMUNICATION : 7,
    VOICE_RECOGNITION   : 6
};

// Default values
audioinput.ENUM.capture.DEFAULT = {
    SAMPLERATE              : audioinput.ENUM.capture.SAMPLERATE.TELEPHONE_8000Hz,
    BUFFER_SIZE             : 1024,
    CHANNELS                : audioinput.ENUM.capture.CHANNELS.MONO,
    FORMAT                  : audioinput.ENUM.capture.FORMAT.PCM_16BIT,
    NORMALIZE               : true,
    NORMALIZATION_FACTOR    : 32767.0,
    STREAM_TO_WEBAUDIO      : false,
    CONCATENATE_MAX_CHUNKS  : 10,
    AUDIOSOURCE_TYPE        : audioinput.ENUM.capture.AUDIOSOURCE_TYPE.DEFAULT
};

audioinput.ENUM.mfcc.DATATYPE={
    NONE        : 0,
    FCC         : 1,
    MFFILTERS   : 2
};

audioinput.ENUM.mfcc.DATAORIGIN={
    JSONDATA    : 1,
    FILE        : 2,
    FOLDER      : 3,
    RAWDATA     : 4
};

audioinput.ENUM.mfcc.DATADEST={
    NONE    : 0,
    JS      : 1,
    FILE    : 2,
    BOTH    : 3
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
    nDataType               : audioinput.ENUM.mfcc.DATATYPE.MFFILTERS,
    nDataDest               : audioinput.ENUM.mfcc.DATADEST.NONE,
    nDataOrig               : audioinput.ENUM.mfcc.DATAORIGIN.RAWDATA,
    sOutputPath             : ""
};

audioinput.mfcc = {};
audioinput.mfcc.params = {};

audioinput.capture = {};
audioinput.capture.params = {};

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
    
    return JSON.stringify(audioinput.mfcc.params); 
};

audioinput.checkCaptureParams = function(capture_params)
{
    audioinput.capture.params.nSampleRate                 = capture_params.nSampleRate             || audioinput.ENUM.capture.DEFAULT.SAMPLERATE;
    audioinput.capture.params.nBufferSize                 = capture_params.nBufferSize             || audioinput.ENUM.capture.DEFAULT.BUFFER_SIZE;
    audioinput.capture.params.nChannels                   = capture_params.nChannels               || audioinput.ENUM.capture.DEFAULT.CHANNELS;
    audioinput.capture.params.sFormat                     = capture_params.sFormat                 || audioinput.ENUM.capture.DEFAULT.FORMAT;
    audioinput.capture.params.nAudioSourceType            = capture_params.nAudioSourceType        || 0;
    audioinput.capture.params.nNormalize                  = typeof capture_params.nNormalize == 'boolean' ? audioinput.ENUM.capture.nNormalize : audioinput.ENUM.capture.DEFAULT.NORMALIZE;
    audioinput.capture.params.fNormalizationFactor        = capture_params.fNormalizationFactor    || audioinput.ENUM.capture.DEFAULT.NORMALIZATION_FACTOR;
    audioinput.capture.params.nConcatenateMaxChunks       = capture_params.nConcatenateMaxChunks   || audioinput.ENUM.capture.DEFAULT.CONCATENATE_MAX_CHUNKS;
    audioinput.capture.params.AudioContext                = null;
    audioinput.capture.params.bStreamToWebAudio           = capture_params.bStreamToWebAudio        || audioinput.ENUM.capture.DEFAULT.STREAM_TO_WEBAUDIO;

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
audioinput.start = function (captureCfg, mfccCfg, startMFCC) {
    if (!audioinput._capturing) 
    {
        // overwrite default params with exogenous ones
        var json_capture_params     = audioinput.checkCaptureParams(captureCfg);
        var json_mfcc_params        = audioinput.checkMfccParams(mfccCfg);
        
        exec(audioinput._audioInputEvent, audioinput._audioInputErrorEvent, audioinput.pluginName, "startCapture",
            [json_capture_params,
             json_mfcc_params,
             startMFCC]);

        audioinput._capturing = true;
        
        if (audioinput.capture.params.bStreamToWebAudio) {
            if (audioinput._initWebAudio(audioinput.capture.params.AudioContext)) {
                audioinput._audioDataQueue = [];
                audioinput._getNextToPlay();
            }
            else {
                throw "The Web Audio API is not supported on this platform!";
            }
        }
    }
    else {
        throw "Already capturing!";
    }
};


/**
 * Stop capturing audio
 */
audioinput.stop = function () {
    if (audioinput._capturing) {
        exec(audioinput._stopaudioInputEvent, audioinput._audioInputErrorEvent, audioinput.pluginName, "stopCapture", []);
        audioinput._capturing = false;    
    }

    if (audioinput.capture.params.bStreamToWebAudio) {
        if (audioinput._timerGetNextAudio) {
            clearTimeout(audioinput._timerGetNextAudio);
        }
        audioinput._audioDataQueue = null;
    }
};

/**
 * Start calculating MFCC
 */
audioinput.startMFCC = function () {
    if (audioinput._capturing) 
        exec(null, audioinput._audioInputErrorEvent, audioinput.pluginName, "stopMFCC", [audioinput.mfcc.params]);
};

/**
 * Stop calculating MFCC
 */
audioinput.stopMFCC = function () {
    if (audioinput._capturing) 
        exec(null, audioinput._audioInputErrorEvent, audioinput.pluginName, "stopMFCC", []);
};


//mfcc.getData = function(successCB, errorCB, mfcc_params, source, datatype, origintype, write, outputpath_noext)
audioinput.getMFCC = function(successCB, errorCB, mfcc_params, source, filepath_noext)
{
    var mfcc_json_params    = audioinput.checkMfccParams(mfcc_params);
    
    audioinput.success      = successCB;
    audioinput.error        = errorCB;
    
    //check params consistency
    if(source == null || !source.length)
    {
        errorCB("ERROR in mfccCalculation: source is empty")
        return false;
    }
    else if(mfcc_params.nDataOrig == audioinput.ENUM.mfcc.DATAORIGIN.JSONDATA && source.constructor !== Array) 
    {
        errorCB("ERROR in mfccCalculation: you set data origin as data, but did not provide a data array");
        return false;
    }
    else if ((mfcc_params.nDataOrig == audioinput.ENUM.mfcc.DATAORIGIN.FILE || mfcc_params.nDataOrig == audioinput.ENUM.mfcc.DATAORIGIN.FOLDER) && source.constructor !== String)        
    {
        errorCB("ERROR in mfccCalculation: you set data origin as file/folder, but did not provide a string parameter");
        return false;
    }
    
    exec(audioinput.onMFCCSuccess, audioinput.onMFCCError, audioinput.pluginName, 'getMFCC', [mfcc_json_params, source, filepath_noext]);            
};
//==================================================================================================================
//==================================================================================================================
/**
 * Callback from plugin
 *
 * @param {Object} audioInputData     keys: data (PCM)
 */
audioinput._audioInputEvent = function (audioInputData) {
    try {
        if (audioInputData && audioInputData.data && audioInputData.data.length > 0) {
            var audioData = JSON.parse(audioInputData.data);
//            audioData = audioinput._normalizeAudio(audioData);

            if (audioinput.capture.params.bStreamToWebAudio && audioinput._capturing) {
                audioinput._enqueueAudioData(audioData);
            }
            else {
                cordova.fireWindowEvent("audioinput", {data: audioData});
            }
        }
        else if (audioInputData && audioInputData.error) {
            audioinput._audioInputErrorEvent(audioInputData.error);
        }
    }
    catch (ex) {
        audioinput._audioInputErrorEvent("audioinput._audioInputEvent ex: " + ex);
    }
};

audioinput._stopaudioInputEvent = function(capturedblocks)
{
    console.log("audioInputMfcc._stopaudioInputEvent: captured " + parseInt(capturedblocks)*12 + " time windows");
};

/**
 * Error callback for AudioInputCapture start
 * @private
 */

audioinput._audioInputErrorEvent = function (e) {
    cordova.fireWindowEvent("audioinputerror", {message: e});
};

/**
 * Callback for MFCC calculation
 *
 * @param {Object} mfccData     
 */
audioinput.onMFCCSuccess = function (mfccData) {
    audioinput.success(mfccData);
};

audioinput.onMFCCError = function (e) {
    audioinput.error(e);
};

//==================================================================================================================
//==================================================================================================================
//==================================================================================================================
/**
 * Connect the audio node
 *
 * @param audioNode
 */
audioinput.connect = function (audioNode) {
    if (audioinput._micGainNode) {
        audioinput.disconnect();
        audioinput._micGainNode.connect(audioNode);
    }
};

/**
 * Disconnect the audio node
 */
audioinput.disconnect = function () {
    if (audioinput._micGainNode) {
        audioinput._micGainNode.disconnect();
    }
};

/**
 * Returns the internally created Web Audio Context (if any exists)
 *
 * @returns {*}
 */
audioinput.getAudioContext = function () {
    return audioinput._audioContext;
};

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


/******************************************************************************************************************/
/*                                                PRIVATE/INTERNAL                                                */
/******************************************************************************************************************/

audioinput._capturing = false;
audioinput._audioDataQueue = null;
audioinput._timerGetNextAudio = null;
audioinput._audioContext = null;
audioinput._micGainNode = null;
audioinput._webAudioAPISupported = false;


/**
 * Normalize audio input
 *
 * @param {Object} pcmData
 * @private
 */
//audioinput._normalizeAudio = function (pcmData) {
//
//    if (audioinput.capture.params.bNormalize) {
//        for (var i = 0; i < pcmData.length; i++) {
//            pcmData[i] = parseFloat(pcmData[i] / audioinput.capture.params.fNormalizationFactor);
//        }
//
//        // If last value is NaN, remove it.
//        if (isNaN(pcmData[pcmData.length - 1])) {
//            pcmData.pop();
//        }
//    }
//
//    return pcmData;
//};


/**
 * Consumes data from the audioinput queue
 * @private
 */
audioinput._getNextToPlay = function () {
    try {
        var duration = 100;

        if (audioinput._audioDataQueue.length > 0) {
            var concatenatedData = [];
            for (var i = 0; i < audioinput.capture.params.nConcatenateMaxChunks; i++) {
                if (audioinput._audioDataQueue.length === 0) {
                    break;
                }
                concatenatedData = concatenatedData.concat(audioinput._dequeueAudioData());
            }

            duration = audioinput._playAudio(concatenatedData) * 1000;
        }

        if (audioinput._capturing) {
            audioinput._timerGetNextAudio = setTimeout(audioinput._getNextToPlay, duration);
        }
    }
    catch (ex) {
        audioinput._audioInputErrorEvent("audioinput._getNextToPlay ex: " + ex);
    }
};


/**
 * Play audio using the Web Audio API
 * @param data
 * @returns {Number}
 * @private
 */
audioinput._playAudio = function (data) {
    try {
        if (data && data.length > 0) {
            var audioBuffer = audioinput._audioContext.createBuffer(audioinput.capture.params.nChannels, (data.length / audioinput.capture.params.nChannels), audioinput.capture.params.nSampleRate),
                chdata = [],
                index = 0;

            if (audioinput.capture.params.nChannels > 1) {
                for (var i = 0; i < audioinput.capture.params.nChannels; i++) {
                    while (index < data.length) {
                        chdata.push(data[index + i]);
                        index += parseInt(audioinput.capture.params.nChannels);
                    }

                    audioBuffer.getChannelData(i).set(new Float32Array(chdata));
                }
            }
            else {
                audioBuffer.getChannelData(0).set(data);
            }

            var source = audioinput._audioContext.createBufferSource();
            source.buffer = audioBuffer;
            source.connect(audioinput._micGainNode);
            source.start(0);

            return audioBuffer.duration;
        }
    }
    catch (ex) {
        audioinput._audioInputErrorEvent("audioinput._playAudio ex: " + ex);
    }

    return 0;
};


/**
 * Creates the Web Audio Context and audio nodes for output.
 * @private
 */
audioinput._initWebAudio = function (audioCtxFromCfg) {
    try {
        if (audioCtxFromCfg) {
            audioinput._audioContext = audioCtxFromCfg;
        }
        else if (!audioinput._audioContext) {
            window.AudioContext = window.AudioContext || window.webkitAudioContext;
            audioinput._audioContext = new window.AudioContext();
            audioinput._webAudioAPISupported = true;
        }

        // Create a gain node for volume control
        if (!audioinput._micGainNode) {
            audioinput._micGainNode = audioinput._audioContext.createGain();
        }

        return true;
    }
    catch (e) {
        audioinput._webAudioAPISupported = false;
        return false;
    }
};

/**
 * Puts audio data at the end of the queue
 *
 * @returns {*}
 * @private
 */
audioinput._enqueueAudioData = function (data) {
    audioinput._audioDataQueue.push(data);
};

/**
 * Gets and removes the oldest audio data from the queue
 *
 * @returns {*}
 * @private
 */
audioinput._dequeueAudioData = function () {
    return audioinput._audioDataQueue.shift();
};

module.exports = audioinput;