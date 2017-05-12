/*
contains static functions to standardize between-threads messaging
*/

package com.allspeak.utility;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONObject;
import org.json.JSONException;

public class Messaging 
{
    private static final String TAG = "Messaging";
    
    public static Bundle sendMessageToHandler(Handler handl, int action_code, String field, String info)
    {
        if(handl == null) return null;
        
        Bundle b                = new Bundle();
        b.putString(field, info);

        Message message         = handl.obtainMessage();
        message.what            = action_code;
        message.setData(b);
        handl.sendMessage(message);  
        return b;
    }
    
    public static Bundle sendDataToHandler(Handler handl, int action_code, String field, int num)
    {
        if(handl == null) return null;
        
        Bundle b                = new Bundle();
        b.putInt(field, num);

        Message message         = handl.obtainMessage();
        message.what            = action_code;
        message.setData(b);
        handl.sendMessage(message);             
        return b;
    }   
    
    public static Bundle sendDataToHandler(Handler handl, int action_code, String field, float[] data)
    {
        if(handl == null) return null;
        
        Bundle b        = new Bundle();
        b.putFloatArray(field, data);        
        
        Message message = handl.obtainMessage();
        message.what    = action_code;
        message.setData(b);
        handl.sendMessage(message);  
        return b;
    }  
    
    public static Bundle sendDataToHandler(Handler handl, int action_code, float[][] data, float[][][] derivatives, int nDim1, int nDim2, String source)
    {
        if(handl == null) return null;
        
        Bundle b        = new Bundle();
        
        float[] mfcc        = flatten2DimArray(data);
        float[] mfcc_1st    = flatten2DimArray(derivatives[0]);
        float[] mfcc_2nd    = flatten2DimArray(derivatives[1]);
        
        b.putFloatArray("data",     mfcc);
        b.putFloatArray("data_1st", mfcc_1st);
        b.putFloatArray("data_2nd", mfcc_2nd);
        b.putInt("nframes", nDim1);
        b.putInt("nparams", nDim2);
        b.putString("source", source);
        
        Message message     = handl.obtainMessage();
        message.what        = action_code;
        message.setData(b);
        handl.sendMessage(message);   
        return b;
    }  
    
    public static Bundle sendDataToHandler(Handler handl, int action_code, float[][] data, int nDim1, int nDim2, String source)
    {
        if(handl == null) return null;
        
        Bundle b            = new Bundle();
        float[] mfcc        = flatten2DimArray(data);
        
        b.putFloatArray("data",     mfcc);
        b.putInt("nframes", nDim1);
        b.putInt("nparams", nDim2);
        b.putString("source", source);
        
        Message message     = handl.obtainMessage();
        message.what        = action_code;
        message.setData(b);
        handl.sendMessage(message);   
        return b;
    }  
    
    public static float[] flatten2DimArray(float[][] input)
    {
        float[] output = new float[input.length * input[0].length];

        for(int i = 0; i < input.length; i++)
            for(int j = 0; j < input[i].length; j++)
                output[i*j] = input[i][j];
        return output;
    }    
    
    // used to convert a 1dim array to a 2dim array
    public static float[][] deFlattenArray(float[] input, int dim1, int dim2)
    {
        float[][] output = new float[dim1][dim2];        

        for(int i = 0; i < input.length; i++){
            output[i/dim2][i % dim2] = input[i];
        }
        return output;        
    }    
        
    //======================================================================================================================
    // MESSAGES TO WEB LAYER
    //======================================================================================================================
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public static void sendUpdate2Web(CallbackContext callbackContext, JSONObject info, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }
    
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public static void sendFloatData2Web(CallbackContext callbackContext, int action_code, float[] data, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            try
            {
                JSONObject info = new JSONObject(); 
                info.put("data", data);
                info.put("type", action_code);                
                info.put("data_type", action_code);                
                PluginResult result = new PluginResult(PluginResult.Status.OK, info);
                result.setKeepCallback(keepCallback);
                callbackContext.sendPluginResult(result);
            }
            catch(JSONException e)
            {
                e.printStackTrace();                  
            }
        }
    }  
    
    /**
     * Create a NO RESULT plugin result and send it back to JavaScript
     */
    public static void sendNoResult2Web(CallbackContext callbackContext)
    {
        if (callbackContext != null) 
        {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        }
    }    
    
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public static void sendErrorString2Web(CallbackContext callbackContext, String msg, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            try
            {
                JSONObject info = new JSONObject(); 
                info.put("message", msg);
                info.put("type", 0);                
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
                result.setKeepCallback(keepCallback);
                callbackContext.sendPluginResult(result);
            }
            catch(JSONException e)
            {
                e.printStackTrace();                  
            }
        }
    }      
    
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public static void sendErrorString2Web(CallbackContext callbackContext, String msg, int error_code, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            try
            {
                JSONObject info = new JSONObject(); 
                info.put("message", msg);
                info.put("type", error_code);
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
                result.setKeepCallback(keepCallback);
                callbackContext.sendPluginResult(result);
            }
            catch(JSONException e)
            {
                e.printStackTrace();                  
            }
        }
    }      
    
    /**
     * Create a new plugin result and send it back to JavaScript
     */
    public static void sendError2Web(CallbackContext callbackContext, JSONObject info, boolean keepCallback) 
    {
        if (callbackContext != null) 
        {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, info);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
        }
    }     
    //======================================================================================================================
}