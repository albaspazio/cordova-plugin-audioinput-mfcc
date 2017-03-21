package com.allspeak.audioprocessing.mfcc;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;


// only the classic parameters used by MFCCCalcJAudio
public class MFCCParams
{
    
    // map objects in the javascript interface
    public static final int DATATYPE_MFCC       = 1;
    public static final int DATATYPE_MFFILTERS  = 2;
    
    public static final int DATADEST_NONE       = 0;    // data(float[][])=> PLUGIN
    public static final int DATADEST_JSPROGRESS = 1;    // data=> PLUGIN + progress(filename)=> WEB
    public static final int DATADEST_JSDATA     = 2;    // data=> PLUGIN + progress(filename) + data(JSONArray)=> WEB
    public static final int DATADEST_JSDATAWEB  = 3;    // data(JSONArray)=> WEB
    public static final int DATADEST_FILE       = 4;    // progress(filename)=> PLUGIN + data(String)=> FILE
    public static final int DATADEST_FILEWEB    = 5;    // progress(filename)=> PLUGIN + data(String)=> FILE + data(JSONArray)=> WEB
    public static final int DATADEST_ALL        = 6;    // data=> PLUGIN + data(String)=> FILE + data(JSONArray)=> WEB
    
    public static final int DATAORIGIN_JSONDATA = 1;
    public static final int DATAORIGIN_FILE     = 2;
    public static final int DATAORIGIN_FOLDER   = 3;
    public static final int DATAORIGIN_RAWDATA  = 4;  
    
    
    public int nNumberOfMFCCParameters      = 12; //without considering 0-th
    public double dSamplingFrequency        = 8000.0;
    public int nNumberofFilters             = 24;
    public int nFftLength                   = 256;    
    public boolean bIsLifteringEnabled      = true;
    public int nLifteringCoefficient        = 22;
    public boolean bCalculate0ThCoeff       = true;   
    public int nWindowDistance              = 80;   
    public int nWindowLength                = 200;   
    public int nDataType                    = DATATYPE_MFFILTERS; 
    public int nDataDest                    = DATADEST_NONE;    
    public int nDataOrig                    = DATAORIGIN_RAWDATA;    
    public String sOutputPath               = "";    
    public int nDeltaWindow                 = 2;    // values used in getDerivatives

    //derived
    public int nData2Reprocess              = 120;  // last part of the processed vector, that must be the first part of the new one.
                                                    // 15ms (25ms-10ms) => 120 samples
    
    public MFCCParams(){}    
    
    public MFCCParams(JSONObject init)
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
                    case "nNumberOfMFCCParameters":
                        nNumberOfMFCCParameters = init.getInt(field);
                        break;
                    case "dSamplingFrequency":
                        dSamplingFrequency      = init.getDouble(field);
                        break;
                    case "nNumberofFilters":
                        nNumberofFilters        = init.getInt(field);
                        break;
                    case "nFftLength":
                        nFftLength              = init.getInt(field);
                        break;
                    case "bIsLifteringEnabled":
                        bIsLifteringEnabled     = init.getBoolean(field);
                        break;
                    case "nLifteringCoefficient":
                        nLifteringCoefficient   = init.getInt(field);
                        break;
                    case "bCalculate0ThCoeff":
                        bCalculate0ThCoeff      = init.getBoolean(field);
                        break;
                    case "nWindowDistance":
                        nWindowDistance         = init.getInt(field);
                        break;
                    case "nWindowLength":
                        nWindowLength           = init.getInt(field);
                        break;               
                    case "nDataType":
                        nDataType               = init.getInt(field);
                        break;
                    case "nDataDest":
                        nDataDest               = init.getInt(field);
                        break;
                    case "nDataOrig":
                        nDataOrig               = init.getInt(field);
                        break;
                    case "sOutputPath":
                        sOutputPath             = init.getString(field);
                        break;                        
                    case "nDeltaWindow":
                        nDeltaWindow            = init.getInt(field);
                        break;                        
                }
            }//end for
            nData2Reprocess = nWindowLength - nWindowDistance;
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }
    
//    private static class DEFAULT
//    {
//        public static int NUMBER_OF_MFCC_PARAMS = 12; //without considering 0-th
//        public static double SAMPLING_FREQUENCY = 8000.0;
//        public static int NUMBER_OF_FILTERS     = 24;
//        public static int WINDOW_LENGTH         = 256;    
//        public static boolean LIFTERING_ENABLED = true;
//        public static int LIFTERING_COEFF       = 22;
//        public static boolean CALCULATE_0PARAM  = false;   
//        public static int WINDOW_DISTANCE       = 64;          
//    }    
}
