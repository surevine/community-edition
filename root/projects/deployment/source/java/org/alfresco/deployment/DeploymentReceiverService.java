/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing
 */

package org.alfresco.deployment;

import java.io.OutputStream;
import java.util.List;


/**
 * Public Interface for File System Deployment Receiver (FSR)
 * 
 * @author britt
 */
public interface DeploymentReceiverService
{
    /**
     * Start a deployment. 
     * @param target The target to deploy to. A target is simply a key
     * to a receiver side deployment configuration.  
     * @param user The user name for authentication.
     * @param password The password for the user.
     * @return A transaction ticket.
     */
    public String begin(String target, String user, String password);
    
    /**
     * Signals that the deployment is finished and should
     * commit.  
     * @param ticket The transaction ticket.
     */
    public void commit(String ticket);
    
    /**
     * Signals that the deployment should be aborted and
     * rolled back.
     * @param ticket
     */
    public void abort(String ticket);
    
    /**
     * Send a file to a path.
     * @param ticket
     * @param path
     * @return
     */
    public OutputStream send(String ticket, String path, String guid);
    
    /**
     * Tell the deployment receiver that a particular send is done.
     * This closes the output stream.
     * @param ticket
     * @param out
     */
    public void finishSend(String ticket, OutputStream out);
    
    /**
     * Create a directory.
     * @param ticket
     * @param path
     * @param guid The GUID of the directory to be created.
     */
    public void mkdir(String ticket, String path, String guid);
    
    /**
     * Set the Guid on a directory.
     * @param ticket
     * @param path
     * @param guid
     */
    public void setGuid(String ticket, String path, String guid);
    
    /**
     * Delete a file or directory.
     * @param ticket
     * @param path
     */
    public void delete(String ticket, String path);
    
    /**
     * Get a listing of a directory.
     * @param ticket
     * @param path
     * @return The listing in name sorted order.
     */
    public List<FileDescriptor> getListing(String ticket, String path);
    
    /**
     * Shut down the Deployment Receiver.
     * @param user
     * @param password
     */
    public void shutDown(String user, String password);
}
