package com.marklogic.client.ext.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.WriteBatcher;
import com.marklogic.client.dataservices.IOEndpoint;
import com.marklogic.client.dataservices.InputCaller;
import com.marklogic.client.document.DocumentWriteOperation;
import com.marklogic.client.impl.DocumentWriteOperationImpl;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.xcc.*;
import com.marklogic.xcc.template.XccTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class XccTester {

	private static final String COLLECTION = "data";
	private static XccTemplate xccTemplate;
	private static DatabaseClient databaseClient;
	private static final int batches = 100;
	private static final int batchSize = 100;
	private static final int iterations = 1;
	private static final int threadCount = 32;

	/**
	 * Localhost:
	 * XCC Time: 8775
	 * DMSDK Time: 4610
	 * XCC Time: 6272
	 * DMSDK Time: 3610
	 * XCC Time: 6259
	 * DMSDK Time: 3803
	 * <p>
	 * Remote:
	 * XCC Time: 52064
	 * DMSDK Time: 5226
	 * XCC Time: 53555
	 * DMSDK Time: 4460
	 * XCC Time: 52924
	 * DMSDK Time: 4530
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		String host = "localhost";
		int port = 8010;
		int xdbcPort = 8010;
		String username = "admin";
		String password = "admin";

//		host = "rh7v-10-dhf-stress-2";
//		port = 8000;
//		xdbcPort = 8008;

		xccTemplate = new XccTemplate(host, xdbcPort, username, password, null);
		databaseClient = DatabaseClientFactory.newClient(host, port, new DatabaseClientFactory.DigestAuthContext(username, password));

		for (int i = 1; i <= iterations; i++) {
			testXcc();
			testWriteBatcher();
			testBulkCaller();
		}
	}

	private static void deleteData() {
		xccTemplate.executeAdhocQuery(String.format("xdmp:collection-delete('%s')", COLLECTION));
	}

	private static void testWriteBatcher() {
		deleteData();

		DataMovementManager dataMovementManager = databaseClient.newDataMovementManager();
		WriteBatcher writeBatcher = dataMovementManager.newWriteBatcher()
			.withThreadCount(threadCount)
			.withBatchSize(batchSize);

		List<List<DocumentWriteOperation>> documents = buildDocuments();
		long start = System.currentTimeMillis();
		documents.forEach(docs -> {
			writeBatcher.addAll(docs.stream());
		});
		writeBatcher.flushAndWait();
		System.out.println("DMSDK Time: " + (System.currentTimeMillis() - start));
		dataMovementManager.stopJob(writeBatcher);
	}

	private static void testBulkCaller() {
		deleteData();

		String API = "{\n" +
			"    \"endpoint\": \"/data-hub/5/data-services/bulk/writeDocs.sjs\",\n" +
			"    \"functionName\": \"writeDocs\",\n" +
			"    \"params\": [\n" +
			"{\n" +
			"            \"name\": \"endpointConstants\",\n" +
			"            \"datatype\": \"jsonDocument\",\n" +
			"            \"multiple\": false,\n" +
			"            \"nullable\": true\n" +
			"        }," +
			"        {\n" +
			"            \"name\": \"input\",\n" +
			"            \"datatype\": \"jsonDocument\",\n" +
			"            \"multiple\": true,\n" +
			"            \"nullable\": true\n" +
			"        }\n" +
			"    ],\n" +
			"    \"$bulk\": {\n" +
			"        \"inputBatchSize\": 100\n" +
			"    }\n" +
			"}";

		InputCaller<String> inputCaller;
		ObjectMapper objectMapper = new ObjectMapper();

		try {
			inputCaller = InputCaller.on(
				databaseClient,
				new JacksonHandle(new ObjectMapper().readTree(API)),
				new StringHandle().withFormat(Format.JSON)
			);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}

		ObjectNode endpointConstants = objectMapper.createObjectNode();
		IOEndpoint.CallContext[] callContexts = new IOEndpoint.CallContext[threadCount];
		for (int i = 0; i < threadCount; i++) {
			callContexts[i] = inputCaller.newCallContext();
			callContexts[i].withEndpointConstants(new JacksonHandle(endpointConstants));
		}
		InputCaller.BulkInputCaller<String> bulkInputCaller = inputCaller.bulkCaller(callContexts, threadCount);

		List<List<DocumentWriteOperation>> documents = buildDocuments();
		long start = System.currentTimeMillis();
		documents.forEach(docs -> {
			for (DocumentWriteOperation op : docs) {
				// TODO Hardcoded for JSON
				JsonNode content = ((JacksonHandle) op.getContent()).get();
				bulkInputCaller.accept(content.toString());
			}
		});
		bulkInputCaller.awaitCompletion();
		System.out.println("Bulk Time: " + (System.currentTimeMillis() - start));
	}

	private static List<List<DocumentWriteOperation>> buildDocuments() {
		DocumentMetadataHandle metadata = new DocumentMetadataHandle()
			.withCollections(COLLECTION)
			.withPermission("rest-reader", DocumentMetadataHandle.Capability.READ, DocumentMetadataHandle.Capability.UPDATE);

		ObjectMapper objectMapper = new ObjectMapper();
		List<List<DocumentWriteOperation>> documents = new ArrayList<>();
		for (int i = 0; i < batches; i++) {
			List<DocumentWriteOperation> docs = new ArrayList<>();
			for (int j = 0; j < batchSize; j++) {
				ObjectNode content = objectMapper.createObjectNode().put("hello", "world");
				docs.add(new DocumentWriteOperationImpl(DocumentWriteOperation.OperationType.DOCUMENT_WRITE,
					UUID.randomUUID().toString() + ".json", metadata, new JacksonHandle(content)));
			}
			documents.add(docs);
		}
		return documents;
	}

	private static void testXcc() {
		deleteData();
		ContentSource contentSource = xccTemplate.getContentSource();

		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(threadCount);
		taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
		taskExecutor.setAwaitTerminationSeconds(60 * 60);
		taskExecutor.afterPropertiesSet();

		List<Content[]> documents = new ArrayList<>();
		ContentCreateOptions options = new ContentCreateOptions();
		options.setFormatXml();
		options.setCollections(new String[]{COLLECTION});
		for (int i = 0; i < batches; i++) {
			Content[] docs = new Content[batchSize];
			for (int j = 0; j < batchSize; j++) {
				String uuid = UUID.randomUUID().toString();
				docs[j] = ContentFactory.newContent(uuid + ".xml", "<hello>world</hello>", options);
			}
			documents.add(docs);
		}

		long start = System.currentTimeMillis();
		documents.forEach(contentArray -> {
			taskExecutor.execute(() -> {
				// XCC docs state that Session is not thread-safe and also not to bother with pooling Session objects
				// because they are so cheap to create - https://docs.marklogic.com/guide/xcc/concepts#id_55196
				Session session = contentSource.newSession();
				try {
					session.insertContent(contentArray);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				} finally {
					session.close();
				}
			});
		});
		taskExecutor.shutdown();
		System.out.println("XCC Time: " + (System.currentTimeMillis() - start));
	}
}
