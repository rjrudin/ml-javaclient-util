package com.marklogic.client.ext.batch;

import com.marklogic.client.document.DocumentWriteOperation;
import com.marklogic.client.ext.xcc.DefaultDocumentWriteOperationAdapter;
import com.marklogic.client.ext.xcc.DocumentWriteOperationAdapter;
import com.marklogic.xcc.Content;
import com.marklogic.xcc.ContentSource;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XCC implementation for batched writes. Most important thing here is we depend on an instance of
 * DocumentWriteOperationAdapter to adapt a DocumentWriteOperation instance into a Content instance.
 */
public class XccBatchWriter extends BatchWriterSupport {

	private List<ContentSource> contentSources;
	private int contentSourceIndex = 0;
	private DocumentWriteOperationAdapter documentWriteOperationAdapter;
	private Map<Integer, Session> sessionMap;

	public XccBatchWriter(List<ContentSource> contentSources) {
		this.contentSources = contentSources;
		this.documentWriteOperationAdapter = new DefaultDocumentWriteOperationAdapter();
	}

	@Override
	public void initialize() {
		super.initialize();

		sessionMap = new HashMap<>();
		for (int i = 0; i < contentSources.size(); i++) {
			sessionMap.put(i, contentSources.get(i).newSession());
		}
	}

	@Override
	public void write(final List<? extends DocumentWriteOperation> items) {
		Runnable runnable = buildRunnable(determineSessionToUse(), items);
		executeRunnable(runnable, items);
	}

	public void writeContent(Content[] items) {
		// TODO Not passing items, which means failures can't be reported
		executeRunnable(buildContentRunnable(contentSources.get(0).newSession(), items), null);
		//executeRunnable(buildContentRunnable(determineSessionToUse(), items), null);
	}

	@Override
	public void waitForCompletion() {
		super.waitForCompletion();

		sessionMap.forEach((index, session) -> {
			try {
				session.close();
			} catch (Exception e) {
				logger.warn("Unable to close XCC session; cause: " + e.getMessage());
			}
		});
	}

	protected Session determineSessionToUse() {
		if (sessionMap.size() == 1) {
			return sessionMap.get(0);
		}

		if (contentSourceIndex >= contentSources.size()) {
			contentSourceIndex = 0;
		}
		Session session = sessionMap.get(contentSourceIndex);
		contentSourceIndex++;
		return session;
	}

	protected Runnable buildContentRunnable(final Session session, final Content[] items) {
		return () -> {
			int count = items.length;
			if (logger.isDebugEnabled()) {
				logger.debug("Writing " + count + " documents to MarkLogic");
			}
			try {
				session.insertContent(items);
				if (logger.isInfoEnabled()) {
					logger.info("Wrote " + count + " documents to MarkLogic");
				}
			} catch (RequestException e) {
				throw new RuntimeException("Unable to insert content: " + e.getMessage(), e);
			} finally {
				session.close();
			}
		};
	}

	protected Runnable buildRunnable(final Session session, final List<? extends DocumentWriteOperation> items) {
		return () -> {
			int count = items.size();
			Content[] array = new Content[count];
			for (int i = 0; i < count; i++) {
				array[i] = documentWriteOperationAdapter.adapt(items.get(i));
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Writing " + count + " documents to MarkLogic");
			}
			try {
				session.insertContent(array);
				if (logger.isInfoEnabled()) {
					logger.info("Wrote " + count + " documents to MarkLogic");
				}
			} catch (RequestException e) {
				throw new RuntimeException("Unable to insert content: " + e.getMessage(), e);
			}
		};
	}

	public void setDocumentWriteOperationAdapter(DocumentWriteOperationAdapter documentWriteOperationAdapter) {
		this.documentWriteOperationAdapter = documentWriteOperationAdapter;
	}
}
