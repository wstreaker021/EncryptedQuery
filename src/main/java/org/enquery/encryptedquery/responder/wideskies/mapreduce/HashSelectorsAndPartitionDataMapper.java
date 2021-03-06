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
package org.enquery.encryptedquery.responder.wideskies.mapreduce;

import java.io.IOException;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.enquery.encryptedquery.inputformat.hadoop.BytesArrayWritable;
import org.enquery.encryptedquery.query.wideskies.Query;
import org.enquery.encryptedquery.query.wideskies.QueryInfo;
import org.enquery.encryptedquery.responder.wideskies.common.HashSelectorAndPartitionData;
import org.enquery.encryptedquery.schema.data.DataSchema;
import org.enquery.encryptedquery.schema.data.DataSchemaLoader;
import org.enquery.encryptedquery.schema.data.DataSchemaRegistry;
import org.enquery.encryptedquery.schema.query.QuerySchema;
import org.enquery.encryptedquery.schema.query.QuerySchemaLoader;
import org.enquery.encryptedquery.schema.query.QuerySchemaRegistry;
import org.enquery.encryptedquery.schema.query.filter.DataFilter;
import org.enquery.encryptedquery.serialization.HadoopFileSystemStore;
import org.enquery.encryptedquery.utils.StringUtils;
import org.enquery.encryptedquery.utils.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Tuple2;

/**
 * Initialization mapper for EncryptedQuery
 * <p>
 * Reads in data, extracts the selector by queryType from each dataElement, performs a keyed hash of the selector, extracts the partitions of the dataElement,
 * and emits {@link hash(selector), dataPartitions}
 *
 */
public class HashSelectorsAndPartitionDataMapper extends Mapper<Text,MapWritable,IntWritable,BytesArrayWritable>
{
  private static final Logger logger = LoggerFactory.getLogger(HashSelectorsAndPartitionDataMapper.class);

  private IntWritable keyOut = null;

  private QueryInfo queryInfo = null;
  private QuerySchema qSchema = null;
  private DataSchema dSchema = null;
  private Object filter = null;

  @Override
  public void setup(Context ctx) throws IOException, InterruptedException
  {
    super.setup(ctx);

    logger.info("Setting up the mapper");

    keyOut = new IntWritable();
    
    FileSystem fs = FileSystem.newInstance(ctx.getConfiguration());

    // Can make this so that it reads multiple queries at one time...
    String queryDir = ctx.getConfiguration().get("pirMR.queryInputDir");
    Query query = new HadoopFileSystemStore(fs).recall(queryDir, Query.class);
    queryInfo = query.getQueryInfo();

    try
    {
      SystemConfiguration.setProperty("data.schemas", ctx.getConfiguration().get("data.schemas"));
      SystemConfiguration.setProperty("query.schemas", ctx.getConfiguration().get("query.schemas"));
      SystemConfiguration.setProperty("pir.stopListFile", ctx.getConfiguration().get("pirMR.stopListFile"));

      DataSchemaLoader.initialize(true, fs);
      QuerySchemaLoader.initialize(true, fs);

    } catch (Exception e)
    {
      e.printStackTrace();
    }

    if (ctx.getConfiguration().get("pir.allowAdHocQuerySchemas", "false").equals("true"))
    {
      qSchema = queryInfo.getQuerySchema();
    }
    if (qSchema == null)
    {
      qSchema = QuerySchemaRegistry.get(queryInfo.getQueryType());
    }
    dSchema = DataSchemaRegistry.get(qSchema.getDataSchemaName());

    try
    {
      filter = qSchema.getFilter();
    } catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  /**
   * The key is the docID/line number and the value is the doc
   */
  @Override
  public void map(Text key, MapWritable value, Context ctx) throws IOException, InterruptedException
  {
//    logger.debug("key = " + key.toString());
//    logger.debug("value: " + StringUtils.mapWritableToString(value));
    ctx.getCounter(MRStats.NUM_RECORDS_INIT_MAPPER).increment(1);

    boolean passFilter = true;
    if (filter != null)
    {
      passFilter = ((DataFilter) filter).filterDataElement(value, dSchema);
    }

    if (passFilter)
    {
      // Extract the selector, compute the hash, and partition the data element according to query type
      Tuple2<Integer,BytesArrayWritable> returnTuple;
      try
      {
//        returnTuple = HashSelectorAndPartitionData.hashSelectorAndFormPartitions(value, qSchema, dSchema, queryInfo);
        returnTuple = null;
      } catch (Exception e)
      {
        logger.error("Error in partitioning data element value = " + StringUtils.mapWritableToString(value));
        e.printStackTrace();
        throw new RuntimeException(e);
      }

//      logger.debug("Writing key {}", returnTuple._1 );
      keyOut.set(returnTuple._1);
      ctx.write(keyOut, returnTuple._2);
    }
  }

  @Override
  public void cleanup(Context ctx) throws IOException, InterruptedException
  {
    logger.info("finished with the map - cleaning up ");
  }
}
