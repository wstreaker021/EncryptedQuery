/*
 * Copyright 2017 EnQuery.
 * This product includes software licensed to EnQuery under 
 * one or more license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file has been modified from its original source.
 */
package org.enquery.encryptedquery.serialization;

import org.enquery.encryptedquery.utils.SystemConfiguration;

/**
 * Common supertype for types that can store objects using serialization.
 */
abstract class StorageService
{
  SerializationService serializer;

  StorageService()
  {
    if (SystemConfiguration.getBooleanProperty("encryptedquery.serialization.java", false))
    {
      this.setSerializer(new JavaSerializer());
    }
    else
    {
      this.setSerializer(new JsonSerializer());
    }
  }

  StorageService(SerializationService service)
  {
    this.setSerializer(service);
  }

  public void setSerializer(SerializationService service)
  {
    serializer = service;
  }
}
