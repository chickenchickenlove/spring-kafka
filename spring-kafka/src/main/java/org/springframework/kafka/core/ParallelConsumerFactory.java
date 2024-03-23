package org.springframework.kafka.core;

import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ParallelConsumerContext;

import io.confluent.parallelconsumer.ParallelStreamProcessor;

public class ParallelConsumerFactory<K, V> implements SmartLifecycle {

	public static final String BEAN_NAME = "parallelConsumerFactory";
	private final ParallelConsumerContext<K, V> parallelConsumerContext;
	private final DefaultKafkaConsumerFactory<K, V> defaultKafkaConsumerFactory;
	private boolean running;

	public ParallelConsumerFactory(ParallelConsumerContext<K, V> parallelConsumerContext,
								   DefaultKafkaConsumerFactory<K, V> defaultKafkaConsumerFactory) {
		this.parallelConsumerContext = parallelConsumerContext;
		this.defaultKafkaConsumerFactory = defaultKafkaConsumerFactory;
	}


	@Override
	public void start() {
		final Consumer<K, V> consumer = defaultKafkaConsumerFactory.createConsumer();
		final ParallelStreamProcessor<K, V> parallelConsumer = parallelConsumerContext.createConsumer(consumer);
		parallelConsumer.subscribe(parallelConsumerContext.parallelConsumerCallback().getTopics());
		parallelConsumer.poll(recordContexts -> parallelConsumerContext.parallelConsumerCallback());
		this.running = true;
	}

	@Override
	public void stop() {
		this.parallelConsumerContext.stopParallelConsumer();
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}
}
