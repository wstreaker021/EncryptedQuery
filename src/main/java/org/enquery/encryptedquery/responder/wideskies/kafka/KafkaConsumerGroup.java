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
package org.enquery.encryptedquery.responder.wideskies.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KafkaConsumerGroup {

      private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerGroup.class);

	  private final int numberOfConsumers;
	  private final String groupId;
	  private final String topic;
	  private final String brokers;
	  private final String hdfsuri;
	  private final String hdfsUser;
	  private final String hdfsFolder;
	  private final String clientId;
	  private final UUID uuid;
	  private final Boolean forceFromStart;
	  
	  private List<KafkaConsumerThread> consumers;

	  public KafkaConsumerGroup(String brokers, String groupId, String topic, String hdfsURI, String hdfsUser,
			  String hdfsFolder, String clientId, UUID uuid, Boolean forceFromStart, int numberOfConsumers) {
	    this.brokers = brokers;
	    this.topic = topic;
	    this.groupId = groupId;
	    this.hdfsuri = hdfsURI;
	    this.hdfsUser = hdfsUser;
	    this.hdfsFolder = hdfsFolder;
	    this.clientId = clientId;
	    this.uuid = uuid;
	    this.forceFromStart = forceFromStart;
	    this.numberOfConsumers = numberOfConsumers;
	    consumers = new ArrayList<>();
	    for (int i = 0; i < this.numberOfConsumers; i++) {
	      KafkaConsumerThread ncThread =
	          new KafkaConsumerThread(this.brokers, this.groupId, this.topic,
	        		  this.hdfsuri, this.hdfsUser, this.hdfsFolder, this.clientId, this.uuid, this.forceFromStart); 
	      consumers.add(ncThread);
	    }
	  }

	  public void execute() {
	    for (KafkaConsumerThread ncThread : consumers) {
	      Thread t = new Thread(ncThread);
	      t.start();
	    }
	  }

	  /**
	   * @return the numberOfConsumers
	   */
	  public int getNumberOfConsumers() {
	    return numberOfConsumers;
	  }

	  /**
	   * @return the groupId
	   */
	  public String getGroupId() {
	    return groupId;
	  }
	  
	  /**
	   *  Stop the consumer threads
	   */
	  public void stopConsumers() {
		  logger.info("Stopping Consumer Threads");
		  for (KafkaConsumerThread ncThread : consumers) {
			  ncThread.stopListening();
		  }
	  }
	  
	}
