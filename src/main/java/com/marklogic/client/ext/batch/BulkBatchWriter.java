package com.marklogic.client.ext.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.dataservices.IOEndpoint;
import com.marklogic.client.dataservices.InputCaller;
import com.marklogic.client.document.DocumentWriteOperation;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.StringHandle;

import java.util.List;

public class BulkBatchWriter extends BaseBatchWriter implements BatchWriter {

	private final static String API = "{\n" +
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

	private DatabaseClient client;
	private InputCaller.BulkInputCaller<String> bulkInputCaller;
	private ObjectMapper objectMapper;

	public BulkBatchWriter(DatabaseClient client) {
		this.client = client;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public void initialize() {
		try {
			InputCaller<String> inputCaller = InputCaller.on(
				client,
				new JacksonHandle(objectMapper.readTree(API)),
				new StringHandle().withFormat(Format.JSON)
			);

			ObjectNode endpointConstants = objectMapper.createObjectNode();

			int threadCount = 16;
			IOEndpoint.CallContext[] callContexts = new IOEndpoint.CallContext[threadCount];
			for (int i = 0; i < threadCount; i++) {
				callContexts[i] = inputCaller.newCallContext();
				callContexts[i].withEndpointConstants(new JacksonHandle(endpointConstants));
			}
			this.bulkInputCaller = inputCaller.bulkCaller(callContexts, threadCount);
//			this.bulkInputCaller.setErrorListener(new InputCaller.BulkInputCaller.ErrorListener() {
//				@Override
//				public IOEndpoint.BulkIOEndpointCaller.ErrorDisposition processError(int retryCount, Throwable throwable, IOEndpoint.CallContext callContext, BufferableHandle[] input) {
//					System.out.println("Error: " + throwable.getMessage());
//					return IOEndpoint.BulkIOEndpointCaller.ErrorDisposition.STOP_ALL_CALLS;
//				}
//			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * So this is where we need to "unpack" each write op into two structures - a JSON metadata, and then the actual
	 * thing to be written. We'll just hack JSON for now, but really, we need to support either XML or JSON.
	 *
	 * @param items
	 */
	@Override
	public void write(List<? extends DocumentWriteOperation> items) {
		for (DocumentWriteOperation op : items) {
			DocumentMetadataHandle metadata = (DocumentMetadataHandle) op.getMetadata();
			String metadataXml = metadata.toString();
			String uri = op.getUri();

			// TODO Hardcoded for JSON
			JsonNode content = ((JacksonHandle) op.getContent()).get();

			ObjectNode node = objectMapper.createObjectNode();
			node.put("uri", uri);
			node.put("metadataXml", metadataXml);
			node.set("content", content);
			//bulkInputCaller.acceptAll(new String[]{uri, metadataXml, content});
			//bulkInputCaller.acceptAll(new String[]{uri, content});
			bulkInputCaller.accept(node.toString());
			//bulkInputCaller.accept(content);
		}
	}

	@Override
	public void waitForCompletion() {
		this.bulkInputCaller.awaitCompletion();
		super.stop();
	}
}
