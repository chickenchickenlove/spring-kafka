package org.springframework.kafka.core;

import java.util.List;

import io.confluent.parallelconsumer.PollContext;

public interface ParallelConsumerCallback<K,V> {
	void accept(PollContext<K, V> context);
	List<String> getTopics();
}

