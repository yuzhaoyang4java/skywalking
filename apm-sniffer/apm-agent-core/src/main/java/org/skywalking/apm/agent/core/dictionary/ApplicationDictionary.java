/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import org.skywalking.apm.network.proto.Application;
import org.skywalking.apm.network.proto.ApplicationMapping;
import org.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.skywalking.apm.network.proto.KeyWithIntegerValue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.skywalking.apm.agent.core.conf.Config.Dictionary.APPLICATION_CODE_BUFFER_SIZE;

/**
 * 应用字典
 *
 * Map of application id to application code, which is from the collector side.
 *
 * @author wusheng
 */
public enum ApplicationDictionary {

    /**
     * 单例
     */
    INSTANCE;

    /**
     * 应用编码与应用编号的映射
     */
    private Map<String, Integer> applicationDictionary = new ConcurrentHashMap<String, Integer>();
    /**
     * 未知应用编码集合
     */
    private Set<String> unRegisterApplications = new ConcurrentSet<String>();

    public PossibleFound find(String applicationCode) {
        Integer applicationId = applicationDictionary.get(applicationCode);
        if (applicationId != null) {
            return new Found(applicationId);
        } else {
            // 添加到 unRegisterApplications
            if (applicationDictionary.size() + unRegisterApplications.size() < APPLICATION_CODE_BUFFER_SIZE) {
                unRegisterApplications.add(applicationCode);
            }
            return new NotFound();
        }
    }

    /**
     * 从 Collector 同步未知应用编码集合
     *
     * @param applicationRegisterServiceBlockingStub stub
     */
    public void syncRemoteDictionary(
        ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub applicationRegisterServiceBlockingStub) {
        if (unRegisterApplications.size() > 0) {
            // 注册未知应用编码集合
            ApplicationMapping applicationMapping = applicationRegisterServiceBlockingStub.register(
                Application.newBuilder().addAllApplicationCode(unRegisterApplications).build());
            if (applicationMapping.getApplicationCount() > 0) {
                // 注册成功
                for (KeyWithIntegerValue keyWithIntegerValue : applicationMapping.getApplicationList()) {
                    unRegisterApplications.remove(keyWithIntegerValue.getKey());
                    applicationDictionary.put(keyWithIntegerValue.getKey(), keyWithIntegerValue.getValue());
                }
            }
        }
    }
}
