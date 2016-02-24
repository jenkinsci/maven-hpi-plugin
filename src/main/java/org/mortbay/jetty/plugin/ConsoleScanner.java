//========================================================================
//Copyright 2004-2008 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

/**
 * 
 */
package org.mortbay.jetty.plugin;

import java.io.IOException;

import org.mortbay.jetty.plugin.AbstractJettyMojo;

public class ConsoleScanner extends Thread 
{
    
    private final AbstractJettyMojo mojo;
    
    public ConsoleScanner(AbstractJettyMojo mojo) 
    {
        this.mojo = mojo;
        setName("Console scanner");
        setDaemon(true);
    }
    
    public void run() 
    {  
        try 
        {
            while (true) 
            {
                checkSystemInput();
                getSomeSleep();
            }
        } 
        catch (IOException e) 
        {
            mojo.getLog().warn(e);
        }
    }
    
    private void getSomeSleep() 
    {
        try 
        {
            Thread.sleep(500);
        } 
        catch (InterruptedException e) 
        {
            mojo.getLog().debug(e);
        }
    }
    
    private void checkSystemInput() throws IOException 
    {     
        while (System.in.available() > 0) {
            int inputByte = System.in.read();
            if (inputByte >= 0) 
            {
                char c = (char)inputByte;
                if (c == '\n') {
                    restartWebApp();
                }
            }
        }
    }
    
    
    /**
     * Skip buffered bytes of system console.
     */
    private void clearInputBuffer() 
    {
        try
        {
            while (System.in.available() > 0)
            {
                // System.in.skip doesn't work properly. I don't know why
                long available = System.in.available();
                for (int i = 0; i < available; i++)
                {
                    if (System.in.read() == -1)
                    {
                        break;
                    }
                }
            }
        }
        catch (IOException e)
        {
            mojo.getLog().warn("Error discarding console input buffer", e);
        }      
    }
    
    private void restartWebApp()
    {
        try
        {
            mojo.restartWebApp(false);
            // Clear input buffer to discard anything entered on the console
            // while the application was being restarted.
            clearInputBuffer();
        }
        catch (Exception e)
        {
            mojo.getLog().error(
                            "Error reconfiguring/restarting webapp after a new line on the console",
                            e);
        }
    }
}
