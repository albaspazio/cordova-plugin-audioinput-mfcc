package com.allspeak.audioprocessing.mfcc;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;


// not necessary
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.mfcc.MFCC;

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
    private static final String TAG                 = "MFCCHandlerThread";
    
//    private static final int MSG_INIT               = 0;
    public static final int MSG_GETMFCC_FILE       = 1;
    public static final int MSG_GETMFCC_FOLDER     = 2;
    public static final int MSG_GETMFCC_DATA       = 3;
    public static final int MSG_GETMFCC_QUEUEDATA  = 4;
    
    private Handler mHandler, mCallback;
    private CallbackContext mWlCb;
    
    private MFCC mfcc                   = null;
    private MFCCParams mfccParams       = null;
    
    // manage MFCC queue
    private float[] faMFCCQueue         = new float[2048];;
    private int nQueueLastIndex         = 0;    
    private float[] faData2Process      = null;   // contains (nframes, numparams) calculated FilterBanks
    String sSource                      = "";
    
    Bundle bundle;
    //================================================================================================================
    public MFCCHandlerThread(MFCCParams params, Handler cb, String name)
    {
        super(name);
        mCallback   = cb;
        mfccParams  = params;
        mfcc        = new MFCC(params, mCallback);        
    }
    
    public MFCCHandlerThread(MFCCParams params, Handler cb, String name, int priority)
    {
        super(name, priority);
        mCallback   = cb;
        mfccParams  = params;
        mfcc        = new MFCC(params, mCallback);        
    }
    
    public MFCCHandlerThread(MFCCParams params, Handler cb, CallbackContext wlcb, String name, int priority)
    {
        super(name, priority);
        mCallback   = cb;
        mfccParams  = params;
        mWlCb       = wlcb;
        mfcc        = new MFCC(params, mCallback, wlcb);        
    }

    //================================================================================================================
    //================================================================================================================
    @Override
    public boolean handleMessage(Message msg) 
    {
        Bundle bundle = msg.getData();
        float[] data;
        switch(msg.what)
        {
            case MSG_GETMFCC_FILE:
                sSource         = bundle.getString("source");
                mfcc.processFile(sSource);
                break;

            case MSG_GETMFCC_FOLDER:
                
                sSource         = bundle.getString("source");
                mfcc.processFolder(sSource);
                break;

            case MSG_GETMFCC_DATA:
                
                data            = bundle.getFloatArray("data");
                if(bundle.getString("source") != null)
                    sSource         = bundle.getString("source");
                mfcc.processRawData(data);
                break;

            case MSG_GETMFCC_QUEUEDATA:
                    
                data            = bundle.getFloatArray("data");
                sSource         = bundle.getString("source");
                
                int nframes     = processQueueData(data); 
                
                mCallback.sendMessage(Message.obtain(null, MFCC.STATUS_PROCESSINGSTARTED, nframes));
                mfcc.processRawData(faData2Process);
                break;
        }
        return true;
    }    
    
    
    //===============================================================================================
    public Handler init()
    {
        return mHandler;
    }
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
    
    // GET FROM folder or a file
    public void getMFCC(String source)
    {
        sSource = source;
        bundle  = new Bundle();
        Message message;
        switch(mfccParams.nDataOrig)
        {
            case MFCCParams.DATAORIGIN_FILE:
                
                bundle.putString("source", source);
                message = mHandler.obtainMessage();
                message.setData(bundle);
                message.what    = MSG_GETMFCC_FILE;
                mHandler.sendMessage(message);
                break;

            case MFCCParams.DATAORIGIN_FOLDER:

                bundle.putString("source", source);
                message = mHandler.obtainMessage();
                message.setData(bundle);
                message.what    = MSG_GETMFCC_FOLDER;
                mHandler.sendMessage(message);
                break;
        }        
    }    

    // GET FROM data array (a real-time stream)
    public void getMFCC(float[] source, String outfile)
    {
        bundle = new Bundle();
        bundle.putString("source", outfile);
        bundle.putFloatArray("data", source);
        Message message = mHandler.obtainMessage();
        message.setData(bundle);
        message.what    = MSG_GETMFCC_DATA;
        mHandler.sendMessage(message);
    }
    
    public void getMFCC(float[] source)
    {
        bundle          = new Bundle();
        bundle.putFloatArray("data", source);
        
        Message message = mHandler.obtainMessage();
        message.what    = MSG_GETMFCC_DATA;
        message.setData(bundle);
        mHandler.sendMessage(message);
    }
    
    public void getQueueMFCC(float[] source)
    {
        bundle          = new Bundle();
        bundle.putFloatArray("data", source);
        
        Message message = mHandler.obtainMessage();
        message.what    = MSG_GETMFCC_QUEUEDATA;
        message.setData(bundle);
        mHandler.sendMessage(message);
    }     
    
    //===============================================================================================
    @Override
    protected void onLooperPrepared() 
    {
        mHandler = new Handler(getLooper(), this);
    }

    // receive new data, calculate how many samples must be processed and send them to analysis. 
    // copy the last 120 samples of these to-be-processed data plus the remaining not-to-be-processed ones to the queue array
    private int processQueueData(float[] data)
    {
        int nOldData        = nQueueLastIndex;
        int nNewData        = data.length;
        int tot             = nQueueLastIndex + nNewData;
        int nMFCCWindow     = mfcc.getOptimalVectorLength(tot);
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
        
        return mfcc.getFrames(nMFCCWindow);
    }
    //================================================================================================================
}
