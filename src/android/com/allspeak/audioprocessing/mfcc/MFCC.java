/**
 */
package com.allspeak.audioprocessing.mfcc;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.lang.System;

import android.os.Environment;
import android.util.Log;
import java.io.FilenameFilter;

import android.os.ResultReceiver;

import com.allspeak.ENUMS;
import com.allspeak.audioprocessing.WavFile;
import com.allspeak.utility.StringUtility;
import com.allspeak.utility.TrackPerformance;



/*
it sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error

*/

public class MFCC  
{
    private static final String TAG = "MFCC";
    
    private static final int MODE_THREAD                = 0;
    private static final int MODE_INTENTSERVICE         = 1;
    private int nMode                                   = MODE_THREAD;
    
    private MFCCParams mfccParams               = null;                                   // MFCC parameters
    private MFCCCalcJAudio mfcc                 = null; 
    
    // properties to send results back to Plugin Main
    private ResultReceiver mReceiver                = null;     // IntentService
    private Handler mHandler                        = null;     // Thread
    
    // properties to send results back to Web Layer
    private CallbackContext callbackContext = null;
//    private CordovaInterface cordovaInterface;    
    
    private Message message;
    private Bundle messageBundle;  

//    private TrackPerformance tp;//    private int[][] aiElapsedTimes;

    private String sSource; // used as runnable param

    private int nFrames                     = 0;        // number of frames used to segment the input audio
    private int nScores                     = 0;        // either nNumberofFilters or nNumberOfMFCCParameters according to requeste measure
    
    private String sOutputPrecision           = "%.4f";       // label of the element the audio belongs to
    private final String sOutputMFCCPrecision       = "%.4f";       // label of the element the audio belongs to
    private final String sOutputFiltersPrecision    = "%.4f";       // label of the element the audio belongs to
  
    private float[][] faMFCC;           // contains (nframes, numparams) calculated MFCC
    private float[][] faFilterBanks;    // contains (nframes, numparams) calculated FilterBanks
    
    private float[][][] faDerivatives;  // contains (2, nframes, numparams)  1st & 2nd derivatives of the calculated params
    private int nindices1;
    private int[] indices1 ;
    private int nindices2;
    private int[] indices2 ;
    private int nindicesout;
    private int[] indicesout;
    private int nDerivDenom;
    
    //================================================================================================================
    // CONSTRUCTORS
    //================================================================================================================
    // to be used with IntentService implementation
    public MFCC(MFCCParams params, ResultReceiver receiver)
    {
        mReceiver   = receiver;
        nMode       = MODE_INTENTSERVICE;
        setParams(params);
    }
    
    // to be used with Thread implementation
    public MFCC(MFCCParams params, Handler handler)
    {
        mHandler    = handler;
        nMode       = MODE_THREAD;
        setParams(params);       
    }
    
    // to be used with Thread implementation,  may also send results directly to Web Layer
    public MFCC(MFCCParams params, Handler handl, CallbackContext wlcallback)
    {
        this(params, handl);
        callbackContext     = wlcallback;
    }   
    
    public void setParams(MFCCParams params)
    {
        // JS interface call params:     mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
        mfccParams      = params; 
        nScores         = (mfccParams.nDataType == MFCCParams.DATATYPE_MFCC ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        sOutputPrecision= (mfccParams.nDataType == MFCCParams.DATATYPE_MFCC ? sOutputMFCCPrecision : sOutputFiltersPrecision);
        
        if ((boolean)mfccParams.bCalculate0ThCoeff)    
            mfccParams.nNumberOfMFCCParameters = mfccParams.nNumberOfMFCCParameters + 1;//take in account the zero-th MFCC        

        mfcc        = new MFCCCalcJAudio(   mfccParams.nNumberOfMFCCParameters,
                                            mfccParams.dSamplingFrequency,
                                            mfccParams.nNumberofFilters,
                                            mfccParams.nFftLength,
                                            mfccParams.bIsLifteringEnabled ,
                                            mfccParams.nLifteringCoefficient,
                                            mfccParams.bCalculate0ThCoeff,
                                            mfccParams.nWindowDistance,
                                            mfccParams.nWindowLength); 
        
        initDerivativeIndices(mfccParams.nDeltaWindow, nScores);  
    }   
    
    public void setWlCb(CallbackContext wlcallback)
    {
        callbackContext     = wlcallback;
    }    
    //================================================================================================================
    //================================================================================================================
    //================================================================================================================
    //================================================================================================================
    //================================================================================================================
    public void setOutputFile(String output_mfcc_path)
    {
        mfccParams.sOutputPath  = output_mfcc_path;
    }
    
    //=================================================================================================================
    // PRIVATE
    //=================================================================================================================
    public void processRawData(float[] data, int dataType, int dataDest, String output_path)
    {
        mfccParams.nDataType    = dataType;  
        mfccParams.nDataDest    = dataDest;    
        mfccParams.sOutputPath  = output_path;
        processRawData(data);
    }

    // get score, get derivatives => exportData
    public synchronized void processRawData(float[] data)
    {
        try
        {
            if(mfccParams.nDataType == MFCCParams.DATATYPE_MFCC)
            {
                faMFCC          = mfcc.getMFCC(data);
                faDerivatives   = getDerivatives(faMFCC);
                if(faMFCC == null || faMFCC.length == 0)
                    Log.e(TAG, "processFile" + ": Error:  faMFCC is empty");
                else
                {
                    nFrames     = faMFCC.length;
                    exportData(faMFCC, faDerivatives);                
                }
            }
            else
            {
                faFilterBanks   = mfcc.getMFFilters(data);
                faDerivatives   = getDerivatives(faFilterBanks);                
                if(faFilterBanks == null || faFilterBanks.length == 0)
                    Log.e(TAG, "processFile" + ": Error:  faFilterBanks is empty");
                else
                {
                    nFrames     = faFilterBanks.length;
                    exportData(faFilterBanks, faDerivatives);
                }
            } 
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFile" + ": Error: " + e.toString());
            sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
        }        
    }            

    // read wav(String) => processRawData(float[])
    public void processFile(String input_file_noext) 
    {
        try
        {
//            tp                  = new TrackPerformance(5); // I want to monitor : wav read (if applicable), params calculation, data export(if applicable), data write(if applicable), data json packaging(if applicable)
            mfccParams.sOutputPath  = input_file_noext;
            String sAudiofile       = input_file_noext + ".wav";
            float[] data            = readWav(sAudiofile);  
            deleteParamsFiles(mfccParams.sOutputPath);            
//            tp.addTimepoint(1);
            processRawData(data);
//            tp.addTimepoint(2);   
            sendMessageToMain(ENUMS.MFCC_PROGRESS_FILE, "progress_file", mfccParams.sOutputPath);
         }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFile" + ": Error: " + e.toString());
            sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
        }        
    }
    
    // processFolder(String) => for files in... processFile(String) => processRawData(float[])
    public void processFolder(String input_folderpath) 
    {
//        TrackPerformance tp_folder      = new TrackPerformance(1); // I want to monitor just to total time
        File directory                  = new File(Environment.getExternalStorageDirectory().toString() + "/" + input_folderpath);

        try
        {
            String tempfile             = "";
            File[] files                = directory.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".wav");
                }
            });
//            aiElapsedTimes               = new int[files.length][5];
            for (int i = 0; i < files.length; i++)
            {
                tempfile            = input_folderpath + File.separatorChar + files[i].getName();
                processFile(StringUtility.removeExtension(tempfile));
            }   
//            sendMessageToMain(ENUMS.MFCC_PROGRESS_FOLDER, "progress_folder", mfccParams.sOutputPath);          
            // BUG....it doesn't work...since the last-1 file, in the target I get a Bundle with either process_file and process_folder messages
            // folder processing completion is presently resolved in the web layer.
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFolder" + ": Error: " + e.toString());
            sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
        }    
    }
    
    //======================================================================================
    // E X P O R T
    //======================================================================================
    private void exportData(float[][] data, float[][][] derivatives) 
    {
        try
        {
            JSONArray res_jsonarray         = new JSONArray();
            res_jsonarray.put(0, "processed file:" + mfccParams.sOutputPath);
            String scores                   = "";
            String[] str_derivatives        = new String[2];
            
            //------------------------------------------------------------------
            // write data to FILE
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case MFCCParams.DATADEST_FILE:
                case MFCCParams.DATADEST_ALL:
                case MFCCParams.DATADEST_FILEWEB:
                    
                    if(mfccParams.nDataType == MFCCParams.DATATYPE_MFCC)    scores = exportArray2String(faMFCC, nScores, sOutputPrecision);
                    else                                                    scores = exportArray2String(faFilterBanks, nScores, sOutputPrecision);

                    str_derivatives[0]  = exportArray2String(derivatives[0], nScores, sOutputPrecision);
                    str_derivatives[1]  = exportArray2String(derivatives[1], nScores, sOutputPrecision);

                    writeTextParams(scores, mfccParams.sOutputPath + "_scores.dat");
                    writeTextParams(str_derivatives[0], mfccParams.sOutputPath + "_scores1st.dat");
                    writeTextParams(str_derivatives[1], mfccParams.sOutputPath + "_scores2nd.dat");  
                    //                tp.addTimepoint(4);                
                    break;
            }
            //------------------------------------------------------------------
            // send data to WEB LAYER
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case MFCCParams.DATADEST_FILEWEB:
                case MFCCParams.DATADEST_JSDATAWEB:
                    //costruire json e chiamare
                    JSONObject info         = new JSONObject();
                    info.put("type",        ENUMS.MFCC_DATA);
                    info.put("data",        new JSONArray(data));
                    info.put("first_der",   new JSONArray(derivatives[0]));
                    info.put("second_der",  new JSONArray(derivatives[1]));
                    info.put("progress",    mfccParams.sOutputPath);
                    sendUpdate2Web(info, true);
                    break;                 
            }
            //------------------------------------------------------------------
            // send progress to PLUGIN
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case MFCCParams.DATADEST_FILE:
                case MFCCParams.DATADEST_FILEWEB:
                    sendMessageToMain(ENUMS.MFCC_PROGRESS_DATA, "progress", Integer.toString(nFrames));              
                    break;
            }             
            //------------------------------------------------------------------
            // send data to PLUGIN    
            //------------------------------------------------------------------
            switch(mfccParams.nDataDest)
            {
                case MFCCParams.DATADEST_NONE:
                case MFCCParams.DATADEST_JSPROGRESS:            
                case MFCCParams.DATADEST_JSDATA:            
                case MFCCParams.DATADEST_ALL:            
                    sendDataToMain(ENUMS.MFCC_DATA, data, derivatives);  
            }
            //------------------------------------------------------------------
//            else{ int[] elapsed = tp.endTracking();                res_jsonarray.put(1, new JSONArray(elapsed)); }
        }
        catch(JSONException e)
        {
            e.printStackTrace();
            Log.e(TAG, "exportData" + ": Error: " + e.toString());
            sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
        }
    }    
    
    //transform to string to either write to file or send back to Web Layer
    private String exportArray2String(float[][] scores, int nscores, String precision)
    {
         String params = ""; 

        for (int f = 0; f < nFrames; f++)
        {
            for (int p = 0; p < nscores; p++)
                params = params + String.format(precision, scores[f][p]) + " ";
            params = params + System.getProperty("line.separator");
        }    
        return params;
    }
      
    private boolean writeTextParams(String params, String output_file)
    {
        // write parameters to text files...mettere try e catch
        boolean res = false;
        if(output_file != null)
        {
            try
            {
                res = writeFile(output_file, params);
//                if(res) Log.d(TAG, "writeTextParams: written file " + output_file);            
    //            res = writeFile(output_file_noext + "_label.dat", sAudioTag);
            }
            catch(Exception e)
            {
                sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
            }
        }  
        return res;
    }

    private boolean writeTextParamsLabel(String params, String output_file, String label)
    {
        // write parameters to text files...mettere try e catch
        boolean res = writeTextParams(params, output_file);
        if(res)
        {
            try
            {   
                String output_file_noext = StringUtility.removeExtension(output_file);
                res = writeFile(output_file_noext + "_label.dat", label);
                if(res) Log.d(TAG, "writeTextParamsLabel: written file " + output_file_noext + "_label.dat");            
            }
            catch(Exception e)
            {
               sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
            }            
        }
        return res;
    };
    
    private boolean deleteParamsFiles(String output_file)
    {
        boolean res1=false,res2=false,res3=false;
        
        File f;
        f = new File(Environment.getExternalStorageDirectory(), output_file + "_scores.dat");
        if (f.exists()) res1 = f.delete();

        f = new File(Environment.getExternalStorageDirectory(), output_file + "_scores1st.dat");
        if (f.exists()) res2 = f.delete();

        f = new File(Environment.getExternalStorageDirectory(), output_file + "_scores2nd.dat");
        if (f.exists()) res3 = f.delete();
        
        return res1 && res2 && res3;
    }
    
    //======================================================================================    
    // ACCESSORY
    //======================================================================================    
    private boolean writeFile(String filename, String data) throws Exception
    {
        try 
        {
            File f = new File(Environment.getExternalStorageDirectory(), filename);
            if (!f.exists()) {
                f.createNewFile();
            }
            FileWriter writer = new FileWriter(f, true);
            writer.write(data);
            writer.close();            
            return true;
        }
        catch (Exception e) 
        {
            e.printStackTrace();
            sendMessageToMain(ENUMS.MFCC_ERROR, "error", e.getMessage());
            return false;
        }	
    }
    
    private float[] flatten2DimArray(float[][] input)
    {
        float[] output = new float[input.length * input[0].length];

        for(int i = 0; i < input.length; i++)
            for(int j = 0; j < input[i].length; j++)
                output[i*j] = input[i][j];
        return output;
    }

    private float[] readWav(String filepath)
    {
        try
        {
            File f          = new File(Environment.getExternalStorageDirectory(), filepath);
            // Open the wav file specified as the first argument
            WavFile wavFile = WavFile.openWavFile(f);
            int frames      = (int)wavFile.getNumFrames();
            int numChannels = wavFile.getNumChannels();

            if(numChannels > 1)
                return null;           
            // Create the buffer
            float[] buffer = new float[frames * numChannels];
            int framesRead  = wavFile.readFrames(buffer, frames);
            
            if(frames != framesRead)
                return null;

            // Close the wavFile
            wavFile.close();  
            return buffer;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
    
    private void initDerivativeIndices(int ndw, int nscores)
    {
        // nscores = 4; //DEBUG
        nindices1           = nscores + ndw*2;
        indices1            = new int[nindices1];
        for(int r=0; r<nindices1; r++)
            indices1[r]     = ndw + r;

        nindices2           = nscores;
        indices2            = new int[nindices2];
        for(int r=0; r<nindices2; r++)
            indices2[r]     = 2*ndw + r;

        
        nindicesout        = nscores;
        indicesout         = new int[nindicesout];
        for(int r=0; r<nindicesout; r++)
            indicesout[r]  = 2*ndw + r;     
        
        nDerivDenom = 0;
        for(int dw=1; dw<=ndw; dw++)
            nDerivDenom = nDerivDenom + 2*(dw*dw);        
    }
    
    // input data represent (ntimewindows X nfilters)
    private float[][][] getDerivatives(float[][] data)
    {
        //int[][] data = new int[][]{{11,12,13,14},{21,22,23,24},{31,32,33,34}}; // DEBUG
        
        int nscores             = data[0].length;   // num scores
        int ntw                 = data.length;      // num time windows
        float[][][] res         = new float[2][ntw][nscores];
        
        int borderColumnsWidth  = 2*mfccParams.nDeltaWindow;
        int finalColumns        = 2*borderColumnsWidth + nscores;
        
        // with the first and last column (score), I have to emulate the following Python code : 
        // column vector [ntw] => [ntw,1] => [ntw, 2*deltawindow]
        
        float[][] appendedVec   = new float[ntw][finalColumns];
        float[][] deltaVec      = new float[ntw][finalColumns];
        float[][] deltadeltaVec = new float[ntw][finalColumns];
        
        for(int c=0; c<finalColumns; c++)
        {
            if(c<borderColumnsWidth)
                for(int tw=0; tw<ntw; tw++)
                    appendedVec[tw][c] = data[tw][0];
            else if (c>=borderColumnsWidth && c<(borderColumnsWidth+nscores))
                for(int tw=0; tw<ntw; tw++)
                    appendedVec[tw][c] = data[tw][c-borderColumnsWidth];
            else
                for(int tw=0; tw<ntw; tw++)
                    appendedVec[tw][c] = data[tw][nscores-1];
        }
        //-------------------------------------------------------------------
        // first derivative
        float[][] deltaVecCur       = new float[ntw][nindices1];

        for(int dw=1; dw<=mfccParams.nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltaVecCur[tw][r] = appendedVec[tw][indices1[r]+dw] - appendedVec[tw][indices1[r]-dw];
            
            for(int r=0; r<nindices1; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltaVec[tw][indices1[r]] = deltaVec[tw][indices1[r]] + deltaVecCur[tw][r]*dw;
        }
        // final extraction: [ntw][2*dw + nscores + 2*dw] => [ntw][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<ntw; tw++)
                res[0][tw][sc] = deltaVec[tw][sc+2*mfccParams.nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        // second derivative
        float[][] deltadeltaVecCur = new float[ntw][nindices2];        
        
        for(int dw=1; dw<=mfccParams.nDeltaWindow; dw++)
        {
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltadeltaVecCur[tw][r] = deltaVec[tw][indices2[r]+dw] - deltaVec[tw][indices2[r]-dw];
            
            for(int r=0; r<nindices2; r++)
                for(int tw=0; tw<ntw; tw++)
                    deltadeltaVec[tw][indices2[r]] = deltadeltaVec[tw][indices2[r]] + deltadeltaVecCur[tw][r]*dw;
        }
        // final extraction: [ntw][nscores + 4*dw] => [ntw][nscores]
        for(int sc=0; sc<nscores; sc++)
            for(int tw=0; tw<ntw; tw++)
                res[1][tw][sc] = deltadeltaVec[tw][sc+2*mfccParams.nDeltaWindow]/nDerivDenom;
        
        //-------------------------------------------------------------------
        return res;
    }
    
    // returns the number of frames you can divide a vector into
    public int getFrames(int inlen)
    {
        return mfcc.getFrames(inlen);
    }
    
    //used to determine the maximum number of samples you can provide to MFCC analysis to get a clean number of frames
    // assuming I have 1024 samples, I can process 11 frames, consuming 1000 samples => I return it
    public int getOptimalVectorLength(int inlen)
    {
        int nframes = (1 + (int) Math.floor((inlen-mfccParams.nWindowLength)/mfccParams.nWindowDistance));
        return  (mfccParams.nWindowLength + mfccParams.nWindowDistance*(nframes-1));
    }
    
    public static int getOptimalVectorLength(int inlen, int wlength, int wdist)
    {
        int nframes = (1 + (int) Math.floor((inlen-wlength)/wdist));
        return  (wlength + wdist*(nframes-1));
    }
    
    //======================================================================================    
    // NOTIFICATIONS TO PLUGIN MAIN or WEBLayer
    //======================================================================================    
    private void sendMessageToMain(int action_code, String field, String info)
    {
        messageBundle = new Bundle();
        messageBundle.putString(field, info);

        switch(nMode)
        {
            case MODE_INTENTSERVICE:
                mReceiver.send(action_code, messageBundle);        
                break;
                
            case MODE_THREAD:
                message         = mHandler.obtainMessage();
                message.what    = action_code;
                message.setData(messageBundle);
                mHandler.sendMessage(message);             
                break;
        }
    }    

    private void sendDataToMain(int action_code, float[][] mfcc_data, float[][][] derivatives)
    {
        float[] mfcc        = flatten2DimArray(mfcc_data);
        float[] mfcc_1st    = flatten2DimArray(derivatives[0]);
        float[] mfcc_2nd    = flatten2DimArray(derivatives[1]);
        
        messageBundle = new Bundle();        
        messageBundle.putFloatArray("data",     mfcc);
        messageBundle.putFloatArray("data_1st", mfcc_1st);
        messageBundle.putFloatArray("data_2nd", mfcc_2nd);
        messageBundle.putInt("nframes", nFrames);
        messageBundle.putInt("nparams", nScores);
        messageBundle.putString("source", mfccParams.sOutputPath);
        
        switch(nMode)
        {
            case MODE_INTENTSERVICE:
                mReceiver.send(action_code, messageBundle);        
                break;
                
            case MODE_THREAD:
                message         = mHandler.obtainMessage();
                message.what    = action_code;
                message.setData(messageBundle);
                mHandler.sendMessage(message);             
                break;
        }
    }       
    
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public void sendUpdate2Web(JSONObject info, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }    
    //======================================================================================    
}

