/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.ws.nativeimpl.connection;

import org.ballerinalang.bre.Context;
import org.ballerinalang.bre.bvm.BlockingNativeCallableUnit;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.ballerinalang.natives.annotations.Receiver;
import org.ballerinalang.natives.annotations.ReturnType;
import org.ballerinalang.net.ws.WebSocketConnectionManager;
import org.ballerinalang.net.ws.WebSocketConstants;
import org.ballerinalang.net.ws.WsOpenConnectionInfo;

/**
 * Get parent connection is exists.
 *
 * @since 0.94
 */

@BallerinaFunction(
        packageName = "ballerina.net.ws",
        functionName = "getParentConnection",
        receiver = @Receiver(type = TypeKind.STRUCT, structType = "Connection",
                             structPackage = "ballerina.net.ws"),
        returnType = {@ReturnType(type = TypeKind.STRUCT, structType = "Connection",
                                  structPackage = "ballerina.net.ws")},
        isPublic = true
)
public class GetParentConnection extends BlockingNativeCallableUnit {

    @Override
    public void execute(Context context) {
        BStruct wsConnection = (BStruct) context.getRefArgument(0);
        String parentConnectionID = (String) wsConnection.getNativeData(
                WebSocketConstants.NATIVE_DATA_PARENT_CONNECTION_ID);
        WsOpenConnectionInfo connectionInfo =
                WebSocketConnectionManager.getInstance().getConnectionInfo(parentConnectionID);
        context.setReturnValues(connectionInfo.getWsConnection());
    }
}
