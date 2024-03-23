package org.springframework.kafka.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.ParallelConsumerCallback;
import org.springframework.kafka.core.ParallelConsumerFactory;

import java.util.Objects;

@Configuration
public class ParallelConsumerConfiguration<K, V> {

	@Autowired(required = false)
	ParallelConsumerCallback<K, V> parallelConsumerCallback;


	static class EnableParallelConsumerCondition implements Condition {
		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String isEnable = context.getEnvironment().getProperty(ParallelConsumerConfig.PARALLEL_CONSUMER_ENABLE);
			if (Objects.isNull(isEnable)) {
				return false;
			}
			return "true".equals(isEnable.toLowerCase());
		}
	}

	@Bean(name = ParallelConsumerContext.BEAN_NAME)
	@Conditional(EnableParallelConsumerCondition.class)
	public ParallelConsumerContext<K,V> parallelConsumerContext() {
		return new ParallelConsumerContext(this.parallelConsumerCallback);
	}

	@Bean(name = ParallelConsumerFactory.BEAN_NAME)
	@Conditional(EnableParallelConsumerCondition.class)
	public ParallelConsumerFactory<K,V> parallelConsumerFactory(DefaultKafkaConsumerFactory consumerFactory) {
		return new ParallelConsumerFactory(parallelConsumerContext(),
				consumerFactory);
	}

}
