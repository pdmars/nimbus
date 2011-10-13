/*
 * Copyright 1999-2010 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.nimbus.authz;

public class UserAlias {

    private String userId;
    private String aliasName;
    private String friendlyName;
    private int aliasType;
    private String aliasTypeData;

    public UserAlias(String userId, String aliasName, String friendlyName, int aliasType, String aliasTypeData) {
        this.userId = userId;
        this.aliasName = aliasName;
        this.friendlyName = friendlyName;
        this.aliasType = aliasType;
        this.aliasTypeData = aliasTypeData;
    }

    public String getUserId() {
        return userId;
    }

    public String getAliasName() {
        return aliasName;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public int getAliasType() {
        return aliasType;
    }

    public String getAliasTypeData() {
        return aliasTypeData;
    }

    public String toString() {

        return "userID: '" + userId + "' aliasName: '" + aliasName + "' friendlyName: '" + friendlyName
                + "' aliasType: '" + aliasType + "' aliasTypeData: '" + aliasTypeData + "'";
    }
}
