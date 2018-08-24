/*
 * Copyright (C) 2018 The Sylph Authors
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
 */
package ideal.sylph.common.memory;

public class MemoryLocation
{
    Object obj;
    long offset;

    //< 适用于堆内内存
    public MemoryLocation(Object obj, long offset)
    {
        this.obj = obj;
        this.offset = offset;
    }

    //< 适用于堆外内存
    public MemoryLocation()
    {
        this(null, 0);
    }
}
