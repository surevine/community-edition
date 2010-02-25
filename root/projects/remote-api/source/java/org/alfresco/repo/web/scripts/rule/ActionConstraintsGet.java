/*
 * Copyright (C) 2005-2009 Alfresco Software Limited.
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
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.web.scripts.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.service.cmr.action.ParameterConstraint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * @author unknown
 *
 */
public class ActionConstraintsGet extends AbstractRuleWebScript
{

    @SuppressWarnings("unused")
    private static Log logger = LogFactory.getLog(ActionConstraintsGet.class);

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        // get request parameters
        String[] names = req.getParameterValues("name");

        List<ParameterConstraint> parameterConstraints = null;

        if (names != null && names.length > 0)
        {
            // filter is present in request
            parameterConstraints = new ArrayList<ParameterConstraint>();

            // find specified parameter constraints
            for (String name : names)
            {
                ParameterConstraint parameterConstraint = actionService.getParameterConstraint(name);

                if (parameterConstraint != null)
                {
                    parameterConstraints.add(parameterConstraint);
                }
            }
        }
        else
        {
            // no filter was provided, return all parameter constraints
            parameterConstraints = actionService.getParameterConstraints();
        }

        model.put("actionConstraints", parameterConstraints);

        return model;
    }
}
