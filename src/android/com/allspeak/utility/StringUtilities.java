/**
 */
package com.allspeak.utility;

import java.util.Arrays;


public class StringUtilities 
{
    private static final String TAG = "StringUtilities";
    
    //--------------------------------------------------------------------------------------------------------------------    
    public static String removeExtension(String filepath)
    {
        int extPos = filepath.lastIndexOf(".");
        if(extPos == -1)    return filepath;
        else                return filepath.substring(0, extPos);
    }
    //--------------------------------------------------------------------------------------------------------------------    
    // split a string ending with a number as following
    // "gigi23" => ["gigi", "23"];
    //return the following array:
    // 0: string part
    // 1: number part
    public static String[] denumberString(String filepath)
    {
        String[] result = new String[2];
        
        int len = filepath.length();
        String ch; String str;
        
        String last_char = String.valueOf(filepath.charAt(len-1));
        
        if(!isInteger(last_char))
        {   
            result[0] = filepath; result[1] = null;
            return result;
        }
        str     = filepath.substring(0, len-1);
        for(int c=len-2; c>=0; c--)
        {
            ch = String.valueOf(filepath.charAt(c));
            if(!isInteger(ch))
            {
                result[0] = str; result[1] = last_char;
                return result;
            }
            else
            {
                last_char   = ch + last_char;
                str         = filepath.substring(0, c);
            }
        }        
        return result;
    }
    //--------------------------------------------------------------------------------------------------------------------    
    public static boolean isInteger(String s)
    {
        try { 
            Integer.parseInt(s); 
        } catch(NumberFormatException e) { 
            return false; 
        } catch(NullPointerException e) {
            return false;
        }
        // only got here if we didn't return false
        return true;
    }    
    //--------------------------------------------------------------------------------------------------------------------    
    // return the following array:
    // 0: folder
    // 1: filename
    // 2: extension
    public static String[] splitFilePath(String filepath)
    {
        String[] final_parts = new String[3];
        
        String[] parts = filepath.split("\\.");
        String[] foldersfile;
        // extension
        if(parts.length == 1)
        {
            final_parts[2]  = null;
            foldersfile     = filepath.split("/");
        }
        else
        {
            final_parts[2]  = parts[1];
            foldersfile     = parts[0].split("/");
        }
        // file name
        final_parts[1] = foldersfile[foldersfile.length-1];
        // folder
        String[] folders = new String[foldersfile.length-1];
        System.arraycopy(foldersfile, 0, folders, 0, folders.length);
        
        StringBuilder strBuilder = new StringBuilder();
        for (int i = 0; i < folders.length-1; i++) 
            strBuilder.append(folders[i] + "/");
        strBuilder.append(folders[folders.length-1]);
        final_parts[0] = strBuilder.toString();
        
        return final_parts;
    }
}
