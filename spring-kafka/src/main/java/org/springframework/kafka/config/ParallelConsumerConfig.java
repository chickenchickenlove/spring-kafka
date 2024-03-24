/*
 * Copyright 2014-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.kafka.config;

import java.util.Objects;

import org.apache.kafka.clients.consumer.Consumer;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;

/**
 * ParallelConsumerConfig is for config of io.confluent.parallelconsumer.
 * This will be registered as Spring Bean when {@link ParallelConsumerConfiguration}'s condition function is true.
 * @author Sanghyeok An
 * @since 3.2.0
 */

public class ParallelConsumerConfig {

	public static final String PARALLEL_CONSUMER_ENABLE = "PARALLEL_CONSUMER_ENABLE";
	public static final String PARALLEL_CONSUMER_MAX_CONCURRENCY = "PARALLEL_CONSUMER_MAX_CONCURRENCY";
	public static final String PARALLEL_CONSUMER_ORDERING = "PARALLEL_CONSUMER_ORDERING";

	private final boolean enableParallelConsumer;
	private final int maxConcurrency;
	private final ProcessingOrder ordering;

	public ParallelConsumerConfig() {
		final Boolean enableParallelConsumer = Boolean.valueOf(System.getenv(PARALLEL_CONSUMER_ENABLE));
		final Integer maxConcurrency = Integer.valueOf(System.getenv(PARALLEL_CONSUMER_MAX_CONCURRENCY));
		final String ordering = System.getenv(PARALLEL_CONSUMER_ORDERING);

		Objects.requireNonNull(enableParallelConsumer);
		Objects.requireNonNull(maxConcurrency);
		Objects.requireNonNull(ordering);

		this.enableParallelConsumer = enableParallelConsumer;
		this.maxConcurrency = maxConcurrency;
		this.ordering = switch (ordering) {
			case "key" -> ProcessingOrder.KEY;
			case "partition" -> ProcessingOrder.PARTITION;
			default -> ProcessingOrder.UNORDERED;
		};
	}

	public boolean isEnable() {
		return this.enableParallelConsumer;
	}

	public <K, V> ParallelConsumerOptions<K, V> toConsumerOptions(Consumer<K, V> consumer) {
		return ParallelConsumerOptions.<K, V>builder()
				.ordering(ordering)
				.maxConcurrency(this.maxConcurrency)
				.consumer(consumer)
				.build();
	}
}
