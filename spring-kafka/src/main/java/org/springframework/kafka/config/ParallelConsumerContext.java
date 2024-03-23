package org.springframework.kafka.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.kafka.core.ParallelConsumerCallback;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;

public class ParallelConsumerContext<K,V> {

	public static final String BEAN_NAME = "parallelConsumerContext";
	private final ParallelConsumerConfig parallelConsumerConfig;
	private final ParallelConsumerCallback<K,V> parallelConsumerCallback;
	private ParallelStreamProcessor<K, V> processor;

	public ParallelConsumerContext(ParallelConsumerCallback<K, V> callback) {
		this.parallelConsumerConfig = new ParallelConsumerConfig();
		this.parallelConsumerCallback = callback;
	}

	public boolean isEnable() {
		return this.parallelConsumerConfig.isEnable();
	}

	public ParallelConsumerCallback<K,V> parallelConsumerCallback() {
		return this.parallelConsumerCallback;
	}


	public ParallelStreamProcessor<K, V> createConsumer(Consumer<K,V> consumer) {
		final ParallelConsumerOptions<K, V> options =
				parallelConsumerConfig.toConsumerOptions(consumer);

		this.processor = ParallelStreamProcessor.createEosStreamProcessor(options);
		return this.processor;
	}

	public void stopParallelConsumer() {
		this.processor.close();
	}

}
