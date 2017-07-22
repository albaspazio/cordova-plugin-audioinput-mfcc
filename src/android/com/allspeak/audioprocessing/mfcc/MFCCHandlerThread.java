package com.allspeak.audioprocessing.mfcc;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import android.util.Log;

import com.allspeak.ENUMS;
import com.allspeak.ERRORS;

// not necessary
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.mfcc.MFCC;
import com.allspeak.audioprocessing.mfcc.MFCCCalcJAudio;


/*
it's a layer which call the MFCC functions on a new thread
sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error
*/

public class MFCCHandlerThread extends HandlerThread implements Handler.Callback
{
    private static final String LOG_TAG = "MFCCHandlerThread";
    
    private Handler mInternalHandler    = null;   // manage internal messages
    private Handler mStatusCallback     = null;   // destination handler of status messages
    private Handler mResultCallback     = null;   // destination handler of data result
    private Handler mCommandCallback    = null;   // destination handler of output command
    private CallbackContext mWlCb       = null;   // access to web layer 
    
    private MFCC mfcc                   = null;
    private MFCCParams mfccParams       = null;
    
    // manage MFCC queue
    private float[] faMFCCQueue         = new float[2048];;
    private int nQueueLastIndex         = 0;    
    private float[] faData2Process      = null;   // contains (nframes, numparams) calculated FilterBanks
    String sSource                      = "";
    
    Bundle bundle;
    //================================================================================================================
    public MFCCHandlerThread(String name)
    {
        super(name);
    }
    public MFCCHandlerThread(String name, int priority)
    {
        super(name, priority);
    }
    //===============================================================================================
    public void setParams(MFCCParams params)
    {
        mfccParams  = params;
        mfcc.setParams(params);
    }
    public void setWlCb(CallbackContext wlcb)
    {
        mWlCb = wlcb;        
        mfcc.setWlCb(wlcb);
    }
    public void setCallbacks(Handler cb)
    {
        mStatusCallback = cb;        
        mCommandCallback = cb;        
        mResultCallback = cb;        
        mfcc.setCallbacks(cb);
    }
    public void setCallbacks(Handler scb, Handler ccb, Handler rcb)
    {
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;        
        mfcc.setCallbacks(mStatusCallback, mCommandCallback, mResultCallback);
    }
//--------------------------------------------------------------------------------------------------
    public void init(MFCCParams params, Handler cb)
    {
        mfccParams          = params;
        mStatusCallback     = cb;        
        mCommandCallback    = cb;        
        mResultCallback     = cb;  
        mfcc                = new MFCC(mfccParams, cb, mWlCb);
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb)
    {
        mfccParams          = params;
        mStatusCallback     = scb;        
        mCommandCallback    = ccb;        
        mResultCallback     = rcb;   
        mfcc                = new MFCC(mfccParams, scb, ccb, rcb, mWlCb);
    }
    public void init(MFCCParams params, Handler cb, CallbackContext wlcb)
    {
        mWlCb       = wlcb; 
        init(params, cb);
    }
    public void init(MFCCParams params, Handler scb, Handler ccb, Handler rcb, CallbackContext wlcb)
    {
        mWlCb       = wlcb;
        init(params, scb, ccb, rcb);
    }    
    //===============================================================================================
    // wrapper to thread execution 
    //===============================================================================================
    // GET FROM folder or a file
    public void getMFCC(String source)
    {
        sSource = source;
        bundle  = new Bundle();
        Message message;
        switch((int)mfccParams.nDataOrig)
        {
            case ENUMS.MFCC_DATAORIGIN_FILE:
                
                bundle.putString("source", source);
                message         = mInternalHandler.obtainMessage();
                message.what    = ENUMS.MFCC_CMD_GETFILE;
                message.setData(bundle);
                mInternalHandler.sendMessage(message);
                break;

            case ENUMS.MFCC_DATAORIGIN_FOLDER:

                bundle.putString("source", source);
                message         = mInternalHandler.obtainMessage();
                message.what    = ENUMS.MFCC_CMD_GETFOLDER;
                message.setData(bundle);
                mInternalHandler.sendMessage(message);
                break;
        }        
    }    

    // GET FROM data array (a real-time stream)
    public void getMFCC(float[] data, String outfile)
    {
        bundle          = new Bundle();
        bundle.putString("source", outfile);
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }
    
    public void getMFCC(float[] data)
    {
        bundle          = new Bundle();
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }
    
    public void getQueueMFCC(float[] data)
    {
        bundle          = new Bundle();
        bundle.putFloatArray("data", data);
        
        Message message = mInternalHandler.obtainMessage();
        message.what    = ENUMS.MFCC_CMD_GETQDATA;
        message.setData(bundle);
        mInternalHandler.sendMessage(message);
    }     
    
    //===============================================================================================

    // receive new data, calculate how many samples must be processed and send them to analysis. 
    // copy the last 120 samples of these to-be-processed data plus the remaining not-to-be-processed ones to the queue array
    private int processQueueData(float[] data)
    {
        int nOldData        = nQueueLastIndex;
        int nNewData        = data.length;
        int tot             = nQueueLastIndex + nNewData;
        int nMFCCWindow     = MFCCCalcJAudio.getOptimalVectorLength(tot, mfccParams.nWindowLength, mfccParams.nWindowDistance);
        int nData2take      = nMFCCWindow - nQueueLastIndex;             
        int nData2Store     = data.length - nData2take + mfccParams.nData2Reprocess; 

        // assumes that first [0-(nQueueLastIndex-1)] elements of faMFCCQueue contains the still not processed data 
        faData2Process      = new float[nMFCCWindow]; 

        // writes the to be processed vector
        System.arraycopy(faMFCCQueue, 0, faData2Process, 0, nOldData);  // whole faMFCCQueue => mfccvector, then, 
        System.arraycopy(data, 0, faData2Process, nOldData, nData2take);// first nData2Take of data => mfccvector  

        // update queue vector
        // take from id= (nData2take - mfccParams.nData2Reprocess) of data => beginning of queue        
        System.arraycopy(data, nData2take - mfccParams.nData2Reprocess, faMFCCQueue, 0, nData2Store); 
        nQueueLastIndex = nData2Store;  
        
        return MFCCCalcJAudio.getFrames(nMFCCWindow, mfccParams.nWindowLength, mfccParams.nWindowDistance);
    }
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        Bundle bundle = msg.getData();
        float[] data;
        switch((int)msg.what)
        {
            case ENUMS.MFCC_CMD_GETFILE:
                sSource         = bundle.getString("source");
                mfcc.processFile(sSource);
                break;

            case ENUMS.MFCC_CMD_GETFOLDER:
                
                sSource         = bundle.getString("source");
                mfcc.processFolder(sSource);
                break;

            case ENUMS.MFCC_CMD_GETDATA:
                
                data            = bundle.getFloatArray("data");
                if(bundle.getString("source") != null)
                sSource         = bundle.getString("source");
                mfcc.processRawData(data);
                break;

            case ENUMS.MFCC_CMD_GETQDATA:  
            case ENUMS.CAPTURE_RESULT:  //  to process real-time data captured by the AudioInputReceiver thread
                    
                data            = bundle.getFloatArray("data");
                int nframes     = processQueueData(data); 
                
                mStatusCallback.sendMessage(Message.obtain(null, ENUMS.MFCC_STATUS_PROCESS_STARTED, nframes));
                mfcc.processRawData(faData2Process);
                break;
        }
        return true;
    }    
    
    @Override
    protected void onLooperPrepared() 
    {
        mInternalHandler = new Handler(getLooper(), this);
    }
    
    public Handler getHandlerLooper()
    {
        if(mInternalHandler == null)   Log.w(LOG_TAG, "MFCCHandlerThread mInternalHandler is NULL !!!!!!!!!!!!!!!!!");
        return mInternalHandler;
    }    
    //================================================================================================================
}
