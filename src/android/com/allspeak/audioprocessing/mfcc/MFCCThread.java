package com.allspeak.audioprocessing.mfcc;

import android.os.Bundle;
import android.os.Handler;

import org.apache.cordova.CallbackContext;

// not necessary
import com.allspeak.audioprocessing.mfcc.MFCCParams;
import com.allspeak.audioprocessing.mfcc.MFCC;
import java.util.concurrent.ExecutorService;

/*
it's a layer which call the MFCC functions on a new thread
sends the following messages to Plugin Activity:
- data
- progress_file
- progress_folder
- error
*/
public class MFCCThread  
{
    private static final String TAG     = "MFCCThread";
    
    private MFCC mfcc                   = null;
    private MFCCParams mfccParams       = null;
    private int nDataOrig;
    private ExecutorService mExecutor   = null;
    
    // manage MFCC queue
    private float[] faMFCCQueue         = new float[2048];;
    private int nQueueLastIndex         = 0;    
    private float[] faData2Process      = null;   // contains (nframes, numparams) calculated FilterBanks
    String sSource                      = "";

    //================================================================================================================
    public MFCCThread(MFCCParams params, ExecutorService executor, Handler handler)
    {
        nDataOrig   = params.nDataOrig;
        mExecutor   = executor;
        mfccParams  = params;
        mfcc        = new MFCC(params, handler);
    }
    
    public MFCCThread(MFCCParams params, ExecutorService executor, Handler handler, CallbackContext wlcallback)
    {
        nDataOrig   = params.nDataOrig;
        mExecutor   = executor;
        mfccParams  = params;
        mfcc        = new MFCC(params, handler, wlcallback);
    }
    //================================================================================================================
    
    // GET FROM folder or a file
    public void getMFCC(String source)
    {
        sSource = source;
        switch(nDataOrig)
        {
            case MFCCParams.DATAORIGIN_FILE:

                mExecutor.execute(new Runnable() {
                    @Override
                    public void run(){ mfcc.processFile(sSource); }
                });                        
                break;

            case MFCCParams.DATAORIGIN_FOLDER:

                mExecutor.execute(new Runnable() {
                    @Override
                    public void run(){ mfcc.processFolder(sSource); }
                });
                break;
        }        
    }    

    // GET FROM data array (a real-time stream)
    public void getMFCC(float[] source, String outfile)
    {
        mfcc.setOutputFile(outfile);
        getMFCC(source);
    }
    
    public void getMFCC(float[] source)
    {
        faData2Process  = source;        
        mExecutor.execute(new Runnable() {
            @Override
            public void run(){ mfcc.processRawData(faData2Process); }
        });
    }     
    
    // receive new data, calculate how many samples must be processed and send them to analysis. 
    // copy the last 120 samples of these to-be-processed data plus the remaining not-to-be-processed ones to the queue array
    public int processQueueData(float[] data)
    {
        int nOldData        = nQueueLastIndex;
        int nNewData        = data.length;
        int tot             = nQueueLastIndex + nNewData;
        int nMFCCWindow     = mfcc.getOptimalVectorLength(tot);
        int nData2take      = nMFCCWindow - nQueueLastIndex;             
        int nData2Store     = data.length - nData2take + mfccParams.nData2Reprocess; 

        // assumes that first [0-(nQueueLastIndex-1)] elements of faMFCCQueue contains the still not processed data 
        float[] mfccvector  = new float[nMFCCWindow]; 

        // writes the to be processed vector
        System.arraycopy(faMFCCQueue, 0, mfccvector, 0, nOldData);  // whole faMFCCQueue => mfccvector, then, 
        System.arraycopy(data, 0, mfccvector, nOldData, nData2take);// first nData2Take of data => mfccvector  

        // update queue vector
        // take from id= (nData2take - mfccParams.nData2Reprocess) of data => beginning of queue        
        System.arraycopy(data, nData2take - mfccParams.nData2Reprocess, faMFCCQueue, 0, nData2Store); 
        nQueueLastIndex = nData2Store;  
        
        getMFCC(mfccvector);
        return mfcc.getFrames(nMFCCWindow);
    }
    //================================================================================================================
}
