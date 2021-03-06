package com.amebame.proteus.triton;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amebame.triton.client.TritonClient;
import com.amebame.triton.client.cassandra.entity.TritonCassandraBatchOperation;
import com.amebame.triton.client.cassandra.entity.TritonCassandraColumnFamily;
import com.amebame.triton.client.cassandra.entity.TritonCassandraKeyspace;
import com.amebame.triton.client.cassandra.method.BatchUpdate;
import com.amebame.triton.client.cassandra.method.CreateColumnFamily;
import com.amebame.triton.client.cassandra.method.CreateKeyspace;
import com.amebame.triton.client.cassandra.method.DropKeyspace;
import com.amebame.triton.client.cassandra.method.GetColumns;
import com.amebame.triton.client.cassandra.method.ListColumnFamily;
import com.amebame.triton.client.cassandra.method.ListKeyspace;
import com.amebame.triton.client.cassandra.method.RemoveColumns;
import com.amebame.triton.client.cassandra.method.SetColumns;
import com.amebame.triton.client.cassandra.method.TruncateColumnFamily;
import com.amebame.triton.exception.TritonClientConnectException;
import com.amebame.triton.exception.TritonClientException;
import com.amebame.triton.exception.TritonException;
import com.amebame.triton.json.Json;
import com.amebame.triton.server.TritonServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TritonCassandraClientTest {
	
	private TritonServer server;
	private TritonClient client;
	
	private String clusterName = "test";
	private String keyspaceName = "triton_test";
	
	public TritonCassandraClientTest() throws TritonClientConnectException {
		// create a server
		server = new TritonServer();
		server.setConfigPath("src/test/conf/test_cassandra.json");
		server.start();
		// create a client
		client = new TritonClient();
		client.open("127.0.0.1", 4848);
	}
	
	@Before
	public void before() throws TritonClientException {
		// create
		CreateKeyspace create = new CreateKeyspace();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setReplicationFactor(1);
		JsonNode result = client.send(create);
		assertNotNull(result);
	}
	
	@After
	public void after() throws TritonClientException {
		// drop before
		DropKeyspace drop = new DropKeyspace();
		drop.setCluster(clusterName);
		drop.setKeyspace(keyspaceName);
		JsonNode result = client.send(drop);
		assertNotNull(result);
		// stop the server
		if (server != null) {
			server.stop();
		}
		client.close();
		assertFalse(client.isOpen());
	}

	@Test
	public void testOpenClose() throws TritonClientConnectException {
		assertTrue(client.isOpen());
	}
	
	@Test
	public void listKeyspace() throws TritonException {
		// check list
		ListKeyspace list = new ListKeyspace();
		list.setCluster(clusterName);
		JsonNode body = client.send(list);
		List<TritonCassandraKeyspace> keyspaces = Json.convertAsList(body, TritonCassandraKeyspace.class);
		boolean hasTriton = false;
		for (TritonCassandraKeyspace keyspace : keyspaces) {
			if (keyspace.getName().equals(keyspaceName)) {
				hasTriton = true;
			}
		}
		assertTrue(hasTriton);
	}
	
	@Test
	public void testCreateColumnFamily() throws TritonException {
		// create column family
		CreateColumnFamily create = new CreateColumnFamily();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setColumnFamily("test1");
		create.setKeyValidationClass("UTF8Type");
		create.setComparator("UTF8Type");
		create.setDefaultValidationClass("UTF8Type");
		// do create
		JsonNode result = client.send(create);
		assertTrue(result.asBoolean());
		// get result
		ListColumnFamily list = new ListColumnFamily();
		list.setCluster(clusterName);
		list.setKeyspace(keyspaceName);
		result = client.send(list);
		List<TritonCassandraColumnFamily> families = Json.convertAsList(result, TritonCassandraColumnFamily.class);
		boolean hasTestFamily = false;
		for (TritonCassandraColumnFamily family : families) {
			if (family.getName().equals("test1")) {
				hasTestFamily = true;
				log(family);
			}
		}
		assertTrue(hasTestFamily);
	}
	
	@Test
	public void testSetGet() throws TritonException {
		
		String familyName = "test_getset";
		
		// creating test family
		CreateColumnFamily create = new CreateColumnFamily();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setColumnFamily(familyName);
		create.setKeyValidationClass("UTF8Type");
		create.setComparator("UTF8Type");
		create.setDefaultValidationClass("UTF8Type");
		JsonNode result = client.send(create);
		assertTrue(result.asBoolean());
		
		// set data
		SetColumns set = new SetColumns();
		set.setCluster(clusterName);
		set.setKeyspace(keyspaceName);
		set.setColumnFamily(familyName);
		
		// rows
		Map<String, Map<String, JsonNode>> rows = new HashMap<>();
		Map<String, JsonNode> columns = new HashMap<>();
		columns.put("column1", Json.text("value1"));
		columns.put("column2", Json.text("value2"));
		columns.put("column2_1", Json.text("value2_1"));
		columns.put("column2_2", Json.text("value2_2"));
		columns.put("column3", Json.object().put("name1", "valuechild").put("name2", 1000));
		columns.put("column4", Json.number(100));
		rows.put("row1", columns);
		set.setRows(rows);
		
		assertTrue(client.send(set).asBoolean());
		
		// get row
		GetColumns get = new GetColumns();
		get.setCluster(clusterName);
		get.setKeyspace(keyspaceName);
		get.setColumnFamily(familyName);
		// single key
		get.setKey(Json.text("row1"));
		// single column
		get.setColumns(Json.text("column1"));
		
		// get column
		result = client.send(get);
		assertNotNull(result);
		assertEquals("value1", result.asText());
		
		// multiple column
		get.setColumns(Json.array().add("column1").add("column3").add("column4"));
		result = client.send(get);
		assertEquals(3, result.size());
		assertEquals("value1", result.get("column1").asText());
		assertEquals(2, result.get("column3").size());
		assertEquals("valuechild", result.get("column3").get("name1").asText());
		assertEquals(1000, result.get("column3").get("name2").asInt());
		assertEquals(100, result.get("column4").asInt());
		assertFalse(result.has("value2"));
		
		// range column (start)
		JsonNode start = Json.text("column2");
		ObjectNode range = Json.object();
		range.put("start", start);
		get.setColumns(range);
		result = client.send(get);
		assertEquals(5, result.size());
		assertEquals("column2", result.get(0).get("column").asText());
		assertEquals("column2_1", result.get(1).get("column").asText());
		assertEquals("column2_2", result.get(2).get("column").asText());
		assertEquals("column3", result.get(3).get("column").asText());
		assertEquals("valuechild", result.get(3).get("value").get("name1").asText());
		assertEquals(1000, result.get(3).get("value").get("name2").asInt());
		assertEquals("column4", result.get(4).get("column").asText());
		assertEquals(100, result.get(4).get("value").asInt());
		
		// range column (end)
		range = Json.object();
		JsonNode end = Json.text("column3");
		range.put("end", end);
		get.setColumns(range);
		result = client.send(get);
		assertEquals(5, result.size());
		assertEquals("column1", result.get(0).get("column").asText());
		assertEquals("column2", result.get(1).get("column").asText());
		assertEquals("column2_1", result.get(2).get("column").asText());
		assertEquals("column2_2", result.get(3).get("column").asText());
		assertEquals("column3", result.get(4).get("column").asText());
		assertEquals("valuechild", result.get(4).get("value").get("name1").asText());
		assertEquals(1000, result.get(4).get("value").get("name2").asInt());
		
		// range column (start and end)
		range.put("start", start);
		get.setColumns(range);
		result = client.send(get);
		assertEquals(4, result.size());
		assertEquals("column2", result.get(0).get("column").asText());
		assertEquals("column2_1", result.get(1).get("column").asText());
		assertEquals("column2_2", result.get(2).get("column").asText());
		assertEquals("column3", result.get(3).get("column").asText());
		assertEquals("valuechild", result.get(3).get("value").get("name1").asText());
		assertEquals(1000, result.get(3).get("value").get("name2").asInt());
		
		// exclusive range
		ObjectNode startObj = Json.object();
		JsonNode startValue = Json.text("column2");
		startObj.put("value", startValue);
		JsonNode exclusive = Json.bool(true);
		startObj.put("value", startValue);
		startObj.put("exclusive", exclusive);
		ObjectNode endObj = Json.object();
		JsonNode endValue = Json.text("column4");
		endObj.put("value", endValue);
		endObj.put("exclusive", exclusive);
		range = Json.object();
		range.put("start", startObj);
		range.put("end", endObj);
		get.setColumns(range);
		result = client.send(get);
		assertEquals(3, result.size());
		assertEquals("column2_1", result.get(0).get("column").asText());
		assertEquals("column2_2", result.get(1).get("column").asText());
		assertEquals("column3", result.get(2).get("column").asText());
		assertEquals("valuechild", result.get(2).get("value").get("name1").asText());
		assertEquals(1000, result.get(2).get("value").get("name2").asInt());
		
		// start with
		JsonNode startWith = Json.text("column2");
		range = Json.object();
		range.put("startWith", startWith);
		get.setColumns(range);
		result = client.send(get);
		assertEquals(3, result.size());
		
		// all columns
		get.setColumns(null);
		result = client.send(get);
		assertEquals(6, result.size());
		assertEquals("value1", result.get("column1").asText());
		assertEquals("value2", result.get("column2").asText());
		assertEquals("valuechild", result.get("column3").get("name1").asText());
		assertEquals(1000, result.get("column3").get("name2").asInt());
		assertEquals(100, result.get("column4").asInt());
		
		
		// TODO reverse order
		
		// put multiple rows
		rows.clear();
		get.setColumns(null);
		Map<String, JsonNode> row2 = new HashMap<>();
		row2.put("column1", Json.text("value1"));
		row2.put("column2", Json.text("value2"));
		row2.put("column3", Json.text("value3"));
		
		Map<String, JsonNode> row3 = new HashMap<>();
		row3.put("column1", Json.text("value1"));
		row3.put("column3", Json.text("value3"));
		row3.put("column4", Json.text("value4"));
		
		Map<String, JsonNode> row4 = new HashMap<>();
		row4.put("column1", Json.text("value1"));
		
		Map<String, JsonNode> row5 = new HashMap<>();
		row5.put("column1", Json.text("value1"));
		
		rows.put("row2", row2);
		rows.put("row3", row3);
		rows.put("row4", row4);
		rows.put("row5", row5);
		
		assertTrue(client.send(set).asBoolean());
		
		// multiple key
		get.setKeys(Json.array().add("row2").add("row3").add("row6"));
		result = client.send(get);
		assertEquals(2, result.size());
		assertTrue(result.has("row2"));
		assertTrue(result.has("row3"));
		assertEquals(3, result.get("row2").size());
		assertEquals("value1", result.get("row2").get("column1").asText());
		assertEquals("value2", result.get("row2").get("column2").asText());
		assertEquals("value3", result.get("row2").get("column3").asText());
		assertEquals(3, result.get("row3").size());
		assertEquals("value1", result.get("row3").get("column1").asText());
		assertEquals("value3", result.get("row3").get("column3").asText());
		assertEquals("value4", result.get("row3").get("column4").asText());
		
		// key range ()
		range = Json.object();
		start = Json.text("row3");
		range.put("start", start);
		end = Json.text("row5");
		range.put("end", end);
		get.setKeys(range);
		result = client.send(get);
//		assertEquals(3, result.size()); // result will be changed according to what partitioner you use.
		
	}
	
	@Test
	public void testRemove() throws TritonException {
		
		String familyName = "test_remove";
		
		// creating test family
		CreateColumnFamily create = new CreateColumnFamily();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setColumnFamily(familyName);
		create.setKeyValidationClass("UTF8Type");
		create.setComparator("UTF8Type");
		create.setDefaultValidationClass("UTF8Type");
		assertTrue(client.send(create, Boolean.class));
		
		// rows
		SetColumns set = new SetColumns();
		Map<String, Map<String, JsonNode>> rows = new HashMap<>();
		Map<String, JsonNode> columns = new HashMap<>();
		set.setCluster(clusterName);
		set.setKeyspace(keyspaceName);
		set.setColumnFamily(familyName);
		columns.put("column1", Json.text("value11"));
		columns.put("column2", Json.text("value12"));
		columns.put("column3", Json.text("value13"));
		rows.put("row1", columns);
		columns = new HashMap<>();
		columns.put("column1", Json.text("value21"));
		columns.put("column2", Json.text("value22"));
		columns.put("column3", Json.text("value23"));
		rows.put("row2", columns);
		columns = new HashMap<>();
		columns.put("column1", Json.text("lalue31"));
		columns.put("column2", Json.text("value32"));
		columns.put("column3", Json.text("value33"));
		rows.put("row3", columns);
		set.setRows(rows);
		assertTrue(client.send(set, Boolean.class));
		
		GetColumns get = new GetColumns();
		get.setCluster(clusterName);
		get.setKeyspace(keyspaceName);
		get.setColumnFamily(familyName);
		JsonNode result = client.send(get);
		assertEquals(3, result.size());
		assertEquals("value11", result.get("row1").get("column1").asText());
		assertEquals("value12", result.get("row1").get("column2").asText());
		assertEquals("value13", result.get("row1").get("column3").asText());
		assertEquals(3, result.get("row2").size());
		assertEquals(3, result.get("row3").size());
		
		RemoveColumns remove = new RemoveColumns();
		remove.setCluster(clusterName);
		remove.setKeyspace(keyspaceName);
		remove.setColumnFamily(familyName);
		List<String> keys = new ArrayList<>();
		Map<String, List<String>> removes = new HashMap<>();
		// only single row with columns
		removes.put("row1", Arrays.asList("column1", "column2"));
		remove.setRows(removes);
		remove.setKeys(keys);
		assertTrue(client.send(remove, Boolean.class));
		
		result = client.send(get);
		assertEquals(3, result.size());
		assertEquals(1, result.get("row1").size());
		assertFalse(result.get("row1").has("column1"));
		assertFalse(result.get("row1").has("column2"));
		assertTrue(result.get("row1").has("column3"));
		assertEquals(3, result.get("row2").size());
		assertEquals(3, result.get("row3").size());
		
		remove.setKey("row2");
		removes.clear();
		// remove entire row
		assertTrue(client.send(remove, Boolean.class));
		
		result = client.send(get);
		assertEquals(2, result.size());
		assertFalse(result.has("row2"));
		assertTrue(result.has("row1"));
		assertTrue(result.has("row3"));
		
		// remove multiple row columns
		removes.clear();
		keys.clear();
		removes.put("row1", Arrays.asList("column3"));
		removes.put("row3", Arrays.asList("column2","column3"));
		assertTrue(client.send(remove, Boolean.class));
		
		result = client.send(get);
		assertEquals(1, result.size());
		assertFalse(result.has("row1"));
		assertTrue(result.has("row3"));
		assertEquals(1, result.get("row3").size());
		assertTrue(result.get("row3").has("column1"));
		
		log(result);
	}
	
	@Test
	public void testTruncate() throws TritonException {
		
		String familyName = "test_truncate";
		
		// creating test family
		CreateColumnFamily create = new CreateColumnFamily();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setColumnFamily(familyName);
		create.setKeyValidationClass("UTF8Type");
		create.setComparator("UTF8Type");
		create.setDefaultValidationClass("UTF8Type");
		assertTrue(client.send(create, Boolean.class));
		
		// rows
		SetColumns set = new SetColumns();
		Map<String, Map<String, JsonNode>> rows = new HashMap<>();
		Map<String, JsonNode> columns = new HashMap<>();
		set.setCluster(clusterName);
		set.setKeyspace(keyspaceName);
		set.setColumnFamily(familyName);
		columns.put("column1", Json.text("value11"));
		rows.put("row1", columns);
		columns = new HashMap<>();
		columns.put("column1", Json.text("value21"));
		rows.put("row2", columns);
		columns = new HashMap<>();
		columns.put("column1", Json.text("lalue31"));
		rows.put("row3", columns);
		set.setRows(rows);
		assertTrue(client.send(set, Boolean.class));
		
		GetColumns get = new GetColumns();
		get.setCluster(clusterName);
		get.setKeyspace(keyspaceName);
		get.setColumnFamily(familyName);
		
		JsonNode result = client.send(get);
		assertEquals(3, result.size());
		
		// truncate
		TruncateColumnFamily truncate = new TruncateColumnFamily();
		truncate.setCluster(clusterName);
		truncate.setKeyspace(keyspaceName);
		truncate.setColumnFamily(familyName);
		assertTrue(client.send(truncate, Boolean.class));
		
		result = client.send(get);
		assertEquals(0, result.size());
	}
	
	@Test
	public void testBatch() throws TritonException {
		
		// creating batch test family
		CreateColumnFamily create = new CreateColumnFamily();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setColumnFamily("batch1");
		create.setKeyValidationClass("UTF8Type");
		create.setComparator("UTF8Type");
		create.setDefaultValidationClass("UTF8Type");
		assertTrue(client.send(create, Boolean.class));
		
		// creating second batch test family
		create = new CreateColumnFamily();
		create.setCluster(clusterName);
		create.setKeyspace(keyspaceName);
		create.setColumnFamily("batch2");
		create.setKeyValidationClass("UTF8Type");
		create.setComparator("IntegerType");
		create.setDefaultValidationClass("UTF8Type");
		assertTrue(client.send(create, Boolean.class));
		
		BatchUpdate batch = new BatchUpdate();
		batch.setCluster(clusterName);
		batch.setKeyspace(keyspaceName);
		
		// return false if batch is empty
		assertFalse(client.send(batch, Boolean.class));
		
		TritonCassandraBatchOperation operation = new TritonCassandraBatchOperation();
		operation.setColumnFamily("batch1");
		operation.putUpdate("row1", "column1", Json.text("value11"));
		operation.putUpdate("row1", "column2", Json.text("value12"));
		operation.putUpdate("row2", "column1", Json.text("value21"));
		batch.addOperation(operation);
		
		operation = new TritonCassandraBatchOperation();
		operation.setColumnFamily("batch2");
		operation.putUpdate("row1", "100", Json.text("value100"));
		operation.putUpdate("row2", "200", Json.text("value200"));
		batch.addOperation(operation);
		assertTrue(client.send(batch, Boolean.class));
		
		GetColumns get = new GetColumns();
		get.setCluster(clusterName);
		get.setKeyspace(keyspaceName);
		get.setColumnFamily("batch1");
		
		get.setKey(Json.text("row1"));
		
		JsonNode node = client.send(get);
		assertTrue(node.has("column1"));
		assertTrue(node.has("column2"));
		assertEquals("value11", node.get("column1").asText());
		assertEquals("value12", node.get("column2").asText());
		assertEquals(2, node.size());
		
		get.setKey(Json.text("row2"));
		node = client.send(get);
		assertTrue(node.has("column1"));
		assertEquals("value21", node.get("column1").asText());
		assertEquals(1, node.size());
		
		get.setColumnFamily("batch2");
		get.setKey(Json.text("row1"));
		node = client.send(get);
		assertTrue(node.has("100"));
		assertEquals("value100", node.get("100").asText());
		assertEquals(1, node.size());
		
		get.setKey(Json.text("row2"));
		node = client.send(get);
		assertTrue(node.has("200"));
		assertEquals("value200", node.get("200").asText());
		assertEquals(1, node.size());
		
		// start removing with batch operation
		batch = new BatchUpdate();
		batch.setCluster(clusterName);
		batch.setKeyspace(keyspaceName);
		
		operation = new TritonCassandraBatchOperation();
		operation.setColumnFamily("batch1");
		operation.putRemove("row1", "column1");
		operation.putRemove("row2");
		batch.addOperation(operation);
		
		operation = new TritonCassandraBatchOperation();
		operation.setColumnFamily("batch2");
		operation.putRemove("row1");
		batch.addOperation(operation);
		
		assertTrue(client.send(batch, Boolean.class));
		
		get.setColumnFamily("batch1");
		get.setKey(Json.text("row1"));
		
		node = client.send(get);
		assertEquals(1, node.size());
		
		get.setKey(Json.text("row2"));
		node = client.send(get);
		assertTrue(node.isNull());
		
		get.setColumnFamily("batch2");
		get.setKey(Json.text("row1"));
		node = client.send(get);
		assertTrue(node.isNull());
		
	}
	
	private static final void log(Object ... args) {
		System.out.println(StringUtils.join(args, ' '));
	}
}
