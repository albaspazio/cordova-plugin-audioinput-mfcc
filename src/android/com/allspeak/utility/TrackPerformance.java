/**
 */
package com.allspeak.utility;

public class TrackPerformance 
{
    private static final String TAG = "TrackPerformance";

    long lCurr      = 0;
    long lStart     = 0;
    long lLastTime  = 0;
    
    int nStep       = 0;
    int[] aiElapsed;
    
    public TrackPerformance(int step)
    {
        lStart      = System.nanoTime();
        lLastTime   = lStart;
        nStep       = step;
        aiElapsed   = new int[nStep+1]; // last element is the total elapsed time
        
        for(int s=0; s<=nStep; s++) aiElapsed[s] = 0;   // init to ZERO
    }
    
    public int addTimepoint(int step) // step is 1-based
    {
        if(step <  1 || step > nStep) return -1;
        
        lCurr           = System.nanoTime();
        int elapsed     = (int) Math.round((lCurr-lLastTime)/1000000);
        aiElapsed[step] = elapsed;
        lLastTime       = lCurr;
        return elapsed;
    }
    
    public int[] endTracking(int step)
    {
        if (step > 0 && step <= nStep)  addTimepoint(step);
        else                            return null;
        
        return endTracking();
    }
    
    public int[] endTracking()
    {
        aiElapsed[nStep] = 0;
        for (int s=0; s<nStep; s++)
            aiElapsed[nStep] = aiElapsed[nStep] + aiElapsed[s];
        return aiElapsed;
    }
}
        