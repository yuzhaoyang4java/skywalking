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

package org.skywalking.apm.collector.core.graph;

import org.skywalking.apm.collector.core.framework.Executor;

import java.util.LinkedList;
import java.util.List;

/**
 * 包含 WayToNode 数组，即 Node 提交数据给 Next 的 Node 数组的方式。
 *
 * The <code>Next</code> is a delegate object for the following {@link Node}.
 *
 * @author peng-yongsheng, wu-sheng
 */
public class Next<INPUT> implements Executor<INPUT> {

    private final List<WayToNode> ways;

    public Next() {
        this.ways = new LinkedList<>();
    }

    final void addWay(WayToNode way) {
        ways.add(way);
    }

    /**
     * Drive to the next nodes
     *
     * @param input 数据
     */
    @Override public void execute(INPUT input) {
        ways.forEach(way -> way.in(input));
    }
}
