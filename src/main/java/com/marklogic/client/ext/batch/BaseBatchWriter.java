package com.marklogic.client.ext.batch;

import com.marklogic.client.ext.helper.LoggingObject;

public abstract class BaseBatchWriter extends LoggingObject {

	private long startTime;
	private long endTime;

	public void start() {
		startTime = System.currentTimeMillis();
	}

	public void stop() {
		endTime = System.currentTimeMillis();
	}

	public long getDuration() {
		return endTime - startTime;
	}
}
