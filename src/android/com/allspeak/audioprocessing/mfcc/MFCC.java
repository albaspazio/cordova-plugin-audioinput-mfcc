/**
 */
package com.allspeak.audioprocessing.mfcc;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.util.concurrent.ExecutorService;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileWriter;
import java.lang.System;

import android.os.Environment;
import android.util.Log;
import java.io.FilenameFilter;

import com.allspeak.audioprocessing.WavFile;
import com.allspeak.utility.StringUtility;
import com.allspeak.utility.TrackPerformance;

public class MFCC  
{
    private static final String TAG = "MFCC";
    
    private MFCCParams mfccParams               = null;                                   // MFCC parameters
    private MFCCCalcJAudio mfcc                 = null; 
    
    
    private String sInputPathNoExt          = "";
//    private TrackPerformance tp;//    private int[][] aiElapsedTimes;
    
    private int nFrames                     = 0;        // number of frames used to segment the input audio
    private int nParams                     = 0;        // either nNumberofFilters or nNumberOfMFCCParameters according to requeste measure
    
    private final String sOutputMFCCPrecision     = "%.4f";       // label of the element the audio belongs to
    private final String sOutputFiltersPrecision  = "%.4f";       // label of the element the audio belongs to
  
    private float[][] faMFCC;          // contains (nframes, numparams) calculated MFCC
    private float[][] faFilterBanks;   // contains (nframes, numparams) calculated FilterBanks
    
    private float[] faData2Process;   // contains (nframes, numparams) calculated FilterBanks
    private String sOutputFile;   // contains (nframes, numparams) calculated FilterBanks
    
    private Handler handler;
    private Message message;
    private Bundle messageBundle = new Bundle();
    
    //================================================================================================================
    public MFCC(MFCCParams params, Handler handl)
    {
        // JS interface call params:     mfcc_json_params, source, datatype, origintype, write, [outputpath_noext];  params have been validated in the js interface
        mfccParams  = params; 
        handler     = handl;
        nParams     = (mfccParams.nDataType == MFCCParams.DATATYPE_MFCC ? mfccParams.nNumberOfMFCCParameters : mfccParams.nNumberofFilters);
        
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
    }
    // calculate MFCC from either a folder or a file
    public void getMFCC(String source, ExecutorService executor)
    {
        sInputPathNoExt = source;
        
        switch(mfccParams.nDataOrig)
        {
            case MFCCParams.DATAORIGIN_FILE:

                executor.execute(new Runnable() {
                    @Override
                    public void run(){ processFile(sInputPathNoExt); }
                });                        
                break;

            case MFCCParams.DATAORIGIN_FOLDER:

                executor.execute(new Runnable() {
                    @Override
                    public void run(){ processFolder(sInputPathNoExt); }
                });
                break;
        }        
    }
    
    // calculate MFCC from a data array (a real-time stream)
    public void getMFCC(float[] source, String outfile, ExecutorService executor)
    {
        faData2Process          = source;
        mfccParams.sOutputPath  = outfile;
        
        executor.execute(new Runnable() {
            @Override
            public void run(){ processRawData(faData2Process, sOutputFile); }
        });
    }
    
    // calculate MFCC from a data array (a rela-time stream)
    public void getMFCC(float[] source, ExecutorService executor)
    {
        faData2Process  = source;        
        executor.execute(new Runnable() {
            @Override
            public void run(){ processRawData(faData2Process, mfccParams.sOutputPath); }
        });
    }
    
    public void setOutputFile(String output_mfcc_path)
    {
        mfccParams.sOutputPath  = output_mfcc_path;
    }
    
    //=================================================================================================================
    // PRIVATE
    //=================================================================================================================
    // processFolder(String) => for files... processFile(String) => processRawData(float[])
    
    private void processRawData(float[] data, int dataType, int dataDest, String output_path)
    {
        mfccParams.nDataType    = dataType;  
        mfccParams.nDataDest    = dataDest;    
        mfccParams.sOutputPath  = output_path;
        processRawData(data);
    }
    
    private void processRawData(float[] data, String output_path)
    {
        mfccParams.sOutputPath  = output_path;
        processRawData(data);
    }
            
    private synchronized void processRawData(float[] data)
    {
        try
        {
            if(mfccParams.nDataType == MFCCParams.DATATYPE_MFCC)
            {
                faMFCC          = mfcc.getMFCC(data);
                if(faMFCC == null || faMFCC.length == 0)
                    Log.e(TAG, "processFile" + ": Error:  faMFCC is empty");
                else
                {
                    nFrames     = faMFCC.length;
                    outputData(faMFCC);                
                }
            }
            else
            {
                faFilterBanks   = mfcc.getMFFilters(data);
                if(faFilterBanks == null || faFilterBanks.length == 0)
                    Log.e(TAG, "processFile" + ": Error:  faFilterBanks is empty");
                else
                {
                    nFrames         = faFilterBanks.length;
                    outputData(faFilterBanks);
                }
            } 
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFile" + ": Error: " + e.toString());
            sendMessageToHandler("error", e.toString());
        }        
    }            

    private void processFile(String input_file_noext) 
    {
        try
        {
//            tp                  = new TrackPerformance(5); // I want to monitor : wav read (if applicable), params calculation, data export(if applicable), data write(if applicable), data json packaging(if applicable)
            mfccParams.sOutputPath  = input_file_noext;
            String sAudiofile       = input_file_noext + ".wav";
            float[] data            = readWav(sAudiofile);  
//            tp.addTimepoint(1);
            processRawData(data);
//            tp.addTimepoint(2);                
         }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFile" + ": Error: " + e.toString());
        }        
    }
    
    private void processFolder(String input_folderpath) 
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
//            int elapsed = tp_folder.addTimepoint(1);  tp_folder.endTracking(1);
//            JSONArray array = new JSONArray();  array.put(0, "Finished parsing the " + input_folderpath + " folder");  array.put(1, Integer.toString(elapsed));            
//            return new PluginResult(PluginResult.Status.OK, array);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "processFolder" + ": Error: " + e.toString());
        }    
    }
    //======================================================================================
    // E X P O R T
    //======================================================================================
    private void outputData(float[][] data) 
    {
        try
        {
            JSONArray res_jsonarray         = new JSONArray();
            res_jsonarray.put(0, "processed file:" + mfccParams.sOutputPath);
            String params = "";
            if(mfccParams.nDataDest > 0) 
            {
                if(mfccParams.nDataType == MFCCParams.DATATYPE_MFCC)   params = exportMFCC2String(); 
                else                                        params = exportMFFilters2String();        
//                tp.addTimepoint(3);                
            }
//            else{ int[] elapsed = tp.endTracking();                res_jsonarray.put(1, new JSONArray(elapsed)); }

            if(mfccParams.nDataDest == MFCCParams.DATADEST_FILE || mfccParams.nDataDest == MFCCParams.DATADEST_BOTH)
            {
                writeTextParams(params, mfccParams.sOutputPath);
//                tp.addTimepoint(4);                
            }
            
            if(mfccParams.nDataDest == MFCCParams.DATADEST_JS || mfccParams.nDataDest == MFCCParams.DATADEST_BOTH)
            {
                JSONArray array_params;
                if(mfccParams.nDataType == MFCCParams.DATATYPE_MFCC)
                    array_params    = new JSONArray(faMFCC);
                else
                    array_params    = new JSONArray(faFilterBanks);   

//                tp.addTimepoint(5);                
                res_jsonarray.put(2, array_params);
                sendDataToHandler("data", data);
            }
            else    sendMessageToHandler("progress", mfccParams.sOutputPath);
//            int[] elapsed = tp.endTracking(); res_jsonarray.put(1, new JSONArray(elapsed));            
        }
        catch(JSONException e)
        {
            e.printStackTrace();
            Log.e(TAG, "outputData" + ": Error: " + e.toString());
            sendMessageToHandler("error", e.toString());
        }
    }    
    
    //transform to string to either write to file or send back to Web Layer
    private String exportMFCC2String()
    {
         String params = ""; 

        for (int f = 0; f < nFrames; f++)
        {
            for (int p = 0; p < mfccParams.nNumberOfMFCCParameters; p++)
                params = params + String.format(sOutputMFCCPrecision, faMFCC[f][p]) + " ";
            params = params + System.getProperty("line.separator");
        }    
        return params;
    }
    
    private String exportMFFilters2String()
    {
        String params = ""; 

        for (int f = 0; f < nFrames; f++)
        {
            for (int p = 0; p < mfccParams.nNumberofFilters; p++)
                params = params + String.format(sOutputFiltersPrecision, faFilterBanks[f][p]) + " ";
            params = params + System.getProperty("line.separator");
        }
        return params;
    }
      
    private boolean writeTextParams(String params, String output_file_noext)
    {
        // write parameters to text files...mettere try e catch
        boolean res = false;
        if(mfccParams.sOutputPath != null)
        {
            try
            {
                res = writeFile(output_file_noext + "_mfcc.dat", params);
                if(res) Log.d(TAG, "writeTextParams: written file " + output_file_noext + "_mfcc.dat" );            
    //            res = writeFile(output_file_noext + "_label.dat", sAudioTag);
            }
            catch(Exception e)
            {
                sendMessageToHandler("error", e.toString());
            }
        }  
        return res;
    };

    private boolean writeTextParamsLabel(String params, String output_file_noext, String label)
    {
        // write parameters to text files...mettere try e catch
        boolean res = writeTextParams(params, output_file_noext);
        if(res)
        {
            try
            {            
                res = writeFile(output_file_noext + "_label.dat", label);
                if(res) Log.d(TAG, "writeTextParamsLabel: written file " + output_file_noext + "_label.dat" );            
            }
            catch(Exception e)
            {
                sendMessageToHandler("error", e.toString());
            }            
        }
        return res;
    };
    //======================================================================================    
    // NOTIFICATIONS TO PLUGIN MAIN
    //======================================================================================    
    private void sendMessageToHandler(String field, String info)
    {
        message = handler.obtainMessage();
        messageBundle.putString(field, info);
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }    
    
    private void sendDataToHandler(String field, float[][] mfcc_data)
    {
        message = handler.obtainMessage();
        float[] mfcc = flatten2DimArray(mfcc_data);
        messageBundle.putFloatArray(field, mfcc);
        messageBundle.putInt("nframes", nFrames);
        messageBundle.putInt("nparams", nParams);
        message.setData(messageBundle);
        handler.sendMessage(message);        
    }      
    
    //======================================================================================    
    // ACCESSORY
    //======================================================================================    
    private static boolean writeFile(String filename, String data) throws Exception
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
            throw e;
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
    //======================================================================================    
}

