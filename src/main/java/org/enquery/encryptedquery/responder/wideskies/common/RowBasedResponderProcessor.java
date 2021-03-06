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
package org.enquery.encryptedquery.responder.wideskies.common;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.enquery.encryptedquery.encryption.ModPowAbstraction;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.query.wideskies.QueryUtils;
import org.enquery.encryptedquery.response.wideskies.Response;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.utils.ConversionUtils;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will process data from queue one row at a time.  This is memory efficient, but performance poor.
 *
 */
public class RowBasedResponderProcessor implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(RowBasedResponderProcessor.class);
	private boolean stopProcessing = false;
	private boolean isRunning = true;
	private final Query query;
	private QueryInfo queryInfo = null;
	private QuerySchema qSchema = null;
	private int dataPartitionBitSize = 8;
	private Response response = null;
	private long threadId = 0; 
	private long recordCount = 0;

	private TreeMap<Integer,BigInteger> columns = null; // the column values for the encrypted query calculations
	private ArrayList<Integer> rowColumnCounters; // keeps track of how many hit partitions have been recorded for each row/selector

	private ConcurrentLinkedQueue<QueueRecord> inputQueue;
	private ConcurrentLinkedQueue<Response> responseQueue;

	public RowBasedResponderProcessor(ConcurrentLinkedQueue<QueueRecord> inputQueue, ConcurrentLinkedQueue<Response> responseQueue,
			Query queryInput) {

		logger.debug("Initializing Responder Processing Thread");
		this.query = queryInput;
		this.inputQueue = inputQueue;
		this.responseQueue = responseQueue;

		queryInfo = query.getQueryInfo();
		String queryType = queryInfo.getQueryType();
		dataPartitionBitSize = queryInfo.getDataPartitionBitSize();

		if (SystemConfiguration.getBooleanProperty("pir.allowAdHocQuerySchemas", false))
		{
			qSchema = queryInfo.getQuerySchema();
		}
		if (qSchema == null)
		{
			qSchema = QuerySchemaRegistry.get(queryType);
		}

		resetResponse();

	}

	public void stopProcessing() {
		stopProcessing = true;
		logger.debug("Stop responder processing command received for thread {}", threadId);
	}

	public boolean isRunning() {
		return isRunning;
	}

	public long getRecordsProcessed() {
		return recordCount;
	}
	
	@Override
	public void run() {

		threadId = Thread.currentThread().getId();
		logger.info("Starting Responder Processing Thread {}", threadId);
		QueueRecord nextRecord = null;

		while (!stopProcessing) {
			while ((nextRecord = inputQueue.poll()) != null) {
				try {
					addDataElement(nextRecord);
					recordCount++;
				} catch (Exception e) {
					logger.error("Exception processing record {} Exception: {}", nextRecord, e.getMessage());
				}
			}
		}

		//Process Response
		setResponseElements();
		responseQueue.add(response);
		logger.info("Thread {} processed {} records", threadId, recordCount);
		isRunning = false;

	}

	/**
	 * Method to add a data element associated with the given selector to the Response
	 * Assumes that the dataMap contains the data in the schema specified
	 * Initialize Paillier ciphertext values Y_i to 1 (as needed -- column values as the # of hits grows)
	 * Initialize 2^hashBitSize counters: c_t = 0, 0 <= t <= (2^hashBitSize - 1)
	 * For selector T:
	 * For data element D, split D into partitions of size partitionSize-many bits:
	 * D = D_0 || ... ||D_{\ceil{bitLength(D)/partitionSize} - 1)}
	 * Compute H_k(T); let E_T = query.getQueryElement(H_k(T)).
	 * For each data partition D_i:
	 * Compute/Update:
	 * Y_{i+c_{H_k(T)}} = (Y_{i+c_{H_k(T)}} * ((E_T)^{D_i} mod N^2)) mod N^2 ++c_{H_k(T)}
	 * 
	 */
	public void addDataElement(QueueRecord qr) throws Exception
	{
		List<Byte> inputData = qr.getParts();
		List<Byte[]> hitValPartitions = QueryUtils.createPartitions(inputData, dataPartitionBitSize);

		int rowIndex = qr.getRowIndex();
		int rowCounter = rowColumnCounters.get(rowIndex);
		BigInteger rowQuery = query.getQueryElement(rowIndex);

		for (int i = 0; i < hitValPartitions.size(); ++i)
		{
			if (!columns.containsKey(i + rowCounter))
			{
				columns.put(i + rowCounter, BigInteger.valueOf(1));
			}
			BigInteger column = columns.get(i + rowCounter); // the next 'free' column relative to the selector
			BigInteger exp;
			BigInteger BI = new BigInteger(1, ConversionUtils.toPrimitives(hitValPartitions.get(i)));

			if (query.getQueryInfo().useExpLookupTable() && !query.getQueryInfo().useHDFSExpLookupTable()) // using the standalone
				// lookup table
			{
				exp = query.getExp(rowQuery, BI.intValue());
			}
			else
				// without lookup table
			{
				//	        logger.debug("i = " + i + " hitValPartitions.get(i).intValue() = " + hitValPartitions.get(i).intValue());
				exp = ModPowAbstraction.modPow(rowQuery, BI, query.getNSquared());
			}
			column = (column.multiply(exp)).mod(query.getNSquared());

			columns.put(i + rowCounter, column);
		}

		// Update the rowCounter (next free column position) for the selector
		rowColumnCounters.set(rowIndex, (rowCounter + hitValPartitions.size()));
	}


	/**
	 * Reset The response for the next iteration
	 */
	private void resetResponse() {

		response = new Response(queryInfo);

		// Columns are allocated as needed, initialized to 1
		columns = new TreeMap<>();

		// Initialize row counters
		rowColumnCounters = new ArrayList<>();
		for (int i = 0; i < Math.pow(2, queryInfo.getHashBitSize()); ++i)
		{
			rowColumnCounters.add(0);
		}
	}

	public void setResponseElements()
	{
		logger.debug("There are {} columns in the response from QPT {}", columns.size(), Thread.currentThread().getId());
		response.addResponseElements(columns);
	}
}
