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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ParallelConsumerCallback;
import org.springframework.kafka.core.ParallelConsumerFactory;

/**
 * If User decide to use parallelConsumer on SpringKafka, User should import this class to their ComponentScan scopes.
 * If so, this class will register both {@link ParallelConsumerContext} and {@link ParallelConsumerFactory} as Spring Bean.
 * User has responsibility to include this file to their componentScan scopes, to register ConcreteClass of {@link ParallelConsumerCallback}.
 * @author Sanghyeok An
 * @since 3.2.0
 */

public class ParallelConsumerConfiguration<K, V> {

	@Bean(name = ParallelConsumerContext.DEFAULT_BEAN_NAME)
	public ParallelConsumerContext<K,V> parallelConsumerContext(ParallelConsumerCallback<K, V> parallelConsumerCallback) {
		return new ParallelConsumerContext(parallelConsumerCallback);
	}

	@Bean(name = ParallelConsumerFactory.DEFAULT_BEAN_NAME)
	public ParallelConsumerFactory<K,V> parallelConsumerFactory(DefaultKafkaConsumerFactory<K,V> consumerFactory,
																ParallelConsumerContext<K,V> parallelConsumerContext) {
		return new ParallelConsumerFactory(parallelConsumerContext, consumerFactory);
	}
}
