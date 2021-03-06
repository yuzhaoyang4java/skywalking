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

package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.cache.CacheModule;
import org.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.skywalking.apm.collector.storage.table.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceTable;

import java.util.Iterator;
import java.util.Map;

/**
 * @author peng-yongsheng
 */
public class ServiceTreeService {

    private final IServiceEntryUIDAO serviceEntryDAO;
    private final IServiceReferenceUIDAO serviceReferenceDAO;
    private final ApplicationCacheService applicationCacheService;
    private final ServiceNameCacheService serviceNameCacheService;

    public ServiceTreeService(ModuleManager moduleManager) {
        this.serviceEntryDAO = moduleManager.find(StorageModule.NAME).getService(IServiceEntryUIDAO.class);
        this.serviceReferenceDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferenceUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }

    public JsonObject loadEntryService(int applicationId, String entryServiceName, long startTime, long endTime,
        int from, int size) {
        // 查询 ServiceEntry 分页数组
        JsonObject response = serviceEntryDAO.load(applicationId, entryServiceName, startTime, endTime, from, size);

        // 设置 applicationCode
        JsonArray entryServices = response.get("array").getAsJsonArray();
        for (JsonElement element : entryServices) {
            JsonObject entryService = element.getAsJsonObject();
            int respApplication = entryService.get(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID)).getAsInt();
            String applicationCode = applicationCacheService.get(respApplication);
            entryService.addProperty("applicationCode", applicationCode);
        }

        return response;
    }

    public JsonArray loadServiceTree(int entryServiceId, long startTime, long endTime) {
        //  获得 ServiceReference 的映射
        Map<String, JsonObject> serviceReferenceMap = serviceReferenceDAO.load(entryServiceId, startTime, endTime);

        // 设置 操作名
        serviceReferenceMap.values().forEach(serviceReference -> {
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            int behindServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
            String frontServiceName = serviceNameCacheService.getSplitServiceName(serviceNameCacheService.get(frontServiceId));
            String behindServiceName = serviceNameCacheService.getSplitServiceName(serviceNameCacheService.get(behindServiceId));
            serviceReference.addProperty("frontServiceName", frontServiceName);
            serviceReference.addProperty("behindServiceName", behindServiceName);
        });

        // 创建树结构
        return buildTreeData(serviceReferenceMap);
    }

    private JsonArray buildTreeData(Map<String, JsonObject> serviceReferenceMap) {
        JsonArray serviceReferenceArray = new JsonArray();

        // 获得根 ServiceReference
        JsonObject rootServiceReference = findRoot(serviceReferenceMap);


        if (ObjectUtils.isNotEmpty(rootServiceReference)) {
            // 从 serviceReferenceMap 移除根节点( rootServiceReference )
            serviceReferenceArray.add(rootServiceReference);
            String id = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
            serviceReferenceMap.remove(id);

            // 递归，获得树
            int rootServiceId = rootServiceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
            sortAsTree(rootServiceId, serviceReferenceArray, serviceReferenceMap);
        }

        return serviceReferenceArray;
    }

    /**
     * 获得根 ServiceReference
     *
     * frontServiceId 为 1 ( Const.NONE_SERVICE_ID )
     *
     * @param serviceReferenceMap ServiceReference 的映射
     * @return 根 ServiceReference
     */
    private JsonObject findRoot(Map<String, JsonObject> serviceReferenceMap) {
        for (JsonObject serviceReference : serviceReferenceMap.values()) {
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (frontServiceId == 1) { // Const.NONE_SERVICE_ID
                return serviceReference;
            }
        }
        return null;
    }

    /**
     * 递归，获得树
     *
     * @param serviceId 父操作编号
     * @param serviceReferenceArray 结果集
     * @param serviceReferenceMap ServiceReference 的映射
     */
    private void sortAsTree(int serviceId, JsonArray serviceReferenceArray, Map<String, JsonObject> serviceReferenceMap) {
        Iterator<JsonObject> iterator = serviceReferenceMap.values().iterator();
        while (iterator.hasNext()) {
            JsonObject serviceReference = iterator.next();
            int frontServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).getAsInt();
            if (serviceId == frontServiceId) {
                serviceReferenceArray.add(serviceReference);

                // 递归，获得树
                int behindServiceId = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt();
                sortAsTree(behindServiceId, serviceReferenceArray, serviceReferenceMap);
            }
        }
    }

    @Deprecated // add by 芋艿，并未调用
    private void merge(Map<String, JsonObject> serviceReferenceMap, JsonObject serviceReference) {
        String id = serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)) + Const.ID_SPLIT + serviceReference.get(ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));

        if (serviceReferenceMap.containsKey(id)) {
            JsonObject reference = serviceReferenceMap.get(id);
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S1_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S3_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_LTE));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_S5_GT));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_ERROR));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_SUMMARY));
            add(reference, serviceReference, ColumnNameUtils.INSTANCE.rename(ServiceReferenceTable.COLUMN_COST_SUMMARY));
        } else {
            serviceReferenceMap.put(id, serviceReference);
        }
    }

    private void add(JsonObject oldReference, JsonObject newReference, String key) {
        long oldValue = oldReference.get(key).getAsLong();
        long newValue = newReference.get(key).getAsLong();
        oldReference.addProperty(key, oldValue + newValue);
    }
}