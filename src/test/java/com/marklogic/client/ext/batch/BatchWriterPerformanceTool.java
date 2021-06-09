package com.marklogic.client.ext.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.document.DocumentWriteOperation;
import com.marklogic.client.impl.DocumentWriteOperationImpl;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.xcc.Content;
import com.marklogic.xcc.ContentCreateOptions;
import com.marklogic.xcc.ContentFactory;
import com.marklogic.xcc.template.XccTemplate;

import java.util.*;

public class BatchWriterPerformanceTool {

	private static final String COLLECTION = "data";
	private static XccTemplate xccTemplate;
	private static final int batches = 100;
	private static final int batchSize = 100;
	private static final int iterations = 2;

	public static void main(String[] args) {
		String host = "localhost";
		int port = 8010;
		int xdbcPort = 8010;
		String database = "data-hub-STAGING";
		String username = "admin";
		String password = "admin";

		host = "rh7v-10-dhf-stress-2";
		port = 8000;
		xdbcPort = 8008;
		database = "Documents";

		xccTemplate = new XccTemplate(host, xdbcPort, username, password, database);

		DatabaseClient client = DatabaseClientFactory.newClient(host, port,
			new DatabaseClientFactory.DigestAuthContext(username, password));

//		DatabaseClient modulesClient = DatabaseClientFactory.newClient("localhost", 8000, "Modules",
//			new DatabaseClientFactory.DigestAuthContext("admin", "admin"));
//		AssetFileLoader fileLoader = new AssetFileLoader(modulesClient);
//		fileLoader.setPermissions("rest-reader,read,rest-extension-user,execute,rest-admin,update");
//		DefaultModulesLoader modulesLoader = new DefaultModulesLoader(fileLoader);
//		modulesLoader.loadModules(modulesClient, new DefaultModulesFinder(), "src/test/resources/bulk-modules");

		Map<String, BatchWriter> batchWriters = new LinkedHashMap<>();

		XccBatchWriter xccWriter = new XccBatchWriter(Arrays.asList(xccTemplate.getContentSource()));
		xccWriter.setThreadCount(16);
		batchWriters.put("XCC", xccWriter);

		RestBatchWriter restWriter = new RestBatchWriter(client, false);
		restWriter.setThreadCount(16);
		restWriter.setContentFormat(Format.JSON);
		//batchWriters.put("REST", restWriter);

		DataMovementBatchWriter dmsdkWriter = new DataMovementBatchWriter(client);
		dmsdkWriter.setThreadCount(16);
		batchWriters.put("DMSDK", dmsdkWriter);

		BulkBatchWriter bulkWriter = new BulkBatchWriter(client);
//		batchWriters.put("BULK", bulkWriter);

		for (int i = 0; i < iterations; i++) {
			batchWriters.forEach((label, writer) -> testWriter(label, writer));
		}
	}

	private static void deleteData() {
		xccTemplate.executeAdhocQuery(String.format("xdmp:collection-delete('%s')", COLLECTION));
	}

	private static void testWriter(String label, BatchWriter writer) {
		deleteData();

		writer.initialize();

		DocumentMetadataHandle metadata = new DocumentMetadataHandle()
			.withCollections(COLLECTION)
			.withPermission("rest-reader", DocumentMetadataHandle.Capability.READ, DocumentMetadataHandle.Capability.UPDATE);

		ObjectMapper mapper = new ObjectMapper();

		// To avoid the overhead of converting a REST DocumentWriteOperation object to an XCC Content object, we have
		// a different code path where we create Content objects
		if (writer instanceof XccBatchWriter) {
			List<Content[]> documents = new ArrayList<>();
			for (int i = 0; i < batches; i++) {
				Content[] docs = new Content[batchSize];
				for (int j = 0; j < batchSize; j++) {
					String uuid = UUID.randomUUID().toString();
					ObjectNode objectNode = mapper.createObjectNode().put("hello", uuid);
					ContentCreateOptions options = new ContentCreateOptions();
					options.setFormatJson();
					options.setCollections(new String[]{COLLECTION});
					docs[j] = ContentFactory.newContent(uuid + ".json", objectNode.toString(), options);
				}
				documents.add(docs);
			}

			XccBatchWriter xccWriter = (XccBatchWriter)writer;
			xccWriter.start();
			documents.forEach(batch -> xccWriter.writeContent(batch));
			writer.waitForCompletion();
			System.out.println(label + "; time: " + ((BaseBatchWriter) writer).getDuration());
		}

		else {
			List<List<DocumentWriteOperation>> documents = new ArrayList();
			for (int i = 0; i < batches; i++) {
				List<DocumentWriteOperation> docs = new ArrayList<>();
				for (int j = 0; j < batchSize; j++) {
					String uuid = UUID.randomUUID().toString();
					JacksonHandle handle = new JacksonHandle(mapper.createObjectNode().put("hello", uuid));
					docs.add(new DocumentWriteOperationImpl(DocumentWriteOperation.OperationType.DOCUMENT_WRITE,
						uuid + ".json", metadata, handle));
				}
				documents.add(docs);
			}

			((BaseBatchWriter) writer).start();
			documents.forEach(batch -> writer.write(batch));
			writer.waitForCompletion();
			System.out.println(label + "; time: " + ((BaseBatchWriter) writer).getDuration());
		}
	}
}
